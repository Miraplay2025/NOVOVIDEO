package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.example.MainActivity
import com.example.R
import com.example.data.db.AppDatabase
import com.example.data.model.MovementEffect
import com.example.data.model.ParsedAnimationConfig
import com.example.engine.RenderingManager
import com.example.engine.VideoEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class VideoRenderingService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "channel_app_animador_render"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (ACTION_CANCEL == intent.action) {
            RenderingManager.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val projectId = intent.getLongExtra(EXTRA_PROJECT_ID, -1L)
        val configs = intent.getParcelableArrayListExtra<RenderConfigParcel>(EXTRA_CONFIGS)
        val customOutputDirUri = intent.getStringExtra(EXTRA_CUSTOM_DIR_URI)
        val videoWidth = intent.getIntExtra(EXTRA_VIDEO_WIDTH, 1280)
        val videoHeight = intent.getIntExtra(EXTRA_VIDEO_HEIGHT, 720)
        val videoFps = intent.getIntExtra(EXTRA_VIDEO_FPS, 30)
        val videoBitrate = intent.getIntExtra(EXTRA_VIDEO_BITRATE, 5_000_000)
        val resolutionLabel = intent.getStringExtra(EXTRA_RESOLUTION_LABEL) ?: "${videoWidth}x${videoHeight}"

        if (projectId == -1L || configs.isNullOrEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification("Iniciando renderização...", 0, configs.size, 0))

        serviceScope.launch {
            processBatch(
                projectId = projectId,
                configs = configs,
                customOutputDirUri = customOutputDirUri,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                videoFps = videoFps,
                videoBitrate = videoBitrate,
                resolutionLabel = resolutionLabel
            )
        }

        return START_NOT_STICKY
    }

    private suspend fun processBatch(
        projectId: Long,
        configs: List<RenderConfigParcel>,
        customOutputDirUri: String?,
        videoWidth: Int,
        videoHeight: Int,
        videoFps: Int,
        videoBitrate: Int,
        resolutionLabel: String
    ) {
        val totalImages = configs.size
        RenderingManager.startBatch(totalImages)

        val bitrateFormatted = String.format(java.util.Locale.US, "%.1f Mbps", videoBitrate / 1_000_000f)
        RenderingManager.log("Configurações de Saída: $resolutionLabel | $videoFps FPS | $bitrateFormatted")

        val db = AppDatabase.getInstance(applicationContext)
        val images = db.projectDao().getImagesForProjectSync(projectId)
        val imagesByOrder = images.associateBy { it.orderIndex }

        // Diretório padrão conforme requisito 7: /Movies/AppAnimador/
        val moviesPublicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val defaultOutputDir = File(moviesPublicDir, "AppAnimador").apply { mkdirs() }

        var completedCount = 0

        for ((index, item) in configs.withIndex()) {
            if (RenderingManager.isCancelRequested) {
                RenderingManager.log("Renderização cancelada pelo usuário.")
                break
            }

            val currentImgNum = item.imageIndex
            val effect = MovementEffect.findById(item.movementId) ?: MovementEffect.ALL_EFFECTS[0]
            val durationSec = item.durationSeconds

            val projectImage = imagesByOrder[currentImgNum]
            if (projectImage == null) {
                RenderingManager.log("Erro: Arquivo de imagem para IMAGEM $currentImgNum não foi localizado.")
                continue
            }

            val imageFile = File(projectImage.filePath)
            if (!imageFile.exists()) {
                RenderingManager.log("Erro: Arquivo físico não encontrado em ${imageFile.absolutePath}")
                continue
            }

            val effectLogName = "${effect.name}, ${String.format(java.util.Locale.US, "%.1fs", durationSec)}"
            RenderingManager.log("Renderizando IMAGEM $currentImgNum ($effectLogName)...")

            val overallPercent = ((index.toFloat() / totalImages) * 100).toInt()
            updateNotification(
                "Processando imagem ${index + 1} de $totalImages",
                index + 1,
                totalImages,
                overallPercent
            )
            RenderingManager.updateProgress(
                currentImageIndex = index + 1,
                totalImages = totalImages,
                currentMovementName = effect.name,
                overallPercent = overallPercent
            )

            // Arquivo temporário de saída
            val tempOutputFile = File(cacheDir, "vid_render_${System.currentTimeMillis()}_${item.imageIndex}.mp4")

            val success = VideoEncoder.encodeImageToVideo(
                imageFile = imageFile,
                movementEffect = effect,
                durationSeconds = durationSec,
                outputFile = tempOutputFile,
                targetWidth = videoWidth,
                targetHeight = videoHeight,
                frameRate = videoFps,
                bitRate = videoBitrate,
                onFrameProgress = { currentFrame, totalFrames ->
                    val framePercent = ((index + (currentFrame.toFloat() / totalFrames)) / totalImages * 100).toInt()
                    RenderingManager.updateProgress(
                        currentImageIndex = index + 1,
                        totalImages = totalImages,
                        currentMovementName = effect.name,
                        overallPercent = framePercent
                    )
                },
                isCancelled = { RenderingManager.isCancelRequested }
            )

            if (success && tempOutputFile.exists() && tempOutputFile.length() > 0) {
                // Salva no destino conforme especificação
                val savedLocation = saveVideoToDestination(
                    tempFile = tempOutputFile,
                    fileName = "vid_${item.imageIndex}.mp4",
                    customOutputDirUri = customOutputDirUri,
                    defaultDir = defaultOutputDir
                )

                completedCount++
                RenderingManager.addOutputFile(savedLocation)
                RenderingManager.log("IMAGEM $currentImgNum salva com sucesso em $savedLocation ($resolutionLabel @ ${videoFps}fps)")
                tempOutputFile.delete()
            } else {
                if (RenderingManager.isCancelRequested) {
                    RenderingManager.log("Processo interrompido na IMAGEM $currentImgNum.")
                } else {
                    RenderingManager.log("Falha ao renderizar IMAGEM $currentImgNum.")
                }
                tempOutputFile.delete()
            }
        }

        if (RenderingManager.isCancelRequested) {
            updateNotification("Renderização cancelada", completedCount, totalImages, 100)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        // Marca projeto com data do último render
        val project = db.projectDao().getProjectSync(projectId)
        if (project != null) {
            db.projectDao().updateProject(project.copy(lastRenderedAt = System.currentTimeMillis()))
        }

        RenderingManager.completeBatch()
        updateNotification("Processamento de $completedCount vídeos concluído!", totalImages, totalImages, 100)
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun saveVideoToDestination(
        tempFile: File,
        fileName: String,
        customOutputDirUri: String?,
        defaultDir: File
    ): String {
        // Se usuário definiu pasta personalizada via SAF
        if (!customOutputDirUri.isNullOrBlank()) {
            try {
                val treeUri = Uri.parse(customOutputDirUri)
                val pickedDir = DocumentFile.fromTreeUri(applicationContext, treeUri)
                if (pickedDir != null && pickedDir.canWrite()) {
                    var targetDoc = pickedDir.findFile(fileName)
                    if (targetDoc != null) {
                        targetDoc.delete()
                    }
                    targetDoc = pickedDir.createFile("video/mp4", fileName)
                    if (targetDoc != null) {
                        contentResolver.openOutputStream(targetDoc.uri)?.use { outStream ->
                            FileInputStream(tempFile).use { inStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                        return targetDoc.uri.toString()
                    }
                }
            } catch (e: Exception) {
                RenderingManager.log("Aviso: Falha ao salvar no diretório SAF personalizado: ${e.message}. Salvando no diretório padrão.")
            }
        }

        // Diretório padrão: /Movies/AppAnimador/
        try {
            defaultDir.mkdirs()
            val destinationFile = File(defaultDir, fileName)
            tempFile.copyTo(destinationFile, overwrite = true)
            MediaScannerConnection.scanFile(
                applicationContext,
                arrayOf(destinationFile.absolutePath),
                arrayOf("video/mp4"),
                null
            )
            return destinationFile.absolutePath
        } catch (e: Exception) {
            // Em versões de Android com Scoped Storage estrito (MediaStore insert)
            return try {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/AppAnimador")
                    }
                }
                val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { outStream ->
                        FileInputStream(tempFile).use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    uri.toString()
                } else {
                    val appMovies = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "AppAnimador").apply { mkdirs() }
                    val fb = File(appMovies, fileName)
                    tempFile.copyTo(fb, overwrite = true)
                    fb.absolutePath
                }
            } catch (_: Exception) {
                val appMovies = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "AppAnimador").apply { mkdirs() }
                val fb = File(appMovies, fileName)
                tempFile.copyTo(fb, overwrite = true)
                fb.absolutePath
            }
        }
    }

    private fun buildNotification(
        content: String,
        current: Int,
        total: Int,
        percent: Int
    ): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, VideoRenderingService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("AppAnimador — Renderizando Vídeos")
            .setContentText(content)
            .setSubText("$percent%")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancelar", cancelPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(content: String, current: Int, total: Int, percent: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(content, current, total, percent))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Renderização de Vídeo",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progresso contínuo de renderização de vídeos em segundo plano"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    companion object {
        const val ACTION_START = "com.example.service.action.START"
        const val ACTION_CANCEL = "com.example.service.action.CANCEL"
        const val EXTRA_PROJECT_ID = "extra_project_id"
        const val EXTRA_CONFIGS = "extra_configs"
        const val EXTRA_CUSTOM_DIR_URI = "extra_custom_dir_uri"
        const val EXTRA_VIDEO_WIDTH = "extra_video_width"
        const val EXTRA_VIDEO_HEIGHT = "extra_video_height"
        const val EXTRA_VIDEO_FPS = "extra_video_fps"
        const val EXTRA_VIDEO_BITRATE = "extra_video_bitrate"
        const val EXTRA_RESOLUTION_LABEL = "extra_resolution_label"

        fun start(
            context: Context,
            projectId: Long,
            configs: List<ParsedAnimationConfig>,
            customDirUri: String?,
            videoWidth: Int = 1280,
            videoHeight: Int = 720,
            videoFps: Int = 30,
            videoBitrateBps: Int = 5_000_000,
            resolutionLabel: String = "720p (1280x720) [HD]"
        ) {
            val parcelList = ArrayList(configs.map {
                RenderConfigParcel(it.imageIndex, it.movementId, it.durationSeconds)
            })
            val intent = Intent(context, VideoRenderingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROJECT_ID, projectId)
                putParcelableArrayListExtra(EXTRA_CONFIGS, parcelList)
                putExtra(EXTRA_CUSTOM_DIR_URI, customDirUri)
                putExtra(EXTRA_VIDEO_WIDTH, videoWidth)
                putExtra(EXTRA_VIDEO_HEIGHT, videoHeight)
                putExtra(EXTRA_VIDEO_FPS, videoFps)
                putExtra(EXTRA_VIDEO_BITRATE, videoBitrateBps)
                putExtra(EXTRA_RESOLUTION_LABEL, resolutionLabel)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancel(context: Context) {
            val intent = Intent(context, VideoRenderingService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }
}
