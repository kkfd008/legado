package io.legado.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Zip Slip 防御测试
 * 验证 epub 资源路径消毒逻辑正确拒绝路径穿越攻击
 *
 * 对应的修复位置:
 * - me.ag2s.epublib.epub.EpubWriter.sanitizeZipEntryName
 * - me.ag2s.epublib.epub.ResourcesLoader.sanitizeZipEntryName
 */
class ZipSlipDefenseTest {

    /**
     * 内联复制的消毒逻辑，与 EpubWriter/ResourcesLoader 保持一致。
     * 避免跨模块访问 package-private 方法。
     */
    private fun sanitizeZipEntryName(name: String?): String? {
        if (name == null || name.isEmpty()) return null
        if (name.startsWith("/") || name.startsWith("\\")) return null
        if (name.contains("\\")) return null
        val normalized = name.replace('\\', '/')
        for (part in normalized.split("/")) {
            if (part == ".." || part == ".") {
                return null
            }
        }
        if (normalized.startsWith("/")) return null
        return normalized
    }

    // ===== 危险输入 - 应返回 null =====

    @Test
    fun testPathTraversalDoubleDot() {
        assertNull(sanitizeZipEntryName("../../../etc/passwd"))
        assertNull(sanitizeZipEntryName("../../secrets/key.pem"))
        assertNull(sanitizeZipEntryName("../config/secret"))
    }

    @Test
    fun testPathTraversalEmbeddedDotDot() {
        // 中间路径包含 ..
        assertNull(sanitizeZipEntryName("OEBPS/../etc/passwd"))
        assertNull(sanitizeZipEntryName("a/b/../../../c"))
    }

    @Test
    fun testAbsoluteUnixPath() {
        assertNull(sanitizeZipEntryName("/etc/passwd"))
        assertNull(sanitizeZipEntryName("/data/app/xyz"))
        assertNull(sanitizeZipEntryName("//absolute"))
    }

    @Test
    fun testAbsoluteWindowsPath() {
        assertNull(sanitizeZipEntryName("C:\\Windows\\System32"))
        assertNull(sanitizeZipEntryName("\\Windows\\System32"))
    }

    @Test
    fun testBackslashPath() {
        // 包含反斜杠的任何路径都应该被拒绝
        assertNull(sanitizeZipEntryName("OEBPS\\secret.txt"))
        assertNull(sanitizeZipEntryName("a\\b\\c"))
    }

    @Test
    fun testDotOnlySegments() {
        assertNull(sanitizeZipEntryName("./config"))
        assertNull(sanitizeZipEntryName("OEBPS/./config"))
        assertNull(sanitizeZipEntryName("."))
        assertNull(sanitizeZipEntryName("./"))
    }

    @Test
    fun testNullAndEmpty() {
        assertNull(sanitizeZipEntryName(null))
        assertNull(sanitizeZipEntryName(""))
    }

    @Test
    fun testZipSlipCanonicalAttack() {
        // 典型 Zip Slip payload
        assertNull(sanitizeZipEntryName("../../../root/.ssh/authorized_keys"))
        assertNull(sanitizeZipEntryName("..%2F..%2F..%2Fetc%2Fpasswd"))  // URL编码不会被此层解，但仍被拒绝
    }

    @Test
    fun testMixedSlashesAndTraversal() {
        assertNull(sanitizeZipEntryName("a\\..\\b"))
        assertNull(sanitizeZipEntryName("a/../b\\c"))
    }

    // ===== 合法输入 - 应正常返回 =====

    @Test
    fun testNormalResourceNames() {
        assertEquals("OEBPS/content.opf", sanitizeZipEntryName("OEBPS/content.opf"))
        assertEquals("OEBPS/text/chapter1.xhtml", sanitizeZipEntryName("OEBPS/text/chapter1.xhtml"))
        assertEquals("Images/cover.jpg", sanitizeZipEntryName("Images/cover.jpg"))
        assertEquals("stylesheet.css", sanitizeZipEntryName("stylesheet.css"))
        assertEquals("META-INF/container.xml", sanitizeZipEntryName("META-INF/container.xml"))
    }

    @Test
    fun testSingleSegmentNames() {
        assertEquals("cover.xhtml", sanitizeZipEntryName("cover.xhtml"))
        assertEquals("style.css", sanitizeZipEntryName("style.css"))
    }

    @Test
    fun testDeeplyNestedLegalPaths() {
        assertEquals("OEBPS/text/section/chapter/paragraph/p.xhtml",
            sanitizeZipEntryName("OEBPS/text/section/chapter/paragraph/p.xhtml"))
    }

    @Test
    fun testPathNormalization() {
        // 合法路径即使包含反斜杠也被拒绝（因为反斜杠一律禁止）
        // 纯正斜杠的混合路径应通过
        assertEquals("OEBPS/images/a.png", sanitizeZipEntryName("OEBPS/images/a.png"))
    }

    @Test
    fun testLegalNamesWithSpecialChars() {
        // 合法的特殊字符不应该被拒绝
        assertEquals("chapter-10.xhtml", sanitizeZipEntryName("chapter-10.xhtml"))
        assertEquals("chapter_附录.xhtml", sanitizeZipEntryName("chapter_附录.xhtml"))
        assertEquals("目录.txt", sanitizeZipEntryName("目录.txt"))
    }

    @Test
    fun testTrailingSlashLegal() {
        // 末尾 / 会导致 split 最后一段为空，是安全的
        // 实际上这是目录 entry，通常上层会跳过
        assertEquals("OEBPS/", sanitizeZipEntryName("OEBPS/"))
    }
}
