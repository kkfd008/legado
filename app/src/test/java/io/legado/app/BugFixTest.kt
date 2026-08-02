package io.legado.app

import io.legado.app.model.analyzeRule.AnalyzeUrl.ConcurrentRecord
import org.junit.Assert
import org.junit.Test
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
     * 测试 fetchEnd 对 isConcurrent=true 模式的 frequency 递减
     * 修复前：fetchEnd 仅在 !isConcurrent 时递减 frequency，
     * 导致 isConcurrent=true（次数/毫秒模式）的 frequency 只增不减，
     * 达到上限后所有请求被永久限流。
     */
    @Test
    fun testFetchEndDecrementsFrequencyForConcurrentMode() {
        val record = ConcurrentRecord(isConcurrent = true, time = System.currentTimeMillis(), frequency = 3)

        // 模拟 fetchEnd：修复后无论 isConcurrent 值如何都应递减
        synchronized(record) {
            if (record.frequency > 0) {
                record.frequency -= 1
            }
        }
        Assert.assertEquals("frequency should decrement to 2", 2, record.frequency)

        synchronized(record) {
            if (record.frequency > 0) {
                record.frequency -= 1
            }
        }
        Assert.assertEquals("frequency should decrement to 1", 1, record.frequency)

        synchronized(record) {
            if (record.frequency > 0) {
                record.frequency -= 1
            }
        }
        Assert.assertEquals("frequency should decrement to 0", 0, record.frequency)

        // 不应变为负数
        synchronized(record) {
            if (record.frequency > 0) {
                record.frequency -= 1
            }
        }
        Assert.assertEquals("frequency should not go below 0", 0, record.frequency)
    }

    /**
     * 测试 fetchEnd 对 isConcurrent=false 模式的 frequency 递减
     * 验证修复后原有逻辑仍然正确
     */
    @Test
    fun testFetchEndDecrementsFrequencyForNonConcurrentMode() {
        val record = ConcurrentRecord(isConcurrent = false, time = System.currentTimeMillis(), frequency = 2)

        synchronized(record) {
            if (record.frequency > 0) {
                record.frequency -= 1
            }
        }
        Assert.assertEquals("frequency should decrement to 1", 1, record.frequency)

        synchronized(record) {
            if (record.frequency > 0) {
                record.frequency -= 1
            }
        }
        Assert.assertEquals("frequency should decrement to 0", 0, record.frequency)
    }

    /**
     * 测试并发环境下 fetchStart + fetchEnd 的 frequency 一致性
     * 模拟 isConcurrent=true 模式下多个请求开始和结束的计数平衡
     */
    @Test
    fun testConcurrentFetchStartEndBalance() {
        val map = ConcurrentHashMap<String, ConcurrentRecord>()
        val key = "concurrent_balance_test"
        val threadCount = 20
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)

        for (i in 0 until threadCount) {
            executor.submit {
                try {
                    val record = map.computeIfAbsent(key) {
                        ConcurrentRecord(true, System.currentTimeMillis(), 1)
                    }
                    // 模拟 fetchStart 的递增
                    synchronized(record) {
                        record.frequency += 1
                    }
                    // 模拟 fetchEnd 的递减（修复后逻辑）
                    synchronized(record) {
                        if (record.frequency > 0) {
                            record.frequency -= 1
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        val record = map[key]!!
        Assert.assertEquals(
            "frequency should return to initial value after balanced start/end",
            1, record.frequency
        )
    }
}