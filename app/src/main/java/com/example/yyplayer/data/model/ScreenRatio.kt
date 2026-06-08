package com.example.yyplayer.data.model

enum class ScreenRatio(val id: String, val ratio: Float, val displayName: String, val coverSizePercent: Float) {
    RATIO_16_9("16:9", 16f / 9f, "16 : 9", 0.5f),
    RATIO_3_2("3:2", 3f / 2f, "3 : 2", 0.5f),
    RATIO_4_3("4:3", 4f / 3f, "4 : 3", 0.5f),
    RATIO_1_1("1:1", 1f, "1 : 1", 0.5f),
    RATIO_8_7("8:7", 8f / 7f, "8 : 7", 0.5f);

    companion object {
        fun getById(id: String): ScreenRatio =
            entries.find { it.id == id } ?: RATIO_1_1
    }
}
