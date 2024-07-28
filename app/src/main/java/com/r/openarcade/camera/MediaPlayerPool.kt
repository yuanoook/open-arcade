package com.r.openarcade.camera

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import java.util.LinkedList
import java.util.Queue

class MediaPlayerPool(private val context: Context, private val maxPlayers: Int = 5) {
    private val mediaPlayers: Queue<MediaPlayer> = LinkedList()

    fun playSound(resId: Int) {
        val mediaPlayer = if (mediaPlayers.size < maxPlayers) {
            MediaPlayer.create(context, resId)
        } else {
            val mp = mediaPlayers.poll()!!
            mp.reset()
            mp.setDataSource(context, Uri.parse("android.resource://${context.packageName}/$resId"))
            mp.prepare()
            mp
        }

        mediaPlayer.setOnCompletionListener {
            it.reset()
            mediaPlayers.offer(it)
        }

        mediaPlayers.offer(mediaPlayer)
        mediaPlayer.start()
    }

    fun release() {
        while (mediaPlayers.isNotEmpty()) {
            mediaPlayers.poll()?.release()
        }
    }
}
