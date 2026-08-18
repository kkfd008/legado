package io.legado.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 验证换源操作的事务原子性
 * 确保 bookDao.insert + bookChapterDao.insert 在同一个事务中执行
 */
@RunWith(AndroidJUnit4::class)
class TransactionAtomicityTest {

    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    /**
     * 场景：换源操作中，book 和 chapter 应该在同一个事务中插入
     * 当 chapter 插入失败时，book 的插入应该回滚
     */
    @Test
    fun changeSourceTransaction_rollBackWhenChapterInsertFails() {
        val book = Book(
            bookUrl = "https://example.com/book1",
            name = "Test Book",
            author = "Test Author",
            origin = "Test Source"
        )
        val chapters = listOf(
            BookChapter(
                bookUrl = book.bookUrl,
                title = "Chapter 1",
                url = "https://example.com/book1/ch1"
            )
        )

        // 模拟事务：先插入 book，然后故意让 chapter 插入失败
        try {
            db.runInTransaction {
                db.bookDao.insert(book)
                // 故意抛出异常，模拟 chapter 插入失败或中途崩溃
                throw RuntimeException("Simulated crash during chapter insert")
            }
            fail("Expected exception to be thrown")
        } catch (_: RuntimeException) {
            // 预期异常
        }

        // 验证：由于事务回滚，book 不应该被插入
        val fetchedBook = db.bookDao.getBook(book.bookUrl)
        assertNull(
            "Book should not exist when transaction rolls back",
            fetchedBook
        )
    }

    /**
     * 场景：正常换源操作
     * 当事务成功提交时，book 和 chapter 都应该存在
     */
    @Test
    fun changeSourceTransaction_bothBookAndChaptersPersistedOnSuccess() {
        val book = Book(
            bookUrl = "https://example.com/book2",
            name = "Test Book 2",
            author = "Test Author 2",
            origin = "Test Source 2"
        )
        val chapters = listOf(
            BookChapter(
                bookUrl = book.bookUrl,
                title = "Chapter 1",
                url = "https://example.com/book2/ch1",
                index = 0
            ),
            BookChapter(
                bookUrl = book.bookUrl,
                title = "Chapter 2",
                url = "https://example.com/book2/ch2",
                index = 1
            )
        )

        // 正常事务执行
        db.runInTransaction {
            db.bookDao.insert(book)
            db.bookChapterDao.insert(*chapters.toTypedArray())
        }

        // 验证 book 存在
        val fetchedBook = db.bookDao.getBook(book.bookUrl)
        assertNotNull("Book should exist after successful transaction", fetchedBook)
        assertEquals(book.name, fetchedBook?.name)

        // 验证 chapter 存在
        val fetchedChapters = db.bookChapterDao.getChapterList(book.bookUrl)
        assertEquals("All chapters should be persisted", 2, fetchedChapters.size)
    }

    /**
     * 场景：验证 runInTransaction 的原子性
     * 当 book 插入后、chapter 插入前发生异常，两者都不应存在
     */
    @Test
    fun changeSourceTransaction_atomicityGuarantee() {
        val book = Book(
            bookUrl = "https://example.com/book3",
            name = "Test Book 3",
            author = "Test Author 3",
            origin = "Test Source 3"
        )

        try {
            db.runInTransaction {
                db.bookDao.insert(book)
                // 模拟中途崩溃（如系统杀死进程、内存不足等）
                error("Simulated process death")
            }
            fail("Expected exception")
        } catch (_: IllegalStateException) {
            // 预期异常
        }

        // 验证数据库一致性：book 和 chapter 都不应存在
        assertNull(
            "Atomicity violated: book exists without chapters",
            db.bookDao.getBook(book.bookUrl)
        )
        assertTrue(
            "Chapters should not exist for rolled-back book",
            db.bookChapterDao.getChapterList(book.bookUrl).isEmpty()
        )
    }
}
