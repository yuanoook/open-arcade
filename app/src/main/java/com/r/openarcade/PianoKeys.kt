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
    yTotal: Int = 11
) {
    private var reStartedAt: Long = System.currentTimeMillis()
    private var switchMusicShowing: Boolean = false
    private val GROUP_SIZE = 4
    private val soundManager = SoundManager(context)
    private val switchMusicBtn = GridButton(7, 1, soundManager, xTotal, yTotal, "Switch", 0x80FFFFFF.toInt(), soundKey = 0)

    private val musicChoiceBtns: List<GridButton> = listOf(
        GridButton(2, 3, soundManager, 7, 5, "Just Piano", 0x80FF0000.toInt(), soundKey = 0),
        GridButton(4, 3, soundManager, 7, 5, "Little Star", 0x80FF7F00.toInt(), soundKey = 1),
        GridButton(6, 3, soundManager, 7, 5, "Mommy Good", 0x80FFFF00.toInt(), soundKey = 2),
    )

    val emptyNotes: List<Int> = listOf(-1)

    val littleStarNotes: List<Int> = listOf(
        1, 1, 5, 5, 6, 6, 5, 0,
        4, 4, 3, 3, 2, 2, 1, 0,
        5, 5, 4, 4, 3, 3, 2, 0,
        5, 5, 4, 4, 3, 3, 2, 0,
        1, 1, 5, 5, 6, 6, 5, 0,
        4, 4, 3, 3, 2, 2, 1, 0
    )

    val mommyGoodNotes: List<Int> = listOf(
        6, 5, 3, 5, 0, 0, 0, 0,    // 世 上 只 有
        1, 6, 5, 6, 0, 0, 0, 0,    // 妈 妈 好
        3, 5, 6, 5, 3, 0, 0, 0,    // 有 妈 的 孩 子
        1, 6, 5, 3, 2, 0, 0, 0,    // 像 个 宝
        2, 3, 5, 6, 6, 0, 0, 0,    // 投 进 妈 妈 的
        3, 2, 1, 0, 0, 0, 0, 0,    // 怀 抱
        5, 3, 2, 1, 6, 1, 0, 0,    // 幸 福 享 不 了
        6, 5, 3, 5, 0, 0, 0, 0,    // 没 有 妈 妈
        1, 6, 5, 6, 0, 0, 0, 0,    // 最 苦 恼
        3, 5, 6, 5, 3, 0, 0, 0,    // 没 妈 的 孩 子
        1, 6, 5, 3, 2, 0, 0, 0,    // 像 根 草
        2, 3, 5, 6, 6, 0, 0, 0,    // 离 开 妈 妈 的
        3, 2, 1, 0, 0, 0, 0, 0,    // 怀 抱
        5, 3, 2, 1, 6, 1, 0, 0,    // 幸 福 那 里 找？
        6, 5, 3, 5, 0, 0, 0, 0,    // 世 上 只 有
        1, 6, 5, 6, 0, 0, 0, 0,    // 妈 妈 好
        3, 5, 6, 5, 3, 0, 0, 0,    // 有 妈 的 孩 子
        1, 6, 5, 3, 2, 0, 0, 0,    // 不 知 道
        2, 3, 5, 3, 5, 0, 0, 0,    // 要 是 他 知 道
        2, 1, 6, 0, 0, 0, 0, 0,    // 梦 里 也 会 笑
        6, 5, 3, 5, 0, 0, 0, 0,    // 世 上 只 有
        1, 6, 5, 6, 0, 0, 0, 0,    // 妈 妈 好
        3, 5, 6, 5, 3, 0, 0, 0,    // 有 妈 的 孩 子
        1, 6, 5, 3, 2, 0, 0, 0,    // 不 知 道
        2, 3, 5, 3, 5, 0, 0, 0,    // 要 是 他 知 道
        2, 1, 6, 0, 0, 0, 0, 0,    // 梦 里 也 会 笑
        5, 3, 2, 7, 6, 5, 1, 0,    // 梦 里 也 会 笑
        0, 0, 0, 0, 0, 0, 0, 0     // Ending line (optional)
    )

    private val musicChoices: List<List<Int>> = listOf(emptyNotes, littleStarNotes, mommyGoodNotes)

    private var musicNotes: List<Int> = musicChoices[0]

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
        musicChoiceBtns.forEach { it.update(canvas) }
        switchMusicBtn.update(canvas)
    }

    fun draw() {
        if (switchMusicShowing) {
            musicChoiceBtns.forEach { it.draw() }
            return
        }

        keys.forEach { it.draw() }
        switchMusicBtn.draw()
    }

    private var currentNoteIndex = 0
    private var noteZeroPassed = 0

    private fun switchMusic(index: Int) {
        currentNoteIndex = 0
        noteZeroPassed = 0
        musicNotes = musicChoices[index]
        reStartedAt = System.currentTimeMillis()
        switchMusicShowing = false
    }

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

    fun justStarted(): Boolean {
        return System.currentTimeMillis() - reStartedAt < strokeResetTime * 3
    }

    fun stroke(oldPoint: PointF, newPoint: PointF): Boolean {
        if (switchMusicShowing) {
            musicChoiceBtns.forEachIndexed { index, it ->
                val stroked = it.stroke(oldPoint, newPoint)
                if (stroked) {
                    switchMusic(index)
                    return true
                }
            }
            return false
        }

        val switchMusicBtnClick = switchMusicBtn.stroke(oldPoint, newPoint)
        if (switchMusicBtnClick) {
            switchMusicShowing = true
            return true
        }

        if (justStarted()) return false

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
                    while (getNote() == 0) {
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
}
