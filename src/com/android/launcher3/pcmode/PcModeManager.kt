package com.android.launcher3.pcmode

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.android.launcher3.Launcher

/**
 * Singleton manager for PC Mode functionality
 * Handles lifecycle and state management for PC Mode UI
 */
class PcModeManager private constructor(private val context: Context) {
    
    private var controller: PcModeController? = null
    private var launcher: Launcher? = null
    private var isActive = false
    
    companion object {
        private const val TAG = "PcModeManager"
        
        @Volatile
        private var INSTANCE: PcModeManager? = null
        
        /**
         * Get singleton instance
         * Can accept Context or Launcher (Launcher extends Context)
         */
        @JvmStatic
        fun getInstance(context: Context): PcModeManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PcModeManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        /**
         * Overload for Launcher specifically
         */
        @JvmStatic
        fun getInstance(launcher: Launcher): PcModeManager {
            return getInstance(launcher as Context)
        }
    }
    
    /**
     * Initialize PC Mode with Launcher instance
     */
    fun init(launcher: Launcher) {
        this.launcher = launcher
        this.controller = PcModeController(launcher)
        this.controller?.init()
        Log.d(TAG, "PcModeManager initialized")
    }
    
    /**
     * Activate PC Mode UI
     */
    fun activatePcMode(connectionInfo: String = "Connected") {
        if (isActive) {
            Log.d(TAG, "PC Mode already active")
            return
        }
        
        Log.i(TAG, "Activating PC Mode: $connectionInfo")
        controller?.activatePcMode()
        controller?.updateDesktopModeStatus("Connected at $connectionInfo")
        isActive = true
    }
    
    /**
     * Deactivate PC Mode UI
     */
    fun deactivatePcMode() {
        if (!isActive) {
            Log.d(TAG, "PC Mode already inactive")
            return
        }
        
        Log.i(TAG, "Deactivating PC Mode")
        controller?.deactivatePcMode()
        isActive = false
    }
    
    /**
     * Check if VNCFlinger service is currently running
     * If running, automatically activate PC Mode
     */
    fun checkVncState() {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.let { activityManager ->
                val runningServices = activityManager.getRunningServices(Int.MAX_VALUE)
                for (service in runningServices) {
                    if (service.service.className.contains("VncFlinger")) {
                        Log.d(TAG, "VNCFlinger is running, activating PC Mode")
                        activatePcMode("Reconnected")
                        return
                    }
                }
            }
            Log.d(TAG, "VNCFlinger is not running")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking VNC state", e)
        }
    }
    
    /**
     * Cleanup PC Mode resources
     */
    fun cleanup() {
        if (isActive) {
            deactivatePcMode()
        }
        controller = null
        launcher = null
        Log.d(TAG, "PcModeManager cleaned up")
    }
    
    /**
     * Check if PC Mode is currently active
     */
    fun isActive(): Boolean = isActive
}
