package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.levimc.launcher.R;
import org.levimc.launcher.core.mods.inbuilt.ExternalModuleProvider;
import org.levimc.launcher.core.mods.inbuilt.InbuiltModuleProvider;
import org.levimc.launcher.core.mods.inbuilt.UnifiedMod;
import org.levimc.launcher.core.mods.inbuilt.model.ModIds;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Redesigned Mod Menu Overlay - Liquid glass style with top tabs.
 */
public class ModMenuOverlay {

    public enum Tab {
        QUICK_ACCESS,
        VISUAL,
        HUD,
        INPUT,
        MISC
    }

    public interface ModMenuCallback {
        void onModToggled(String modId, boolean enabled);
        void onButtonOpacityChanged(int opacity);
    }

    private final Activity activity;
    private View overlayView;
    private WindowManager windowManager;
    private WindowManager.LayoutParams wmParams;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isShowing = false;

    private RecyclerView modsRecycler;
    private ModMenuAdapter adapter;
    private EditText searchInput;
    private TextView emptyStateText;

    private TextView tabQuickAccess, tabVisual, tabHud, tabInput, tabMisc;
    private ImageButton navHome, navSettings, navProfile, navClose;

    private List<UnifiedMod> allMods = new ArrayList<>();
    private Tab activeTab = Tab.QUICK_ACCESS;
    private String currentQuery = "";

    private ModMenuCallback callback;
    private ModNotificationManager notificationManager;

    public ModMenuOverlay(Activity activity) {
        this.activity = activity;
        this.windowManager = (WindowManager) activity.getSystemService(Activity.WINDOW_SERVICE);
        this.notificationManager = new ModNotificationManager(activity);
    }

    public void setCallback(ModMenuCallback callback) {
        this.callback = callback;
    }

    public boolean isShowing() {
        return isShowing;
    }

    public void show() {
        if (isShowing) {
            refreshMods();
            return;
        }
        showInternal();
    }

    public void hide() {
        if (!isShowing || overlayView == null) return;
        try {
            if (windowManager != null && overlayView.getParent() != null) {
                windowManager.removeView(overlayView);
            } else {
                ViewGroup parent = (ViewGroup) overlayView.getParent();
                if (parent != null) parent.removeView(overlayView);
            }
        } catch (Exception ignored) {}
        isShowing = false;
        overlayView = null;
    }

    public void toggle() {
        if (isShowing) hide();
        else show();
    }

