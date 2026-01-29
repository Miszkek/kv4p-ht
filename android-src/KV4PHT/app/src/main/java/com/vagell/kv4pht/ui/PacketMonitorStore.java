package com.vagell.kv4pht.ui;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.vagell.kv4pht.data.APRSMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Very small in-memory "packet monitor" store.
 *
 * Goal: show *all* received APRS packets like a terminal, independent of chat filtering.
 * This is intentionally lightweight: no DB, no paging. (Easy to later replace with Room.)
 */
public final class PacketMonitorStore {

    public static final int DEFAULT_MAX_LINES = 800;

    private static PacketMonitorStore instance;

    private final MutableLiveData<List<PacketLine>> linesLiveData = new MutableLiveData<>(Collections.emptyList());
    private final Object lock = new Object();

    private int maxLines = DEFAULT_MAX_LINES;

    private PacketMonitorStore() {}

    public static PacketMonitorStore get() {
        if (instance == null) instance = new PacketMonitorStore();
        return instance;
    }

    public LiveData<List<PacketLine>> getLines() {
        return linesLiveData;
    }

    public void setMaxLines(int max) {
        synchronized (lock) {
            maxLines = Math.max(100, max);
        }
    }

    public void clear() {
        synchronized (lock) {
            linesLiveData.postValue(Collections.emptyList());
        }
    }

    /**
     * Add a raw line to the monitor.
     *
     * @param freqHzOrMHz frequency (whatever units you prefer to display; recommended MHz)
     * @param rawLine the raw packet / parsed summary you want shown
     */
    public void addLine(double freqHzOrMHz, @NonNull String rawLine) {
        addLine(freqHzOrMHz, rawLine, System.currentTimeMillis());
    }

    public void addLine(double freqHzOrMHz, @NonNull String rawLine, long timestampMs) {
        final PacketLine line = new PacketLine(timestampMs, freqHzOrMHz, rawLine);

        synchronized (lock) {
            List<PacketLine> current = linesLiveData.getValue();
            if (current == null) current = Collections.emptyList();

            ArrayList<PacketLine> next = new ArrayList<>(current.size() + 1);
            next.addAll(current);
            next.add(line);

            if (next.size() > maxLines) {
                int start = Math.max(0, next.size() - maxLines);
                next = new ArrayList<>(next.subList(start, next.size()));
            }

            linesLiveData.postValue(Collections.unmodifiableList(next));
        }
    }

    /**
     * Loads historical packets from APRS messages database.
     */
    public void loadFromAPRSMessages(List<APRSMessage> messages) {
        if (messages == null) return;

        synchronized (lock) {
            ArrayList<PacketLine> next = new ArrayList<>();
            for (APRSMessage msg : messages) {
                String summary = formatMessageForMonitor(msg);
                // APRSMessage uses seconds since epoch, PacketLine uses milliseconds
                next.add(new PacketLine(msg.timestamp * 1000, 0.0, summary));
            }

            // Apply line limit
            if (next.size() > maxLines) {
                int start = next.size() - maxLines;
                next = new ArrayList<>(next.subList(start, next.size()));
            }

            linesLiveData.postValue(Collections.unmodifiableList(next));
        }
    }

    private String formatMessageForMonitor(APRSMessage msg) {
        StringBuilder sb = new StringBuilder();
        sb.append(msg.fromCallsign != null ? msg.fromCallsign : "NOCALL");
        sb.append(">APRS:");

        if (msg.type == APRSMessage.MESSAGE_TYPE) {
            sb.append("::").append(msg.toCallsign != null ? msg.toCallsign : "UNKNOWN").append(":");
            sb.append(msg.msgBody != null ? msg.msgBody : "");
        } else if (msg.type == APRSMessage.POSITION_TYPE) {
            sb.append("!").append(String.format(Locale.US, "%.4f/%.4f", msg.positionLat, msg.positionLong));
            if (msg.comment != null && !msg.comment.isEmpty()) {
                sb.append(" ").append(msg.comment);
            }
        } else if (msg.type == APRSMessage.WEATHER_TYPE) {
            sb.append("_WX:").append(msg.temperature).append("F/").append(msg.humidity).append("%");
        } else {
            if (msg.comment != null && !msg.comment.isEmpty()) {
                sb.append(msg.comment);
            } else {
                sb.append("APRS Packet");
            }
        }
        return sb.toString();
    }

    public static final class PacketLine {
        public final long timestampMs;
        public final double freq;
        public final String raw;

        public PacketLine(long timestampMs, double freq, @NonNull String raw) {
            this.timestampMs = timestampMs;
            this.freq = freq;
            this.raw = raw;
        }

        @NonNull
        public String toDisplayString(boolean showFrequency) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            String ts = sdf.format(new Date(timestampMs));
            if (showFrequency) {
                return ts + "  " + String.format(Locale.US, "%.3f", freq) + "  " + raw;
            }
            return ts + "  " + raw;
        }
    }
}
