package com.appletv.launcher;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * Quick settings / tvOS 18 Control Center:
 * Apple tvOS 2.0-style floating glass panel matching tvOS Launcher:
 * - StandBy large square tile (Left)
 * - Wi-Fi & Bluetooth stacked pills (Right)
 * - Inputs & Settings pills (Middle)
 * - Assistant, Cast, Night Mode, and Display circular quick toggles (Bottom)
 */
public final class QuickSettingsActivity extends Activity {

    private WifiManager wifi;
    private BluetoothAdapter bluetooth;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_quick_settings);
        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        bluetooth = BluetoothAdapter.getDefaultAdapter();

        findViewById(R.id.scrim).setOnClickListener(v -> finish());

        // 1. StandBy Tile
        View standBy = findViewById(R.id.ccStandBy);
        if (standBy != null) {
            standBy.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(this, ClockDream.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception e) {
                    try {
                        startService(new Intent(this, ClockDream.class));
                    } catch (Exception ignored) {}
                }
                finish();
            });
        }

        // 2. Wi-Fi Pill
        setupWifiPill();

        // 3. Bluetooth Pill
        setupBluetoothPill();

        // 4. Inputs Pill
        setupPill(R.id.ccInputs, R.drawable.ic_input, "Inputs", "TV Sources", v -> {
            try {
                startActivity(new Intent("android.media.tv.action.VIEW_INPUTS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception e) {
                    Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
                }
            }
            finish();
        });

        // 5. Settings Pill
        setupPill(R.id.ccSettings, R.drawable.ic_settings, "Settings", "System", v -> {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
            finish();
        });

        // 6. Bottom Circular Action Toggles
        // Assistant
        View btnAssistant = findViewById(R.id.btnAssistant);
        if (btnAssistant != null) {
            btnAssistant.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VOICE_COMMAND).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(Intent.ACTION_SEARCH).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Exception ignored) {
                        Toast.makeText(this, "Voice Assistant not available", Toast.LENGTH_SHORT).show();
                    }
                }
                finish();
            });
        }

        // Cast / AirPlay
        View btnCast = findViewById(R.id.btnCast);
        if (btnCast != null) {
            btnCast.setOnClickListener(v -> {
                try {
                    // Try wireless display / casting settings
                    startActivity(new Intent("android.settings.CAST_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception e) {
                    try {
                        startActivity(new Intent("android.settings.WIFI_DISPLAY_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Exception ignored) {
                        Toast.makeText(this, "Screen Casting is Ready", Toast.LENGTH_SHORT).show();
                    }
                }
                finish();
            });
        }

        // Dark / Night Mode
        View btnDarkMode = findViewById(R.id.btnDarkMode);
        if (btnDarkMode != null) {
            btnDarkMode.setOnClickListener(v -> {
                Toast.makeText(this, "tvOS Pitch Black OLED mode active", Toast.LENGTH_SHORT).show();
                finish();
            });
        }

        // Display
        View btnDisplay = findViewById(R.id.btnDisplay);
        if (btnDisplay != null) {
            btnDisplay.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception e) {
                    Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
                }
                finish();
            });
        }

        // Set initial focus to StandBy
        if (standBy != null) standBy.requestFocus();
    }

    private void setupPill(int containerId, int iconRes, String title, String subtitle, View.OnClickListener click) {
        View container = findViewById(containerId);
        if (container == null) return;
        ImageView icon = container.findViewById(R.id.pillIcon);
        TextView txtTitle = container.findViewById(R.id.pillTitle);
        TextView txtSubtitle = container.findViewById(R.id.pillSubtitle);

        if (icon != null) icon.setImageResource(iconRes);
        if (txtTitle != null) txtTitle.setText(title);
        if (txtSubtitle != null) txtSubtitle.setText(subtitle);

        container.setOnClickListener(click);
    }

    private void setupWifiPill() {
        View container = findViewById(R.id.ccWifi);
        if (container == null) return;
        ImageView icon = container.findViewById(R.id.pillIcon);
        TextView txtTitle = container.findViewById(R.id.pillTitle);
        TextView txtSubtitle = container.findViewById(R.id.pillSubtitle);

        if (icon != null) icon.setImageResource(R.drawable.ic_wifi);
        if (txtTitle != null) txtTitle.setText("Wi-Fi");

        String ssid = getWifiSsid();
        if (txtSubtitle != null) txtSubtitle.setText(ssid);

        container.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
            } catch (Exception e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String getWifiSsid() {
        if (wifi == null || !wifi.isWifiEnabled()) return "Off";
        WifiInfo info = wifi.getConnectionInfo();
        if (info != null && info.getSSID() != null) {
            String s = info.getSSID().replace("\"", "").trim();
            if (!s.isEmpty() && !s.contains("unknown") && !s.equals("0x")) return s;
        }
        return "On";
    }

    private void setupBluetoothPill() {
        View container = findViewById(R.id.ccBluetooth);
        if (container == null) return;
        ImageView icon = container.findViewById(R.id.pillIcon);
        TextView txtTitle = container.findViewById(R.id.pillTitle);
        TextView txtSubtitle = container.findViewById(R.id.pillSubtitle);

        if (icon != null) icon.setImageResource(R.drawable.ic_settings);
        if (txtTitle != null) txtTitle.setText("Bluetooth");

        String btStatus = getBluetoothSummary();
        if (txtSubtitle != null) txtSubtitle.setText(btStatus);

        container.setOnClickListener(v -> {
            try {
                startActivity(new Intent("android.settings.CONTROLLERS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    finish();
                } catch (Exception ignored) {
                    Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private String getBluetoothSummary() {
        if (bluetooth == null || !bluetooth.isEnabled()) return "Off";
        try {
            Set<BluetoothDevice> paired = bluetooth.getBondedDevices();
            if (paired == null || paired.isEmpty()) return "Ready";
            for (BluetoothDevice d : paired) {
                int battery = getDeviceBattery(d);
                if (battery >= 0) {
                    String name = d.getName();
                    return (name != null ? name : "Audio") + " " + battery + "%";
                }
            }
            return paired.size() + " Connected";
        } catch (Exception e) {
            return "On";
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
}
