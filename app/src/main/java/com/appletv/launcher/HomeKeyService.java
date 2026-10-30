package com.appletv.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.SystemClock;
import android.media.AudioManager;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * Eliminates the Fire OS 5-second Home-press delay using FireFrame's proven overlay approach:
 *
 *  1. A TYPE_ACCESSIBILITY_OVERLAY window is pre-attached to the WindowManager on startup.
 *     The window server draws overlay windows immediately — no launch throttle.
 *
 *  2. When Amazon's launcher is detected (HOME_PRESSED broadcast or window-state event),
 *     the overlay is made visible instantly covering Amazon Home, then MainActivity is
 *     started (which Fire OS throttles, but the user never sees the Amazon home screen).
 *
 *  3. When MainActivity notifies appShown(), the overlay hides — same visual content,
 *     invisible seam.
 *
 *  4. Hardware key filtering intercepts KEYCODE_HOME, KEYCODE_ALL_APPS, Settings, and
 *     the Recents button.
 */
public class HomeKeyService extends AccessibilityService {

    static final String AMAZON_HOME = "com.amazon.tv.launcher";
    private static final java.util.Set<String> IGNORED = new java.util.HashSet<>(
            java.util.Arrays.asList("android", "com.android.systemui", "com.amazon.tv.ime"));

    static final int FLAG_SHOWN = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            | WindowManager.LayoutParams.FLAG_FULLSCREEN;
    static final int FLAG_HIDDEN = FLAG_SHOWN
            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

    static long pausedUntil;
    static HomeKeyService running;

    WindowManager wm;
    WindowManager.LayoutParams lp;
    View overlay;
    boolean overlayAdded;
    boolean overlayShowing;
    final Runnable hideOverlayRunnable = this::hideOverlay;

    private int[] settingsScancodes = new int[0];
    private int[] settingsKeycodes = new int[0];
    private boolean settingsPanel;

    final BroadcastReceiver homePressed = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            pausedUntil = 0;
            cover();
        }
    };

    static void appShown() {
        HomeKeyService s = running;
        if (s != null && s.overlayShowing) {
            s.overlay.postDelayed(s.hideOverlayRunnable, 120);
        }
    }

    static void hideOverlayNow() {
        if (running != null) running.hideOverlay();
    }

    static boolean isSettingsPackage(String p) {
        return p.startsWith("com.amazon.tv.settings") || p.equals("com.android.settings");
    }

    static boolean paused() {
        return SystemClock.elapsedRealtime() < pausedUntil;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        running = this;
        Config config = Config.load(this);
        settingsScancodes = getResources().getIntArray(R.array.settings_key_scancodes);
        settingsKeycodes = config.settingsKeycodes;
        settingsPanel = config.settingsKeyPanel;

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlay = new View(this);
        overlay.setBackgroundColor(config.backgroundColor != 0 ? config.backgroundColor : Color.BLACK);
        overlay.setVisibility(View.GONE);
        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                FLAG_HIDDEN,
                PixelFormat.OPAQUE);
        attachOverlay();

        try {
            registerReceiver(homePressed, new IntentFilter("com.amazon.tv.launcher.HOME_PRESSED"));
        } catch (Exception ignored) {}
    }

    @Override
    public boolean onUnbind(Intent intent) {
        try { unregisterReceiver(homePressed); } catch (Exception ignored) {}
        if (overlayAdded && wm != null && overlay != null) {
            try { wm.removeViewImmediate(overlay); } catch (Exception ignored) {}
        }
        overlayAdded = false;
        running = null;
        return super.onUnbind(intent);
    }

    private boolean attachOverlay() {
        if (overlayAdded) return true;
        try {
            wm.addView(overlay, lp);
            overlayAdded = true;
        } catch (RuntimeException e) {
            overlayAdded = false;
        }
        return overlayAdded;
    }

    private void cover() {
        if (paused()) return;
        showOverlay();
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }

    private void showOverlay() {
        if (overlay == null || !attachOverlay()) return;
        overlay.removeCallbacks(hideOverlayRunnable);
        if (!overlayShowing) {
            overlayShowing = true;
            overlay.setVisibility(View.VISIBLE);
        }
        setOverlayFocusable(true);
    }

    void hideOverlay() {
        if (overlay == null || !overlayShowing || !overlayAdded) return;
        overlay.removeCallbacks(hideOverlayRunnable);
        overlayShowing = false;
        overlay.setVisibility(View.GONE);
        setOverlayFocusable(false);
    }

    private void setOverlayFocusable(boolean focusable) {
        if (lp == null || wm == null || !overlayAdded) return;
        int flags = focusable ? FLAG_SHOWN : FLAG_HIDDEN;
        if (lp.flags == flags) return;
        lp.flags = flags;
        try { wm.updateViewLayout(overlay, lp); } catch (Exception ignored) {}
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        String p = pkg.toString();
        if (p.equals(AMAZON_HOME)) {
            cover();
        } else if (!IGNORED.contains(p) && !p.equals(getPackageName())) {
            if (!isSettingsPackage(p)) pausedUntil = 0;
            if (overlayShowing) {
                setOverlayFocusable(false);
                overlay.postDelayed(hideOverlayRunnable, 600);
            }
        }
    }

    private boolean isSettingsKey(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS) return true;
        for (int code : settingsScancodes) if (event.getScanCode() == code) return true;
        for (int code : settingsKeycodes) if (event.getKeyCode() == code) return true;
        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (isSettingsKey(event)) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                Intent open = settingsPanel
                        ? new Intent(this, QuickSettingsActivity.class)
                        : new Intent(Settings.ACTION_SETTINGS);
                startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_2 || event.getScanCode() == 150) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                startActivity(new Intent(this, RecentAppsActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_HOME
                || event.getKeyCode() == KeyEvent.KEYCODE_ALL_APPS) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                pausedUntil = 0;
                cover();
            }
            return true;
        }
        // App Button 3 (KEY_APP_3, scancode 746) -> Volume Down
        if (event.getScanCode() == 746 || event.getKeyCode() == 286) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                if (am != null) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
            }
            return true;
        }
        // App Button 4 (KEY_APP_4, scancode 747) -> Volume Up
        if (event.getScanCode() == 747 || event.getKeyCode() == 287) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                if (am != null) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
            }
            return true;
        }
        return false;
    }

    @Override
    public void onInterrupt() {}
}
