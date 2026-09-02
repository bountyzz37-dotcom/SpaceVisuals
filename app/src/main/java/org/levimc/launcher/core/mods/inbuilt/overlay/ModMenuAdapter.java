package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.levimc.launcher.R;
import org.levimc.launcher.core.mods.inbuilt.UnifiedMod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ModMenuAdapter extends RecyclerView.Adapter<ModMenuAdapter.ModRowHolder> {

    private final List<UnifiedMod> items = new ArrayList<>();
    private final Map<String, Boolean> toggleStates = new HashMap<>();
    private OnModActionListener listener;

    public interface OnModActionListener {
        void onToggle(UnifiedMod mod, boolean enabled);
        void onConfig(UnifiedMod mod);
    }

    public void setOnModActionListener(OnModActionListener listener) {
        this.listener = listener;
    }

    public void updateMods(List<UnifiedMod> mods) {
        List<UnifiedMod> oldItems = new ArrayList<>(items);
        Map<String, Boolean> oldStates = new HashMap<>(toggleStates);

        items.clear();
        toggleStates.clear();
        if (mods != null) {
            items.addAll(mods);
            for (UnifiedMod mod : mods) {
                toggleStates.put(mod.getStableKey(), mod.isEnabled());
            }
        }

        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldItems.size();
            }

            @Override
            public int getNewListSize() {
                return items.size();
            }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return oldItems.get(oldPos).getStableKey()
                        .equals(items.get(newPos).getStableKey());
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                UnifiedMod oldMod = oldItems.get(oldPos);
                UnifiedMod newMod = items.get(newPos);
                boolean oldEnabled = Boolean.TRUE.equals(oldStates.get(oldMod.getStableKey()));
                boolean newEnabled = Boolean.TRUE.equals(toggleStates.get(newMod.getStableKey()));
                return Objects.equals(oldMod.getName(), newMod.getName())
                        && oldEnabled == newEnabled
                        && oldMod.hasConfig() == newMod.hasConfig();
            }
        });
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ModRowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mod_menu_row, parent, false);
        return new ModRowHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ModRowHolder holder, int position) {
        UnifiedMod mod = items.get(position);
        holder.bind(mod);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).getStableKey().hashCode();
    }

    class ModRowHolder extends RecyclerView.ViewHolder {
        final View root;
        final ImageView icon;
        final TextView name;
        final ImageButton configBtn;
        final SwitchCompat toggle;

        ModRowHolder(@NonNull View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.mod_row_root);
            icon = itemView.findViewById(R.id.mod_row_icon);
            name = itemView.findViewById(R.id.mod_row_name);
            configBtn = itemView.findViewById(R.id.mod_row_config);
            toggle = itemView.findViewById(R.id.mod_row_switch);
        }

        void bind(UnifiedMod mod) {
            name.setText(mod.getName());

            boolean enabled = Boolean.TRUE.equals(toggleStates.get(mod.getStableKey()));
            root.setSelected(enabled);

            toggle.setOnCheckedChangeListener(null);
            toggle.setChecked(enabled);
            toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                toggleStates.put(mod.getStableKey(), isChecked);
                root.setSelected(isChecked);
                if (listener != null) {
                    listener.onToggle(mod, isChecked);
                }
            });

            if (mod.hasConfig()) {
                configBtn.setVisibility(View.VISIBLE);
                configBtn.setOnClickListener(v -> {
                    if (listener != null) listener.onConfig(mod);
                });
            } else {
                configBtn.setVisibility(View.INVISIBLE);
                configBtn.setOnClickListener(null);
            }

            // Optional: set icon if available
            // icon.setImageResource(...);
        }
    }
}
