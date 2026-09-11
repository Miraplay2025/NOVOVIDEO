package com.example.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import com.example.data.model.MovementEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object VideoEncoder {

    private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC // H.264
    private const val DEFAULT_FRAME_RATE = 30
    private const val I_FRAME_INTERVAL = 1
    private const val DEFAULT_BIT_RATE = 5_000_000 // 5.0 Mbps

    suspend fun encodeImageToVideo(
        imageFile: File,
        movementEffect: MovementEffect,
        durationSeconds: Float,
        outputFile: File,
        targetWidth: Int = 1280,
        targetHeight: Int = 720,
        frameRate: Int = DEFAULT_FRAME_RATE,
        bitRate: Int = DEFAULT_BIT_RATE,
        onFrameProgress: (frame: Int, totalFrames: Int) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): Boolean = withContext(Dispatchers.Default) {
        val totalFrames = (durationSeconds * frameRate).toInt().coerceAtLeast(1)

        // 1. Decodifica o bitmap com respeito ao Aspect Ratio e orientação EXIF
        val sourceBitmap = loadOptimizedBitmap(imageFile, targetWidth * 2, targetHeight * 2)
            ?: return@withContext false

        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) {
            outputFile.delete()
        }

        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null

        try {
            val colorFormat = selectColorFormat()
            val format = MediaFormat.createVideoFormat(MIME_TYPE, targetWidth, targetHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            }

            encoder = MediaCodec.createEncoderByType(MIME_TYPE)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoTrackIndex = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()
            val frameBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(frameBitmap)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            val matrix = Matrix()

            val argbArray = IntArray(targetWidth * targetHeight)
            val yuvArray = ByteArray(targetWidth * targetHeight * 3 / 2)

            var frameIndex = 0

            while (frameIndex < totalFrames) {
                if (isCancelled()) {
                    cleanup(encoder, muxer, muxerStarted)
                    sourceBitmap.recycle()
                    frameBitmap.recycle()
                    outputFile.delete()
                    return@withContext false
                }

                val progress = frameIndex.toFloat() / (totalFrames - 1).coerceAtLeast(1)

                // Renderiza o quadro com a matriz calculada do efeito de câmera
                canvas.drawColor(Color.BLACK)
                movementEffect.applyToMatrix(
                    matrix = matrix,
                    progress = progress,
                    canvasW = targetWidth.toFloat(),
                    canvasH = targetHeight.toFloat(),
                    bitmapW = sourceBitmap.width.toFloat(),
                    bitmapH = sourceBitmap.height.toFloat()
                )
                canvas.drawBitmap(sourceBitmap, matrix, paint)

                // Converte frame para YUV
                frameBitmap.getPixels(argbArray, 0, targetWidth, 0, 0, targetWidth, targetHeight)
                if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                    convertArgbToYuv420Planar(argbArray, yuvArray, targetWidth, targetHeight)
                } else {
                    convertArgbToYuv420SemiPlanar(argbArray, yuvArray, targetWidth, targetHeight)
                }

                // Envia para o encoder
                val inputBufferIndex = encoder.dequeueInputBuffer(10_000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                    inputBuffer?.clear()
                    inputBuffer?.put(yuvArray)

                    val ptsUs = (frameIndex * 1_000_000L) / frameRate
                    val isLastFrame = frameIndex == totalFrames - 1
                    val flags = if (isLastFrame) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0

                    encoder.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        yuvArray.size,
                        ptsUs,
                        flags
                    )
                    frameIndex++
                    onFrameProgress(frameIndex, totalFrames)
                }

                // Drena saídas disponíveis do encoder
                var outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
                while (outputBufferIndex >= 0) {
                    val encodedBuffer = encoder.getOutputBuffer(outputBufferIndex)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0 && muxerStarted && encodedBuffer != null) {
                        encodedBuffer.position(bufferInfo.offset)
                        encodedBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(videoTrackIndex, encodedBuffer, bufferInfo)
                    }

                    encoder.releaseOutputBuffer(outputBufferIndex, false)
                    outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
                }

                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxerStarted) {
                        throw RuntimeException("Formato de saída mudou mais de uma vez")
                    }
                    val newFormat = encoder.outputFormat
                    videoTrackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                }
            }

            // Drena os frames finais até BUFFER_FLAG_END_OF_STREAM
            var eosReached = false
            var drainAttempts = 0
            while (!eosReached && drainAttempts < 100) {
                val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputBufferIndex >= 0) {
                    val encodedBuffer = encoder.getOutputBuffer(outputBufferIndex)
                    if (bufferInfo.size != 0 && muxerStarted && encodedBuffer != null) {
                        encodedBuffer.position(bufferInfo.offset)
                        encodedBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(videoTrackIndex, encodedBuffer, bufferInfo)
                    }
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        eosReached = true
                    }
                    encoder.releaseOutputBuffer(outputBufferIndex, false)
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !muxerStarted) {
                    val newFormat = encoder.outputFormat
                    videoTrackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                } else {
                    drainAttempts++
                }
            }

            cleanup(encoder, muxer, muxerStarted)
            sourceBitmap.recycle()
            frameBitmap.recycle()
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            cleanup(encoder, muxer, false)
            if (sourceBitmap != null && !sourceBitmap.isRecycled) {
                sourceBitmap.recycle()
            }
            if (outputFile.exists()) {
                outputFile.delete()
            }
            return@withContext false
        }
    }

    private fun cleanup(encoder: MediaCodec?, muxer: MediaMuxer?, muxerStarted: Boolean) {
        try {
            encoder?.stop()
        } catch (_: Exception) {}
        try {
            encoder?.release()
        } catch (_: Exception) {}
        try {
            if (muxerStarted) {
                muxer?.stop()
            }
        } catch (_: Exception) {}
        try {
            muxer?.release()
        } catch (_: Exception) {}
    }

    private fun selectColorFormat(): Int {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in codecList.codecInfos) {
            if (!info.isEncoder) continue
            val types = info.supportedTypes
            for (type in types) {
                if (type.equals(MIME_TYPE, ignoreCase = true)) {
                    val caps = info.getCapabilitiesForType(type)
                    for (format in caps.colorFormats) {
                        if (format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                            return format
                        }
                    }
                    for (format in caps.colorFormats) {
                        if (format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                            return format
                        }
                    }
                }
            }
        }
        return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
    }

    private fun convertArgbToYuv420SemiPlanar(
        argb: IntArray,
        yuv: ByteArray,
        width: Int,
        height: Int
    ) {
        val frameSize = width * height
        var yIndex = 0
        var uvIndex = frameSize

        var index = 0
        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixel = argb[index++]
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                yuv[yIndex++] = y.coerceIn(0, 255).toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                    yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
    }

    private fun convertArgbToYuv420Planar(
        argb: IntArray,
        yuv: ByteArray,
        width: Int,
        height: Int
    ) {
        val frameSize = width * height
        val qFrameSize = frameSize / 4
        var yIndex = 0
        var uIndex = frameSize
        var vIndex = frameSize + qFrameSize

        var index = 0
        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixel = argb[index++]
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                yuv[yIndex++] = y.coerceIn(0, 255).toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    yuv[uIndex++] = u.coerceIn(0, 255).toByte()
                    yuv[vIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
    }

    private fun loadOptimizedBitmap(file: File, maxW: Int, maxH: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)

        var sampleSize = 1
        while (options.outWidth / sampleSize > maxW || options.outHeight / sampleSize > maxH) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null

        // Corrige orientação EXIF
        return try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return decoded
            }
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (rotated != decoded) {
                decoded.recycle()
            }
            rotated
        } catch (_: Exception) {
            decoded
        }
    }
}
