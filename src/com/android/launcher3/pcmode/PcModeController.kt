package com.android.launcher3.pcmode

import android.animation.ObjectAnimator
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.view.isVisible
import com.android.launcher3.R
import com.android.launcher3.Launcher
import com.android.launcher3.dragndrop.DragLayer

/**
 * Controller for modern PC Mode UI
 * Manages the desktop view, taskbar, start menu, and quick settings
 */
class PcModeController(private val launcher: Launcher) {
    
    private val context: Context = launcher
    private val dragLayer: DragLayer = launcher.dragLayer
    
    // Views
    private var pcModeRoot: ViewGroup? = null
    private var taskbar: View? = null
    private var startMenu: View? = null
    private var quickSettings: View? = null
    
    // State
    private var isStartMenuVisible = false
    private var isQuickSettingsVisible = false
    private var isPcModeActive = false
    
    fun init() {
        // Inflate PC mode layout
        val inflater = LayoutInflater.from(context)
        pcModeRoot = inflater.inflate(R.layout.pc_mode_layout, dragLayer, false) as ViewGroup
        
        // Get view references
        taskbar = pcModeRoot?.findViewById(R.id.pc_taskbar)
        startMenu = pcModeRoot?.findViewById(R.id.pc_start_menu)
        quickSettings = pcModeRoot?.findViewById(R.id.pc_quick_settings)
        
        setupListeners()
    }
    
    private fun setupListeners() {
        // Start button click
        pcModeRoot?.findViewById<View>(R.id.start_button)?.setOnClickListener {
            toggleStartMenu()
        }
        
        // System settings button click  
        pcModeRoot?.findViewById<View>(R.id.system_settings_button)?.setOnClickListener {
            toggleQuickSettings()
        }
        
        // All apps button in start menu
        startMenu?.findViewById<View>(R.id.all_apps_button)?.setOnClickListener {
            hideStartMenu()
            launcher.stateManager.goToState(com.android.launcher3.LauncherState.ALL_APPS)
        }
        
        // Disconnect button in quick settings
        quickSettings?.findViewById<View>(R.id.disconnect_button)?.setOnClickListener {
            deactivatePcMode()
        }
    }
    
    /**
     * Activates PC mode - shows modern desktop UI
     */
    fun activatePcMode() {
        if (isPcModeActive) return
        
        isPcModeActive = true
        
        // Add PC mode root to drag layer
        if (pcModeRoot?.parent == null) {
            dragLayer.addView(pcModeRoot)
        }
        
        // Hide normal launcher UI
        launcher.workspace.alpha = 0f
        launcher.hotseat.alpha = 0f
        
        // Show taskbar with animation
        taskbar?.translationY = taskbar?.height?.toFloat() ?: 0f
        taskbar?.animate()
            ?.translationY(0f)
            ?.setDuration(300)
            ?.setInterpolator(DecelerateInterpolator())
            ?.start()
            
        // Update system UI
        updateSystemBarsForPcMode(true)
    }
    
    /**
     * Deactivates PC mode - returns to normal launcher
     */
    fun deactivatePcMode() {
        if (!isPcModeActive) return
        
        isPcModeActive = false
        
        // Hide start menu and quick settings
        hideStartMenu()
        hideQuickSettings()
        
        // Animate taskbar out
        taskbar?.animate()
            ?.translationY(taskbar?.height?.toFloat() ?: 0f)
            ?.setDuration(250)
            ?.withEndAction {
                dragLayer.removeView(pcModeRoot)
            }
            ?.start()
        
        // Show normal launcher UI
        ObjectAnimator.ofFloat(launcher.workspace, View.ALPHA, 1f).apply {
            duration = 200
            startDelay = 100
        }.start()
        
        ObjectAnimator.ofFloat(launcher.hotseat, View.ALPHA, 1f).apply {
            duration = 200
            startDelay = 100
        }.start()
        
        // Update system UI
        updateSystemBarsForPcMode(false)
    }
    
    /**
     * Toggles the start menu visibility
     */
    private fun toggleStartMenu() {
        if (isStartMenuVisible) {
            hideStartMenu()
        } else {
            showStartMenu()
        }
    }
    
    /**
     * Shows the start menu with animation
     */
    private fun showStartMenu() {
        if (isStartMenuVisible) return
        
        isStartMenuVisible = true
        hideQuickSettings() // Hide quick settings if open
        
        startMenu?.isVisible = true
        startMenu?.alpha = 0f
        startMenu?.translationY = 50f
        
        startMenu?.animate()
            ?.alpha(1f)
            ?.translationY(0f)
            ?.setDuration(250)
            ?.setInterpolator(DecelerateInterpolator())
            ?.start()
    }
    
    /**
     * Hides the start menu with animation
     */
    private fun hideStartMenu() {
        if (!isStartMenuVisible) return
        
        isStartMenuVisible = false
        
        startMenu?.animate()
            ?.alpha(0f)
            ?.translationY(50f)
            ?.setDuration(200)
            ?.withEndAction {
                startMenu?.isVisible = false
            }
            ?.start()
    }
    
    /**
     * Toggles the quick settings panel visibility
     */
    private fun toggleQuickSettings() {
        if (isQuickSettingsVisible) {
            hideQuickSettings()
        } else {
            showQuickSettings()
        }
    }
    
    /**
     * Shows the quick settings panel with animation
     */
    private fun showQuickSettings() {
        if (isQuickSettingsVisible) return
        
        isQuickSettingsVisible = true
        hideStartMenu() // Hide start menu if open
        
        quickSettings?.isVisible = true
        quickSettings?.alpha = 0f
        quickSettings?.translationY = 50f
        
        quickSettings?.animate()
            ?.alpha(1f)
            ?.translationY(0f)
            ?.setDuration(250)
            ?.setInterpolator(DecelerateInterpolator())
            ?.start()
    }
    
    /**
     * Hides the quick settings panel with animation
     */
    private fun hideQuickSettings() {
        if (!isQuickSettingsVisible) return
        
        isQuickSettingsVisible = false
        
        quickSettings?.animate()
            ?.alpha(0f)
            ?.translationY(50f)
            ?.setDuration(200)
            ?.withEndAction {
                quickSettings?.isVisible = false
            }
            ?.start()
    }
    
    /**
     * Updates system bars (status bar, navigation bar) for PC mode
     */
    private fun updateSystemBarsForPcMode(pcModeActive: Boolean) {
        // You can customize system UI visibility here
        // For example, hide navigation bar in PC mode
        if (pcModeActive) {
            // Hide system navigation bar
            launcher.window?.decorView?.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        } else {
            // Restore normal system UI
            launcher.window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }
    
    /**
     * Updates the network icon in system tray
     */
    fun updateNetworkIcon(connected: Boolean, type: NetworkType) {
        // TODO: Implement network status updates
    }
    
    /**
     * Updates the battery icon in system tray
     */
    fun updateBatteryIcon(level: Int, charging: Boolean) {
        // TODO: Implement battery status updates
    }
    
    /**
     * Updates the desktop mode status text
     */
    fun updateDesktopModeStatus(status: String) {
        quickSettings?.findViewById<android.widget.TextView>(R.id.desktop_mode_status)?.text = status
    }
    
    enum class NetworkType {
        WIFI, MOBILE, ETHERNET, DISCONNECTED
    }
    
    companion object {
        private const val TAG = "PcModeController"
    }
}
