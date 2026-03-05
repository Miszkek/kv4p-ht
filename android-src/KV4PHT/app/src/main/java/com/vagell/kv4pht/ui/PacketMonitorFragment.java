package com.vagell.kv4pht.ui;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.vagell.kv4pht.R;

import java.util.List;

/**
 * Simple "terminal-like" monitor for all received APRS packets.
 *
 * Note: This fragment only renders what PacketMonitorStore receives.
 * You still need to hook store.addLine(...) from the radio receive path.
 */
public final class PacketMonitorFragment extends Fragment {

    private PacketMonitorAdapter adapter;
    private LinearLayoutManager layoutManager;
    private boolean paused = false;

    public PacketMonitorFragment() {
        super(R.layout.fragment_packet_monitor);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView rv = view.findViewById(R.id.packet_monitor_recycler);
        layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true); // newest at bottom like terminal
        rv.setLayoutManager(layoutManager);

        adapter = new PacketMonitorAdapter();
        rv.setAdapter(adapter);

        MaterialButton btnPause = view.findViewById(R.id.btn_pause);

        MaterialButtonToggleGroup toggle = view.findViewById(R.id.toggle_freq);
        // default checked = show frequency
        toggle.check(R.id.toggle_show_freq);

        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            boolean showFreq = (checkedId == R.id.toggle_show_freq);
            adapter.setShowFrequency(showFreq);
        });

        btnPause.setOnClickListener(v -> {
            paused = !paused;
            btnPause.setText(paused ? R.string.monitor_resume : R.string.monitor_pause);
        });

        // btn_clear was removed from layout

        PacketMonitorStore.get().getLines().observe(getViewLifecycleOwner(), new Observer<List<PacketMonitorStore.PacketLine>>() {
            @Override
            public void onChanged(List<PacketMonitorStore.PacketLine> packetLines) {
                adapter.submit(packetLines);
                if (!paused && packetLines != null && !packetLines.isEmpty()) {
                    rv.scrollToPosition(packetLines.size() - 1);
                }
            }
        });
    }
}
