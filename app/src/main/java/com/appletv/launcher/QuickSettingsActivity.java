package com.appletv.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The panel the remote's Settings button opens (settings_key_panel = true):
 * a glass platter under the menu bar, top-right, over whatever app is playing.
 * Now includes an inline volume control row.
 */
public final class QuickSettingsActivity extends Activity {

    private AudioManager audio;
    private int streamType = AudioManager.STREAM_MUSIC;
    private TextView volumeStatus;
    private ProgressBar volumeBar;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_quick_settings);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);

        findViewById(R.id.scrim).setOnClickListener(v -> finish());
        LinearLayout rows = findViewById(R.id.rows);

        // ── Standard rows ──────────────────────────────────────────────────
        addRow(rows, R.drawable.ic_settings,      R.string.tv_settings,    0,
                new Intent(Settings.ACTION_SETTINGS));
        addRow(rows, R.drawable.ic_picture,       R.string.quick_picture,  0,
                new Intent(Settings.ACTION_DISPLAY_SETTINGS));
        addRow(rows, R.drawable.ic_wifi,          R.string.quick_network,  0,
                new Intent(Settings.ACTION_WIFI_SETTINGS));
        addRow(rows, R.drawable.ic_apps,          R.string.quick_apps,     R.string.quick_apps_hint,
                new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));

        // ── Volume control row (left/right to adjust) ──────────────────────
        addVolumeRow(rows);

        // ── Launcher settings (goes via Main so tiles rebuild) ─────────────
        addRow(rows, R.drawable.ic_home_settings, R.string.launcher_settings, 0,
                new Intent(this, MainActivity.class)
                        .setAction(MainActivity.ACTION_LAUNCHER_SETTINGS));

        rows.getChildAt(0).requestFocus();
    }

    /** Settings pressed again while the panel is up → close. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    /** Anything else taking the screen closes the panel. */
    @Override
    protected void onPause() {
        super.onPause();
        finish();
        overridePendingTransition(0, 0);
    }

    // ── helpers ───────────────────────────────────────────────────────────

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
            } catch (ActivityNotFoundException e) {
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

        // Left/Right D-pad adjusts volume while this row is focused.
        row.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                int direction = (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
                        ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER;
                audio.adjustStreamVolume(streamType, direction, AudioManager.FLAG_SHOW_UI);
                refreshVolumeStatus();
                return true;
            }
            // OK / Center also raises by one (convenience click)
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
        int cur  = audio.getStreamVolume(streamType);
        int max  = audio.getStreamMaxVolume(streamType);
        // Show "cur / max  ◀ ▶" so the user knows they can press left/right
        volumeStatus.setText(cur + " / " + max);
        volumeStatus.setVisibility(View.VISIBLE);
    }
}
