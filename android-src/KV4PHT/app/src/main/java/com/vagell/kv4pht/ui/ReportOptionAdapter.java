package com.vagell.kv4pht.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Prosty adapter do listy wyboru "Zgłoś" (ikona + tekst).
 */
public class ReportOptionAdapter extends ArrayAdapter<ReportOption> {

    public ReportOptionAdapter(@NonNull Context context, @NonNull List<ReportOption> options) {
        super(context, android.R.layout.select_dialog_item, options);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View v = super.getView(position, convertView, parent);
        TextView tv = (TextView) v.findViewById(android.R.id.text1);
        ReportOption opt = getItem(position);
        if (opt != null) {
            tv.setText(opt.title);
            tv.setCompoundDrawablesWithIntrinsicBounds(opt.iconRes, 0, 0, 0);
            tv.setCompoundDrawablePadding(dp(12));
            tv.setPadding(dp(16), tv.getPaddingTop(), dp(16), tv.getPaddingBottom());
        }
        return v;
    }

    private int dp(int dp) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
