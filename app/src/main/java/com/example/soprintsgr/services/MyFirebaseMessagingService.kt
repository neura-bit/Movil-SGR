package com.example.soprintsgr.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.soprintsgr.MainActivity
import com.example.soprintsgr.R
import com.example.soprintsgr.data.AuthService
import com.example.soprintsgr.data.SessionManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Check if message contains a data payload.
        var title = remoteMessage.data["title"]
        var body = remoteMessage.data["body"]

        // If not in data, check notification payload
        if (title == null && body == null) {
            remoteMessage.notification?.let {
                title = it.title
                body = it.body
            }
        }

        // If we have at least a title or body, send notification
        if (title != null || body != null) {
            sendNotification(title, body)
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String) {
        val sessionManager = SessionManager(this)
        val userId = sessionManager.getIdUsuario()
        
        if (userId != -1) {
            val authService = AuthService(this)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    authService.updateFcmToken(userId.toLong(), token)
                    Log.d(TAG, "Token sent to server successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending token to server", e)
                }
            }
        }
    }

    private fun sendNotification(title: String?, messageBody: String?) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0 /* Request code */, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )

        val channelId = getString(R.string.default_notification_channel_id)
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher) // Usando el icono de la app
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Para versiones anteriores a Android 8.0
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Mostrar en pantalla de bloqueo
            .setDefaults(NotificationCompat.DEFAULT_ALL) // Vibrar, sonar, luz

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.default_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH // Importancia ALTA para heads-up notifications
            )
            channel.enableVibration(true)
            channel.lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0 /* ID of notification */, notificationBuilder.build())
    }

    companion object {
        private const val TAG = "MyFirebaseMsgService"
    }
}
