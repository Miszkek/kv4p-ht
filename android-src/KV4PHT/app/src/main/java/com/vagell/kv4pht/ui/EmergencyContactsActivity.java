/*
kv4p HT (see http://kv4p.com)
Copyright (C) 2024 Vance Vagell

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package com.vagell.kv4pht.ui;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import androidx.annotation.NonNull;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;
import com.vagell.kv4pht.R;
import com.vagell.kv4pht.data.AppSetting;
import com.vagell.kv4pht.utils.DevCallsigns;
import com.vagell.kv4pht.BuildConfig;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Settings screen for emergency contact/reporting behavior.
 * Note: "groups" are not implemented yet, so the report group field is a placeholder.
 */
public class EmergencyContactsActivity extends AppCompatActivity {
    private final ExecutorService threadPoolExecutor = Executors.newSingleThreadExecutor();
    private MainViewModel viewModel = null;

    private static final String DEFAULT_REPORT_GROUP_PLACEHOLDER = "grupa";
    private static final String DEFAULT_REPORT_GROUP_PLACEHOLDER_EN = "group";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        setContentView(R.layout.activity_emergency_contacts);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        populateDropdowns();
        populateOriginalValues(this::attachListeners);
    }
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        threadPoolExecutor.shutdown();
    }

    private void setDropdownOptions(int viewId, List<String> options) {
        this.<AutoCompleteTextView>findViewById(viewId)
            .setAdapter(new ArrayAdapter<>(this, R.layout.dropdown_item, options));
    }

    private void populateDropdowns() {
        // "Zgłoś" button
        setDropdownOptions(R.id.reportBeaconTextView,
            List.of(getResources().getStringArray(R.array.emergency_report_beacon_options)));

        // Developer-only callsign suggestions for group destination.
        // Not exposed in normal/release builds.
        if (BuildConfig.DEBUG) {
            setDropdownOptions(R.id.reportGroupCallsignTextView, DevCallsigns.LIST);
        }

        // "Panic" button
        setDropdownOptions(R.id.panicMessageSelectTextView,
            List.of(getResources().getStringArray(R.array.emergency_panic_message_slots)));
    }

    private void setTextIfPresent(Map<String, String> settings, String key, int viewId) {
        if (settings.containsKey(key)) {
            this.<TextInputEditText>findViewById(viewId).setText(settings.get(key));
        }
    }

    private void setDropdownIfPresent(Map<String, String> settings, String key, int viewId, String defaultValue) {
        this.<AutoCompleteTextView>findViewById(viewId)
            .setText(settings.getOrDefault(key, defaultValue), false);
    }

    private void populateOriginalValues(Runnable callback) {
        threadPoolExecutor.execute(() -> {
            final Map<String, String> settings = viewModel.getAppDb().appSettingDao().getAll().stream()
                .collect(Collectors.toMap(AppSetting::getName, AppSetting::getValue));

            // Backward compatibility: older builds used a placeholder value ("group"/"grupa").
            // If we find it, treat it as "unset".
            if (settings.containsKey(AppSetting.SETTING_EMERGENCY_REPORT_GROUP)) {
                String v = settings.get(AppSetting.SETTING_EMERGENCY_REPORT_GROUP);
                if (v != null) {
                    String t = v.trim();
                    if (t.equalsIgnoreCase(DEFAULT_REPORT_GROUP_PLACEHOLDER)
                        || t.equalsIgnoreCase(DEFAULT_REPORT_GROUP_PLACEHOLDER_EN)) {
                        viewModel.getAppDb().saveAppSetting(AppSetting.SETTING_EMERGENCY_REPORT_GROUP, "");
                        settings.put(AppSetting.SETTING_EMERGENCY_REPORT_GROUP, "");
                    }
                }
            }

            runOnUiThread(() -> {
                // Report button settings
                setDropdownIfPresent(settings, AppSetting.SETTING_EMERGENCY_REPORT_BEACON,
                    R.id.reportBeaconTextView, getResources().getStringArray(R.array.emergency_report_beacon_options)[0]);
                setDropdownIfPresent(settings, AppSetting.SETTING_EMERGENCY_REPORT_GROUP,
                    R.id.reportGroupCallsignTextView, "");

                // Panic button settings
                setTextIfPresent(settings, AppSetting.SETTING_EMERGENCY_PANIC_RECIPIENTS, R.id.panicRecipientsTextInputEditText);
                setDropdownIfPresent(settings, AppSetting.SETTING_EMERGENCY_PANIC_MESSAGE_SELECTED,
                    R.id.panicMessageSelectTextView, getResources().getStringArray(R.array.emergency_panic_message_slots)[0]);
                setTextIfPresent(settings, AppSetting.SETTING_EMERGENCY_PANIC_MESSAGE_1, R.id.panicMessage1TextInputEditText);
                setTextIfPresent(settings, AppSetting.SETTING_EMERGENCY_PANIC_MESSAGE_2, R.id.panicMessage2TextInputEditText);
                setTextIfPresent(settings, AppSetting.SETTING_EMERGENCY_PANIC_MESSAGE_3, R.id.panicMessage3TextInputEditText);
                callback.run();
            });
        });
    }

    private void attachTextView(int viewId, Consumer<String> onTextChanged) {
        TextView view = findViewById(viewId);
        view.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { /* NOOP */ }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { /* NOOP */ }
            @Override public void afterTextChanged(Editable s) {
                onTextChanged.accept(s.toString());
            }
        });
    }

    private void attachDropdown(int viewId, Consumer<String> onTextChanged) {
        AutoCompleteTextView view = findViewById(viewId);
        view.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { /* NOOP */ }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { /* NOOP */ }
            @Override public void afterTextChanged(Editable s) {
                onTextChanged.accept(s.toString().trim());
            }
        });
    }

    private void saveAppSettingAsync(String key, String value) {
        threadPoolExecutor.execute(() -> viewModel.getAppDb().saveAppSetting(key, value));
    }

    private void attachListeners() {
        attachDropdown(R.id.reportBeaconTextView, value -> saveAppSettingAsync(AppSetting.SETTING_EMERGENCY_REPORT_BEACON, value));
        attachDropdown(R.id.reportGroupCallsignTextView, value -> saveAppSettingAsync(AppSetting.SETTING_EMERGENCY_REPORT_GROUP, value.toUpperCase()));
        attachTextView(R.id.panicRecipientsTextInputEditText, value -> saveAppSettingAsync(AppSetting.SETTING_EMERGENCY_PANIC_RECIPIENTS, value.trim()));
        attachDropdown(R.id.panicMessageSelectTextView, value -> saveAppSettingAsync(AppSetting.SETTING_EMERGENCY_PANIC_MESSAGE_SELECTED, value));

        // TODO: You can later replace these 3 free-text templates with a richer editor (e.g., placeholders, variables).
        attachTextView(R.id.panicMessage1TextInputEditText, value -> saveAppSettingAsync(AppSetting.SETTING_EMERGENCY_PANIC_MESSAGE_1, value));
        attachTextView(R.id.panicMessage2TextInputEditText, value -> saveAppSettingAsync(AppSetting.SETTING_EMERGENCY_PANIC_MESSAGE_2, value));
        attachTextView(R.id.panicMessage3TextInputEditText, value -> saveAppSettingAsync(AppSetting.SETTING_EMERGENCY_PANIC_MESSAGE_3, value));
    }

    public void doneButtonClicked(View view) {
        setResult(Activity.RESULT_OK, getIntent());
        finish();
    }
}
