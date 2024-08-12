import android.content.Context
import android.os.Handler
import android.graphics.Canvas
import android.graphics.PointF
import com.r.openarcade.GridButton
import com.r.openarcade.camera.SoundManager
import kotlin.math.ceil

class PianoKeys(
    context: Context,
    xTotal: Int = 7,
    yIndex: Int = 6,
    yTotal: Int = 11,
    val musicNotes: List<Int> = listOf(
        1, 1, 5, 5, 6, 6, 5, 0,
        4, 4, 3, 3, 2, 2, 1, 0,
        5, 5, 4, 4, 3, 3, 2, 0,
        5, 5, 4, 4, 3, 3, 2, 0,
        1, 1, 5, 5, 6, 6, 5, 0,
        4, 4, 3, 3, 2, 2, 1, 0
    )
) {
    private val GROUP_SIZE = 4
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

    private var currentNoteIndex = 0
    private var noteZeroPassed = 0

    private fun getNote(): Int {
        return musicNotes[currentNoteIndex % musicNotes.size]
    }

    private fun getNoteGroupIndex(): Int {
        return (currentNoteIndex % musicNotes.size) / GROUP_SIZE
    }

    private fun getNoteHints(): Map<Int, List<Pair<Int, Int>>> {
        val groupIndex = getNoteGroupIndex()

        val passedRound = currentNoteIndex / musicNotes.size
        val roundStartIndex = passedRound * musicNotes.size

        val inRoundStartIndex = currentNoteIndex % musicNotes.size
        val inRoundEndIndex = minOf((groupIndex + 1) * GROUP_SIZE, musicNotes.size)

        return (inRoundStartIndex until inRoundEndIndex)
            .map { it to musicNotes[it] }
            .groupBy(
                keySelector = { it.second },
                valueTransform = { Pair(roundStartIndex + it.first + 1, it.first - inRoundStartIndex) }
            )
    }

    private val strokeResetTime: Long = 200L

    fun stroke(oldPoint: PointF, newPoint: PointF): Boolean {
        // Log the current time
        val currentTime = System.currentTimeMillis()
        var hasStroked: Boolean = false
        var nextNote = getNote()
        var noteHints = getNoteHints()

        // Iterate through all keys to handle time-based margin reset
        keys.forEach { key ->
            var checkNote = key.soundKey + 1
            val keyStroked = !hasStroked && key.stroke(oldPoint, newPoint)
            key.hints = noteHints[checkNote]

            if (keyStroked) {
                if (checkNote == nextNote) {
                    currentNoteIndex++
                    if (getNote() == 0) {
                        noteZeroPassed++
                        currentNoteIndex++
                    }
                }
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
