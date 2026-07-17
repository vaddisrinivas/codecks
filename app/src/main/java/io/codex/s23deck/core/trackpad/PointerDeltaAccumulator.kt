package io.codex.s23deck.core.trackpad

data class PointerDelta(
    val dx: Int,
    val dy: Int,
)

class PointerDeltaAccumulator {
    private var residualX = 0f
    private var residualY = 0f

    fun consume(dx: Float, dy: Float): PointerDelta? {
        val totalX = residualX + dx
        val totalY = residualY + dy
        val wholeX = totalX.toInt()
        val wholeY = totalY.toInt()
        residualX = totalX - wholeX
        residualY = totalY - wholeY
        return if (wholeX == 0 && wholeY == 0) null else PointerDelta(wholeX, wholeY)
    }

    fun reset() {
        residualX = 0f
        residualY = 0f
    }
}
