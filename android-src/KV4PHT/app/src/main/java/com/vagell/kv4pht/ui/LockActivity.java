package com.vagell.kv4pht.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.vagell.kv4pht.R;

public class LockActivity extends AppCompatActivity {

    private PinStore pinStore;
    private EditText pinEditText;
    private TextView infoText;
    private Button unlockButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock);

        pinStore = new PinStore(this);

        pinEditText = findViewById(R.id.pinEditText);
        infoText = findViewById(R.id.pinInfo);
        unlockButton = findViewById(R.id.unlockButton);

        // Jeśli PIN nie ustawiony — pierwszy wpis ustawia PIN
        if (!pinStore.isPinSet()) {
            unlockButton.setText("Ustaw PIN");
            infoText.setText("Ustaw PIN (4–8 cyfr). Pierwsze wpisanie zapisze PIN.");
        } else {
            unlockButton.setText("Odblokuj");
            infoText.setText("");
        }

        unlockButton.setOnClickListener(v -> handleSubmit());

        // Enter na klawiaturze też działa
        pinEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
                handleSubmit();
                return true;
            }
            return false;
        });
    }

    private void handleSubmit() {
        String pin = pinEditText.getText() != null ? pinEditText.getText().toString().trim() : "";
        if (TextUtils.isEmpty(pin)) {
            infoText.setText("Wpisz PIN.");
            return;
        }

        if (!pinStore.isPinSet()) {
            boolean ok = pinStore.setPin(pin);
            if (ok) {
                infoText.setText("");
                unlockAndGo();
            } else {
                infoText.setText("PIN musi mieć 4–8 cyfr.");
            }
            return;
        }

        boolean verified = pinStore.verifyPin(pin);
        if (verified) {
            infoText.setText("");
            unlockAndGo();
        } else {
            infoText.setText("Nieprawidłowy PIN.");
            pinEditText.setText("");
        }
    }

    private void unlockAndGo() {
        SessionManager.unlocked = true;
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
