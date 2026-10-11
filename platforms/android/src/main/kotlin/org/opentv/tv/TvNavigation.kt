package org.opentv.tv

/** Android KeyEvent values shared by phone, tablet, and Android TV adapters. */
object TvKeyCodes {
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val DPAD_CENTER = 23
    const val MEDIA_PLAY_PAUSE = 85
    const val BACK = 4
    const val MENU = 82
}

enum class TvAction {
    PREVIOUS_CHANNEL,
    NEXT_CHANNEL,
    VOLUME_DOWN,
    VOLUME_UP,
    PLAY_PAUSE,
    BACK,
    OPEN_MENU
}

fun actionForKeyCode(keyCode: Int): TvAction? = when (keyCode) {
    TvKeyCodes.DPAD_UP -> TvAction.PREVIOUS_CHANNEL
    TvKeyCodes.DPAD_DOWN -> TvAction.NEXT_CHANNEL
    TvKeyCodes.DPAD_LEFT -> TvAction.VOLUME_DOWN
    TvKeyCodes.DPAD_RIGHT -> TvAction.VOLUME_UP
    TvKeyCodes.DPAD_CENTER, TvKeyCodes.MEDIA_PLAY_PAUSE -> TvAction.PLAY_PAUSE
    TvKeyCodes.BACK -> TvAction.BACK
    TvKeyCodes.MENU -> TvAction.OPEN_MENU
    else -> null
}
