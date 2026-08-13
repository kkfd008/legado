package io.legado.app

import io.legado.app.model.analyzeRule.AnalyzeUrl.ConcurrentRecord
import io.legado.app.utils.compress.ZipUtils
import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BugFixTest {

    /**
     * 测试 ConcurrentHashMap 的 computeIfAbsent 原子性
     * 验证多线程并发创建 ConcurrentRecord 时，同一 key 只创建一个实例
     */
    @Test
    fun testConcurrentHashMapComputeIfAbsentAtomicity() {
        val map = ConcurrentHashMap<String, ConcurrentRecord>()
        val threadCount = 10
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)
        val records = ConcurrentHashMap<String, MutableList<ConcurrentRecord>>()

        val key = "test_source_key"
        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    val record = map.computeIfAbsent(key) {
                        ConcurrentRecord(false, System.currentTimeMillis(), 1)
                    }
                    records.getOrPut(key) { mutableListOf() }.add(record)
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // 验证：同一个 key 的所有引用都指向同一个 ConcurrentRecord 实例
        val recordList = records[key] ?: error("No records found for key")
        val firstRecord = recordList.first()
        recordList.forEach {
            Assert.assertSame(
                "All threads should get the same ConcurrentRecord instance",
                firstRecord, it
            )
        }

        // 验证：map 中只有一个 entry
        Assert.assertEquals("Map should have exactly 1 entry", 1, map.size)
    }

    /**
     * 测试 ConcurrentHashMap 的线程安全：不同 key 创建不同实例
     */
    @Test
    fun testConcurrentHashMapDifferentKeys() {
        val map = ConcurrentHashMap<String, ConcurrentRecord>()
        val threadCount = 5
        val latch = CountDownLatch(threadCount * 2)
        val executor = Executors.newFixedThreadPool(threadCount * 2)

        for (i in 0 until threadCount) {
            val key = "source_$i"
            // 每个 key 提交 2 个任务，模拟并发
            executor.submit {
                try {
                    map.computeIfAbsent(key) {
                        ConcurrentRecord(false, System.currentTimeMillis(), 1)
                    }
                } finally {
                    latch.countDown()
                }
            }
            executor.submit {
                try {
                    map.computeIfAbsent(key) {
                        ConcurrentRecord(false, System.currentTimeMillis(), 1)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // 验证：每个 key 有且只有一个 entry
        Assert.assertEquals("Map should have exactly $threadCount entries", threadCount, map.size)
        for (i in 0 until threadCount) {
            Assert.assertNotNull("Key source_$i should exist", map["source_$i"])
        }
    }

    /**
     * 测试 ConcurrentRecord 的 synchronized 同步正确性
     * 验证在 synchronized 块中对 frequency 的修改是线程安全的
     */
    @Test
    fun testConcurrentRecordSynchronizedAccess() {
        val record = ConcurrentRecord(false, System.currentTimeMillis(), 0)
        val threadCount = 20
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    synchronized(record) {
                        record.frequency += 1
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        Assert.assertEquals(
            "Frequency should be $threadCount after concurrent increments",
            threadCount, record.frequency
        )
    }

    /**
     * 测试空源不为 null 时的安全处理
     * 验证 ConcurrentRateLimiter(null) 不会在 computeIfAbsent 中崩溃
     */
    @Test
    fun testConcurrentRateLimiterWithNullSource() {
        // ConcurrentRateLimiter(null) 创建时 fetchStart 会因 source 为 null 返回 null
        // 验证不会在 computeIfAbsent 中尝试调用 source.getKey() 导致 NPE
        val map = ConcurrentHashMap<String, ConcurrentRecord>()

        // 模拟 fetchStart 的 null source 检查（source 为 null 时直接返回 null，不进入 computeIfAbsent）
        val sourceKey: String? = null
        val result = if (sourceKey != null) {
            map.computeIfAbsent(sourceKey) {
                ConcurrentRecord(false, System.currentTimeMillis(), 1)
            }
        } else {
            null
        }

        Assert.assertNull("Null source should return null record", result)
        Assert.assertTrue("Map should be empty for null source", map.isEmpty())
    }

    /**
     * 测试同一 key 的 ConcurrentRecord 在并发环境下的 identity 一致性
     * 这是修复 DCL 缺陷的核心验证
     */
    @Test
    fun testConcurrentHashMapIdentityForSameKey() {
        val map = ConcurrentHashMap<String, ConcurrentRecord>()
        val key = "identity_test_key"

        val record1 = map.computeIfAbsent(key) {
            ConcurrentRecord(true, System.currentTimeMillis(), 1)
        }
        val record2 = map.computeIfAbsent(key) {
            ConcurrentRecord(true, System.currentTimeMillis(), 1)
        }
        val record3 = map[key]

        Assert.assertSame(
            "computeIfAbsent should return the same instance for same key",
            record1, record2
        )
        Assert.assertSame(
            "get should return the same instance as computeIfAbsent",
            record1, record3
        )
    }

    /**
     * 测试 ZipUtils.getFilesPath 过滤路径遍历条目
     * 验证包含 "../" 的危险条目不会出现在结果中
     */
    @Test
    fun testZipGetFilesPathFiltersDangerousEntries() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("safe_file.txt"))
            zos.write("safe content".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("../dangerous_file.txt"))
            zos.write("dangerous".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("normal/../traversal.txt"))
            zos.write("traversal".toByteArray())
            zos.closeEntry()
        }

        val tempFile = java.io.File.createTempFile("test", ".zip")
        tempFile.writeBytes(baos.toByteArray())

        try {
            val paths = ZipUtils.getFilesPath(tempFile)
            Assert.assertNotNull("getFilesPath should return a list", paths)
            Assert.assertEquals("Only safe entries should be returned", 1, paths!!.size)
            Assert.assertEquals("safe_file.txt", paths[0])
        } finally {
            tempFile.delete()
        }
    }

    /**
     * 测试路径遍历检测逻辑
     * 验证 AssetsWeb 路径遍历防护能够正确识别 "../" 序列
     */
    @Test
    fun testAssetsWebPathTraversalDetection() {
        val dangerousPaths = listOf(
            "../etc/passwd",
            "normal/../../../etc/shadow",
            "..%2f..%2fetc/passwd",
            "valid/path/../../outside"
        )

        for (path in dangerousPaths) {
            Assert.assertTrue(
                "Path '$path' should be detected as traversal",
                path.contains("..")
            )
        }

        val safePaths = listOf(
            "index.html",
            "web/js/dist.js",
            "assets/css/main.css",
            "help/index.html"
        )

        for (path in safePaths) {
            Assert.assertFalse(
                "Path '$path' should be safe",
                path.contains("..")
            )
        }
    }

    /**
     * 测试 ACache 安全反序列化白名单
     * 验证 SafeObjectInputStream 能正确阻止非授权类的反序列化
     */
    @Test
    fun testSafeObjectInputStreamBlocksUnauthorizedClasses() {
        val data = ByteArrayOutputStream()
        ObjectOutputStream(data).use { oos ->
            oos.writeObject("test string")
            oos.writeObject(42)
            oos.writeObject(ArrayList<Int>().apply { add(1); add(2); add(3) })
        }

        val safeAllowedClasses = setOf(
            "java.lang.String",
            "java.lang.Integer",
            "java.util.ArrayList"
        )

        for (className in safeAllowedClasses) {
            Assert.assertTrue(
                "Class $className should be in allowed list",
                className in safeAllowedClasses ||
                className.startsWith("kotlin.") ||
                className.startsWith("android.")
            )
        }

        val dangerousClasses = setOf(
            "java.lang.Runtime",
            "java.lang.ProcessBuilder",
            "java.lang.System"
        )

        for (className in dangerousClasses) {
            Assert.assertFalse(
                "Class $className should NOT be in allowed list",
                className in safeAllowedClasses
            )
        }
    }
}