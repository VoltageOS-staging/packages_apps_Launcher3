package com.android.launcher3.pcmode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState

class PcModeReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "PcModeReceiver"
        private const val ACTION_PC_MODE_STATE = "com.libremobileos.vncflinger.PC_MODE_STATE"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_PC_MODE_STATE) {
            val isActive = intent.getBooleanExtra("active", false)
            val ipAddress = intent.getStringExtra("ip_address") ?: "Unknown"
            val port = intent.getIntExtra("port", 5900)
            
            Log.d(TAG, "PC Mode state received: active=$isActive, ip=$ipAddress:$port")
            
            // Get PcModeManager and toggle
            val manager = PcModeManager.getInstance(context)
            
            if (isActive) {
                manager.activatePcMode("$ipAddress:$port")
            } else {
                manager.deactivatePcMode()
            }
        }
    }
}
