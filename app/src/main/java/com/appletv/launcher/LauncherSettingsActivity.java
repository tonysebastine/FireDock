package com.appletv.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private TextView wallpaperChoice;
    private int columns;
    private boolean animations;
    private boolean advancedEffects;

    // Wallpapers bundled in assets/wallpapers/ — filename → display label
    private static final String[] WALLPAPER_FILES = {
        "gta6_official.jpg",
        "gta6_lockload.jpg",
        "gta6_album.jpg",
        "Official_Cover_Art_landscape.jpg",
        "aurora.jpg",
        "citylights.jpg",
        "earthrise.jpg",
        "southernlights.jpg",
        "sunrise.jpg",
    };
    private static final String[] WALLPAPER_LABELS = {
        "GTA VI — Lucia & Jason",
        "GTA VI — Lock & Load",
        "GTA VI — Album Cover",
        "GTA VI — Official Cover Art",
        "Aurora Borealis",
        "City Lights",
        "Earthrise",
        "Southern Lights",
        "Sunrise from Orbit",
    };
    // "None" is the first item (index 0) in the dialog
    private static final String NONE_LABEL = "None (plain background)";

    private final Executor bg = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        Config config = Config.load(this);
        columns = config.columns;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 4;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            LinearLayout row = (LinearLayout) columnChoices[0].getParent();
            row.removeAllViews();
            for (int i = columnChoices.length - 1; i >= 0; i--) row.addView(columnChoices[i]);
        }

        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            setResult(RESULT_OK);
            refresh();
        });

        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });

        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove("animations").remove("advanced_effects").commit();
            Config current = Config.load(this);
            columns = current.columns;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            setResult(RESULT_OK);
            refresh();
        });

        // ── Wallpaper picker ──────────────────────────────────────────────
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());

        // ── System rows ───────────────────────────────────────────────────
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(new Intent(Settings.ACTION_SETTINGS)));

        refresh();
        columnChoices[Math.max(0, Math.min(columns - 4, 3))].requestFocus();
    }

    // ── wallpaper ─────────────────────────────────────────────────────────

    private void showWallpaperPicker() {
        // Build dialog items: "None" + one entry per wallpaper
        String[] items = new String[WALLPAPER_FILES.length + 1];
        items[0] = NONE_LABEL;
        System.arraycopy(WALLPAPER_LABELS, 0, items, 1, WALLPAPER_LABELS.length);

        // Determine currently active wallpaper (by saved pref or file presence)
        String currentFile = choices.getString("wallpaper_file", "");
        int checkedItem = 0; // default = None
        for (int i = 0; i < WALLPAPER_FILES.length; i++) {
            if (WALLPAPER_FILES[i].equals(currentFile)) { checkedItem = i + 1; break; }
        }
        final int[] selected = {checkedItem};

        new AlertDialog.Builder(this)
                .setTitle(R.string.wallpapers_title)
                .setItems(items, (dialog, which) -> applyWallpaper(which))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void applyWallpaper(int index) {
        File destFile = new File(getExternalFilesDir(null), Config.WALLPAPER_NAME);

        if (index == 0) {
            // "None": delete the wallpaper file and clear pref
            destFile.delete();
            choices.edit().remove("wallpaper_file").apply();
            setResult(RESULT_OK);
            refreshWallpaperLabel("");
            return;
        }

        String assetName = WALLPAPER_FILES[index - 1];
        String label = WALLPAPER_LABELS[index - 1];
        wallpaperChoice.setEnabled(false);
        wallpaperChoice.setText(getString(R.string.wallpapers_title) + "  —  copying…");

        bg.execute(() -> {
            boolean ok = false;
            try (InputStream in = getAssets().open("wallpapers/" + assetName);
                 OutputStream out = new FileOutputStream(destFile)) {
                byte[] buf = new byte[32768];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                ok = true;
            } catch (Exception e) {
                // failed — leave old file intact
            }
            final boolean success = ok;
            ui.post(() -> {
                wallpaperChoice.setEnabled(true);
                if (success) {
                    choices.edit().putString("wallpaper_file", assetName).apply();
                    refreshWallpaperLabel(label);
                    setResult(RESULT_OK);
                    // Restart MainActivity to pick up new wallpaper
                    Toast.makeText(this, "Wallpaper set: " + label, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
                    refreshWallpaperLabel(choices.getString("wallpaper_file", ""));
                }
            });
        });
    }

    private void refreshWallpaperLabel(String fileOrLabel) {
        String label = NONE_LABEL;
        for (int i = 0; i < WALLPAPER_FILES.length; i++) {
            if (WALLPAPER_FILES[i].equals(fileOrLabel) || WALLPAPER_LABELS[i].equals(fileOrLabel)) {
                label = WALLPAPER_LABELS[i]; break;
            }
        }
        String savedFile = choices.getString("wallpaper_file", "");
        if (!savedFile.isEmpty()) {
            for (int i = 0; i < WALLPAPER_FILES.length; i++) {
                if (WALLPAPER_FILES[i].equals(savedFile)) { label = WALLPAPER_LABELS[i]; break; }
            }
        }
        wallpaperChoice.setText(getString(R.string.wallpapers_title) + "  —  " + label);
    }

    // ── animations ────────────────────────────────────────────────────────

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        setResult(RESULT_OK);
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 4);
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        followThemeChoice.setSelected(!hasOnTvChoices());

        // Refresh wallpaper label
        if (wallpaperChoice != null) {
            String saved = choices.getString("wallpaper_file", "");
            refreshWallpaperLabel(saved);
        }
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains("animations")
                || choices.contains("advanced_effects");
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
