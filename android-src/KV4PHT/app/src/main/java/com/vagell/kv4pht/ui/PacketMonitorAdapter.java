package com.vagell.kv4pht.ui.monitor;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.vagell.kv4pht.R;

import java.util.ArrayList;
import java.util.List;

public final class PacketMonitorAdapter extends RecyclerView.Adapter<PacketMonitorAdapter.VH> {

    private final ArrayList<PacketMonitorStore.PacketLine> items = new ArrayList<>();
    private boolean showFrequency = true;

    public void setShowFrequency(boolean show) {
        showFrequency = show;
        notifyDataSetChanged();
    }

    public void submit(List<PacketMonitorStore.PacketLine> next) {
        items.clear();
        if (next != null) items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_packet_monitor_line, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        PacketMonitorStore.PacketLine line = items.get(position);
        holder.line.setText(line.toDisplayString(showFrequency));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView line;
        VH(@NonNull View itemView) {
            super(itemView);
            line = itemView.findViewById(R.id.packet_line_text);
        }
    }
}
