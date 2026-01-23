package com.vagell.kv4pht.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vagell.kv4pht.R;
import com.vagell.kv4pht.data.AppDatabase;
import com.vagell.kv4pht.data.AppSetting;
import com.vagell.kv4pht.radio.RadioAudioService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Minimal activity used to transmit an emergency APRS message (panic / report) even when the app
 * is locked. It starts/binds {@link RadioAudioService}, sends a single chat message, then finishes.
 */
public class EmergencySendActivity extends AppCompatActivity {

    public static final String EXTRA_TO_CALLSIGN = "extra_to_callsign";
    public static final String EXTRA_MESSAGE_BODY = "extra_message_body";

    private static final int REQUEST_ALL_PERMISSIONS = 9013;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Consumer<Boolean> pendingGrantCallback = null;
    private List<String> pendingPerms = null;

    private RadioAudioService radioAudioService = null;
    private boolean bound = false;

    private String toCallsign;
    private String messageBody;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            radioAudioService = ((RadioAudioService.RadioBinder) service).getService();
            bound = true;

            // We don't need full callbacks here; we just attempt to send.
            trySendAndFinish();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            radioAudioService = null;
            bound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_send);

        Intent i = getIntent();
        toCallsign = i.getStringExtra(EXTRA_TO_CALLSIGN);
        messageBody = i.getStringExtra(EXTRA_MESSAGE_BODY);

        if (TextUtils.isEmpty(toCallsign) || TextUtils.isEmpty(messageBody)) {
            Toast.makeText(this, "Missing emergency destination or message", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        ((TextView) findViewById(R.id.emergencySendStatus)).setText(
                getString(R.string.emergency_contact_option) + ": " + toCallsign_toggleCase(toCallsign));

        startAndBindServiceWithSettings();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        try {
            if (bound) {
                unbindService(connection);
                bound = false;
            }
        } catch (Exception ignored) {
        }
    }

    private void startAndBindServiceWithSettings() {
        ensurePermissions(foregroundServicePermissions(), allGranted -> {
            if (!Boolean.TRUE.equals(allGranted)) {
                Toast.makeText(this, "Required permissions not granted", Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            executor.execute(() -> {
                AppDatabase db = AppDatabase.getInstance(getApplicationContext());
                Map<String, String> settings = db.appSettingDao().getAll().stream()
                        .collect(Collectors.toMap(AppSetting::getName, AppSetting::getValue));

                String callsign = safe(settings.get(AppSetting.SETTING_CALLSIGN));
                int squelch = 0;
                try {
                    String sq = settings.get(AppSetting.SETTING_SQUELCH);
                    if (sq != null) {
                        squelch = Integer.parseInt(sq.trim());
                    }
                } catch (Exception ignored) { }

                int activeMemoryId = -1;
                try {
                    String m = settings.get(AppSetting.SETTING_LAST_MEMORY_ID);
                    if (m != null) {
                        activeMemoryId = Integer.parseInt(m.trim());
                    }
                } catch (Exception ignored) { }

                String activeFreq = safe(settings.get(AppSetting.SETTING_LAST_FREQ));
                if (activeFreq.isEmpty()) {
                    activeFreq = "146.5200"; // safe default
                }

                if (callsign.isEmpty()) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Set your callsign first", Toast.LENGTH_LONG).show();
                        finish();
                    });
                    return;
                }

                Intent svc = new Intent(this, RadioAudioService.class)
                        .putExtra(AppSetting.SETTING_CALLSIGN, callsign)
                        .putExtra(AppSetting.SETTING_SQUELCH, squelch)
                        .putExtra("activeMemoryId", activeMemoryId)
                        .putExtra("activeFrequencyStr", activeFreq);

                runOnUiThread(() -> {
                    try {
                        startForegroundService(svc);
                        bindService(svc, connection, Context.BIND_AUTO_CREATE);
                    } catch (Exception e) {
                        Toast.makeText(this, "Can't start radio service: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
            });
        });
    }

    private void trySendAndFinish() {
        if (radioAudioService == null) {
            Toast.makeText(this, "Radio service unavailable", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (!radioAudioService.isTxAllowed()) {
            Toast.makeText(this, "TX not allowed (check frequency / band)", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        // Attempt to send.
        radioAudioService.sendChatMessage(toCallsign.trim().toUpperCase(), messageBody.trim());
        Toast.makeText(this, "Emergency message sent", Toast.LENGTH_SHORT).show();
        finish();
    }

    private List<String> foregroundServicePermissions() {
        return List.of(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
        );
    }

    public void ensurePermissions(List<String> requestedPerms, Consumer<Boolean> callback) {
        // Drop POST_NOTIFICATIONS on < 33
        List<String> needed = new ArrayList<>();
        for (String p : requestedPerms) {
            if (Manifest.permission.POST_NOTIFICATIONS.equals(p) && Build.VERSION.SDK_INT < 33) {
                continue;
            }
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                needed.add(p);
            }
        }
        if (needed.isEmpty()) {
            callback.accept(true);
            return;
        }
        pendingGrantCallback = callback;
        pendingPerms = needed;

        boolean showRationale = false;
        for (String p : pendingPerms) {
            if (shouldShowRequestPermissionRationale(p)) {
                showRationale = true;
                break;
            }
        }
        if (showRationale) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Permissions needed")
                    .setMessage("We need permissions for the radio foreground service (audio/location/notifications).")
                    .setPositiveButton("Continue", (d, idx) ->
                            requestPermissions(pendingPerms.toArray(new String[0]), REQUEST_ALL_PERMISSIONS))
                    .setNegativeButton("Cancel", (d, idx) -> {
                        pendingGrantCallback = null;
                        pendingPerms = null;
                        callback.accept(false);
                    })
                    .show();
        } else {
            requestPermissions(pendingPerms.toArray(new String[0]), REQUEST_ALL_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_ALL_PERMISSIONS) {
            return;
        }
        boolean allGranted = true;
        for (int r : grantResults) {
            if (r != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        Consumer<Boolean> done = pendingGrantCallback;
        pendingGrantCallback = null;
        pendingPerms = null;
        if (done != null) {
            done.accept(allGranted);
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String toCallsign_toggleCase(String s) {
        // Keep output readable (avoid null). No transformation besides trim.
        return safe(s);
    }
}
