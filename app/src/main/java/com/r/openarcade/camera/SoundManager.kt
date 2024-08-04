package com.r.openarcade.camera

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.r.openarcade.R

class SoundManager(context: Context) {
    private val soundPool: SoundPool
    private val soundIds: List<Int>

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(7) // Maximum number of simultaneous sounds
            .setAudioAttributes(audioAttributes)
            .build()

        //  Preload the sounds and store their IDs
        //  https://github.com/ideastudios/PianoKeyBoard/tree/master/library/src/main/res/raw

        val soundResources = listOf(
            R.raw.p40, // C4
            R.raw.p42, // D4
            R.raw.p44, // E4
            R.raw.p45, // F4
            R.raw.p47, // G4
            R.raw.p49, // A4
            R.raw.p51  // B4
        )

        // Load sounds and store their IDs in a list
        soundIds = soundResources.map { resId ->
            soundPool.load(context, resId, 1)
        }
    }

    fun playSound(keyIndex: Int) {
        if (keyIndex in soundIds.indices) {
            val soundId = soundIds[keyIndex]
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
        }
    }

    fun release() {
        soundPool.release()
    }
}
