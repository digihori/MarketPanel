package com.digihori.marketpanel.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.digihori.marketpanel.data.settings.SettingsStore
import com.digihori.marketpanel.ui.dashboard.DashboardActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (SettingsStore(context.applicationContext).load().autoStart) {
                    context.startActivity(
                        Intent(context, DashboardActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        },
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
