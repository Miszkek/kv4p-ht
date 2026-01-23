package com.vagell.kv4pht.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.vagell.kv4pht.data.AppDatabase;
import com.vagell.kv4pht.data.AppSetting;

public class LauncherActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Read the setting off the UI thread (Room doesn't allow DB access on main thread).
        new Thread(() -> {
            boolean pinLockDisabled = false;

            try {
                AppSetting setting = AppDatabase.getInstance(getApplicationContext())
                        .appSettingDao()
                        .getByName(AppSetting.SETTING_PIN_LOCK_DISABLED);

                pinLockDisabled = (setting != null)
                        && setting.value != null
                        && Boolean.parseBoolean(setting.value);

            } catch (Exception ignored) {
                // Default: PIN lock enabled
                pinLockDisabled = false;
            }

            final boolean finalPinLockDisabled = pinLockDisabled;

            runOnUiThread(() -> {
                // Keep session state consistent.
                SessionManager.unlocked = finalPinLockDisabled;

                Intent next = new Intent(
                        LauncherActivity.this,
                        finalPinLockDisabled ? MainActivity.class : LockActivity.class
                );

                startActivity(next);
                finish();
            });
        }).start();
    }
}