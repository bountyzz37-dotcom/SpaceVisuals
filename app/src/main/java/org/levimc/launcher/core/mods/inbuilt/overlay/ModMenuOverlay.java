package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.levimc.launcher.R;
import org.levimc.launcher.core.mods.inbuilt.ExternalModuleProvider;
import org.levimc.launcher.core.mods.inbuilt.InbuiltModuleProvider;
import org.levimc.launcher.core.mods.inbuilt.UnifiedMod;
import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Atlas-inspired in-game module menu. */
public class ModMenuOverlay {
    private static final String TAG = "ModMenuOverlay";
    private final Activity activity;
    private final WindowManager windowManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private View overlayView;
    private boolean isShowing;
    private RecyclerView modsRecycler;
    private ModMenuAdapter adapter;
    private EditText searchInput;
    private TextView emptyStateText;
    private ImageButton navClose;
    private TextView resetButton;
    private final List<UnifiedMod> allMods = new ArrayList<>();
    private String currentQuery = "";
    private ModMenuCallback callback;

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
            int ui = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            overlayView.setSystemUiVisibility(ui);
            setupViews();
            loadMods();
            applyFilters();

            int sw = activity.getResources().getDisplayMetrics().widthPixels;
            int sh = activity.getResources().getDisplayMetrics().heightPixels;
            int panelW = Math.max(620, Math.min((int) (sw * 0.60f), sw - 28));
            int panelH = Math.max(330, Math.min((int) (sh * 0.48f), sh - 28));
            View container = overlayView.findViewById(R.id.mod_menu_container);
            FrameLayoutParamsHelper.setCenteredSize(container, panelW, panelH);

