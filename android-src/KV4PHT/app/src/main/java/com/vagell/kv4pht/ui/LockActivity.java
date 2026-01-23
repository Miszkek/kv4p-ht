package com.vagell.kv4pht.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.vagell.kv4pht.R;
import com.vagell.kv4pht.data.AppDatabase;
import com.vagell.kv4pht.data.AppSetting;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ekran logowania PIN:
 * - klawiatura numeryczna
 * - PIN ma dokładnie 4 cyfry
 * - brak pola tekstowego: zamiast tego 4 kwadraciki, wypełniane kropką
 * - automatyczne przejście dalej po poprawnym PIN
 * - PANIC oraz Zgłoś: tworzą SMS do numeru awaryjnego zapisanego w SharedPreferences
 *
 * Uwaga: konfiguracja numeru awaryjnego będzie w Ustawieniach (tu tylko odczyt).
 */
public class LockActivity extends AppCompatActivity {

    private static final int PIN_LEN = 4;
    private static final char DOT = '•';

    private PinStore pinStore;
    private AppDatabase appDb;

    private final StringBuilder pinBuffer = new StringBuilder(PIN_LEN);
    private TextView[] pinBoxes;
    private TextView infoText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock);

        pinStore = new PinStore(this);
        appDb = AppDatabase.getInstance(getApplicationContext());

        infoText = findViewById(R.id.pinInfo);

        pinBoxes = new TextView[]{
                findViewById(R.id.pinBox1),
                findViewById(R.id.pinBox2),
                findViewById(R.id.pinBox3),
                findViewById(R.id.pinBox4)
        };
        renderPinBoxes();

        FloatingActionButton panicButton = findViewById(R.id.panicButton);
        FloatingActionButton reportButton = findViewById(R.id.reportButton);

        panicButton.setOnClickListener(v -> onPanicClicked());
        reportButton.setOnClickListener(v -> onReportClicked());

        wireKeypad();

        if (!pinStore.isPinSet()) {
            infoText.setText("Ustaw PIN: wpisz 4 cyfry");
        } else {
            infoText.setText("");
        }
    }

    private void wireKeypad() {
        // 0..9
        bindDigit(R.id.key0, '0');
        bindDigit(R.id.key1, '1');
        bindDigit(R.id.key2, '2');
        bindDigit(R.id.key3, '3');
        bindDigit(R.id.key4, '4');
        bindDigit(R.id.key5, '5');
        bindDigit(R.id.key6, '6');
        bindDigit(R.id.key7, '7');
        bindDigit(R.id.key8, '8');
        bindDigit(R.id.key9, '9');

        Button clear = findViewById(R.id.keyClear);
        Button del = findViewById(R.id.keyDel);

        clear.setOnClickListener(v -> {
            clearPin();
            infoText.setText(pinStore.isPinSet() ? "" : "Ustaw PIN: wpisz 4 cyfry");
        });

        del.setOnClickListener(v -> {
            if (pinBuffer.length() == 0) return;
            pinBuffer.deleteCharAt(pinBuffer.length() - 1);
            renderPinBoxes();
            infoText.setText(pinStore.isPinSet() ? "" : "Ustaw PIN: wpisz 4 cyfry");
        });
    }

    private void bindDigit(int buttonId, char digit) {
        Button b = findViewById(buttonId);
        b.setOnClickListener(v -> {
            if (pinBuffer.length() >= PIN_LEN) return;

            pinBuffer.append(digit);
            renderPinBoxes();

            if (pinBuffer.length() == PIN_LEN) {
                onPinComplete();
            }
        });
    }

    private void onPinComplete() {
        String pin = safePin();

        if (pin.length() != PIN_LEN) return;

        // PIN ma dokładnie 4 znaki -> działaj
        if (!pinStore.isPinSet()) {
            // pierwsze uruchomienie: ustaw PIN i przejdź dalej
            try {
                pinStore.setPin(pin);
                SessionManager.unlocked = true;
                unlockAndGo();
            } catch (Exception e) {
                showBadPinAndReset();
            }
            return;
        }

        // weryfikacja
        if (pinStore.verifyPin(pin)) {
            unlockAndGo();
            SessionManager.unlocked = true;
            unlockAndGo();
        } else {
            showBadPinAndReset();
        }
    }

    private void showBadPinAndReset() {
        infoText.setText("Błędny PIN");
        clearPin();
    }

    private void clearPin() {
        pinBuffer.setLength(0);
        renderPinBoxes();
    }

    private String safePin() {
        return pinBuffer.toString();
    }

    private void renderPinBoxes() {
        for (int i = 0; i < pinBoxes.length; i++) {
            pinBoxes[i].setText(i < pinBuffer.length() ? String.valueOf(DOT) : "");
        }
    }

    private void onPanicClicked() {
        // Build message body from EmergencyContacts settings (3 slots + selected slot).
        appDbReadSettings(settings -> {
            String to = readEmergencyGroupCallsign(settings);
            if (to == null) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Ustaw callsign grupy w Emergency contact", Toast.LENGTH_LONG).show());
                return;
            }

            String selected = safe(settings.get(AppSetting.SETTING_EMERGENCY_PANIC_MESSAGE_SELECTED));
            if (selected.isEmpty()) {
                selected = getResources().getStringArray(R.array.emergency_panic_message_slots)[0];
            }

            String msg1 = safe(settings.get(AppSetting.SETTING_EMERGENCY_PANIC_MESSAGE_1));
            String msg2 = safe(settings.get(AppSetting.SETTING_EMERGENCY_PANIC_MESSAGE_2));
            String msg3 = safe(settings.get(AppSetting.SETTING_EMERGENCY_PANIC_MESSAGE_3));

            String body;
            if (selected.contains("2")) {
                body = msg2.isEmpty() ? "KV4PHT: PANIC (message 2)" : msg2;
            } else if (selected.contains("3")) {
                body = msg3.isEmpty() ? "KV4PHT: PANIC (message 3)" : msg3;
            } else {
                body = msg1.isEmpty() ? "KV4PHT: PANIC / EMERGENCY" : msg1;
            }

            startEmergencySend(to, body);
        });
    }

    private void onReportClicked() {
        List<ReportOption> options = buildDefaultReportOptions();

        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(this);
        b.setTitle("Wybierz zgłoszenie");

        ReportOptionAdapter adapter = new ReportOptionAdapter(this, options);
        b.setAdapter(adapter, (dialog, which) -> {
            ReportOption chosen = options.get(which);
            appDbReadSettings(settings -> {
                String to = readEmergencyGroupCallsign(settings);
                if (to == null) {
                    runOnUiThread(() -> Toast.makeText(this,
                            "Ustaw callsign grupy w Emergency contact", Toast.LENGTH_LONG).show());
                    return;
                }
                startEmergencySend(to, chosen.smsBody);
            });
        });

        b.setNegativeButton("Anuluj", (dialog, which) -> dialog.dismiss());
        b.show();
    }

    private List<ReportOption> buildDefaultReportOptions() {
        List<ReportOption> list = new ArrayList<>();

        // Ikony: używamy bezpiecznych zasobów systemowych, żeby uniknąć braków w drawable projektu.
        list.add(new ReportOption("Potrzebuję wsparcia", "KV4PHT: ZGŁOSZENIE 1 - POTRZEBUJĘ WSPARCIA", android.R.drawable.ic_dialog_info));
        list.add(new ReportOption("Uraz / wypadek", "KV4PHT: ZGŁOSZENIE 2 - URAZ / WYPADEK", android.R.drawable.ic_menu_compass));
        list.add(new ReportOption("Zagrożenie", "KV4PHT: ZGŁOSZENIE 3 - ZAGROŻENIE", android.R.drawable.ic_dialog_alert));
        list.add(new ReportOption("Test / OK", "KV4PHT: ZGŁOSZENIE 4 - TEST / OK", android.R.drawable.checkbox_on_background));

        return list;
    }

    private void startEmergencySend(String toCallsign, String messageBody) {
        Intent i = new Intent(this, EmergencySendActivity.class);
        i.putExtra(EmergencySendActivity.EXTRA_TO_CALLSIGN, toCallsign);
        i.putExtra(EmergencySendActivity.EXTRA_MESSAGE_BODY, messageBody);
        startActivity(i);
    }

    private interface SettingsCallback {
        void onSettings(Map<String, String> settings);
    }

    private void appDbReadSettings(SettingsCallback cb) {
        new Thread(() -> {
            Map<String, String> settings = appDb.appSettingDao().getAll().stream()
                    .collect(java.util.stream.Collectors.toMap(AppSetting::getName, AppSetting::getValue));
            cb.onSettings(settings);
        }).start();
    }

    private String readEmergencyGroupCallsign(Map<String, String> settings) {
        String group = safe(settings.get(AppSetting.SETTING_EMERGENCY_REPORT_GROUP));
        if (group.isEmpty()) return null;
        if (group.equalsIgnoreCase("group") || group.equalsIgnoreCase("grupa")) return null;
        return group.toUpperCase();
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private void unlockAndGo() {
        // W projekcie KV4PHT LockActivity zwykle prowadzi do MainActivity
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
