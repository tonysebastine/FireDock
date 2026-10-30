package com.appletv.launcher;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * Quick settings / tvOS 18 Control Center:
 * Glass platter with inline Volume, Wi-Fi toggle/status, Bluetooth Devices & TWS battery levels,
 * Display, and TV system shortcuts.
 */
public final class QuickSettingsActivity extends Activity {

    private AudioManager audio;
    private WifiManager wifi;
    private BluetoothAdapter bluetooth;
    private int streamType = AudioManager.STREAM_MUSIC;
    private TextView volumeStatus;
    private TextView wifiStatus;
    private View wifiDot;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_quick_settings);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        bluetooth = BluetoothAdapter.getDefaultAdapter();

        findViewById(R.id.scrim).setOnClickListener(v -> finish());
        LinearLayout rows = findViewById(R.id.rows);

        // 1. Volume control row (left/right to adjust)
        addVolumeRow(rows);

        // 2. Wi-Fi control row (Center/OK toggles On/Off, Left/Right or Long Click opens Wi-Fi settings)
        addWifiRow(rows);

        // 3. Bluetooth devices / TWS battery row
        addBluetoothRow(rows);

        // 4. Picture / Display
        addRow(rows, R.drawable.ic_picture, R.string.quick_picture, 0,
                new Intent(Settings.ACTION_DISPLAY_SETTINGS));

        // 5. Apps Manager
        addRow(rows, R.drawable.ic_apps, R.string.quick_apps, R.string.quick_apps_hint,
                new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));

        // 6. Full TV System Settings
        addRow(rows, R.drawable.ic_settings, R.string.tv_settings, 0,
                new Intent(Settings.ACTION_SETTINGS));

        // 7. Launcher Settings
        addRow(rows, R.drawable.ic_home_settings, R.string.launcher_settings, 0,
                new Intent(this, MainActivity.class)
                        .setAction(MainActivity.ACTION_LAUNCHER_SETTINGS));

        rows.getChildAt(0).requestFocus();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        finish();
        overridePendingTransition(0, 0);
    }

    private void addRow(LinearLayout rows, int icon, int title, int status, Intent target) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        TextView hint = row.findViewById(R.id.status);
        if (status != 0) hint.setText(status);
        else hint.setVisibility(View.GONE);
        row.setOnClickListener(v -> {
            try {
                startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
            finish();
        });
        rows.addView(row);
    }

    private void addVolumeRow(LinearLayout rows) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(R.drawable.ic_volume);
        ((TextView) row.findViewById(R.id.title)).setText(R.string.quick_volume);
        row.findViewById(R.id.dot).setVisibility(View.GONE);

        volumeStatus = row.findViewById(R.id.status);
        refreshVolumeStatus();

        row.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                int direction = (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
                        ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER;
                audio.adjustStreamVolume(streamType, direction, AudioManager.FLAG_SHOW_UI);
                refreshVolumeStatus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                audio.adjustStreamVolume(streamType, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                refreshVolumeStatus();
                return true;
            }
            return false;
        });
        rows.addView(row);
    }

    private void refreshVolumeStatus() {
        if (volumeStatus == null || audio == null) return;
        int cur = audio.getStreamVolume(streamType);
        int max = audio.getStreamMaxVolume(streamType);
        volumeStatus.setText(cur + " / " + max + "  ◀ ▶");
        volumeStatus.setVisibility(View.VISIBLE);
    }

    private void addWifiRow(LinearLayout rows) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(R.drawable.ic_wifi);
        ((TextView) row.findViewById(R.id.title)).setText(R.string.quick_wifi_toggle);

        wifiDot = row.findViewById(R.id.dot);
        wifiStatus = row.findViewById(R.id.status);
        refreshWifiStatus();

        row.setOnClickListener(v -> {
            if (wifi != null) {
                boolean target = !wifi.isWifiEnabled();
                wifi.setWifiEnabled(target);
                Toast.makeText(this, target ? "Turning Wi-Fi ON..." : "Turning Wi-Fi OFF...", Toast.LENGTH_SHORT).show();
                v.postDelayed(this::refreshWifiStatus, 600);
            }
        });

        row.setOnLongClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
                return true;
            } catch (Exception e) {
                return false;
            }
        });

        rows.addView(row);
    }

    private void refreshWifiStatus() {
        if (wifi == null || wifiStatus == null) return;
        boolean enabled = wifi.isWifiEnabled();
        if (!enabled) {
            wifiStatus.setText(R.string.off);
            wifiDot.setVisibility(View.GONE);
            return;
        }
        WifiInfo info = wifi.getConnectionInfo();
        String ssid = info != null ? info.getSSID() : null;
        if (ssid != null) {
            ssid = ssid.replace("\"", "").trim();
            if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) {
                wifiStatus.setText(ssid);
                wifiDot.setVisibility(View.VISIBLE);
                return;
            }
        }
        wifiStatus.setText(R.string.on);
        wifiDot.setVisibility(View.VISIBLE);
    }

    private void addBluetoothRow(LinearLayout rows) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(R.drawable.ic_settings);
        ((TextView) row.findViewById(R.id.title)).setText(R.string.quick_bluetooth);

        TextView btStatus = row.findViewById(R.id.status);
        View btDot = row.findViewById(R.id.dot);

        // Inspect paired audio devices (TWS / Headsets) and battery
        String summary = getBluetoothSummary();
        btStatus.setText(summary);
        btStatus.setVisibility(View.VISIBLE);
        btDot.setVisibility(summary.contains("%") || summary.contains("Connected") ? View.VISIBLE : View.GONE);

        row.setOnClickListener(v -> {
            try {
                // Open native controllers / bluetooth manager safely
                startActivity(new Intent("android.settings.CONTROLLERS_SETTINGS")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    finish();
                } catch (Exception ignored) {
                    Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
                }
            }
        });

        rows.addView(row);
    }

    private String getBluetoothSummary() {
        if (bluetooth == null || !bluetooth.isEnabled()) return getString(R.string.off);
        try {
            Set<BluetoothDevice> paired = bluetooth.getBondedDevices();
            if (paired == null || paired.isEmpty()) return getString(R.string.not_connected);
            BluetoothDevice bestConnected = null;
            int bestBattery = -1;
            for (BluetoothDevice d : paired) {
                int battery = getDeviceBattery(d);
                if (battery >= 0) {
                    bestConnected = d;
                    bestBattery = battery;
                    break;
                }
            }
            if (bestConnected != null) {
                String name = bestConnected.getName();
                return (name != null ? name : "Audio") + " " + bestBattery + "%";
            }
            return paired.size() + " Paired";
        } catch (Exception e) {
            return getString(R.string.on);
        }
    }

    private int getDeviceBattery(BluetoothDevice d) {
        try {
            Method m = d.getClass().getMethod("getBatteryLevel");
            Object res = m.invoke(d);
            if (res instanceof Integer) {
                int level = (Integer) res;
                if (level >= 0) return level;
            }
        } catch (Exception ignored) {}
        return -1;
    }
}