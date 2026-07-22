package io.legado.app.constant

import androidx.annotation.IntDef

@Suppress("ConstPropertyName")
object BookSourceType {

    const val TEXT = 0
    const val AUDIO = 1
    const val IMAGE = 2
    const val VIDEO = 3
    const val WEB = 4
    const val UNKNOWN = -1
    const val LUA = 5
    const val JS = 6

    const val audio = 1
    const val image = 2
    const val file = 3
    const val default = 0

    @Target(AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(TEXT, AUDIO, IMAGE, VIDEO, WEB, UNKNOWN, LUA, JS)
    annotation class Type

}