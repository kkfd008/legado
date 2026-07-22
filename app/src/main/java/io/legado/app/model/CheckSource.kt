package io.legado.app.model

object CheckSource {
    var timeout: Long = 30000L
    var checkSearch: Boolean = true
    var checkDiscovery: Boolean = true
    var checkInfo: Boolean = true
    var checkCategory: Boolean = true
    var checkContent: Boolean = true

    val summary: String
        get() = "$timeout,$checkSearch,$checkDiscovery,$checkInfo,$checkCategory,$checkContent"

    fun putConfig() {
        // stub
    }
}