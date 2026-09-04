package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.levimc.launcher.R;
import org.levimc.launcher.core.mods.inbuilt.ExternalModuleProvider;
import org.levimc.launcher.core.mods.inbuilt.InbuiltModuleProvider;
import org.levimc.launcher.core.mods.inbuilt.UnifiedMod;
import org.levimc.launcher.core.mods.inbuilt.model.ModIds;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ModMenuOverlay {
    private static final String TAG = "ModMenuOverlay";
    private static final String[] CATEGORY_ORDER = {"Movement", "Visuals", "Player", "Other", "External"};

    private final Activity activity;
    private final WindowManager windowManager;
    private View overlayView;
    private boolean isShowing;
    private LinearLayout panelsContainer;
    private EditText searchInput;
    private TextView emptyStateText;
    private ImageButton navClose;
    private final List<UnifiedMod> allMods = new ArrayList<>();
    private String currentQuery = "";
    private ModMenuCallback callback;
    private UnifiedMod expandedMod;

    public interface ModMenuCallback {
        void onModToggled(String modId, boolean enabled);
        void onButtonOpacityChanged(int opacity);
    }

    public ModMenuOverlay(Activity activity) {
        this.activity = activity;
        this.windowManager = (WindowManager) activity.getSystemService(Activity.WINDOW_SERVICE);
    }

    public void setCallback(ModMenuCallback callback) { this.callback = callback; }
    public boolean isShowing() { return isShowing; }
    public void toggle() { if (isShowing) hide(); else show(); }

    public void show() {
        if (isShowing) { refreshMods(); return; }
        if (activity.isFinishing() || activity.isDestroyed()) return;
        try {
            overlayView = LayoutInflater.from(activity).inflate(R.layout.overlay_mod_menu, null);
            setupViews(); loadMods(); rebuildPanels();
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
                    android.graphics.PixelFormat.TRANSLUCENT);
            windowManager.addView(overlayView, lp);
            isShowing = true;
            overlayView.setAlpha(0f);
            overlayView.animate().alpha(1f).setDuration(160).start();
        } catch (Exception e) {
            Log.e(TAG, "Unable to show menu", e);
            showFallback();
        }
    }

    public void hide() {
        if (!isShowing || overlayView == null) return;
        try {
            if (windowManager != null) {
                try { windowManager.removeViewImmediate(overlayView); }
                catch (Exception ignored) {
                    if (overlayView.getParent() instanceof ViewGroup)
                        ((ViewGroup) overlayView.getParent()).removeView(overlayView);
                }
            } else if (overlayView.getParent() instanceof ViewGroup) {
                ((ViewGroup) overlayView.getParent()).removeView(overlayView);
            }
        } catch (Exception e) { Log.e(TAG, "Unable to hide menu", e); }
        isShowing = false; overlayView = null; expandedMod = null;
    }

    private void showFallback() {
        try {
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) return;
            overlayView = LayoutInflater.from(activity).inflate(R.layout.overlay_mod_menu, null);
            setupViews(); loadMods(); rebuildPanels();
            root.addView(overlayView, new ViewGroup.LayoutParams(-1, -1));
            isShowing = true;
        } catch (Exception e) { Log.e(TAG, "Fallback failed", e); }
    }

    private void setupViews() {
        panelsContainer = overlayView.findViewById(R.id.panels_container);
        searchInput = overlayView.findViewById(R.id.search_input);
        emptyStateText = overlayView.findViewById(R.id.empty_state_text);
        navClose = overlayView.findViewById(R.id.nav_close);
        if (navClose != null) navClose.setOnClickListener(v -> hide());
        if (searchInput != null) searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                currentQuery = s == null ? "" : s.toString().trim().toLowerCase(Locale.US);
                rebuildPanels();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        overlayView.setOnClickListener(v -> hide());
        View container = overlayView.findViewById(R.id.mod_menu_container);
        if (container != null) container.setOnClickListener(v -> {});
    }

    private void loadMods() {
        allMods.clear();
        try {
            List<UnifiedMod> inbuilt = InbuiltModuleProvider.load(activity);
            if (inbuilt != null) allMods.addAll(inbuilt);
            List<UnifiedMod> external = ExternalModuleProvider.load(activity);
            if (external != null) allMods.addAll(external);
        } catch (Exception e) { Log.e(TAG, "loadMods failed", e); }
    }

    public void refreshMods() { if (!isShowing) return; loadMods(); rebuildPanels(); }

    private String categoryFor(UnifiedMod mod) {
        if (mod.getSource() == UnifiedMod.Source.EXTERNAL) return "External";
        String id = mod.getId() == null ? "" : mod.getId();
        switch (id) {
            case ModIds.AUTO_SPRINT: case ModIds.SNAPLOOK: case ModIds.GYRO:
                return "Movement";
            case ModIds.TOGGLE_HUD: case ModIds.ZOOM: case ModIds.FPS_DISPLAY:
            case ModIds.CPS_DISPLAY: case ModIds.CHICK_PET: case ModIds.HOTBAR_SLOT:
                return "Visuals";
            case ModIds.QUICK_DROP: case ModIds.CAMERA_PERSPECTIVE: case ModIds.VIRTUAL_CURSOR:
                return "Player";
            default: return "Other";
        }
    }

    private void rebuildPanels() {
        if (panelsContainer == null) return;
        panelsContainer.removeAllViews();
        Map<String, List<UnifiedMod>> grouped = new LinkedHashMap<>();
        for (String cat : CATEGORY_ORDER) grouped.put(cat, new ArrayList<>());
        for (UnifiedMod mod : allMods) {
            if (!currentQuery.isEmpty()) {
                String n = mod.getName() == null ? "" : mod.getName().toLowerCase(Locale.US);
                String d = mod.getDescription() == null ? "" : mod.getDescription().toLowerCase(Locale.US);
                if (!n.contains(currentQuery) && !d.contains(currentQuery)) continue;
            }
            String cat = categoryFor(mod);
            if (!grouped.containsKey(cat)) grouped.put(cat, new ArrayList<>());
            grouped.get(cat).add(mod);
        }
        float density = activity.getResources().getDisplayMetrics().density;
        int panelWidth = (int) (168 * density);
        int gap = (int) (8 * density);
        boolean any = false;
        for (Map.Entry<String, List<UnifiedMod>> entry : grouped.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            any = true;
            View panel = buildCategoryPanel(entry.getKey(), entry.getValue(), density);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.MATCH_PARENT);
            lp.setMargins(0, 0, gap, 0);
            panelsContainer.addView(panel, lp);
        }
        if (emptyStateText != null) emptyStateText.setVisibility(any ? View.GONE : View.VISIBLE);
    }

    private View buildCategoryPanel(String title, List<UnifiedMod> mods, float density) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.bg_clickgui_panel);
        panel.setPadding((int)(10*density), (int)(10*density), (int)(10*density), (int)(10*density));

        TextView header = new TextView(activity);
        header.setText(title);
        header.setTextColor(Color.parseColor("#E8F4FF"));
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(0, 0, 0, (int)(8*density));
        panel.addView(header);

        View divider = new View(activity);
        divider.setBackgroundColor(Color.parseColor("#33FFFFFF"));
        panel.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int)density)));

        ScrollView scroll = new ScrollView(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (UnifiedMod mod : mods) {
            list.addView(buildModRow(mod, density));
            if (expandedMod != null && expandedMod.getStableKey().equals(mod.getStableKey()) && mod.hasConfig()) {
                list.addView(buildInlineConfig(mod, density));
            }
        }
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return panel;
    }

    private View buildModRow(UnifiedMod mod, float density) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_clickgui_mod);
        row.setSelected(mod.isEnabled());
        row.setPadding((int)(6*density), (int)(8*density), (int)(6*density), (int)(8*density));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = (int)(2*density);
        row.setLayoutParams(rowLp);

        TextView name = new TextView(activity);
        name.setText(mod.getName());
        name.setTextColor(mod.isEnabled() ? Color.parseColor("#B8D4FF") : Color.parseColor("#C8D0DC"));
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (mod.isEnabled()) {
            TextView check = new TextView(activity);
            check.setText("✓");
            check.setTextColor(Color.parseColor("#6EC8FF"));
            check.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            row.addView(check);
        }
        if (mod.hasConfig()) {
            TextView gear = new TextView(activity);
            gear.setText(" ⚙");
            gear.setTextColor(Color.parseColor("#88A0B8"));
            gear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            gear.setOnClickListener(v -> {
                expandedMod = (expandedMod != null && expandedMod.getStableKey().equals(mod.getStableKey())) ? null : mod;
                rebuildPanels();
            });
            row.addView(gear);
        }
        row.setOnClickListener(v -> {
            boolean next = !mod.isEnabled();
            mod.applyEnabled(next);
            if (callback != null) callback.onModToggled(mod.getId(), next);
            rebuildPanels();
        });
        row.setOnLongClickListener(v -> {
            if (mod.hasConfig()) {
                expandedMod = (expandedMod != null && expandedMod.getStableKey().equals(mod.getStableKey())) ? null : mod;
                rebuildPanels();
            } else Toast.makeText(activity, "No settings", Toast.LENGTH_SHORT).show();
            return true;
        });
        return row;
    }

    private View buildInlineConfig(UnifiedMod mod, float density) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(8*density);
        box.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#AA0A1020"));
        bg.setCornerRadius(8*density);
        bg.setStroke(Math.max(1, (int)density), Color.parseColor("#33FFFFFF"));
        box.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int)(4*density);
        lp.bottomMargin = (int)(6*density);
        box.setLayoutParams(lp);
        try { ModConfigView.render(activity, box, mod, () -> {}); }
        catch (Exception e) {
            TextView err = new TextView(activity);
            err.setText("Settings error");
            err.setTextColor(Color.RED);
            box.addView(err);
        }
        return box;
    }
}
