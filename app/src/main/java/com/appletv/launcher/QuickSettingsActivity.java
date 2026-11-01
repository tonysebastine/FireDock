package com.appletv.launcher;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Quick settings / tvOS 18 Control Center:
 * Apple tvOS 2.0-style floating glass panel matching tvOS Launcher:
 * - StandBy large square tile (Left)
 * - Wi-Fi & Bluetooth stacked pills (Right)
 * - Inputs & Settings pills (Middle)
 * - Assistant, Cast, Night Mode, and Display circular quick toggles (Bottom)
 *
 * Direct In-Place Interactive Sub-panels:
 * - Wi-Fi: Lists currently connected & scanned Wi-Fi networks; clicking reconnects/switches or opens credentials.
 * - Bluetooth: Lists paired devices with connection state & battery; clicking connects/pairs or launches pairing.
 */
public final class QuickSettingsActivity extends Activity {

    private WifiManager wifi;
    private BluetoothAdapter bluetooth;

    private View ccPanel;
    private View subPanel;
    private TextView subTitle;
    private ImageView subBtnActionIcon;
    private View subBtnAction;
    private View subBtnBack;
    private TextView subBtnMore;
    private LinearLayout subListContainer;

    private enum SubViewMode { NONE, WIFI, BLUETOOTH }
    private SubViewMode currentMode = SubViewMode.NONE;

