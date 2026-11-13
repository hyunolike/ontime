package io.github.hyunolike.ontime.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import io.github.hyunolike.ontime.design.R as DesignR

/**
 * 앱이 화면 뒤로 가도 프로세스가 멈추지 않게 붙잡아 둔다.
 *
 * Pleos Connect에서 경로를 요청하면 내비가 앞으로 나오는데, 그때 시스템이 온타임을
 * 동결해 콜백을 받지 못하는 것을 에뮬레이터에서 확인했다. 계산은 [OnTimeRuntime]이 하고
 * 이 서비스는 프로세스를 포그라운드로 유지하는 일만 한다.
 */
class KeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "여유 시간 계산", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(DesignR.drawable.ic_schedule)
            .setContentTitle("온타임")
            .setContentText("마감까지의 여유를 계산하고 있습니다")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    companion object {
        private const val CHANNEL = "ontime-slack"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            context.startForegroundService(Intent(context, KeepAliveService::class.java))
        }
    }
}
