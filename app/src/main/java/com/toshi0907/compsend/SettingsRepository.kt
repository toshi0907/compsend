package com.toshi0907.compsend

import android.content.Context

/** 圧縮・送信の設定を SharedPreferences に保存する。 */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var recipient: String
        get() = prefs.getString(KEY_RECIPIENT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_RECIPIENT, value).apply()

    /** 長辺の最大ピクセル数。0 は原寸(リサイズしない)。 */
    var maxLongSide: Int
        get() = prefs.getInt(KEY_MAX_LONG_SIDE, DEFAULT_MAX_LONG_SIDE)
        set(value) = prefs.edit().putInt(KEY_MAX_LONG_SIDE, value).apply()

    var quality: Int
        get() = prefs.getInt(KEY_QUALITY, DEFAULT_QUALITY)
        set(value) = prefs.edit().putInt(KEY_QUALITY, value).apply()

    companion object {
        private const val KEY_RECIPIENT = "recipient"
        private const val KEY_MAX_LONG_SIDE = "max_long_side"
        private const val KEY_QUALITY = "quality"

        const val DEFAULT_MAX_LONG_SIDE = 1920
        const val DEFAULT_QUALITY = 80

        val LONG_SIDE_OPTIONS = listOf(1280, 1920, 2560, 0)
        val QUALITY_OPTIONS = listOf(60, 70, 80, 90)
    }
}
