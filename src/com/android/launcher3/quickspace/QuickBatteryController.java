package com.android.launcher3.quickspace;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.android.launcher3.LauncherPrefs;

public class QuickBatteryController {

    private static final String ACTION_BLUETOOTH_BATTERY_UPDATE =
            "com.android.systemui.action.BLUETOOTH_BATTERY_UPDATE";

    private final Context mContext;
    private final QuickspaceController mController;
    private boolean mRegistered = false;

    private String mDeviceName = null;
    private int mBatteryLevel = -1;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_BLUETOOTH_BATTERY_UPDATE.equals(intent.getAction())) {
                mDeviceName = intent.getStringExtra("device_name");
                mBatteryLevel = intent.getIntExtra("battery_level", -1);
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
            registerReceiver();
        } else {
            unRegisterReceiver();
            clearData();
        }
    }

    public void onPause() {
        unRegisterReceiver();
    }

    private void registerReceiver() {
        if (mRegistered) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_BLUETOOTH_BATTERY_UPDATE);
        mContext.registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);
        mRegistered = true;
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
        if (mDeviceName == null && mBatteryLevel == -1) return;
        mDeviceName = null;
        mBatteryLevel = -1;
        mController.notifyListeners();
    }

    public String getDeviceName() {
        return mDeviceName;
    }

    public int getBatteryLevel() {
        return mBatteryLevel;
    }
}