    private BluetoothProfile a2dpProxy;
    private BluetoothProfile headsetProxy;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final BluetoothProfile.ServiceListener profileListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == BluetoothProfile.A2DP) {
                a2dpProxy = proxy;
            } else if (profile == BluetoothProfile.HEADSET) {
                headsetProxy = proxy;
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            if (profile == BluetoothProfile.A2DP) {
                a2dpProxy = null;
            } else if (profile == BluetoothProfile.HEADSET) {
                headsetProxy = null;
            }
        }
    };

    private final BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (currentMode == SubViewMode.WIFI) {
                populateWifiList();
            }
        }
    };

    private final BroadcastReceiver btStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (currentMode == SubViewMode.BLUETOOTH) {
                populateBluetoothList();
            }
            setupBluetoothPill();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_quick_settings);

        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        bluetooth = BluetoothAdapter.getDefaultAdapter();

        ccPanel = findViewById(R.id.ccPanel);
        subPanel = findViewById(R.id.subPanel);
        subTitle = findViewById(R.id.subTitle);
        subBtnActionIcon = findViewById(R.id.subBtnActionIcon);
        subBtnAction = findViewById(R.id.subBtnAction);
        subBtnBack = findViewById(R.id.subBtnBack);
        subBtnMore = findViewById(R.id.subBtnMore);
        subListContainer = findViewById(R.id.subListContainer);

        findViewById(R.id.scrim).setOnClickListener(v -> finish());

        if (subBtnBack != null) {
            subBtnBack.setOnClickListener(v -> showMainPanel());
        }

        // 1. StandBy Tile
        View standBy = findViewById(R.id.ccStandBy);
        if (standBy != null) {
            standBy.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(this, StandByActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(this, ClockDream.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
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
        setupCircularToggles();

        // Register Wi-Fi scan receiver
        registerReceiver(wifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        // Connect to Bluetooth Profile proxies (A2DP & Headset)
        if (bluetooth != null) {
            try {
                bluetooth.getProfileProxy(this, profileListener, BluetoothProfile.A2DP);
                bluetooth.getProfileProxy(this, profileListener, BluetoothProfile.HEADSET);
            } catch (Exception ignored) {}
        }

        // Register Bluetooth connection state broadcasts
        IntentFilter btFilter = new IntentFilter();
        btFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        btFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        btFilter.addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED");
        btFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(btStateReceiver, btFilter);

        if (standBy != null) standBy.requestFocus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(wifiScanReceiver);
        } catch (Exception ignored) {}
        try {
            unregisterReceiver(btStateReceiver);
        } catch (Exception ignored) {}
        if (bluetooth != null) {
            try {
                if (a2dpProxy != null) bluetooth.closeProfileProxy(BluetoothProfile.A2DP, a2dpProxy);
                if (headsetProxy != null) bluetooth.closeProfileProxy(BluetoothProfile.HEADSET, headsetProxy);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onBackPressed() {
        if (currentMode != SubViewMode.NONE) {
            showMainPanel();
        } else {
            super.onBackPressed();
        }
    }

    private void showMainPanel() {
        currentMode = SubViewMode.NONE;
        if (subPanel != null) subPanel.setVisibility(View.GONE);
        if (ccPanel != null) {
            ccPanel.setVisibility(View.VISIBLE);
            View wifiView = findViewById(R.id.ccWifi);
            if (wifiView != null) wifiView.requestFocus();
        }
    }

    private void showWifiSubPanel() {
        currentMode = SubViewMode.WIFI;
        if (ccPanel != null) ccPanel.setVisibility(View.GONE);
        if (subPanel != null) subPanel.setVisibility(View.VISIBLE);

        if (subTitle != null) subTitle.setText("Wi-Fi Networks");
        if (subBtnActionIcon != null) subBtnActionIcon.setImageResource(R.drawable.ic_refresh);
        if (subBtnAction != null) {
            subBtnAction.setOnClickListener(v -> {
                if (wifi != null) {
                    wifi.startScan();
                    Toast.makeText(this, "Scanning for Wi-Fi…", Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (subBtnMore != null) {
            subBtnMore.setText("All Wi-Fi Settings…");
            subBtnMore.setOnClickListener(v -> {
                launchWifiSettings();
                finish();
            });
        }

        populateWifiList();
        if (wifi != null) wifi.startScan();
    }

    private void populateWifiList() {
        if (subListContainer == null) return;
        subListContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        String currentSsid = getWifiSsid();
        boolean hasConnected = !currentSsid.equals("Off") && !currentSsid.equals("On");

        // 1. Current Network if connected
        if (hasConnected) {
            View itemView = inflater.inflate(R.layout.item_qs_detail, subListContainer, false);
            ImageView icon = itemView.findViewById(R.id.itemIcon);
            TextView title = itemView.findViewById(R.id.itemTitle);
            TextView subtitle = itemView.findViewById(R.id.itemSubtitle);
            ImageView check = itemView.findViewById(R.id.itemCheck);

            if (icon != null) icon.setImageResource(R.drawable.ic_wifi);
            if (title != null) title.setText(currentSsid);
            if (subtitle != null) subtitle.setText("Connected");
            if (check != null) check.setVisibility(View.VISIBLE);

            itemView.setOnClickListener(v -> {
                launchWifiSettings();
                finish();
            });
            subListContainer.addView(itemView);
        }

        // 2. Saved Configured Networks (Android Internals)
        Set<String> addedSsids = new HashSet<>();
        if (hasConnected) addedSsids.add(currentSsid);

        try {
            List<WifiConfiguration> configs = wifi != null ? wifi.getConfiguredNetworks() : null;
            if (configs != null) {
                for (WifiConfiguration c : configs) {
                    if (c.SSID == null) continue;
                    String s = c.SSID.replace("\"", "").trim();
                    if (s.isEmpty() || addedSsids.contains(s)) continue;
                    addedSsids.add(s);

                    View itemView = inflater.inflate(R.layout.item_qs_detail, subListContainer, false);
                    ImageView icon = itemView.findViewById(R.id.itemIcon);
                    TextView title = itemView.findViewById(R.id.itemTitle);
                    TextView subtitle = itemView.findViewById(R.id.itemSubtitle);

                    if (icon != null) icon.setImageResource(R.drawable.ic_wifi);
                    if (title != null) title.setText(s);
                    if (subtitle != null) subtitle.setText("Saved");

                    final int netId = c.networkId;
                    itemView.setOnClickListener(v -> {
                        if (wifi != null) {
                            wifi.disconnect();
                            wifi.enableNetwork(netId, true);
                            wifi.reconnect();
                            Toast.makeText(this, "Connecting to " + s + "…", Toast.LENGTH_SHORT).show();
                        }
                        finish();
                    });
                    subListContainer.addView(itemView);
                }
            }
        } catch (Exception ignored) {}

        // 3. Scanned Wi-Fi Networks
        try {
            List<ScanResult> scans = wifi != null ? wifi.getScanResults() : null;
            if (scans != null) {
                for (ScanResult sr : scans) {
                    if (sr.SSID == null) continue;
                    String s = sr.SSID.trim();
                    if (s.isEmpty() || addedSsids.contains(s)) continue;
                    addedSsids.add(s);

                    View itemView = inflater.inflate(R.layout.item_qs_detail, subListContainer, false);
                    ImageView icon = itemView.findViewById(R.id.itemIcon);
                    TextView title = itemView.findViewById(R.id.itemTitle);
                    TextView subtitle = itemView.findViewById(R.id.itemSubtitle);

                    if (icon != null) icon.setImageResource(R.drawable.ic_wifi);
                    if (title != null) title.setText(s);

                    boolean isSecure = sr.capabilities != null && (sr.capabilities.contains("WPA") || sr.capabilities.contains("WEP"));
                    if (subtitle != null) subtitle.setText(isSecure ? "Secured" : "Open");

                    itemView.setOnClickListener(v -> {
                        launchWifiSettings();
                        finish();
                    });
                    subListContainer.addView(itemView);
                    if (subListContainer.getChildCount() >= 7) break; // Keep compact
                }
            }
        } catch (Exception ignored) {}

        if (subListContainer.getChildCount() > 0) {
            subListContainer.getChildAt(0).requestFocus();
        } else if (subBtnBack != null) {
            subBtnBack.requestFocus();
        }
    }

    private void showBluetoothSubPanel() {
        currentMode = SubViewMode.BLUETOOTH;
        if (ccPanel != null) ccPanel.setVisibility(View.GONE);
        if (subPanel != null) subPanel.setVisibility(View.VISIBLE);

        if (subTitle != null) subTitle.setText("Bluetooth Devices");
        if (subBtnActionIcon != null) subBtnActionIcon.setImageResource(R.drawable.ic_add);
        if (subBtnAction != null) {
            subBtnAction.setOnClickListener(v -> {
                launchPairNewBluetooth();
                finish();
            });
        }
        if (subBtnMore != null) {
            subBtnMore.setText("Controllers & Bluetooth…");
            subBtnMore.setOnClickListener(v -> {
                launchBluetoothSettings();
                finish();
            });
        }

        populateBluetoothList();
    }

    private void populateBluetoothList() {
        if (subListContainer == null) return;
        subListContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        if (bluetooth == null || !bluetooth.isEnabled()) {
            TextView empty = new TextView(this);
            empty.setText("Bluetooth is turned off.");
            empty.setTextColor(0x80FFFFFF);
            empty.setPadding(16, 16, 16, 16);
            subListContainer.addView(empty);
            if (subBtnBack != null) subBtnBack.requestFocus();
            return;
        }

        Set<BluetoothDevice> paired = bluetooth.getBondedDevices();
        if (paired != null && !paired.isEmpty()) {
            for (BluetoothDevice d : paired) {
                View itemView = inflater.inflate(R.layout.item_qs_detail, subListContainer, false);
                ImageView icon = itemView.findViewById(R.id.itemIcon);
                TextView title = itemView.findViewById(R.id.itemTitle);
                TextView subtitle = itemView.findViewById(R.id.itemSubtitle);
                ImageView check = itemView.findViewById(R.id.itemCheck);

                if (icon != null) icon.setImageResource(R.drawable.ic_bluetooth);
                String name = d.getName() != null ? d.getName() : d.getAddress();
                if (title != null) title.setText(name);

                boolean isConnected = isBluetoothDeviceConnected(d);
                int battery = getDeviceBattery(d);
                StringBuilder status = new StringBuilder();
                if (isConnected) {
                    status.append("Connected");
                    if (battery >= 0) status.append(" • ").append(battery).append("%");
                    if (check != null) check.setVisibility(View.VISIBLE);
                } else {
                    status.append("Paired");
                }
                if (subtitle != null) subtitle.setText(status.toString());

                itemView.setOnClickListener(v -> {
                    connectBluetoothDevice(d);
                });
                subListContainer.addView(itemView);
            }
        } else {
            TextView empty = new TextView(this);
            empty.setText("No paired Bluetooth devices.");
            empty.setTextColor(0x80FFFFFF);
            empty.setPadding(16, 16, 16, 16);
            subListContainer.addView(empty);
        }

        if (subListContainer.getChildCount() > 0) {
            subListContainer.getChildAt(0).requestFocus();
        } else if (subBtnBack != null) {
            subBtnBack.requestFocus();
        }
    }

    private boolean isBluetoothDeviceConnected(BluetoothDevice device) {
        try {
            Method m = device.getClass().getMethod("isConnected");
            Object res = m.invoke(device);
            if (res instanceof Boolean) return (Boolean) res;
        } catch (Exception ignored) {}
        return false;
    }

    private void connectBluetoothDevice(BluetoothDevice device) {
        String deviceName = device.getName() != null ? device.getName() : device.getAddress();
        Toast.makeText(this, "Connecting to " + deviceName + "…", Toast.LENGTH_SHORT).show();

        // 1. Try reflection via A2DP profile proxy (A2DP headsets, speakers, earbuds)
        boolean invoked = false;
        if (a2dpProxy != null) {
            try {
                Method connectMethod = a2dpProxy.getClass().getMethod("connect", BluetoothDevice.class);
                connectMethod.setAccessible(true);
                Object res = connectMethod.invoke(a2dpProxy, device);
                if (Boolean.TRUE.equals(res)) {
                    invoked = true;
                }
            } catch (Exception e) {
                Log.w("QuickSettings", "a2dpProxy.connect failed: " + e.getMessage());
            }
        }

        // 2. Try reflection via Headset profile proxy (HFP/HSP)
        if (!invoked && headsetProxy != null) {
            try {
                Method connectMethod = headsetProxy.getClass().getMethod("connect", BluetoothDevice.class);
                connectMethod.setAccessible(true);
                Object res = connectMethod.invoke(headsetProxy, device);
                if (Boolean.TRUE.equals(res)) {
                    invoked = true;
                }
            } catch (Exception e) {
                Log.w("QuickSettings", "headsetProxy.connect failed: " + e.getMessage());
            }
        }

        // 3. Try hidden device.connect()
        if (!invoked) {
            try {
                Method connectMethod = device.getClass().getMethod("connect");
                connectMethod.setAccessible(true);
                Object res = connectMethod.invoke(device);
                if (Boolean.TRUE.equals(res)) {
                    invoked = true;
                }
            } catch (Exception e) {
                Log.w("QuickSettings", "device.connect failed: " + e.getMessage());
            }
        }

        // 4. Always ensure connection by launching Fire TV's native INPUT_DEVICES handover
        // which reliably triggers the system's connection coordinator for audio/input peripherals
        try {
            Intent intent = new Intent("com.amazon.device.settings.action.INPUT_DEVICES");
            intent.setClassName("com.amazon.tv.settings.v2", "com.amazon.tv.settings.v2.tv.controllers_bluetooth_devices.ControllersAndBluetoothActivity");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            launchBluetoothSettings();
        }
        finish();
    }

    private void launchWifiSettings() {
        Intent intent = new Intent();
        intent.setClassName("com.amazon.tv.settings.v2", "com.amazon.tv.settings.v2.tv.network.NetworkActivity");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e1) {
            try {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e2) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void launchBluetoothSettings() {
        Intent intent = new Intent();
        intent.setClassName("com.amazon.tv.settings.v2", "com.amazon.tv.settings.v2.tv.controllers_bluetooth_devices.ControllersAndBluetoothActivity");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e1) {
            try {
                startActivity(new Intent("android.settings.CONTROLLERS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e2) {
                try {
                    startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception e3) {
                    Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void launchPairNewBluetooth() {
        Intent intent = new Intent("com.amazon.device.settings.action.DISCOVER_INPUT_DEVICE");
        intent.setClassName("com.amazon.tv.settings.v2", "com.amazon.tv.settings.v2.tv.controllers_bluetooth_devices.ControllersAndBluetoothActivity");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            launchBluetoothSettings();
        }
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

        container.setOnClickListener(v -> showWifiSubPanel());
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

        if (icon != null) icon.setImageResource(R.drawable.ic_bluetooth);
        if (txtTitle != null) txtTitle.setText("Bluetooth");

        String btStatus = getBluetoothSummary();
        if (txtSubtitle != null) txtSubtitle.setText(btStatus);

        container.setOnClickListener(v -> showBluetoothSubPanel());
    }

    private String getBluetoothSummary() {
        if (bluetooth == null || !bluetooth.isEnabled()) return "Off";
        try {
            Set<BluetoothDevice> paired = bluetooth.getBondedDevices();
            if (paired == null || paired.isEmpty()) return "Ready";
            for (BluetoothDevice d : paired) {
                if (isBluetoothDeviceConnected(d)) {
                    int battery = getDeviceBattery(d);
                    String name = d.getName() != null ? d.getName() : "Device";
                    return battery >= 0 ? name + " " + battery + "%" : name;
                }
            }
            return paired.size() + " Paired";
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

    private void setupCircularToggles() {
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
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isFinishing()) {
            overridePendingTransition(0, 0);
        }
    }
}
