package com.vagell.kv4pht.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Prosty magazyn PIN-u:
 * - PIN nie jest przechowywany wprost
 * - zapisywany jest hash SHA-256 + losowa sól
 * - Java only, brak zależności od Kotlina
 */
public class PinStore {

    private static final String PREFS_NAME = "kv4pht_pin_store";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_PIN_SALT = "pin_salt";

    private final SharedPreferences prefs;

    public PinStore(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Czy PIN jest już ustawiony */
    public boolean isPinSet() {
        return prefs.contains(KEY_PIN_HASH) && prefs.contains(KEY_PIN_SALT);
    }

    /** Usunięcie PIN-u */
    public void clearPin() {
        prefs.edit()
                .remove(KEY_PIN_HASH)
                .remove(KEY_PIN_SALT)
                .apply();
    }

    /** Ustawia PIN (4–8 cyfr) */
    public boolean setPin(String pin) {
        if (!isValidPin(pin)) {
            return false;
        }

        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);

        String hash = hashPin(pin, salt);

        prefs.edit()
                .putString(KEY_PIN_HASH, hash)
                .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .apply();

        return true;
    }

    /** Sprawdza poprawność PIN-u */
    public boolean verifyPin(String pin) {
        if (!isValidPin(pin)) {
            return false;
        }

        String storedHash = prefs.getString(KEY_PIN_HASH, null);
        String storedSalt = prefs.getString(KEY_PIN_SALT, null);

        if (storedHash == null || storedSalt == null) {
            return false;
        }

        byte[] salt = Base64.decode(storedSalt, Base64.NO_WRAP);
        String computedHash = hashPin(pin, salt);

        return constantTimeEquals(storedHash, computedHash);
    }

    /* ===================== PRIVATE ===================== */

    private boolean isValidPin(String pin) {
        if (pin == null) return false;
        if (pin.length() < 4 || pin.length() > 8) return false;

        for (int i = 0; i < pin.length(); i++) {
            char c = pin.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    private String hashPin(String pin, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            digest.update(pin.getBytes(StandardCharsets.UTF_8));
            byte[] result = digest.digest();
            return Base64.encodeToString(result, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Stałoczasowe porównanie (chroni przed timing attack)
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;

        byte[] aa = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);

        if (aa.length != bb.length) return false;

        int diff = 0;
        for (int i = 0; i < aa.length; i++) {
            diff |= aa[i] ^ bb[i];
        }
        return diff == 0;
    }
}
