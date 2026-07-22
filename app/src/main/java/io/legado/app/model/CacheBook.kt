package io.legado.app.model

import android.content.Context
import io.legado.app.data.entities.Book
import kotlinx.coroutines.CoroutineDispatcher
import java.util.concurrent.ConcurrentHashMap

object CacheBook {
    var isRun: Boolean = false
        private set

    class CacheTask {
        fun isStop(): Boolean = false
    }

    val cacheBookMap = ConcurrentHashMap<String, CacheTask>()

    fun setWorkingState(working: Boolean) {
        // stub
    }

    fun startProcessJob(dispatcher: CoroutineDispatcher) {
        // stub
    }

    fun start(context: Context, book: Book, start: Int, end: Int) {
        // stub
    }

    fun stop(context: Context) {
        // stub
    }

    fun remove(context: Context, bookUrl: String) {
        // stub
    }

    fun close() {
        // stub
    }
}