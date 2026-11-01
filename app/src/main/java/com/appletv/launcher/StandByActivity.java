package com.appletv.launcher;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.Random;

/**
 * Authentic Apple TV / iOS 17 & tvOS 18 StandBy Mode:
 * Specially tuned with high contrast and specular glass aesthetics for IPS & OLED displays.
 *
 * 4 Distinct StandBy Layout Styles:
 * 0. Dual Widgets (iOS StandBy Dual Platters: Big Digital Clock + Analog Watch Dial & Weather).
 * 1. Full-Screen Minimalist Ultra-Clean Digital Typography (Thin 170sp hero time).
 * 2. IPS Aurora Horizon (Apple tvOS Specular Glow + Live Tri-Pill Calendar/Weather/Audio Glance).
 * 3. Apple Chronograph Studio (380dp High-Definition Center Chronometer for IPS viewing angles).
 *
 * Features:
 * - IPS-tuned Vivid Radial Gradient Ambient Glow (Subtle navy/indigo/teal illumination, never washes out black levels).
 * - Deep Red OLED/IPS Night Mode (Press Menu or Down key).
 * - 60-Second Anti-Burn-In Pixel Drift.
 * - Single-press dismiss on any remote key.
 */
public class StandByActivity extends Activity {

    private static final String PREF_NAME = "standby_settings";
    private static final String KEY_STYLE = "standby_style";
    private static final String KEY_NIGHT = "standby_night";

    private static final int TOTAL_MODES = 4;

    private static final long DRIFT_INTERVAL_MS = 60_000L;
    private static final long ANALOG_TICK_INTERVAL_MS = 100L;

    // Specular IPS Ambient Glow Palette (Deep multi-hue saturation, vivid specular tone)
    private static final int[] AMBIENT_PALETTE = {
            0xFF1E3C72, // Ocean Deep Blue
            0xFF2A5298, // Royal Indigo
            0xFF0F2027, // Slate Teal
            0xFF2C3E50, // Midnight Graphite
            0xFF4A00E0, // Electric Iris
            0xFF8E2DE2  // Deep Orchid
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private View root;
    private View ambientGlowView;
    private GradientDrawable ambientDrawable;
    private FrameLayout container;

    // Mode Views
    private View viewDualWidgets;
    private View viewFullScreenDigital;
    private View viewAuroraHorizon;
    private View viewChronoStudio;

    // Clocks
    private StandByAnalogClock analogClock;
    private StandByAnalogClock chronoStudioClock;

    private TextView standbyStatusText;
    private ImageView standbyWifiIcon;
    private TextView hintText;

    // State
    private int currentMode = 0;
    private boolean isNightMode = false;
    private int paletteIndex = 0;

    // Analog clock ticker
    private final Runnable analogTick = new Runnable() {
        @Override
        public void run() {
            if (currentMode == 0 && analogClock != null) {
                analogClock.invalidate();
            } else if (currentMode == 3 && chronoStudioClock != null) {
                chronoStudioClock.invalidate();
            }
            handler.postDelayed(this, ANALOG_TICK_INTERVAL_MS);
        }
    };

    // Burn-in drift runner
    private final Runnable burnInDriftTick = new Runnable() {
        @Override
        public void run() {
            applyBurnInDrift();
            shiftAmbientGlow();
            handler.postDelayed(this, DRIFT_INTERVAL_MS);
        }
    };

    // Hint fade-out runner
    private final Runnable hideHintRunnable = new Runnable() {
        @Override
        public void run() {
            if (hintText != null) {
                hintText.animate().alpha(0f).setDuration(1000).start();
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen awake & fullscreen for tvOS leanback
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_standby);

        root = findViewById(R.id.standbyRoot);
        ambientGlowView = findViewById(R.id.standbyAmbientGlow);
        container = (FrameLayout) findViewById(R.id.standbyContentContainer);

        viewDualWidgets = findViewById(R.id.viewDualWidgets);
        viewFullScreenDigital = findViewById(R.id.viewFullScreenDigital);
        viewAuroraHorizon = findViewById(R.id.viewAuroraHorizon);
        viewChronoStudio = findViewById(R.id.viewChronoStudio);

        analogClock = findViewById(R.id.standbyAnalogClock);
        chronoStudioClock = findViewById(R.id.chronoStudioClock);

        standbyStatusText = findViewById(R.id.standbyStatusText);
        standbyWifiIcon = findViewById(R.id.standbyWifiIcon);
        hintText = findViewById(R.id.standbyHintText);

        // Load saved preferences
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        currentMode = prefs.getInt(KEY_STYLE, 0) % TOTAL_MODES;
        isNightMode = prefs.getBoolean(KEY_NIGHT, false);

        setupAmbientGlow();
        updateNetworkStatus();
        applyModeVisuals();
        applyNightModeVisuals();

        // Start tickers
        handler.post(analogTick);
        handler.postDelayed(burnInDriftTick, DRIFT_INTERVAL_MS);
        handler.postDelayed(hideHintRunnable, 5000);

        // Entrance animation
        if (container != null) {
            container.setAlpha(0f);
            container.setScaleX(0.96f);
            container.setScaleY(0.96f);
            container.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(600).start();
        }
    }

    private void setupAmbientGlow() {
        ambientDrawable = new GradientDrawable();
        ambientDrawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambientGlowView.setBackground(ambientDrawable);
        ambientGlowView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            ambientDrawable.setGradientRadius((r - l) * 0.60f);
            ambientDrawable.setGradientCenter(0.5f, 0.45f);
        });
        paletteIndex = random.nextInt(AMBIENT_PALETTE.length);
        setAmbientColor(AMBIENT_PALETTE[paletteIndex]);
    }

