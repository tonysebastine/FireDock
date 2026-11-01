with open(r'app/src/main/java/com/appletv/launcher/QuickSettingsActivity.java', 'r', encoding='utf-8') as f:
    code = f.read()

# 1. Update SubViewMode enum to include SETTINGS
code = code.replace(
    'private enum SubViewMode { NONE, WIFI, BLUETOOTH }',
    'private enum SubViewMode { NONE, WIFI, BLUETOOTH, SETTINGS }'
)

# 2. Update setupPill for Settings to showSettingsSubPanel
old_settings_click = '''        // 5. Settings Pill
        setupPill(R.id.ccSettings, R.drawable.ic_settings, "Settings", "System", v -> {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
            finish();
        });'''

new_settings_click = '''        // 5. Settings Pill
        setupPill(R.id.ccSettings, R.drawable.ic_settings, "Settings", "Preferences", v -> {
            showSettingsSubPanel();
        });'''

code = code.replace(old_settings_click, new_settings_click)

# 3. Add showSettingsSubPanel and populateSettingsList
subpanel_methods = '''    private void showSettingsSubPanel() {
        currentMode = SubViewMode.SETTINGS;
        if (ccPanel != null) ccPanel.setVisibility(View.GONE);
        if (subPanel != null) subPanel.setVisibility(View.VISIBLE);

        if (subTitle != null) subTitle.setText("Settings");
        if (subBtnActionIcon != null) subBtnActionIcon.setImageResource(R.drawable.ic_settings);
        if (subBtnAction != null) {
            subBtnAction.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) {}
                finish();
            });
        }
        if (subBtnMore != null) {
            subBtnMore.setText("System Settings…");
            subBtnMore.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) {}
                finish();
            });
        }

        populateSettingsList();
    }

    private void populateSettingsList() {
        if (subListContainer == null) return;
        subListContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        // Option 1: FireDock Launcher Settings (Wallpapers, Columns, Animations)
        View itemLauncher = inflater.inflate(R.layout.item_qs_detail, subListContainer, false);
        ImageView iconLauncher = itemLauncher.findViewById(R.id.itemIcon);
        TextView titleLauncher = itemLauncher.findViewById(R.id.itemTitle);
        TextView subtitleLauncher = itemLauncher.findViewById(R.id.itemSubtitle);
        if (iconLauncher != null) iconLauncher.setImageResource(R.drawable.ic_home_settings);
        if (titleLauncher != null) titleLauncher.setText("Launcher Settings");
        if (subtitleLauncher != null) subtitleLauncher.setText("Wallpapers, layout, grid & animations");
        itemLauncher.setOnClickListener(v -> {
            try {
                startActivity(new Intent(this, LauncherSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                Toast.makeText(this, "Launcher settings unavailable", Toast.LENGTH_SHORT).show();
            }
            finish();
        });
        subListContainer.addView(itemLauncher);

        // Option 2: Android TV / Fire TV System Settings
        View itemSystem = inflater.inflate(R.layout.item_qs_detail, subListContainer, false);
        ImageView iconSystem = itemSystem.findViewById(R.id.itemIcon);
        TextView titleSystem = itemSystem.findViewById(R.id.itemTitle);
        TextView subtitleSystem = itemSystem.findViewById(R.id.itemSubtitle);
        if (iconSystem != null) iconSystem.setImageResource(R.drawable.ic_settings);
        if (titleSystem != null) titleSystem.setText("System Settings");
        if (subtitleSystem != null) subtitleSystem.setText("Device, display, audio, accounts & apps");
        itemSystem.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
            finish();
        });
        subListContainer.addView(itemSystem);

        // Option 3: StandBy Screensaver Preferences
        View itemStandBy = inflater.inflate(R.layout.item_qs_detail, subListContainer, false);
        ImageView iconStandBy = itemStandBy.findViewById(R.id.itemIcon);
        TextView titleStandBy = itemStandBy.findViewById(R.id.itemTitle);
        TextView subtitleStandBy = itemStandBy.findViewById(R.id.itemSubtitle);
        if (iconStandBy != null) iconStandBy.setImageResource(R.drawable.ic_clock);
        if (titleStandBy != null) titleStandBy.setText("StandBy Mode");
        if (subtitleStandBy != null) subtitleStandBy.setText("4 IPS/OLED styles, night tint, clock dials");
        itemStandBy.setOnClickListener(v -> {
            try {
                startActivity(new Intent(this, StandByActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {}
            finish();
        });
        subListContainer.addView(itemStandBy);

        // Focus first option
        itemLauncher.requestFocus();
    }
'''

# insert after populateBluetoothList block
target_bt = '        if (subListContainer.getChildCount() > 0) {\n            subListContainer.getChildAt(0).requestFocus();\n        } else if (subBtnBack != null) {\n            subBtnBack.requestFocus();\n        }\n    }'
if target_bt in code:
    code = code.replace(target_bt, target_bt + '\n\n' + subpanel_methods)
    print('Inserted showSettingsSubPanel successfully!')
else:
    print('Could not find target_bt, checking normalized...')
    code_norm = code.replace('\r\n', '\n')
    target_bt_norm = target_bt.replace('\r\n', '\n')
    if target_bt_norm in code_norm:
        code_norm = code_norm.replace(target_bt_norm, target_bt_norm + '\n\n' + subpanel_methods)
        code = code_norm
        print('Inserted normalized showSettingsSubPanel successfully!')
    else:
        print('Failed to match target_bt')

with open(r'app/src/main/java/com/appletv/launcher/QuickSettingsActivity.java', 'w', encoding='utf-8') as f:
    f.write(code)
print('Updated QuickSettingsActivity!')

