package com.vagell.kv4pht.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Lightweight local storage for received beacon locations.
 * This avoids needing a Room migration right now; it still persists in app storage.
 */
public class BeaconLocationStore extends SQLiteOpenHelper {

    private static final String DB_NAME = "kv4pht_beacons.db";
    private static final int DB_VERSION = 1;

    private static final String T = "beacon_locations";

    public BeaconLocationStore(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS " + T + " (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "sender TEXT," +
                        "lat REAL NOT NULL," +
                        "lon REAL NOT NULL," +
                        "received_at INTEGER NOT NULL" +
                        ")"
        );
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_beacon_received ON " + T + "(received_at DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // no-op for v1
    }

    /** Call this from your message-receiving code when you detect a beacon with lat/lon. */
    public void insert(String sender, double lat, double lon, long receivedAt) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("sender", sender);
        cv.put("lat", lat);
        cv.put("lon", lon);
        cv.put("received_at", receivedAt);
        db.insert(T, null, cv);
    }

    /** Returns newest-first. */
    public List<BeaconPoint> getLastPoints(int limit) {
        List<BeaconPoint> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT sender, lat, lon, received_at FROM " + T + " ORDER BY received_at DESC LIMIT ?",
                new String[]{String.valueOf(limit)}
        )) {
            while (c.moveToNext()) {
                String sender = c.isNull(0) ? null : c.getString(0);
                double lat = c.getDouble(1);
                double lon = c.getDouble(2);
                long ts = c.getLong(3);
                out.add(new BeaconPoint(sender, lat, lon, ts));
            }
        }
        return out;
    }

    public static class BeaconPoint {
        public final String sender;
        public final double lat;
        public final double lon;
        public final long receivedAt;

        public BeaconPoint(String sender, double lat, double lon, long receivedAt) {
            this.sender = sender;
            this.lat = lat;
            this.lon = lon;
            this.receivedAt = receivedAt;
        }

        public String receivedAtString() {
            return DateFormat.getDateTimeInstance().format(new Date(receivedAt));
        }
    }
}
