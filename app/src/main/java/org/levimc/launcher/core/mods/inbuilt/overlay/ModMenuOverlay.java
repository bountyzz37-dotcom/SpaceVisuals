package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
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
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
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

    private static final String TAG = "ModMenuOverlay";

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

    private View mainContentContainer, settingsPanel;
    private android.widget.SeekBar uiScaleSeekBar;
    private TextView uiScaleValueText;
    private boolean settingsPanelOpen = false;

    private static final String PREFS_NAME = "levi_mod_menu_prefs";
    private static final String PREF_UI_SCALE = "ui_scale_progress";

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
        } catch (Exception e) {
            Log.e(TAG, "hide failed", e);
        }
        isShowing = false;
        overlayView = null;
    }

    public void toggle() {
        if (isShowing) hide();
        else show();
    }

    private void showInternal() {
        if (isShowing || activity.isFinishing() || activity.isDestroyed()) return;

        try {
            overlayView = LayoutInflater.from(activity).inflate(R.layout.overlay_mod_menu, null);

            int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            overlayView.setSystemUiVisibility(uiOptions);

            overlayView.setOnSystemUiVisibilityChangeListener(visibility -> {
                if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    if (overlayView != null) {
                        overlayView.setSystemUiVisibility(uiOptions);
                    }
                }
            });

            setupViews();
            loadMods();
            applyFilters();

            int wmFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_FULLSCREEN;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                wmFlags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
            }
            wmParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                    wmFlags,
                    PixelFormat.TRANSLUCENT
            );
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                wmParams.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            wmParams.gravity = Gravity.CENTER;
            wmParams.token = activity.getWindow().getDecorView().getWindowToken();
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                wmParams.setBlurBehindRadius(110);
            }

            windowManager.addView(overlayView, wmParams);
            isShowing = true;

            overlayView.setAlpha(0f);
            overlayView.animate().alpha(1f).setDuration(220).start();

            View menuContainer = overlayView.findViewById(R.id.mod_menu_container);
            if (menuContainer != null) {
                menuContainer.setAlpha(0f);
                menuContainer.setScaleX(0.90f);
                menuContainer.setScaleY(0.90f);
                menuContainer.setTranslationY(24f);
                menuContainer.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .translationY(0f)
                        .setDuration(260)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.1f))
                        .start();
            }
        } catch (Exception e) {
            Log.e(TAG, "showInternal failed", e);
            showFallback();
        }
    }

    private void showFallback() {
        if (isShowing) return;
        try {
            ViewGroup rootView = activity.findViewById(android.R.id.content);
            if (rootView == null) {
                Log.e(TAG, "showFallback: rootView is null");
                return;
            }

            overlayView = LayoutInflater.from(activity).inflate(R.layout.overlay_mod_menu, null);
            setupViews();
            loadMods();
            applyFilters();

            rootView.addView(overlayView, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            isShowing = true;
            wmParams = null;

            overlayView.setAlpha(0f);
            overlayView.animate().alpha(1f).setDuration(220).start();
        } catch (Exception e) {
            Log.e(TAG, "showFallback failed", e);
        }
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

        mainContentContainer = overlayView.findViewById(R.id.main_content_container);
        settingsPanel = overlayView.findViewById(R.id.settings_panel);
        uiScaleSeekBar = overlayView.findViewById(R.id.ui_scale_seekbar);
        uiScaleValueText = overlayView.findViewById(R.id.ui_scale_value);
        setupSettingsPanel();

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
                // Config handled by existing system
            }
        });

        // Single column list for the new row layout
        modsRecycler.setLayoutManager(new LinearLayoutManager(activity));
        modsRecycler.setAdapter(adapter);
        modsRecycler.setHasFixedSize(true);

        if (tabQuickAccess != null) tabQuickAccess.setOnClickListener(v -> selectTab(Tab.QUICK_ACCESS));
        if (tabVisual != null) tabVisual.setOnClickListener(v -> selectTab(Tab.VISUAL));
        if (tabHud != null) tabHud.setOnClickListener(v -> selectTab(Tab.HUD));
        if (tabInput != null) tabInput.setOnClickListener(v -> selectTab(Tab.INPUT));
        if (tabMisc != null) tabMisc.setOnClickListener(v -> selectTab(Tab.MISC));

        if (navClose != null) navClose.setOnClickListener(v -> hide());
        if (navHome != null) navHome.setOnClickListener(v -> {
            showSettingsPanel(false);
            selectTab(Tab.QUICK_ACCESS);
        });
        if (navSettings != null) navSettings.setOnClickListener(v -> showSettingsPanel(!settingsPanelOpen));

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
            container.setOnClickListener(v -> {}); // consume clicks inside panel
        }

        updateTabStyles();
    }

    private void showSettingsPanel(boolean show) {
        settingsPanelOpen = show;
        if (mainContentContainer != null) {
            mainContentContainer.setVisibility(show ? View.GONE : View.VISIBLE);
        }
        if (settingsPanel != null) {
            settingsPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (navSettings != null) {
            navSettings.setBackgroundResource(show
                    ? R.drawable.bg_nav_icon_selected
                    : android.R.color.transparent);
        }
    }

    private void setupSettingsPanel() {
        if (uiScaleSeekBar == null || overlayView == null) return;

        android.content.SharedPreferences prefs =
                activity.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        int savedProgress = prefs.getInt(PREF_UI_SCALE, 50); // 50 -> 1.0x
        uiScaleSeekBar.setProgress(savedProgress);
        applyUiScale(savedProgress);

        uiScaleSeekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                applyUiScale(progress);
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
                prefs.edit().putInt(PREF_UI_SCALE, seekBar.getProgress()).apply();
            }
        });
    }

    private void applyUiScale(int progress) {
        float scale = 0.5f + (progress / 100f); // 0 -> 0.5x, 50 -> 1.0x, 100 -> 1.5x
        View menuContainer = overlayView != null ? overlayView.findViewById(R.id.mod_menu_container) : null;
        if (menuContainer != null) {
            menuContainer.setScaleX(scale);
            menuContainer.setScaleY(scale);
        }
        if (uiScaleValueText != null) {
            uiScaleValueText.setText(String.format(Locale.US, "%.1fx", scale));
        }
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
            Log.e(TAG, "loadMods failed", e);
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
