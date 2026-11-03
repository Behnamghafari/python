package com.behnam.timerfloat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public class FloatingTimerService extends Service {
    public static final String ACTION_SHOW = "com.behnam.timerfloat.SHOW";
    private static final String CHANNEL_ID = "timer_float_channel";
    private static final int NOTIFICATION_ID = 8124;

    private WindowManager windowManager;
    private View overlay;
    private TextView timeView;
    private TextView toggleView;
    private boolean running = true;
    private boolean attached = false;
    private long accumulatedMs = 0L;
    private long startedAt = System.currentTimeMillis();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateTime();
            handler.postDelayed(this, 50);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_SHOW.equals(intent.getAction())) {
            showOverlay();
        }
        return START_STICKY;
    }

    private void showOverlay() {
        if (attached) return;
        if (Build.VERSION.SDK_INT >= 23 && !android.provider.Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(7), dp(3), dp(5), dp(3));
        pill.setElevation(dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(31, 33, 38));
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), 0xFF555B65);
        pill.setBackground(bg);

        timeView = new TextView(this);
        timeView.setText("00:00");
        timeView.setTextColor(Color.WHITE);
        timeView.setTextSize(12);
        timeView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeView.setGravity(Gravity.CENTER);
        pill.addView(timeView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(24)));

        toggleView = button("Ⅱ", 0xFFE7E9ED, this::toggleTimer);
        pill.addView(toggleView, buttonParams());

        TextView reset = button("↺", 0xFFE7E9ED, this::resetTimer);
        pill.addView(reset, buttonParams());

        TextView restart = button("⟳", 0xFFE7E9ED, this::restartTimer);
        pill.addView(restart, buttonParams());

        TextView close = button("×", 0xFFFF7777, this::closeOverlay);
        pill.addView(close, buttonParams());

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(8);
        params.y = dp(100);

        attachDrag(pill, params);
        try {
            windowManager.addView(pill, params);
            overlay = pill;
            attached = true;
            handler.removeCallbacks(ticker);
            handler.post(ticker);
        } catch (Exception e) {
            stopSelf();
        }
    }

    private TextView button(String label, int color, Runnable action) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(color);
        view.setTextSize(16);
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        view.setFocusable(false);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(25), dp(25));
        p.leftMargin = dp(1);
        return p;
    }

    private void attachDrag(View view, WindowManager.LayoutParams params) {
        view.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean moved;

            @Override public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = params.x;
                        startY = params.y;
                        moved = false;
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) moved = true;
                        if (moved) {
                            params.x = startX + (int) dx;
                            params.y = startY + (int) dy;
                            try { windowManager.updateViewLayout(view, params); } catch (Exception ignored) {}
                            return true;
                        }
                        return false;
                    default:
                        return moved;
                }
            }
        });
    }

    private void toggleTimer() {
        if (running) {
            accumulatedMs += System.currentTimeMillis() - startedAt;
            running = false;
            toggleView.setText("▶");
        } else {
            startedAt = System.currentTimeMillis();
            running = true;
            toggleView.setText("Ⅱ");
        }
        updateTime();
    }

    private void resetTimer() {
        accumulatedMs = 0L;
        if (running) startedAt = System.currentTimeMillis();
        updateTime();
    }

    private void restartTimer() {
        accumulatedMs = 0L;
        startedAt = System.currentTimeMillis();
        running = true;
        if (toggleView != null) toggleView.setText("Ⅱ");
        updateTime();
    }

    private long elapsed() {
        return accumulatedMs + (running ? System.currentTimeMillis() - startedAt : 0L);
    }

    private void updateTime() {
        if (timeView == null) return;
        long centiseconds = elapsed() / 10L;
        long hours = centiseconds / 360000L;
        long minutes = (centiseconds / 6000L) % 60L;
        long seconds = (centiseconds / 100L) % 60L;
        long cs = centiseconds % 100L;
        String value = hours > 0
                ? String.format(Locale.US, "%d:%02d:%02d.%02d", hours, minutes, seconds, cs)
                : String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, cs);
        timeView.setText(value);
    }

    private void closeOverlay() {
        handler.removeCallbacks(ticker);
        if (attached && overlay != null) {
            try { windowManager.removeView(overlay); } catch (Exception ignored) {}
        }
        attached = false;
        overlay = null;
        timeView = null;
        stopForeground(true);
        stopSelf();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "کرنومتر شناور", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("اعلان لازم برای نگه داشتن کرنومتر شناور");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder.setContentTitle("کرنومتر شناور فعال است")
                .setContentText("برای بستن، × روی نوار شناور را بزن")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(ticker);
        if (attached && overlay != null) {
            try { windowManager.removeView(overlay); } catch (Exception ignored) {}
        }
        attached = false;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
