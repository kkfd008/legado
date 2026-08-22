package io.legado.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 验证目录刷新时书籍元数据与章节数据应在同一事务中更新。
 *
 * 修复前：appDb.bookDao.update/replace 在 runInTransaction 之外执行，
 * 若后续章节删除/插入事务失败回滚，会导致书籍元数据已更新但章节数据仍是旧值，
 * 用户打开书籍时看到新书名但章节列表缺失或错误。
 */
@RunWith(AndroidJUnit4::class)
class BookTocTransactionTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun bookAndChaptersShouldBeUpdatedAtomically() {
        val bookUrl = "https://example.com/book1"
        val book = Book(
            bookUrl = bookUrl,
            name = "Old Name",
            author = "Author",
            origin = BookType.localTag,
            totalChapterNum = 1
        )
        val oldChapter = BookChapter(
            url = "https://example.com/book1/chapter1",
            title = "Old Chapter 1",
            bookUrl = bookUrl,
            index = 0
        )

        db.bookDao.insert(book)
        db.bookChapterDao.insert(oldChapter)

        val newChapter = BookChapter(
            url = "https://example.com/book1/chapter2",
            title = "New Chapter 1",
            bookUrl = bookUrl,
            index = 0
        )

        // 模拟修复后的目录刷新逻辑：书籍与章节在同一事务中更新
        db.runInTransaction {
            book.name = "New Name"
            book.totalChapterNum = 1
            db.bookDao.update(book)
            db.bookChapterDao.delByBook(bookUrl)
            db.bookChapterDao.insert(newChapter)
        }

        val updatedBook = db.bookDao.getBook(bookUrl)
        assertNotNull(updatedBook)
        assertEquals("New Name", updatedBook!!.name)

        val chapters = db.bookChapterDao.getChapterList(bookUrl)
        assertEquals(1, chapters.size)
        assertEquals("New Chapter 1", chapters[0].title)
    }

    @Test
    fun transactionFailureShouldRollbackBothBookAndChapters() {
        val bookUrl = "https://example.com/book2"
        val book = Book(
            bookUrl = bookUrl,
            name = "Original Name",
            author = "Author",
            origin = BookType.localTag,
            totalChapterNum = 1
        )
        val chapter = BookChapter(
            url = "https://example.com/book2/chapter1",
            title = "Original Chapter 1",
            bookUrl = bookUrl,
            index = 0
        )

        db.bookDao.insert(book)
        db.bookChapterDao.insert(chapter)

        val badChapter = BookChapter(
            url = "",
            title = "Bad Chapter",
            bookUrl = bookUrl,
            index = 0
        )

        // 模拟事务中章节插入失败（例如主键冲突或违反非空约束）
        try {
            db.runInTransaction {
                book.name = "Updated Name"
                db.bookDao.update(book)
                db.bookChapterDao.delByBook(bookUrl)
                db.bookChapterDao.insert(badChapter)
                throw RuntimeException("Simulated chapter insert failure")
            }
            assertTrue("Transaction should have thrown", false)
        } catch (_: RuntimeException) {
            // expected
        }

        // 验证书籍和章节都回滚到事务前状态
        val rolledBackBook = db.bookDao.getBook(bookUrl)
        assertNotNull(rolledBackBook)
        assertEquals("Original Name", rolledBackBook!!.name)

        val chapters = db.bookChapterDao.getChapterList(bookUrl)
        assertEquals(1, chapters.size)
        assertEquals("Original Chapter 1", chapters[0].title)
    }

    @Test
    fun replaceBookAndChaptersShouldBeAtomic() {
        val oldUrl = "https://example.com/old"
        val newUrl = "https://example.com/new"
        val oldBook = Book(
            bookUrl = oldUrl,
            name = "Old Book",
            author = "Author",
            origin = BookType.localTag,
            totalChapterNum = 1
        )
        val oldChapter = BookChapter(
            url = "https://example.com/old/chapter1",
            title = "Old Chapter",
            bookUrl = oldUrl,
            index = 0
        )

        db.bookDao.insert(oldBook)
        db.bookChapterDao.insert(oldChapter)

        val newBook = Book(
            bookUrl = newUrl,
            name = "New Book",
            author = "Author",
            origin = BookType.localTag,
            totalChapterNum = 1
        )
        val newChapter = BookChapter(
            url = "https://example.com/new/chapter1",
            title = "New Chapter",
            bookUrl = newUrl,
            index = 0
        )

        // 模拟 bookUrl 变更时的 replace + 章节更新在同一事务中完成
        db.runInTransaction {
            db.bookDao.replace(oldBook, newBook)
            db.bookChapterDao.delByBook(oldUrl)
            db.bookChapterDao.insert(newChapter)
        }

        assertNull(db.bookDao.getBook(oldUrl))
        val updatedBook = db.bookDao.getBook(newUrl)
        assertNotNull(updatedBook)
        assertEquals("New Book", updatedBook!!.name)

        val chapters = db.bookChapterDao.getChapterList(newUrl)
        assertEquals(1, chapters.size)
        assertEquals("New Chapter", chapters[0].title)
    }
}
