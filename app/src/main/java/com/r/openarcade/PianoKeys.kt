import android.content.Context
import android.os.Handler
import android.graphics.Canvas
import android.graphics.PointF
import com.r.openarcade.GridButton
import com.r.openarcade.camera.SoundManager

class PianoKeys(
    context: Context,
    xTotal: Int = 7,
    yIndex: Int = 6,
    yTotal: Int = 11
) {
    private val soundManager = SoundManager(context)
    private val keys: List<GridButton> = listOf(
        GridButton(1, yIndex, soundManager, xTotal, yTotal, "Do", 0x80FF0000.toInt(), soundKey = 0),
        GridButton(2, yIndex, soundManager, xTotal, yTotal, "Re", 0x80FF7F00.toInt(), soundKey = 1),
        GridButton(3, yIndex, soundManager, xTotal, yTotal, "Mi", 0x80FFFF00.toInt(), soundKey = 2),
        GridButton(4, yIndex, soundManager, xTotal, yTotal, "Fa", 0x8000FF00.toInt(), soundKey = 3),
        GridButton(5, yIndex, soundManager, xTotal, yTotal, "Sol", 0x800000FF.toInt(), soundKey = 4),
        GridButton(6, yIndex, soundManager, xTotal, yTotal, "La", 0x80007FFF.toInt(), soundKey = 5),
        GridButton(7, yIndex, soundManager, xTotal, yTotal, "Si", 0x80FF00FF.toInt(), soundKey = 6)
    )

    fun update(canvas: Canvas) {
        keys.forEach { it.update(canvas) }
    }

    private val strokeResetTime: Long = 200L

    fun stroke(oldPoint: PointF, newPoint: PointF): Boolean {
        // Log the current time
        val currentTime = System.currentTimeMillis()
        var hasStroked: Boolean = false

        // Iterate through all keys to handle time-based margin reset
        keys.forEach { key ->
            val keyStroked = !hasStroked && key.stroke(oldPoint, newPoint)

            if (keyStroked) {
                key.active = true
                key.lastActivatedAt = currentTime
                hasStroked = true
            } else {
                val keyExpired = key.active && (currentTime - key.lastActivatedAt > strokeResetTime)
                if (keyExpired) {
                    key.active = false
                }
            }
        }

        return hasStroked
    }

    fun draw() {
        keys.forEach { it.draw() }
    }
}
