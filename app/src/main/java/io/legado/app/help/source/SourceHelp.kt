package io.legado.app.help.source

import io.legado.app.data.appDb
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.RssSource

object SourceHelp {

    fun deleteRssSources(sources: List<RssSource>) {
        sources.forEach { source ->
            appDb.rssSourceDao.delete(source)
        }
    }

    fun getSource(sourceOrigin: String, sourceType: Int): BaseSource? {
        return null
    }

    fun getSource(sourceOrigin: String): BaseSource? {
        return null
    }

    fun enableSource(sourceOrigin: String, sourceType: Int, enabled: Boolean) {
        // stub
    }

    fun deleteSource(sourceOrigin: String, sourceType: Int) {
        // stub
    }

    fun adjustSortNumber() {
        // stub
    }

    fun insertRssSource(vararg sources: RssSource) {
        sources.forEach { source ->
            appDb.rssSourceDao.insert(source)
        }
    }
}