            int flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_FULLSCREEN;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_PANEL, flags, PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.CENTER;
            params.token = activity.getWindow().getDecorView().getWindowToken();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) params.setBlurBehindRadius(28);
            windowManager.addView(overlayView, params);
            isShowing = true;
            overlayView.setAlpha(0f);
            overlayView.animate().alpha(1f).setDuration(180).start();
            container.setAlpha(0f);
            container.setScaleX(0.97f);
            container.setScaleY(0.97f);
            container.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start();
        } catch (Exception e) {
            Log.e(TAG, "Unable to show menu", e);
            showFallback();
        }
    }

    public void hide() {
        if (!isShowing || overlayView == null) return;
        try {
            if (windowManager != null) {
                try {
                    windowManager.removeViewImmediate(overlayView);
                } catch (Exception ignored) {
                    if (overlayView.getParent() instanceof ViewGroup) ((ViewGroup) overlayView.getParent()).removeView(overlayView);
                }
            } else if (overlayView.getParent() instanceof ViewGroup) {
                ((ViewGroup) overlayView.getParent()).removeView(overlayView);
            }
        } catch (Exception e) { Log.e(TAG, "Unable to hide menu", e); }
        isShowing = false;
        overlayView = null;
    }

    private void showFallback() {
        try {
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) return;
            overlayView = LayoutInflater.from(activity).inflate(R.layout.overlay_mod_menu, null);
            setupViews(); loadMods(); applyFilters();
            root.addView(overlayView, new ViewGroup.LayoutParams(-1, -1));
            isShowing = true;
        } catch (Exception e) { Log.e(TAG, "Fallback failed", e); }
    }

    private void setupViews() {
        modsRecycler = overlayView.findViewById(R.id.mods_recycler);
        searchInput = overlayView.findViewById(R.id.search_input);
        emptyStateText = overlayView.findViewById(R.id.empty_state_text);
        navClose = overlayView.findViewById(R.id.nav_close);
        resetButton = overlayView.findViewById(R.id.reset_button);

        adapter = new ModMenuAdapter();
        adapter.setHasStableIds(true);
        adapter.setOnModActionListener(new ModMenuAdapter.OnModActionListener() {
            @Override public void onToggle(UnifiedMod mod, boolean enabled) {
                mod.applyEnabled(enabled);
                if (callback != null) callback.onModToggled(mod.getId(), enabled);
            }
            @Override public void onConfig(UnifiedMod mod) {
                openModConfig(mod);
            }
        });
        modsRecycler.setLayoutManager(new GridLayoutManager(activity, 2));
        modsRecycler.setAdapter(adapter);
        modsRecycler.setHasFixedSize(true);

        if (navClose != null) navClose.setOnClickListener(v -> hide());
        if (searchInput != null) searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) {
                currentQuery = s == null ? "" : s.toString().trim().toLowerCase(Locale.US);
                applyFilters();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        if (resetButton != null) resetButton.setOnClickListener(v -> resetClientSettings());
        setupClientSettings();

        overlayView.setOnClickListener(v -> hide());
        View container = overlayView.findViewById(R.id.mod_menu_container);
        if (container != null) container.setOnClickListener(v -> {});
    }

    private void setupClientSettings() {
        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        SeekBar scale = overlayView.findViewById(R.id.ui_scale_seek);
        TextView scaleValue = overlayView.findViewById(R.id.ui_scale_value);
        SeekBar opacity = overlayView.findViewById(R.id.button_opacity_seek);
        TextView opacityValue = overlayView.findViewById(R.id.button_opacity_value);
        int storedOpacity = manager.getModMenuButtonOpacity();
        if (scale != null) {
            scale.setProgress(50);
            if (scaleValue != null) scaleValue.setText("1.0");
            applyUiScale(1.0f);
            scale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar b, int p, boolean fromUser) {
                    float value = 0.5f + (p / 100f);
                    if (scaleValue != null) scaleValue.setText(String.format(Locale.US, "%.1f", value));
                    applyUiScale(value);
                }
                @Override public void onStartTrackingTouch(SeekBar b) {}
                @Override public void onStopTrackingTouch(SeekBar b) {}
            });
        }
        if (opacity != null) {
            opacity.setProgress(storedOpacity);
            updateOpacityLabel(opacityValue, storedOpacity);
            opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar b, int p, boolean fromUser) {
                    manager.setModMenuButtonOpacity(p);
                    updateOpacityLabel(opacityValue, p);
                    if (callback != null) callback.onButtonOpacityChanged(p);
                }
                @Override public void onStartTrackingTouch(SeekBar b) {}
                @Override public void onStopTrackingTouch(SeekBar b) {}
            });
        }
        selectSegment(R.id.menu_layout_auto, true);
        selectSegment(R.id.show_button_menus, manager.isModMenuEnabled() && manager.isPauseMenuOnly());
        selectSegment(R.id.show_button_always, manager.isModMenuEnabled() && !manager.isPauseMenuOnly());
        selectSegment(R.id.show_button_never, !manager.isModMenuEnabled());
        bindSegment(R.id.menu_layout_auto, R.id.menu_layout_auto, true);
        bindSegment(R.id.menu_layout_compact, R.id.menu_layout_auto, false);
        bindSegment(R.id.menu_layout_expanded, R.id.menu_layout_auto, false);
        bindShowSegment(R.id.show_button_never, false, false);
        bindShowSegment(R.id.show_button_menus, true, true);
        bindShowSegment(R.id.show_button_always, true, false);
        View key = overlayView.findViewById(R.id.hud_editor_key_button);
        if (key != null) key.setOnClickListener(v -> Toast.makeText(activity, "HUD Editor Key: M", Toast.LENGTH_SHORT).show());
    }

    private void bindSegment(int id, int selectedId, boolean selected) {
        View v = overlayView.findViewById(id);
        if (v != null) v.setOnClickListener(x -> {
            for (int other : new int[]{R.id.menu_layout_auto, R.id.menu_layout_compact, R.id.menu_layout_expanded}) selectSegment(other, other == id);
        });
    }

    private void bindShowSegment(int id, boolean enabled, boolean pauseOnly) {
        View v = overlayView.findViewById(id);
        if (v != null) v.setOnClickListener(x -> {
            InbuiltModManager m = InbuiltModManager.getInstance(activity);
            m.setModMenuEnabled(enabled); m.setPauseMenuOnly(pauseOnly);
            selectSegment(R.id.show_button_never, id == R.id.show_button_never);
            selectSegment(R.id.show_button_menus, id == R.id.show_button_menus);
            selectSegment(R.id.show_button_always, id == R.id.show_button_always);
        });
    }

    private void selectSegment(int id, boolean selected) {
        View v = overlayView.findViewById(id);
        if (v != null) {
            v.setBackgroundResource(selected ? R.drawable.bg_atlas_segment_selected : R.drawable.bg_atlas_segment_unselected);
            if (v instanceof TextView) ((TextView) v).setTextColor(selected ? Color.WHITE : Color.DKGRAY);
        }
    }

    private void updateOpacityLabel(TextView view, int value) { if (view != null) view.setText(String.format(Locale.US, "%.1f", value / 100f)); }

    private void resetClientSettings() {
        InbuiltModManager m = InbuiltModManager.getInstance(activity);
        m.setModMenuButtonOpacity(70);
        m.setModMenuEnabled(true);
        m.setPauseMenuOnly(true);
        SeekBar scale = overlayView.findViewById(R.id.ui_scale_seek); if (scale != null) { scale.setProgress(50); applyUiScale(1.0f); TextView sv = overlayView.findViewById(R.id.ui_scale_value); if (sv != null) sv.setText("1.0"); }
        SeekBar opacity = overlayView.findViewById(R.id.button_opacity_seek); if (opacity != null) opacity.setProgress(70);
        selectSegment(R.id.menu_layout_auto, true); selectSegment(R.id.menu_layout_compact, false); selectSegment(R.id.menu_layout_expanded, false);
        selectSegment(R.id.show_button_never, false); selectSegment(R.id.show_button_menus, true); selectSegment(R.id.show_button_always, false);
        if (callback != null) callback.onButtonOpacityChanged(70);
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

    public void refreshMods() { if (!isShowing) return; loadMods(); applyFilters(); }

    private void applyFilters() {
        List<UnifiedMod> filtered = new ArrayList<>();
        for (UnifiedMod mod : allMods) {
            if (!currentQuery.isEmpty()) {
                String n = mod.getName() == null ? "" : mod.getName().toLowerCase(Locale.US);
                String d = mod.getDescription() == null ? "" : mod.getDescription().toLowerCase(Locale.US);
                if (!n.contains(currentQuery) && !d.contains(currentQuery)) continue;
            }
            filtered.add(mod);
        }
        adapter.updateMods(filtered);
        if (emptyStateText != null) emptyStateText.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }


    private void applyUiScale(float scale) {
        if (overlayView == null) return;
        View container = overlayView.findViewById(R.id.mod_menu_container);
        if (container == null) return;
        container.setScaleX(scale);
        container.setScaleY(scale);
        container.setPivotX(container.getWidth() / 2f);
        container.setPivotY(container.getHeight() / 2f);
        if (container.getWidth() == 0) {
            container.post(() -> {
                container.setPivotX(container.getWidth() / 2f);
                container.setPivotY(container.getHeight() / 2f);
            });
        }
    }

    private void openModConfig(UnifiedMod mod) {
        if (mod == null || !mod.hasConfig()) {
            Toast.makeText(activity, "No settings for this module", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            ScrollView scroll = new ScrollView(activity);
            scroll.setFillViewport(true);
            LinearLayout container = new LinearLayout(activity);
            container.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
            container.setPadding(pad, pad, pad, pad);
            scroll.addView(container, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            ModConfigView.render(activity, container, mod, () -> {});
            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle(mod.getName() + " settings")
                    .setView(scroll)
                    .setPositiveButton("Done", (d, w) -> d.dismiss())
                    .create();
            dialog.show();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(
                        (int) (Math.min(activity.getResources().getDisplayMetrics().widthPixels * 0.9f,
                                420 * activity.getResources().getDisplayMetrics().density)),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        } catch (Exception e) {
            Log.e(TAG, "openModConfig failed", e);
            Toast.makeText(activity, "Failed to open settings: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** Small helper kept here so the XML can remain simple and density-independent. */
    private static final class FrameLayoutParamsHelper {
        static void setCenteredSize(View view, int width, int height) {
            if (view == null) return;
            android.widget.FrameLayout.LayoutParams p = new android.widget.FrameLayout.LayoutParams(width, height, Gravity.CENTER);
            view.setLayoutParams(p);
        }
    }
}
