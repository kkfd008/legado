package io.legado.app.help

object IntentData {

    private val bigData: MutableMap<String, Any> = mutableMapOf()
    // 条目写入时间，用于过期清理；大结果集若未被消费不应永久驻留内存
    private val putTimes: MutableMap<String, Long> = mutableMapOf()
    // 最多缓存的条目数，超过后按写入时间淘汰最旧的
    private const val MAX_ENTRIES = 8
    // 条目有效期（毫秒），超过后视为失效
    private const val MAX_AGE_MS = 10 * 60 * 1000L

    @Synchronized
    fun put(key: String, data: Any?): String {
        data?.let {
            bigData[key] = data
            putTimes[key] = System.currentTimeMillis()
            evictIfNeeded()
        }
        return key
    }

    @Synchronized
    fun put(data: Any?): String {
        val key = System.currentTimeMillis().toString()
        data?.let {
            bigData[key] = data
            putTimes[key] = System.currentTimeMillis()
            evictIfNeeded()
        }
        return key
    }

    /** 淘汰过期条目与最旧的条目，防止搜索结果大对象无限堆积导致内存泄漏 */
    private fun evictIfNeeded() {
        val now = System.currentTimeMillis()
        val expired = putTimes.filter { now - it.value > MAX_AGE_MS }.map { it.key }
        expired.forEach {
            bigData.remove(it)
            putTimes.remove(it)
        }
        while (bigData.size > MAX_ENTRIES) {
            val oldest = putTimes.minByOrNull { it.value }?.key ?: break
            bigData.remove(oldest)
            putTimes.remove(oldest)
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Synchronized
    fun <T> get(key: String?): T? {
        if (key == null) return null
        val data = bigData[key]
        bigData.remove(key)
        putTimes.remove(key)
        return data as? T
    }
}