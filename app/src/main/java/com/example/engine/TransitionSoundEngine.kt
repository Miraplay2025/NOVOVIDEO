package com.example.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.model.SoundCategory
import com.example.data.model.TransitionSoundEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object TransitionSoundEngine {

    const val SAMPLE_RATE = 44100
    private var activeAudioTrack: AudioTrack? = null
    private var activeMediaPlayer: MediaPlayer? = null

    private val _customSounds = MutableStateFlow<List<TransitionSoundEffect>>(emptyList())
    val customSounds: StateFlow<List<TransitionSoundEffect>> = _customSounds.asStateFlow()

    private const val PREFS_FILE = "custom_transition_sounds.json"

    fun init(context: Context) {
        loadCustomSounds(context)
    }

    fun getAllSounds(): List<TransitionSoundEffect> {
        return TransitionSoundEffect.BUILT_IN_SOUNDS + _customSounds.value
    }

    fun getSoundById(id: Int): TransitionSoundEffect? {
        return getAllSounds().find { it.id == id }
    }

    /**
     * Toca um som de transição imediatamente (para preview no editor ao clicar).
     */
    fun playSound(context: Context, sound: TransitionSoundEffect) {
        stopPlayback()
        if (sound.id == 0) return // Sem som

        if (sound.isCustom && !sound.customFilePath.isNullOrBlank()) {
            val file = File(sound.customFilePath)
            if (file.exists()) {
                try {
                    activeMediaPlayer = MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        setOnCompletionListener {
                            it.release()
                            if (activeMediaPlayer == it) activeMediaPlayer = null
                        }
                        prepare()
                        start()
                    }
                } catch (_: Exception) {}
            }
        } else {
            // Sintetiza em tempo real PCM e toca no AudioTrack
            val pcmData = generatePcmForBuiltInSound(sound.id)
            if (pcmData.isNotEmpty()) {
                playPcmData(pcmData)
            }
        }
    }

    fun stopPlayback() {
        try {
            activeAudioTrack?.stop()
            activeAudioTrack?.release()
            activeAudioTrack = null
        } catch (_: Exception) {}

        try {
            activeMediaPlayer?.stop()
            activeMediaPlayer?.release()
            activeMediaPlayer = null
        } catch (_: Exception) {}
    }

    private fun playPcmData(pcm: ShortArray) {
        try {
            val bufferSize = pcm.size * 2
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(pcm, 0, pcm.size)
            track.play()
            activeAudioTrack = track
        } catch (_: Exception) {}
    }

    /**
     * Síntese matemática pura de 16-bit PCM Mono para os 12 efeitos de som.
     */
    fun generatePcmForBuiltInSound(id: Int): ShortArray {
        return when (id) {
            1 -> generateClickMechanical()
            2 -> generateClickTactile()
            3 -> generateClickMetallic()
            4 -> generateClickDeep()
            5 -> generateWhooshFast()
            6 -> generateSwooshCinematic()
            7 -> generateWhipSnap()
            8 -> generateGlitchCut()
            9 -> generateBubblePop()
            10 -> generateLaserFast()
            11 -> generateAirPuff()
            12 -> generatePercussiveSnap()
            else -> ShortArray(0)
        }
    }

    // 1. Clique Tecla Mecânica (snap nítido 18ms)
    private fun generateClickMechanical(): ShortArray {
        val numSamples = (SAMPLE_RATE * 0.022).toInt()
        val buffer = ShortArray(numSamples)
        val rnd = Random(42)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val decay = exp(-t * 380.0)
            val sine = sin(2.0 * PI * 2400.0 * t)
            val noise = (rnd.nextDouble() * 2.0 - 1.0) * exp(-t * 900.0)
            val sample = (0.75 * sine + 0.5 * noise) * decay
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
        }
        return buffer
    }

    // 2. Clique Tecla Tátil (duplo clique preciso 24ms)
    private fun generateClickTactile(): ShortArray {
        val numSamples = (SAMPLE_RATE * 0.028).toInt()
        val buffer = ShortArray(numSamples)
        val rnd = Random(101)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val t2 = (t - 0.006).coerceAtLeast(0.0)
            val click1 = sin(2.0 * PI * 3100.0 * t) * exp(-t * 600.0)
            val click2 = if (t >= 0.006) sin(2.0 * PI * 2600.0 * t2) * exp(-t2 * 500.0) else 0.0
            val noise = (rnd.nextDouble() * 2.0 - 1.0) * exp(-t * 800.0) * 0.3
            val sample = (click1 * 0.7 + click2 * 0.8 + noise)
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
        }
        return buffer
    }

    // 3. Clique Tecla Metálica (4200Hz ringing 26ms)
    private fun generateClickMetallic(): ShortArray {
        val numSamples = (SAMPLE_RATE * 0.030).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val decay = exp(-t * 220.0)
            val tone1 = sin(2.0 * PI * 4200.0 * t)
            val tone2 = sin(2.0 * PI * 5800.0 * t) * 0.35
            val sample = (tone1 + tone2) * decay
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 31000).toInt().toShort()
        }
        return buffer
    }

    // 4. Clique Tecla Profunda (bottom-out encorpado 35ms)
    private fun generateClickDeep(): ShortArray {
        val numSamples = (SAMPLE_RATE * 0.038).toInt()
        val buffer = ShortArray(numSamples)
        val rnd = Random(777)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq = 450.0 - t * 4000.0
            val decay = exp(-t * 160.0)
            val thump = sin(2.0 * PI * freq.coerceAtLeast(120.0) * t)
            val pop = (rnd.nextDouble() * 2.0 - 1.0) * exp(-t * 900.0) * 0.4
            val sample = (thump * 0.85 + pop) * decay
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
        }
        return buffer
    }

    // 5. Whoosh Ultra Rápido (110ms)
    private fun generateWhooshFast(): ShortArray {
        val duration = 0.11
        val numSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = ShortArray(numSamples)
        val rnd = Random(55)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = sin(PI * (t / duration)) // sino suave
            val noise = rnd.nextDouble() * 2.0 - 1.0
            val freq = 3200.0 - (t / duration) * 2200.0
            val filtered = sin(2.0 * PI * freq * t) * 0.4 + noise * 0.6
            val sample = filtered * envelope
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 31000).toInt().toShort()
        }
        return buffer
    }

    // 6. Swoosh Cinema (160ms)
    private fun generateSwooshCinematic(): ShortArray {
        val duration = 0.16
        val numSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = ShortArray(numSamples)
        val rnd = Random(999)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / duration
            val envelope = sin(PI * progress) * (if (progress < 0.5) 0.8 else 1.0)
            val subBass = sin(2.0 * PI * (90.0 + progress * 80.0) * t) * 0.5
            val airNoise = (rnd.nextDouble() * 2.0 - 1.0) * 0.6
            val sample = (subBass + airNoise) * envelope
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
        }
        return buffer
    }

    // 7. Whip Transição (90ms)
    private fun generateWhipSnap(): ShortArray {
        val duration = 0.09
        val numSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = ShortArray(numSamples)
        val rnd = Random(333)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val snap = if (t < 0.015) {
                (rnd.nextDouble() * 2.0 - 1.0) * exp(-t * 400.0)
            } else {
                val tTrail = t - 0.015
                (rnd.nextDouble() * 2.0 - 1.0) * exp(-tTrail * 70.0) * 0.4
            }
            buffer[i] = (snap.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
        }
        return buffer
    }

    // 8. Glitch Digital Cut (100ms)
    private fun generateGlitchCut(): ShortArray {
        val duration = 0.10
        val numSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = ShortArray(numSamples)
        val freqs = doubleArrayOf(1200.0, 3400.0, 600.0, 2200.0, 4800.0)
        val rnd = Random(1234)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val segment = ((t / duration) * freqs.size).toInt().coerceIn(0, freqs.size - 1)
            val f = freqs[segment]
            val square = if (sin(2.0 * PI * f * t) > 0) 0.6 else -0.6
            val noise = (rnd.nextDouble() * 2.0 - 1.0) * 0.3
            val env = exp(-t * 25.0)
            val sample = (square + noise) * env
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 30000).toInt().toShort()
        }
        return buffer
    }

    // 9. Pop Transição Bolha (65ms)
    private fun generateBubblePop(): ShortArray {
        val duration = 0.065
        val numSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / duration
            val freq = 350.0 + progress * 1600.0
            val decay = exp(-t * 70.0)
            val sample = sin(2.0 * PI * freq * t) * decay
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
        }
        return buffer
    }

    // 10. Laser Beep Fast (80ms)
    private fun generateLaserFast(): ShortArray {
        val duration = 0.08
        val numSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq = 2200.0 * exp(-t * 35.0)
            val decay = exp(-t * 20.0)
            val sample = sin(2.0 * PI * freq * t) * decay
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 31000).toInt().toShort()
        }
        return buffer
    }

    // 11. Air Puff Impact (130ms)
    private fun generateAirPuff(): ShortArray {
        val duration = 0.13
        val numSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = ShortArray(numSamples)
        val rnd = Random(888)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = sin(PI * (t / duration))
            val thud = sin(2.0 * PI * 85.0 * t) * exp(-t * 35.0) * 0.7
            val wind = (rnd.nextDouble() * 2.0 - 1.0) * 0.5
            val sample = (thud + wind) * env
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 31000).toInt().toShort()
        }
        return buffer
    }

    // 12. Snap Percussivo (55ms)
    private fun generatePercussiveSnap(): ShortArray {
        val duration = 0.055
        val numSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = ShortArray(numSamples)
        val rnd = Random(444)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val impact = sin(2.0 * PI * 1800.0 * t) * exp(-t * 220.0) * 0.7
            val crackle = (rnd.nextDouble() * 2.0 - 1.0) * exp(-t * 300.0) * 0.6
            val sample = impact + crackle
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
        }
        return buffer
    }

    // =========================================================================
    // Upload de Som Personalizado
    // =========================================================================

    suspend fun importCustomSound(context: Context, uri: Uri): Result<TransitionSoundEffect> = withContext(Dispatchers.IO) {
        try {
            val nextId = getNextCustomId()
            val fileName = getFileName(context, uri)
            val ext = fileName.substringAfterLast('.', "wav").lowercase()

            val customSoundsDir = File(context.filesDir, "custom_sounds").apply { mkdirs() }
            val targetFile = File(customSoundsDir, "sound_${nextId}_$fileName")

            context.contentResolver.openInputStream(uri)?.use { inStream ->
                FileOutputStream(targetFile).use { outStream ->
                    inStream.copyTo(outStream)
                }
            }

            val cleanName = fileName.substringBeforeLast('.').take(22)
            val newSound = TransitionSoundEffect(
                id = nextId,
                name = cleanName,
                description = "Som personalizado importado",
                category = SoundCategory.CUSTOM,
                customFilePath = targetFile.absolutePath,
                isCustom = true
            )

            val updated = _customSounds.value + newSound
            _customSounds.value = updated
            saveCustomSounds(context, updated)

            Result.success(newSound)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCustomSound(context: Context, soundId: Int): Boolean = withContext(Dispatchers.IO) {
        val sound = _customSounds.value.find { it.id == soundId } ?: return@withContext false
        if (!sound.customFilePath.isNullOrBlank()) {
            try {
                File(sound.customFilePath).delete()
            } catch (_: Exception) {}
        }
        val updated = _customSounds.value.filter { it.id != soundId }
        _customSounds.value = updated
        saveCustomSounds(context, updated)
        true
    }

    private fun getNextCustomId(): Int {
        val existingMax = (_customSounds.value.map { it.id } + 12).maxOrNull() ?: 12
        return existingMax + 1
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "custom_sound_${System.currentTimeMillis()}.wav"
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = it.getString(nameIndex) ?: name
                }
            }
        }
        return name
    }

    private fun saveCustomSounds(context: Context, list: List<TransitionSoundEffect>) {
        try {
            val jsonArray = JSONArray()
            list.forEach { sound ->
                val obj = JSONObject().apply {
                    put("id", sound.id)
                    put("name", sound.name)
                    put("description", sound.description)
                    put("customFilePath", sound.customFilePath ?: "")
                }
                jsonArray.put(obj)
            }
            val file = File(context.filesDir, PREFS_FILE)
            file.writeText(jsonArray.toString())
        } catch (_: Exception) {}
    }

    private fun loadCustomSounds(context: Context) {
        try {
            val file = File(context.filesDir, PREFS_FILE)
            if (!file.exists()) return
            val jsonArray = JSONArray(file.readText())
            val list = mutableListOf<TransitionSoundEffect>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val path = obj.optString("customFilePath", "")
                if (path.isNotEmpty() && File(path).exists()) {
                    list.add(
                        TransitionSoundEffect(
                            id = obj.getInt("id"),
                            name = obj.getString("name"),
                            description = obj.optString("description", "Som personalizado"),
                            category = SoundCategory.CUSTOM,
                            customFilePath = path,
                            isCustom = true
                        )
                    )
                }
            }
            _customSounds.value = list
        } catch (_: Exception) {}
    }
}
