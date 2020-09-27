package com.example.adxl345;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class SettingsModel implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String DELAY_TIMER              = "delay_timer";
    public static final String POINTS_COUNT             = "points_count";
    public static final String CHECK_BOX_X              = "check_box_x";
    public static final String CHECK_BOX_Y              = "check_box_y";
    public static final String CHECK_BOX_Z              = "check_box_z";
    public static final String LAST_CONNECTED_DEVICE    = "last_connected_device";

    private int     delayTimer;
    private int     pointsCount;

    private boolean checkBoxX;
    private boolean checkBoxY;
    private boolean checkBoxZ;
    private boolean lastConnectedDevice;

    private SharedPreferences preferences;

    public SettingsModel(Context context) {
        this.delayTimer          = 100;
        this.pointsCount         = 1000;
        this.checkBoxX           = true;
        this.checkBoxY           = true;
        this.checkBoxZ           = true;
        this.lastConnectedDevice = false;

        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.registerOnSharedPreferenceChangeListener(this);
    }

    public int getDelayTimer() {
        return delayTimer;
    }

    public void setDelayTimer(int delayTimer) {
        this.delayTimer = delayTimer;
    }

    public int getPointsCount() {
        return pointsCount;
    }

    public void setPointsCount(int pointsCount) {
        this.pointsCount = pointsCount;
    }

    public boolean isCheckBoxX() {
        return checkBoxX;
    }

    public void setCheckBoxX(boolean checkBoxX) {
        this.checkBoxX = checkBoxX;
    }

    public boolean isCheckBoxY() {
        return checkBoxY;
    }

    public void setCheckBoxY(boolean checkBoxY) {
        this.checkBoxY = checkBoxY;
    }

    public boolean isCheckBoxZ() {
        return checkBoxZ;
    }

    public void setCheckBoxZ(boolean checkBoxZ) {
        this.checkBoxZ = checkBoxZ;
    }

    public boolean isLastConnectedDevice() {
        return lastConnectedDevice;
    }

    public void setLastConnectedDevice(boolean lastConnectedDevice) {
        this.lastConnectedDevice = lastConnectedDevice;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        switch (key) {
            case DELAY_TIMER: {
                int delay = Integer.parseInt(preferences.getString(key, "100"));
                setDelayTimer(delay);
                break;
            }
            case POINTS_COUNT: {
                int count = Integer.parseInt(preferences.getString(key, "1000"));
                setPointsCount(count);
                break;
            }
            case CHECK_BOX_X: {
                setCheckBoxX(preferences.getBoolean(key, true));
                break;
            }
            case CHECK_BOX_Y: {
                setCheckBoxY(preferences.getBoolean(key, true));
                break;
            }
            case CHECK_BOX_Z: {
                setCheckBoxZ(preferences.getBoolean(key, true));
                break;
            }
            case LAST_CONNECTED_DEVICE: {
                setLastConnectedDevice(preferences.getBoolean(key, false));
                break;
            }
        }
    }
}
