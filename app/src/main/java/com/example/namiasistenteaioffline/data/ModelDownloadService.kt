package com.example.namiasistenteaioffline.data

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.namiasistenteaioffline.MainActivity
import com.example.namiasistenteaioffline.R
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

class ModelDownloadService : Service() {

    private val activeDownloads = mutableMapOf<String, Job>()
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "model_download_channel"
        const val NOTIFICATION_ID_BASE = 1000
        const val ACTION_STOP_DOWNLOAD = "com.example.namiasistenteaioffline.STOP_DOWNLOAD"

        fun startDownload(context: Context, modelId: String, modelName: String, url: String, fileName: String) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                putExtra("MODEL_ID", modelId)
                putExtra("MODEL_NAME", modelName)
                putExtra("DOWNLOAD_URL", url)
                putExtra("FILE_NAME", fileName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopDownload(context: Context, modelId: String) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_STOP_DOWNLOAD
                putExtra("MODEL_ID", modelId)
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Nami:ModelDownloadWakeLock")
        // Aumentar a 1 hora para modelos grandes como Gemma 4 E4B (3.8GB)
        wakeLock?.acquire(60 * 60 * 1000L) 
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val modelId = intent?.getStringExtra("MODEL_ID") ?: return START_NOT_STICKY

        if (intent.action == ACTION_STOP_DOWNLOAD) {
            activeDownloads[modelId]?.cancel()
            activeDownloads.remove(modelId)
            if (activeDownloads.isEmpty()) {
                stopForeground(true)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        val modelName = intent.getStringExtra("MODEL_NAME") ?: "Modelo"
        val url = intent.getStringExtra("DOWNLOAD_URL") ?: return START_NOT_STICKY
        val fileName = intent.getStringExtra("FILE_NAME") ?: "$modelId.task"

        val notificationId = NOTIFICATION_ID_BASE + (modelId.hashCode() and 0x7FFFFFFF)

        startForeground(notificationId, createNotification(modelName, 0, notificationId))

        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val tempFile = File(filesDir, "$fileName.download")
                val destinationFile = File(filesDir, fileName)

                if (tempFile.exists()) tempFile.delete()

                val expectedSize = downloadFile(url, tempFile, modelId, modelName, notificationId)

                if (!isActive) return@launch

                val actualSize = tempFile.length()
                Log.d("DownloadService", "Descarga terminada. Esperado: $expectedSize, Real: $actualSize")

                if (actualSize < 100_000) {
                    throw Exception("Archivo demasiado pequeño ($actualSize bytes). Posiblemente corrupto.")
                }
                if (expectedSize > 0 && actualSize < expectedSize * 0.99) {
                    throw Exception("Archivo incompleto: se recibieron $actualSize de $expectedSize bytes.")
                }

                if (destinationFile.exists()) destinationFile.delete()
                val renamed = tempFile.renameTo(destinationFile)

                if (renamed) {
                    Log.d("DownloadService", "Modelo guardado en: ${destinationFile.absolutePath} (${destinationFile.length()} bytes)")
                    updateNotification(modelName, 100, notificationId, completed = true)

                    val doneIntent = Intent("MODEL_DOWNLOAD_COMPLETE").apply {
                        putExtra("MODEL_ID", modelId)
                        putExtra("STATUS", "COMPLETED")
                        setPackage(packageName)
                    }
                    sendBroadcast(doneIntent)
                } else {
                    throw Exception("No se pudo renombrar el archivo descargado.")
                }

            } catch (e: CancellationException) {
                Log.d("DownloadService", "Descarga cancelada para $modelId")
                File(filesDir, "$fileName.download").delete()
            } catch (e: Exception) {
                Log.e("DownloadService", "Error descargando $modelId: ${e.message}")
                updateNotification("Error: $modelName", 0, notificationId, error = true)
                File(filesDir, "$fileName.download").delete()
            } finally {
                activeDownloads.remove(modelId)
                if (activeDownloads.isEmpty()) {
                    stopForeground(false)
                    stopSelf()
                }
            }
        }
        activeDownloads[modelId] = job

        return START_NOT_STICKY
    }