    private void setAmbientColor(int color) {
        if (isNightMode) {
            // Ultra dim crimson radial glow
            ambientDrawable.setColors(new int[]{0x2E5A0000, 0x00000000});
        } else {
            // IPS specular ambient radial glow
            int glowCenter = Color.argb(60, Color.red(color), Color.green(color), Color.blue(color));
            ambientDrawable.setColors(new int[]{glowCenter, 0x00000000});
        }
    }

    private void shiftAmbientGlow() {
        if (isNightMode) return;
        int oldColor = AMBIENT_PALETTE[paletteIndex];
        paletteIndex = (paletteIndex + 1) % AMBIENT_PALETTE.length;
        int newColor = AMBIENT_PALETTE[paletteIndex];

        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), oldColor, newColor);
        anim.setDuration(3500);
        anim.addUpdateListener(a -> setAmbientColor((int) a.getAnimatedValue()));
        anim.start();
    }

    /**
     * Subtle random pixel drift to safeguard displays against image persistence.
     */
    private void applyBurnInDrift() {
        if (container == null) return;
        float targetX = (random.nextFloat() * 36f) - 18f;
        float targetY = (random.nextFloat() * 26f) - 13f;

        container.animate()
                .translationX(targetX)
                .translationY(targetY)
                .setDuration(2500)
                .start();

        if (ambientDrawable != null) {
            float cx = 0.5f + (targetX / 1920f);
            float cy = 0.45f + (targetY / 1080f);
            ambientDrawable.setGradientCenter(cx, cy);
        }
    }

    private void updateNetworkStatus() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo active = cm.getActiveNetworkInfo();
            if (active != null && active.isConnected()) {
                if (active.getType() == ConnectivityManager.TYPE_WIFI) {
                    WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    WifiInfo wi = wm != null ? wm.getConnectionInfo() : null;
                    String ssid = (wi != null && wi.getSSID() != null) ? wi.getSSID().replace("\"", "") : "Wi-Fi";
                    standbyStatusText.setText(ssid + " • FireDock");
                    standbyWifiIcon.setImageResource(R.drawable.ic_wifi);
                } else if (active.getType() == ConnectivityManager.TYPE_ETHERNET) {
                    standbyStatusText.setText("Ethernet • FireDock");
                    standbyWifiIcon.setImageResource(R.drawable.ic_ethernet);
                } else {
                    standbyStatusText.setText("Connected • FireDock");
                    standbyWifiIcon.setImageResource(R.drawable.ic_check);
                }
            } else {
                standbyStatusText.setText("No Connection");
                standbyWifiIcon.setImageResource(R.drawable.ic_wifi);
            }
        } catch (Exception e) {
            standbyStatusText.setText("FireDock StandBy");
        }
    }

    private void applyModeVisuals() {
        if (viewDualWidgets != null) viewDualWidgets.setVisibility(currentMode == 0 ? View.VISIBLE : View.GONE);
        if (viewFullScreenDigital != null) viewFullScreenDigital.setVisibility(currentMode == 1 ? View.VISIBLE : View.GONE);
        if (viewAuroraHorizon != null) viewAuroraHorizon.setVisibility(currentMode == 2 ? View.VISIBLE : View.GONE);
        if (viewChronoStudio != null) viewChronoStudio.setVisibility(currentMode == 3 ? View.VISIBLE : View.GONE);
    }

    private void applyNightModeVisuals() {
        int clockPrimaryColor = isNightMode ? 0xFFFF3B30 : 0xFFFFFFFF; // Apple Crimson Red Tint
        int clockSecColor = isNightMode ? 0x99FF3B30 : 0x88FFFFFF;
        int accentColor = 0xFFFF453A;

        // Digital clocks color updates
        int[] textIds = {
                R.id.standbyClockHours, R.id.standbyClockAmPm, R.id.standbyDate,
                R.id.fullBigClock, R.id.fullBigAmPm, R.id.fullBigDate,
                R.id.auroraClock, R.id.auroraDate, R.id.chronoStudioDate
        };

        for (int id : textIds) {
            TextView tv = findViewById(id);
            if (tv != null) {
                if (id == R.id.standbyClockHours || id == R.id.fullBigClock || id == R.id.auroraClock) {
                    tv.setTextColor(clockPrimaryColor);
                } else {
                    tv.setTextColor(clockSecColor);
                }
            }
        }

        if (analogClock != null) {
            analogClock.setColors(clockPrimaryColor, clockSecColor, accentColor);
        }
        if (chronoStudioClock != null) {
            chronoStudioClock.setColors(clockPrimaryColor, clockSecColor, accentColor);
        }

        setAmbientColor(AMBIENT_PALETTE[paletteIndex]);
    }

    private void cycleMode(boolean forward) {
        if (forward) {
            currentMode = (currentMode + 1) % TOTAL_MODES;
        } else {
            currentMode = (currentMode - 1 + TOTAL_MODES) % TOTAL_MODES;
        }

        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_STYLE, currentMode)
                .apply();

        // Smooth Crossfade Transition
        View currentActiveView = getCurrentModeView();
        if (currentActiveView != null) {
            currentActiveView.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                applyModeVisuals();
                View targetView = getCurrentModeView();
                if (targetView != null) {
                    targetView.setAlpha(0f);
                    targetView.animate().alpha(1f).setDuration(300).start();
                }
            }).start();
        } else {
            applyModeVisuals();
        }
    }

    private View getCurrentModeView() {
        switch (currentMode) {
            case 0: return viewDualWidgets;
            case 1: return viewFullScreenDigital;
            case 2: return viewAuroraHorizon;
            case 3: return viewChronoStudio;
            default: return viewDualWidgets;
        }
    }

    private void toggleNightMode() {
        isNightMode = !isNightMode;
        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_NIGHT, isNightMode)
                .apply();
        applyNightModeVisuals();
    }

    private void dismissStandBy() {
        handler.removeCallbacksAndMessages(null);
        if (container != null) {
            container.animate()
                    .alpha(0f)
                    .scaleX(0.94f)
                    .scaleY(0.94f)
                    .setDuration(220)
                    .withEndAction(this::finish)
                    .start();
        } else {
            finish();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                cycleMode(true);
                showTemporaryHint();
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                cycleMode(false);
                showTemporaryHint();
                return true;

            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                toggleNightMode();
                showTemporaryHint();
                return true;

            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_HOME:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_ESCAPE:
            default:
                dismissStandBy();
                return true;
        }
    }

    private void showTemporaryHint() {
        if (hintText != null) {
            hintText.animate().cancel();
            hintText.setAlpha(1f);
            handler.removeCallbacks(hideHintRunnable);
            handler.postDelayed(hideHintRunnable, 4000);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
