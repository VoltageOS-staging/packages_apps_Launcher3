package com.android.launcher3.quickspace;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.android.launcher3.LauncherPrefs;

public class QuickBatteryController {

    // Must match the action string in SystemUI's BluetoothControllerImpl
    private static final String ACTION_BLUETOOTH_BATTERY_UPDATE =
            "com.android.systemui.action.BLUETOOTH_BATTERY_UPDATE";

    private final Context mContext;
    private final QuickspaceController mController;
    private boolean mRegistered = false;

    private String mDeviceName = null;
    private int mBatteryLevel = -1;
    private boolean mIsAudio = false;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_BLUETOOTH_BATTERY_UPDATE.equals(intent.getAction())) {
                boolean isConnected = intent.getBooleanExtra("is_connected", false);

                if (isConnected) {
                    mDeviceName = intent.getStringExtra("device_name");
                    mBatteryLevel = intent.getIntExtra("battery_level", -1);
                    mIsAudio = intent.getBooleanExtra("is_audio", false);
                } else {
                    // Fix stuck UI: Explicit clear if system says disconnected
                    mDeviceName = null;
                    mBatteryLevel = -1;
                    mIsAudio = false;
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
            mDeviceName = null;
            mBatteryLevel = -1;
            mIsAudio = false;
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

    public String getDeviceName() {
        return mDeviceName;
    }

    public int getBatteryLevel() {
        return mBatteryLevel;
    }

    public boolean isAudioDevice() {
        return mIsAudio;
    }
}
