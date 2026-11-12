package com.example.twostepclicker

enum class StepKind { TEXT, DESC, ID, POINT }

/**
 * 單一步驟。座標以螢幕百分比表示 (0-100)，與裝置解析度無關。
 */
data class Step(
    val kind: StepKind = StepKind.TEXT,
    val value: String = "",
    val xPercent: Int = 50,
    val yPercent: Int = 50,
    val waitBeforeMs: Long = 500L
)