    private suspend fun downloadFile(
        urlStr: String,
        destination: File,
        modelId: String,
        modelName: String,
        notificationId: Int
    ): Long {
        var currentUrl = urlStr
        var redirectCount = 0
        val maxRedirects = 10 // Aumentado para Hugging Face LFS

        while (redirectCount < maxRedirects) {
            Log.d("DownloadService", "Conectando a ($redirectCount): $currentUrl")
            val connection = URL(currentUrl).openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = 60000 // Aumentado
                readTimeout = 90000    // Aumentado para archivos grandes
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) NamiAI/1.0")
                instanceFollowRedirects = false
            }

            val status = connection.responseCode
            Log.d("DownloadService", "HTTP Status: $status")

            if (status in 300..308) {
                val newUrl = connection.getHeaderField("Location")
                    ?: throw Exception("Redirect sin header Location")
                connection.disconnect()
                currentUrl = newUrl
                redirectCount++
                continue
            }

            if (status == HttpURLConnection.HTTP_OK) {
                // Hugging Face a veces no envía Content-Length en el primer hit o usa chunked encoding
                val fileLength = connection.getHeaderField("Content-Length")?.toLongOrNull()
                    ?: connection.contentLength.toLong()
                
                // Si el tamaño es -1 o muy pequeño pero sabemos que es un modelo grande, 
                // intentamos obtenerlo de otra forma o simplemente descargamos.
                if (fileLength <= 0) {
                    Log.w("DownloadService", "Advertencia: Content-Length desconocido para $modelName")
                }

                connection.inputStream.use { input ->
                    FileOutputStream(destination).use { output ->
                        val buffer = ByteArray(256 * 1024) // Buffer más grande (256KB)
                        var total = 0L
                        var lastUpdate = 0L

                        while (true) {
                            if (!coroutineContext.isActive) break

                            val count = input.read(buffer)
                            if (count == -1) break

                            output.write(buffer, 0, count)
                            total += count

                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 2000) { // Actualizar UI cada 2 segundos
                                val progressPercent = if (fileLength > 0) (total * 100 / fileLength).toInt() else -1
                                updateNotification(modelName, progressPercent, notificationId)

                                val progressIntent = Intent("MODEL_DOWNLOAD_PROGRESS").apply {
                                    putExtra("MODEL_ID", modelId)
                                    putExtra("PROGRESS", if (fileLength > 0) total.toFloat() / fileLength else 0f)
                                    putExtra("TOTAL_BYTES", total)
                                    putExtra("EXPECTED_BYTES", fileLength)
                                    setPackage(packageName)
                                }
                                sendBroadcast(progressIntent)
                                lastUpdate = now
                            }
                        }

                        output.flush()
                        try { output.fd.sync() } catch (e: Exception) { }
                    }
                }
                connection.disconnect()
                return if (fileLength > 0) fileLength else destination.length()

            } else {
                val errorMsg = connection.errorStream?.bufferedReader()?.readText() ?: "Sin detalle"
                connection.disconnect()
                throw Exception("HTTP Error $status al descargar. Detalle: $errorMsg")
            }
        }
        throw Exception("Demasiadas redirecciones (máximo $maxRedirects)")
    }

    private fun createNotification(modelName: String, progress: Int, notificationId: Int): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Descargando modelo")
            .setContentText(modelName)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == -1)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(
        modelName: String,
        progress: Int,
        notificationId: Int,
        completed: Boolean = false,
        error: Boolean = false
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(
                if (completed) android.R.drawable.stat_sys_download_done
                else android.R.drawable.stat_sys_download
            )
            .setContentTitle(
                when {
                    completed -> "Descarga completa"
                    error -> "Error de descarga"
                    else -> "Descargando $modelName"
                }
            )
            .setContentText(
                when {
                    completed -> modelName
                    error -> "Hubo un problema, intenta de nuevo"
                    else -> "$progress%"
                }
            )
            .setProgress(100, progress, progress == -1 && !completed && !error)
            .setOngoing(!completed && !error)

        notificationManager.notify(notificationId, builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Descargas de Modelos",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        activeDownloads.values.forEach { it.cancel() }
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }
}