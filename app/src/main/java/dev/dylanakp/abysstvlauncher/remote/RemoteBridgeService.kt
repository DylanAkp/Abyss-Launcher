package dev.dylanakp.abysstvlauncher.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.dylanakp.abysstvlauncher.MainActivity
import dev.dylanakp.abysstvlauncher.R
import dev.dylanakp.abysstvlauncher.data.LauncherPreferences
import org.koin.android.ext.android.inject

class RemoteBridgeService : Service() {

    private val httpServer: RemoteHttpServer by inject()
    private val controller: TvCommandController by inject()
    private val preferences: LauncherPreferences by inject()

    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var configuredMdnsName = DEFAULT_MDNS_NAME
    private var mdnsName = DEFAULT_MDNS_NAME
    private var registrationAttempts = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        httpServer.start(TvCommandController.DEFAULT_PORT)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!preferences.remoteServerEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        configuredMdnsName = intent?.getStringExtra(EXTRA_SERVICE_NAME)
            ?.takeIf { it.isNotBlank() } ?: DEFAULT_MDNS_NAME
        mdnsName = configuredMdnsName
        if (!httpServer.isRunning) {
            httpServer.start(TvCommandController.DEFAULT_PORT)
        }
        unregisterMdns()
        registerMdns(TvCommandController.DEFAULT_PORT)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        unregisterMdns()
        httpServer.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerMdns(port: Int) {
        runCatching {
            val mgr = getSystemService(Context.NSD_SERVICE) as NsdManager
            nsdManager = mgr
            val info = NsdServiceInfo().apply {
                serviceName = mdnsName
                serviceType = "_http._tcp."
                setPort(port)
            }
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    val actual = serviceInfo.serviceName
                    if (!actual.equals(mdnsName, ignoreCase = true)) {
                        Log.w(TAG, "mDNS name conflict requested=$mdnsName actual=$actual")
                        if (registrationAttempts < MAX_MDNS_RENAMES) {
                            registrationAttempts++
                            mdnsName = "$configuredMdnsName-$registrationAttempts"
                            unregisterMdns()
                            registerMdns(port)
                        }
                    } else {
                        Log.i(TAG, "mDNS registered as $actual on port ${serviceInfo.port}")
                    }
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "mDNS registration failed code=$errorCode name=$mdnsName")
                }

                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                    Log.i(TAG, "mDNS unregistered ${serviceInfo.serviceName}")
                }

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "mDNS unregistration failed code=$errorCode name=${serviceInfo.serviceName}")
                }
            }
            registrationListener = listener
            mgr.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            Log.w(TAG, "mDNS registration error", it)
        }
    }

    private fun unregisterMdns() {
        val listener = registrationListener ?: return
        runCatching { nsdManager?.unregisterService(listener) }
            .onFailure { Log.w(TAG, "mDNS unregister error", it) }
        registrationListener = null
    }

    private fun createNotificationChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Abyss Web Remote",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }
        mgr.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val ip = controller.getLocalIpAddress() ?: "TV"
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Abyss Web Remote")
            .setContentText("http://$ip:${TvCommandController.DEFAULT_PORT}")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pending)
            .build()
    }

    companion object {
        private const val TAG = "RemoteBridge"
        private const val CHANNEL_ID = "abyss_remote_bridge"
        private const val NOTIFICATION_ID = 5400
        private const val DEFAULT_MDNS_NAME = "abyss-tv"
        private const val MAX_MDNS_RENAMES = 2
        const val EXTRA_SERVICE_NAME = "dev.dylanakp.abysstvlauncher.remote.MDNS_NAME"

        fun start(context: Context, serviceName: String? = null) {
            val intent = Intent(context, RemoteBridgeService::class.java)
            if (!serviceName.isNullOrBlank()) intent.putExtra(EXTRA_SERVICE_NAME, serviceName)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RemoteBridgeService::class.java))
        }
    }
}
