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
 * - OLED pitch-black background with dynamic radial ambient glow.
 * - Dual-Widget Split Mode (iOS StandBy Dual Platter):
 *     * Left: Giant bold digital typography clock + day/calendar + Wi-Fi status.
 *     * Right: Authentic Apple Watch analog dial clock with sweeping hands + weather/glances.
 * - Full-Screen Minimalist Digital Clock Mode (D-Pad Left/Right cycles modes).
 * - Deep Red OLED Night Mode (Press Menu or Down key, or automatic).
 * - 60s Anti-Burn-In Pixel Shifting (smooth drift prevents OLED image retention).
 * - Instant dismiss on any remote key (Select / Back / Home / Navigation).
 */
public class StandByActivity extends Activity {

    private static final String PREF_NAME = "standby_settings";
    private static final String KEY_STYLE = "standby_style";
    private static final String KEY_NIGHT = "standby_night";

    private static final long DRIFT_INTERVAL_MS = 60_000L;
    private static final long ANALOG_TICK_INTERVAL_MS = 100L;

    // tvOS ambient radial glow palette (subtle, low saturation for OLED)
    private static final int[] AMBIENT_PALETTE = {
            0xFF2B5CFF, // Deep Indigo
            0xFF00A39A, // Apple Teal
            0xFF8A3FFC, // Electric Violet
            0xFFE0661B, // Sunset Amber
            0xFFD6336C  // Magenta Glow
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private View root;
    private View ambientGlowView;
    private GradientDrawable ambientDrawable;
    private FrameLayout container;
    private View viewDualWidgets;
    private View viewFullScreenDigital;
    private StandByAnalogClock analogClock;
    private TextView standbyStatusText;
    private ImageView standbyWifiIcon;
    private TextView hintText;

    // State
    private int currentMode = 0; // 0 = Dual Widgets, 1 = Fullscreen Minimal Digital
    private boolean isNightMode = false;
    private int paletteIndex = 0;

    // Analog clock ticker
    private final Runnable analogTick = new Runnable() {
        @Override
        public void run() {
            if (analogClock != null && currentMode == 0) {
                analogClock.invalidate();
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
        analogClock = findViewById(R.id.standbyAnalogClock);
        standbyStatusText = findViewById(R.id.standbyStatusText);
        standbyWifiIcon = findViewById(R.id.standbyWifiIcon);
        hintText = findViewById(R.id.standbyHintText);

        // Load saved preferences
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        currentMode = prefs.getInt(KEY_STYLE, 0);
        isNightMode = prefs.getBoolean(KEY_NIGHT, false);

        setupAmbientGlow();
        updateNetworkStatus();
        applyModeVisuals();
        applyNightModeVisuals();

        // Start tickers
        handler.post(analogTick);
        handler.postDelayed(burnInDriftTick, DRIFT_INTERVAL_MS);
        handler.postDelayed(hideHintRunnable, 5000); // fade out hint after 5s

        // Entrance animation
        if (container != null) {
            container.setAlpha(0f);
            container.setScaleX(0.95f);
            container.setScaleY(0.95f);
            container.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(600).start();
        }
    }

    private void setupAmbientGlow() {
        ambientDrawable = new GradientDrawable();
        ambientDrawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambientGlowView.setBackground(ambientDrawable);
        ambientGlowView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            ambientDrawable.setGradientRadius((r - l) * 0.55f);
            ambientDrawable.setGradientCenter(0.5f, 0.5f);
        });
        paletteIndex = random.nextInt(AMBIENT_PALETTE.length);
        setAmbientColor(AMBIENT_PALETTE[paletteIndex]);
    }

    private void setAmbientColor(int color) {
        if (isNightMode) {
            // Ultra dim crimson radial glow
            ambientDrawable.setColors(new int[]{0x2A500000, 0x00000000});
        } else {
            int glowCenter = Color.argb(45, Color.red(color), Color.green(color), Color.blue(color));
            ambientDrawable.setColors(new int[]{glowCenter, 0x00000000});
        }
    }

    private void shiftAmbientGlow() {
        if (isNightMode) return;
        int oldColor = AMBIENT_PALETTE[paletteIndex];
        paletteIndex = (paletteIndex + 1) % AMBIENT_PALETTE.length;
        int newColor = AMBIENT_PALETTE[paletteIndex];

        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), oldColor, newColor);
        anim.setDuration(3000);
        anim.addUpdateListener(a -> setAmbientColor((int) a.getAnimatedValue()));
        anim.start();
    }

    /**
     * Subtle random pixel drift to safeguard OLED displays against burn-in.
     */
    private void applyBurnInDrift() {
        if (container == null) return;
        // Shift container by subtle offset between -20px and +20px
        float targetX = (random.nextFloat() * 40f) - 20f;
        float targetY = (random.nextFloat() * 30f) - 15f;

        container.animate()
                .translationX(targetX)
                .translationY(targetY)
                .setDuration(2500)
                .start();

        // Also drift ambient glow center slightly
        if (ambientDrawable != null) {
            float cx = 0.5f + (targetX / 1920f);
            float cy = 0.5f + (targetY / 1080f);
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
        if (currentMode == 0) {
            viewDualWidgets.setVisibility(View.VISIBLE);
            viewFullScreenDigital.setVisibility(View.GONE);
        } else {
            viewDualWidgets.setVisibility(View.GONE);
            viewFullScreenDigital.setVisibility(View.VISIBLE);
        }
    }

    private void applyNightModeVisuals() {
        int clockPrimaryColor = isNightMode ? 0xFFFF3B30 : 0xFFFFFFFF; // Apple Red Tint
        int clockSecColor = isNightMode ? 0x99FF3B30 : 0x88FFFFFF;
        int accentColor = isNightMode ? 0xFFFF453A : 0xFFFF453A;

        // Digital clocks color
        TextView hours = findViewById(R.id.standbyClockHours);
        TextView ampm = findViewById(R.id.standbyClockAmPm);
        TextView date = findViewById(R.id.standbyDate);
        TextView fullClock = findViewById(R.id.fullBigClock);
        TextView fullAmPm = findViewById(R.id.fullBigAmPm);
        TextView fullDate = findViewById(R.id.fullBigDate);

        if (hours != null) hours.setTextColor(clockPrimaryColor);
        if (ampm != null) ampm.setTextColor(clockSecColor);
        if (date != null) date.setTextColor(clockSecColor);
        if (fullClock != null) fullClock.setTextColor(clockPrimaryColor);
        if (fullAmPm != null) fullAmPm.setTextColor(clockSecColor);
        if (fullDate != null) fullDate.setTextColor(clockSecColor);

        if (analogClock != null) {
            analogClock.setColors(clockPrimaryColor, clockSecColor, accentColor);
        }

        setAmbientColor(AMBIENT_PALETTE[paletteIndex]);
    }

    private void cycleMode() {
        currentMode = (currentMode == 0) ? 1 : 0;
        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_STYLE, currentMode)
                .apply();

        // Animate transition
        viewDualWidgets.animate().alpha(0f).setDuration(250).withEndAction(() -> {
            applyModeVisuals();
            View target = (currentMode == 0) ? viewDualWidgets : viewFullScreenDigital;
            target.setAlpha(0f);
            target.animate().alpha(1f).setDuration(350).start();
        }).start();
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
                    .setDuration(250)
                    .withEndAction(this::finish)
                    .start();
        } else {
            finish();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                cycleMode();
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
