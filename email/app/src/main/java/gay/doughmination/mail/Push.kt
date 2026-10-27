package gay.doughmination.mail

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * New-mail notifications. When Resend's webhook delivers an email, the Worker
 * sends a data message through Firebase to every install's FCM token; this
 * turns it into a notification that opens the thread.
 */
object Push {
    private const val CHANNEL = "mail"
    private const val TAG = "Push"

    /** False when the build has no google-services.json. */
    fun available(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    /** Tells the server where to reach this install. Called after sign-in. */
    suspend fun register(app: InboxApp) {
        if (!available(app)) return
        runCatching { app.api.registerFcm(FirebaseMessaging.getInstance().token.await()) }
            .onFailure { Log.w(TAG, "couldn't register for notifications", it) }
    }

    /** Before signing out: stop this install getting the account's mail. */
    suspend fun unregister(app: InboxApp) {
        if (!available(app)) return
        runCatching {
            val messaging = FirebaseMessaging.getInstance()
            app.api.unregisterFcm(messaging.token.await())
            // A fresh token next time, so nothing old can still point here.
            messaging.deleteToken().await()
        }.onFailure { Log.w(TAG, "couldn't unregister from notifications", it) }
    }

    fun show(context: Context, data: Map<String, String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "New mail", NotificationManager.IMPORTANCE_HIGH))
        }

        val tag = data["tag"] ?: data.hashCode().toString()
        // Same URLs as the site's, which MainActivity already knows how to open.
        val open = Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.INBOX_URL + (data["url"] ?: "/inbox")), context, MainActivity::class.java)
        val tap = PendingIntent.getActivity(
            context,
            tag.hashCode(),
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFEB459E.toInt())
            .setContentTitle(data["title"] ?: "New email")
            .setContentText(data["body"].orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(data["body"].orEmpty()))
            .setCategory(NotificationCompat.CATEGORY_EMAIL)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(tag, 0, notification)
    }
}

class PushService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data.isNotEmpty()) Push.show(this, message.data)
    }

    /** Firebase rotated this install's token; the server needs the new one. */
    override fun onNewToken(token: String) {
        val app = application as InboxApp
        if (!app.signedIn.value) return
        scope.launch { runCatching { app.api.registerFcm(token) } }
    }
}