    private void showInternal() {
        try {
            LayoutInflater inflater = LayoutInflater.from(activity);
            overlayView = inflater.inflate(R.layout.overlay_mod_menu, null);

            wmParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT
            );
            wmParams.gravity = Gravity.CENTER;
            wmParams.token = activity.getWindow().getDecorView().getWindowToken();

            setupViews();
            loadMods();
            applyFilters();

            windowManager.addView(overlayView, wmParams);
            isShowing = true;

            wmParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
            windowManager.updateViewLayout(overlayView, wmParams);

            animateEnter();
        } catch (Exception e) {
            showFallback();
        }
    }

    private void showFallback() {
        try {
            LayoutInflater inflater = LayoutInflater.from(activity);
            overlayView = inflater.inflate(R.layout.overlay_mod_menu, null);
            ViewGroup root = activity.findViewById(android.R.id.content);
            root.addView(overlayView, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            setupViews();
            loadMods();
            applyFilters();
            isShowing = true;
            animateEnter();
        } catch (Exception ignored) {}
    }

    private void animateEnter() {
        View container = overlayView.findViewById(R.id.mod_menu_container);
        if (container == null) return;
        container.setAlpha(0f);
        container.setScaleX(0.92f);
        container.setScaleY(0.92f);
        container.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                .start();
    }

    private void setupViews() {
        modsRecycler = overlayView.findViewById(R.id.mods_recycler);
        searchInput = overlayView.findViewById(R.id.search_input);
        emptyStateText = overlayView.findViewById(R.id.empty_state_text);

        tabQuickAccess = overlayView.findViewById(R.id.tab_quick_access);
        tabVisual = overlayView.findViewById(R.id.tab_visual);
        tabHud = overlayView.findViewById(R.id.tab_hud);
        tabInput = overlayView.findViewById(R.id.tab_input);
        tabMisc = overlayView.findViewById(R.id.tab_misc);

        navHome = overlayView.findViewById(R.id.nav_home);
        navSettings = overlayView.findViewById(R.id.nav_settings);
        navProfile = overlayView.findViewById(R.id.nav_profile);
        navClose = overlayView.findViewById(R.id.nav_close);

        adapter = new ModMenuAdapter();
        adapter.setHasStableIds(true);
        adapter.setOnModActionListener(new ModMenuAdapter.OnModActionListener() {
            @Override
            public void onToggle(UnifiedMod mod, boolean enabled) {
                mod.applyEnabled(enabled);
                if (callback != null) {
                    callback.onModToggled(mod.getId(), enabled);
                }
            }

            @Override
            public void onConfig(UnifiedMod mod) {
                // Config is handled by existing system
            }
        });

        GridLayoutManager glm = new GridLayoutManager(activity, 2);
        modsRecycler.setLayoutManager(glm);
        modsRecycler.setAdapter(adapter);
        modsRecycler.setHasFixedSize(true);

        tabQuickAccess.setOnClickListener(v -> selectTab(Tab.QUICK_ACCESS));
        tabVisual.setOnClickListener(v -> selectTab(Tab.VISUAL));
        tabHud.setOnClickListener(v -> selectTab(Tab.HUD));
        tabInput.setOnClickListener(v -> selectTab(Tab.INPUT));
        tabMisc.setOnClickListener(v -> selectTab(Tab.MISC));

        if (navClose != null) navClose.setOnClickListener(v -> hide());
        if (navHome != null) navHome.setOnClickListener(v -> selectTab(Tab.QUICK_ACCESS));

        if (searchInput != null) {
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    currentQuery = s != null ? s.toString().trim().toLowerCase(Locale.US) : "";
                    applyFilters();
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        overlayView.setOnClickListener(v -> hide());
        View container = overlayView.findViewById(R.id.mod_menu_container);
        if (container != null) {
            container.setOnClickListener(v -> {});
        }

        updateTabStyles();
    }

    private void selectTab(Tab tab) {
        activeTab = tab;
        updateTabStyles();
        applyFilters();
    }

    private void updateTabStyles() {
        styleTab(tabQuickAccess, activeTab == Tab.QUICK_ACCESS);
        styleTab(tabVisual, activeTab == Tab.VISUAL);
        styleTab(tabHud, activeTab == Tab.HUD);
        styleTab(tabInput, activeTab == Tab.INPUT);
        styleTab(tabMisc, activeTab == Tab.MISC);
    }

    private void styleTab(TextView tab, boolean selected) {
        if (tab == null) return;
        if (selected) {
            tab.setBackgroundResource(R.drawable.bg_tab_selected);
            tab.setTextColor(0xFFFFFFFF);
        } else {
            tab.setBackgroundResource(R.drawable.bg_tab_unselected);
            tab.setTextColor(0xFFA8B0B8);
        }
    }

    private void loadMods() {
        allMods.clear();
        try {
            List<UnifiedMod> inbuilt = InbuiltModuleProvider.load(activity);
            if (inbuilt != null) allMods.addAll(inbuilt);

            List<UnifiedMod> external = ExternalModuleProvider.load(activity);
            if (external != null) allMods.addAll(external);
        } catch (Exception e) {
            // keep empty
        }
    }

    public void refreshMods() {
        if (!isShowing) return;
        loadMods();
        applyFilters();
    }

    private void applyFilters() {
        List<UnifiedMod> filtered = new ArrayList<>();

        for (UnifiedMod mod : allMods) {
            if (!matchesTab(mod, activeTab)) continue;
            if (!currentQuery.isEmpty()) {
                String name = mod.getName() != null ? mod.getName().toLowerCase(Locale.US) : "";
                String desc = mod.getDescription() != null ? mod.getDescription().toLowerCase(Locale.US) : "";
                if (!name.contains(currentQuery) && !desc.contains(currentQuery)) continue;
            }
            filtered.add(mod);
        }

        adapter.updateMods(filtered);

        if (emptyStateText != null) {
            emptyStateText.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private boolean matchesTab(UnifiedMod mod, Tab tab) {
        String id = mod.getId() != null ? mod.getId() : "";
        String name = mod.getName() != null ? mod.getName().toLowerCase(Locale.US) : "";

        switch (tab) {
            case QUICK_ACCESS:
                return id.equals(ModIds.QUICK_DROP)
                        || id.equals(ModIds.SNAPLOOK)
                        || id.equals(ModIds.AUTO_SPRINT)
                        || id.equals(ModIds.VIRTUAL_CURSOR)
                        || id.equals(ModIds.CAMERA_PERSPECTIVE)
                        || name.contains("quick")
                        || name.contains("snap")
                        || name.contains("sprint");

            case VISUAL:
                return id.equals(ModIds.ZOOM)
                        || id.equals(ModIds.FPS_DISPLAY)
                        || id.equals(ModIds.CPS_DISPLAY)
                        || id.equals(ModIds.CHICK_PET)
                        || name.contains("zoom")
                        || name.contains("fps")
                        || name.contains("cps")
                        || name.contains("pet");

            case HUD:
                return id.equals(ModIds.TOGGLE_HUD)
                        || id.equals(ModIds.HOTBAR_SLOT)
                        || id.equals(ModIds.MORE_BUTTONS)
                        || name.contains("hud")
                        || name.contains("hotbar")
                        || name.contains("button");

            case INPUT:
                return id.equals(ModIds.GYRO)
                        || id.equals(ModIds.POJAV_CONTROLS)
                        || name.contains("gyro")
                        || name.contains("control")
                        || name.contains("input");

            case MISC:
            default:
                return !matchesTab(mod, Tab.QUICK_ACCESS)
                        && !matchesTab(mod, Tab.VISUAL)
                        && !matchesTab(mod, Tab.HUD)
                        && !matchesTab(mod, Tab.INPUT);
        }
    }
}
