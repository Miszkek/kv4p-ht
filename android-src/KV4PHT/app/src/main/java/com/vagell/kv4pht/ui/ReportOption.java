package com.vagell.kv4pht.ui;

import androidx.annotation.DrawableRes;

/**
 * Jedna z 4 standardowych opcji "Zgłoś".
 *
 * NOTE: Nazwy i treści można łatwo zmienić w LockActivity.buildDefaultReportOptions().
 */
public class ReportOption {
    public final String title;
    public final String smsBody;
    @DrawableRes public final int iconRes;

    public ReportOption(String title, String smsBody, @DrawableRes int iconRes) {
        this.title = title;
        this.smsBody = smsBody;
        this.iconRes = iconRes;
    }
}
