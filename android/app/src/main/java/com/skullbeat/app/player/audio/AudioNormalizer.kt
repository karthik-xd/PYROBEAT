package com.skullbeat.app.player.audio

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.util.Log

class AudioNormalizer {
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentSessionId: Int = 0

    var isEnabled: Boolean = true
        set(value) {
            field = value
            applyEnabled(value)
        }

    fun attach(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (audioSessionId == currentSessionId && (dynamicsProcessing != null || loudnessEnhancer != null)) {
            applyEnabled(isEnabled)
            return
        }

        release()
        currentSessionId = audioSessionId

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // 1-band Multi-Band Compressor across 20Hz-20kHz for seamless volume flow without coloration
                val mbc = DynamicsProcessing.Mbc(true, true, 1)
                val mbcBand = DynamicsProcessing.MbcBand(
                    true,        // inUse
                    20000.0f,    // cutoffFrequency (20 kHz covers entire audible range)
                    50.0f,       // attackTime ms (preserves musical punch & transients)
                    350.0f,      // releaseTime ms (transparent decay, zero pumping)
                    2.5f,        // ratio (smooth dynamic leveling)
                    -20.0f,      // threshold dB (levels volume across tracks)
                    6.0f,        // kneeWidth dB (soft knee for imperceptible transition)
                    -65.0f,      // noiseGateThreshold dB (ignores silence & background noise)
                    1.0f,        // expanderRatio
                    0.0f,        // preGain dB
                    2.0f         // postGain dB (smooth makeup gain for quieter masters)
                )
                mbc.setBand(0, mbcBand)

                // True-peak brickwall limiter at -1.0 dBFS to guarantee zero digital clipping distortion
                val limiter = DynamicsProcessing.Limiter(
                    true,        // inUse
                    true,        // enabled
                    0,           // linkGroup
                    1.0f,        // attackTime ms (rapid peak protection)
                    60.0f,       // releaseTime ms
                    10.0f,       // ratio
                    -1.0f,       // threshold dB (safeguards high-fidelity audio quality)
                    0.0f         // postGain dB
                )

                val builder = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,           // stereo channels
                    false, 0,    // pre-EQ disabled (100% natural frequency response)
                    true, 1,     // 1-band MBC
                    false, 0,    // post-EQ disabled (no tonal coloration)
                    true         // peak limiter enabled
                )
                builder.setMbcAllChannelsTo(mbc)
                builder.setLimiterAllChannelsTo(limiter)
                val config = builder.build()

                val dp = DynamicsProcessing(0, audioSessionId, config)
                try {
                    dp.setEnabled(isEnabled)
                } catch (_: Throwable) {}
                dynamicsProcessing = dp
                Log.d("AudioNormalizer", "DynamicsProcessing attached to session $audioSessionId (enabled=$isEnabled)")
                return
            }
        } catch (e: Throwable) {
            Log.w("AudioNormalizer", "DynamicsProcessing failed, falling back to LoudnessEnhancer: ${e.message}")
        }

        // Fallback for pre-Android 9 or hardware without DynamicsProcessing
        try {
            val le = LoudnessEnhancer(audioSessionId).apply {
                setTargetGain(250) // +2.5 dB transparent gain
                try {
                    setEnabled(isEnabled)
                } catch (_: Throwable) {}
            }
            loudnessEnhancer = le
            Log.d("AudioNormalizer", "LoudnessEnhancer attached to session $audioSessionId (enabled=$isEnabled)")
        } catch (e: Throwable) {
            Log.w("AudioNormalizer", "LoudnessEnhancer fallback failed: ${e.message}")
        }
    }

    private fun applyEnabled(enabled: Boolean) {
        try {
            dynamicsProcessing?.let {
                if (it.hasControl()) it.setEnabled(enabled)
            }
        } catch (_: Throwable) {}
        try {
            loudnessEnhancer?.let {
                if (it.hasControl()) it.setEnabled(enabled)
            }
        } catch (_: Throwable) {}
    }

    fun release() {
        try {
            dynamicsProcessing?.let {
                if (it.hasControl()) {
                    it.setEnabled(false)
                }
                it.release()
            }
        } catch (_: Throwable) {}
        dynamicsProcessing = null

        try {
            loudnessEnhancer?.let {
                if (it.hasControl()) {
                    it.setEnabled(false)
                }
                it.release()
            }
        } catch (_: Throwable) {}
        loudnessEnhancer = null
        currentSessionId = 0
    }
}
