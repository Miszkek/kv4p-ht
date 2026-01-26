package com.vagell.kv4pht.utils;

import java.util.Arrays;
import java.util.List;

/**
 * Developer-only list of callsigns used for quick testing.
 * This list is intentionally NOT exposed through the normal UI in release builds.
 * It is only used to provide suggestions/autocomplete in debug builds.
 */
public final class DevCallsigns {
    private DevCallsigns() {}

    /**
     * Keep this list short and editable. Add/remove your own test callsigns here.
     */
    public static final List<String> LIST = Arrays.asList(
            "CQ",
            "TEST",
            "KV4P",
            "KV4P-1",
            "KV4P-2",
            "N0CALL",
            "SP0TEST",
            "SP1ABC",
            "SP2DEF",
            "SP3GHI",
            "SP4JKL",
            "SP5MNO",
            "SP6PQR",
            "SP7STU",
            "SP8VWX",
            "SQ1AAA",
            "SQ2BBB",
            "SQ3CCC",
            "SQ4DDD",
            "SQ5EEE"
    );
}
