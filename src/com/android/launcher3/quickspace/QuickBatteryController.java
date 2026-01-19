package com.android.launcher3.quickspace;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;

import com.android.launcher3.LauncherPrefs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class QuickBatteryController {

    // Must match the action string in SystemUI's BluetoothControllerImpl
    private static final String ACTION_BLUETOOTH_BATTERY_UPDATE =
            "com.android.systemui.action.BLUETOOTH_BATTERY_UPDATE";

    private static final long SESSION_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes

    private final Context mContext;
    private final QuickspaceController mController;
    private boolean mRegistered = false;

    // Helper class to store device info
    public static class BatteryDevice {
        public String name;
        public int level;
        public boolean isAudio;

        public BatteryDevice(String name, int level, boolean isAudio) {
            this.name = name;
            this.level = level;
            this.isAudio = isAudio;
        }
    }

    private final List<BatteryDevice> mDevices = new ArrayList<>();
    
    // Smart State Tracking
    private String mCurrentDeviceName = null;
    private int mCurrentIndex = 0;
    private long mLastInteractionTime = 0;
    private final Set<String> mAlertedDevices = new HashSet<>();

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_BLUETOOTH_BATTERY_UPDATE.equals(intent.getAction())) {
                // Clear old list
                mDevices.clear();
                
                // Read Lists from Intent (Requires updated FWB patch)
                ArrayList<String> names = intent.getStringArrayListExtra("device_list_names");
                ArrayList<Integer> levels = intent.getIntegerArrayListExtra("device_list_levels");
                ArrayList<String> audioFlags = intent.getStringArrayListExtra("device_list_audio");

                // Populate local list
                if (names != null && levels != null && !names.isEmpty()) {
                    for (int i = 0; i < names.size(); i++) {
                        boolean isAudio = false;
                        if (audioFlags != null && i < audioFlags.size()) {
                            // "true" / "false" strings because Intent bool array is buggy across processes sometimes
                            isAudio = Boolean.parseBoolean(audioFlags.get(i));
                        }
                        mDevices.add(new BatteryDevice(names.get(i), levels.get(i), isAudio));
                    }

                    // --- SMART SELECTION LOGIC ---

                    // 1. TIMEOUT CHECK: Reset to primary (0) if session expired (screen off/idle)
                    if (SystemClock.elapsedRealtime() - mLastInteractionTime > SESSION_TIMEOUT_MS) {
                        mCurrentDeviceName = null; // Will cause default to index 0 below
                    }

                    // 2. STABLE RESTORE: Find index of previously selected device by Name
                    // (Prevents UI jumping if a device connects/disconnects and shifts the list)
                    int newIndex = 0;
                    if (mCurrentDeviceName != null) {
                        for (int i = 0; i < mDevices.size(); i++) {
                            if (mDevices.get(i).name.equals(mCurrentDeviceName)) {
                                newIndex = i;
                                break;
                            }
                        }
                    }
                    mCurrentIndex = newIndex;

                    // 3. URGENT LOW OVERRIDE
                    // If a device drops to Critical (<15%) and we haven't alerted yet, force switch to it.
                    if (mDevices.size() > 1) {
                        for (int i = 0; i < mDevices.size(); i++) {
                            BatteryDevice d = mDevices.get(i);
                            
                            // Reset alert flag if device charges back up (> 20%)
                            if (d.level > 20) {
                                mAlertedDevices.remove(d.name);
                            }

                            // Trigger override if: Critical AND Not Alerted
                            if (d.level <= 15 && !mAlertedDevices.contains(d.name)) {
                                mCurrentIndex = i;
                                mCurrentDeviceName = d.name;
                                mAlertedDevices.add(d.name);
                                mLastInteractionTime = SystemClock.elapsedRealtime(); // Treat as interaction
                                break; // Only override for one device at a time to prevent fighting
                            }
                        }
                    }
                    
                    // Sync name just in case it changed via index logic above
                    if (!mDevices.isEmpty()) {
                        mCurrentDeviceName = mDevices.get(mCurrentIndex).name;
                    }

                } else {
                    // Fallback or empty
                    mCurrentIndex = 0;
                    mCurrentDeviceName = null;
                }
                mController.notifyListeners();
            }
        }
    };

    public QuickBatteryController(Context context, QuickspaceController controller) {
        mContext = context;
        mController = controller;
    }

    public void onResume() {
        if (LauncherPrefs.SHOW_QUICKSPACE_BATTERY.get(mContext)) {
            // registerReceiver returns the sticky intent if one exists (because of FWB patch)
            Intent stickyIntent = registerReceiver();
            if (stickyIntent != null) {
                // Process the immediate state so the UI updates right away
                // without waiting for a new broadcast event
                mReceiver.onReceive(mContext, stickyIntent);
            }
        } else {
            unRegisterReceiver();
            // Clear data explicitly when disabled via toggle
            mDevices.clear();
            mCurrentDeviceName = null;
            mAlertedDevices.clear();
            mController.notifyListeners();
        }
    }

    public void onPause() {
        unRegisterReceiver();
    }

    private Intent registerReceiver() {
        if (mRegistered) return null;
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_BLUETOOTH_BATTERY_UPDATE);
        mRegistered = true;
        // RECEIVER_EXPORTED is required for dynamic receivers on newer Android versions
        return mContext.registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    private void unRegisterReceiver() {
        if (!mRegistered) return;
        try {
            mContext.unregisterReceiver(mReceiver);
        } catch (Exception e) {
            // ignore
        }
        mRegistered = false;
    }

    private void clearData() {
        if (mDevices.isEmpty()) return;
        mDevices.clear();
        mCurrentDeviceName = null;
        mController.notifyListeners();
    }

    // --- Public API for QuickSpaceView ---

    /**
     * Cycles to the next device in the list.
     * Called when the user taps the battery pill.
     */
    public void advanceIndex() {
        if (mDevices.isEmpty()) return;
        mCurrentIndex = (mCurrentIndex + 1) % mDevices.size();
        mCurrentDeviceName = mDevices.get(mCurrentIndex).name;
        mLastInteractionTime = SystemClock.elapsedRealtime(); // Reset timeout on manual interaction
        // Note: We don't notifyListeners here because the View handles the animation 
        // and calls update on itself.
    }

    public BatteryDevice getCurrentDevice() {
        if (mDevices.isEmpty()) return null;
        if (mCurrentIndex >= mDevices.size()) mCurrentIndex = 0;
        return mDevices.get(mCurrentIndex);
    }

    public int getDeviceCount() {
        return mDevices.size();
    }

    public int getCurrentIndex() {
        return mCurrentIndex;
    }

    public String getDeviceName() {
        BatteryDevice d = getCurrentDevice();
        return d != null ? d.name : null;
    }

    public int getBatteryLevel() {
        BatteryDevice d = getCurrentDevice();
        return d != null ? d.level : -1;
    }

    public boolean isAudioDevice() {
        BatteryDevice d = getCurrentDevice();
        return d != null ? d.isAudio : false;
    }

    public void launchBatterySettings() {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
        } catch (Exception e) {
            // Fallback
        }
    }
}
