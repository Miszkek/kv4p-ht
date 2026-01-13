package com.vagell.kv4pht.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Ekran logowania PIN:
 * - klawiatura numeryczna
 * - PIN ma dokładnie 4 cyfry
 * - automatyczne przejście dalej po poprawnym PIN
 * - PANIC oraz Zgłoś: tworzą SMS do numeru awaryjnego zapisanego w SharedPreferences
 *
 * Uwaga: konfiguracja numeru awaryjnego będzie w Ustawieniach (tu tylko odczyt).
 */
public class LockActivity extends AppCompatActivity {

    private static final int PIN_LEN = 4;

    private static final String EMERGENCY_PREFS = "kv4pht_emergency_prefs";
    private static final String KEY_EMERGENCY_PHONE = "emergency_phone";

    private PinStore pinStore;
    private EditText pinEditText;
    private TextView infoText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock);

        pinStore = new PinStore(this);

        pinEditText = findViewById(R.id.pinEditText);
        infoText = findViewById(R.id.pinInfo);

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
        GridLayout keypad = findViewById(R.id.keypadGrid);

        // 0..9
        bindDigit(R.id.key0, "0");
        bindDigit(R.id.key1, "1");
        bindDigit(R.id.key2, "2");
        bindDigit(R.id.key3, "3");
        bindDigit(R.id.key4, "4");
        bindDigit(R.id.key5, "5");
        bindDigit(R.id.key6, "6");
        bindDigit(R.id.key7, "7");
        bindDigit(R.id.key8, "8");
        bindDigit(R.id.key9, "9");

        Button clear = findViewById(R.id.keyClear);
        Button del = findViewById(R.id.keyDel);

        clear.setOnClickListener(v -> {
            pinEditText.setText("");
            infoText.setText(pinStore.isPinSet() ? "" : "Ustaw PIN: wpisz 4 cyfry");
        });

        del.setOnClickListener(v -> {
            String cur = safePin();
            if (cur.isEmpty()) return;
            pinEditText.setText(cur.substring(0, cur.length() - 1));
            infoText.setText(pinStore.isPinSet() ? "" : "Ustaw PIN: wpisz 4 cyfry");
        });
    }

    private void bindDigit(int buttonId, String digit) {
        Button b = findViewById(buttonId);
        b.setOnClickListener(v -> {
            String cur = safePin();
            if (cur.length() >= PIN_LEN) return;

            pinEditText.setText(cur + digit);
            onPinChanged();
        });
    }

    private void onPinChanged() {
        String pin = safePin();

        if (pin.length() < PIN_LEN) return;
        if (pin.length() > PIN_LEN) {
            // teoretycznie nie powinno zajść (maxLength=4), ale zostawiamy bezpiecznik
            pinEditText.setText("");
            return;
        }

        // PIN ma dokładnie 4 znaki -> działaj
        if (!pinStore.isPinSet()) {
            // pierwsze uruchomienie: ustaw PIN i przejdź dalej
            try {
                pinStore.setPin(pin);
                unlockAndGo();
            } catch (Exception e) {
                showBadPinAndReset();
            }
            return;
        }

        // weryfikacja
        if (pinStore.verifyPin(pin)) {
            unlockAndGo();
        } else {
            showBadPinAndReset();
        }
    }

    private void showBadPinAndReset() {
        infoText.setText("Błędny PIN");
        pinEditText.setText("");
    }

    private String safePin() {
        CharSequence cs = pinEditText.getText();
        return cs == null ? "" : cs.toString().trim();
    }

    private void onPanicClicked() {
        String phone = getEmergencyPhoneOrWarn();
        if (phone == null) return;

        String msg = "KV4PHT: PANIC / SYTUACJA AWARYJNA. Proszę o pilny kontakt.";
        openSmsComposer(phone, msg);
    }

    private void onReportClicked() {
        String phone = getEmergencyPhoneOrWarn();
        if (phone == null) return;

        List<ReportOption> options = buildDefaultReportOptions();

        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(this);
        b.setTitle("Wybierz zgłoszenie");

        ReportOptionAdapter adapter = new ReportOptionAdapter(this, options);
        b.setAdapter(adapter, (dialog, which) -> {
            ReportOption chosen = options.get(which);
            openSmsComposer(phone, chosen.smsBody);
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

    private String getEmergencyPhoneOrWarn() {
        String phone = getSharedPreferences(EMERGENCY_PREFS, MODE_PRIVATE)
                .getString(KEY_EMERGENCY_PHONE, null);

        if (TextUtils.isEmpty(phone)) {
            Toast.makeText(this, "Brak numeru alarmowego. Ustaw w Ustawieniach.", Toast.LENGTH_LONG).show();
            return null;
        }
        return phone;
    }

    private void openSmsComposer(String phoneNumber, String message) {
        try {
            Uri uri = Uri.parse("smsto:" + Uri.encode(phoneNumber));
            Intent intent = new Intent(Intent.ACTION_SENDTO, uri);
            intent.putExtra("sms_body", message);
            startActivity(intent);
        } catch (Exception e) {
            // awaryjnie: wybieranie numeru
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phoneNumber)));
            startActivity(intent);
        }
    }

    private void unlockAndGo() {
        // W projekcie KV4PHT LockActivity zwykle prowadzi do MainActivity
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
