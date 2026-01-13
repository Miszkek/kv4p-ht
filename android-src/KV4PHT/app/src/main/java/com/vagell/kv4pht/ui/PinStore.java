package com.vagell.kv4pht.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Magazyn PIN (dokładnie 4 cyfry):
 * - PIN nie jest przechowywany wprost
 * - zapisywany jest hash SHA-256 + losowa sól
 * - Java only
 */
public class PinStore {

    private static final String PREFS = "kv4pht_pin_prefs";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_PIN_SALT = "pin_salt";

    private final SharedPreferences prefs;

    public PinStore(Context context) {
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isPinSet() {
        return prefs.contains(KEY_PIN_HASH) && prefs.contains(KEY_PIN_SALT);
    }

    public void setPin(String pin4) {
        if (pin4 == null || pin4.length() != 4) {
            throw new IllegalArgumentException("PIN must be exactly 4 digits");
        }

        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);

        String hash = sha256Base64(salt, pin4);

        prefs.edit()
                .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(KEY_PIN_HASH, hash)
                .apply();
    }

    public boolean verifyPin(String pin4) {
        if (pin4 == null || pin4.length() != 4) return false;

        String saltB64 = prefs.getString(KEY_PIN_SALT, null);
        String storedHash = prefs.getString(KEY_PIN_HASH, null);
        if (saltB64 == null || storedHash == null) return false;

        byte[] salt = Base64.decode(saltB64, Base64.NO_WRAP);
        String candidate = sha256Base64(salt, pin4);
        return constantTimeEquals(storedHash, candidate);
    }

    public void clearPin() {
        prefs.edit().remove(KEY_PIN_SALT).remove(KEY_PIN_HASH).apply();
    }

    private static String sha256Base64(byte[] salt, String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            digest.update(pin.getBytes(StandardCharsets.UTF_8));
            byte[] out = digest.digest();
            return Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (Exception e) {
            // nie powinno się zdarzyć na Androidzie
            throw new RuntimeException(e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
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
