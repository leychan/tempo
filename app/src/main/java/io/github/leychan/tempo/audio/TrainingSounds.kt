package io.github.leychan.tempo.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import io.github.leychan.tempo.Cue
import io.github.leychan.tempo.R

/** Short, preloaded sounds. Call from the activity's main thread. */
class TrainingSounds(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build())
        .build()
    private val samples = mutableMapOf<Cue, Int>()
    private val loaded = mutableSetOf<Int>()
    private var streamId = 0
    private var pending: Pair<Cue, Long>? = null
    private var released = false

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (!released && status == 0) {
                loaded += sampleId
                pending?.let { (cue, deadline) ->
                    if (samples[cue] == sampleId) {
                        pending = null
                        if (SystemClock.uptimeMillis() <= deadline) playLoaded(sampleId)
                    }
                }
            }
        }
        samples[Cue.COUNTDOWN] = pool.load(context, R.raw.countdown_tick, 1)
        samples[Cue.TRANSITION] = pool.load(context, R.raw.stage_end, 1)
        samples[Cue.FINISH] = pool.load(context, R.raw.workout_complete, 1)
    }

    fun play(cue: Cue) {
        if (released) return
        stop()
        val sample = samples.getValue(cue)
        if (sample in loaded) playLoaded(sample)
        else pending = cue to (SystemClock.uptimeMillis() + 500)
    }

    private fun playLoaded(sample: Int) {
        streamId = pool.play(sample, 1f, 1f, 1, 0, 1f)
    }

    fun stop() {
        if (released) return
        pending = null
        if (streamId != 0) pool.stop(streamId)
        streamId = 0
    }

    fun release() {
        if (released) return
        stop()
        released = true
        pool.setOnLoadCompleteListener(null)
        pool.release()
    }
}
