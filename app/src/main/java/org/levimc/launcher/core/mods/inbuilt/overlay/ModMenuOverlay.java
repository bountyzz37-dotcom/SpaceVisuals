package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;
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

public class ModMenuOverlay {
    private static final String TAG = "ModMenuOverlay";

    private static final int[] THEME_COLORS = {
            InbuiltModManager.THEME_BLUE,
            InbuiltModManager.THEME_CYAN,
            InbuiltModManager.THEME_PURPLE,
            InbuiltModManager.THEME_GREEN,
            InbuiltModManager.THEME_PINK,
            InbuiltModManager.THEME_ORANGE
    };

    private final Activity activity;
    private final WindowManager windowManager;
    private View overlayView;
    private boolean isShowing;

    private RecyclerView modsRecycler;
    private ModMenuAdapter adapter;
    private EditText searchInput;
    private TextView emptyStateText;
    private ImageButton navClose, navHome, navSettings, navHudEdit;
    private View clientSettingsPage, moduleSettingsPanel, starfieldView, menuContainer, menuShell;
    private LinearLayout moduleSettingsContent, themeRow;
    private TextView moduleSettingsTitle, opacityValue, glassAlphaValue;
    private ImageButton moduleSettingsClose;
    private SeekBar opacitySeek, glassAlphaSeek;
    private SwitchCompat starfieldSwitch;
    private TextView showNever, showMenus, showAlways, tabQuickAccess;

    private final List<UnifiedMod> allMods = new ArrayList<>();
    private String currentQuery = "";
    private ModMenuCallback callback;
    private boolean showingClientSettings;
    private int themeColor = InbuiltModManager.THEME_BLUE;

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
            setupViews();
            loadMods();
            applyFilters();
            applyThemeFromPrefs();
            showHomePage();

            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
                    PixelFormat.TRANSLUCENT);
            windowManager.addView(overlayView, lp);
            isShowing = true;

            if (menuShell != null) {
                menuShell.setAlpha(0f);
                menuShell.setScaleX(0.94f);
                menuShell.setScaleY(0.94f);
                menuShell.animate().alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
            }
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
        } catch (Exception e) { Log.e(TAG, "hide", e); }
        isShowing = false;
        overlayView = null;
        showingClientSettings = false;
    }

    private void showFallback() {
        try {
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) return;
            overlayView = LayoutInflater.from(activity).inflate(R.layout.overlay_mod_menu, null);
            setupViews(); loadMods(); applyFilters(); applyThemeFromPrefs(); showHomePage();
            root.addView(overlayView, new ViewGroup.LayoutParams(-1, -1));
            isShowing = true;
        } catch (Exception e) { Log.e(TAG, "Fallback failed", e); }
    }

    private void setupViews() {
        modsRecycler = overlayView.findViewById(R.id.mods_recycler);
        searchInput = overlayView.findViewById(R.id.search_input);
        emptyStateText = overlayView.findViewById(R.id.empty_state_text);
        navClose = overlayView.findViewById(R.id.nav_close);
        navHome = overlayView.findViewById(R.id.nav_home);
        navSettings = overlayView.findViewById(R.id.nav_settings);
        navHudEdit = overlayView.findViewById(R.id.nav_hud_edit);
        clientSettingsPage = overlayView.findViewById(R.id.client_settings_page);
        moduleSettingsPanel = overlayView.findViewById(R.id.module_settings_panel);
        moduleSettingsContent = overlayView.findViewById(R.id.module_settings_content);
        moduleSettingsTitle = overlayView.findViewById(R.id.module_settings_title);
        moduleSettingsClose = overlayView.findViewById(R.id.module_settings_close);
        opacitySeek = overlayView.findViewById(R.id.button_opacity_seek);
        opacityValue = overlayView.findViewById(R.id.button_opacity_value);
        glassAlphaSeek = overlayView.findViewById(R.id.glass_alpha_seek);
        glassAlphaValue = overlayView.findViewById(R.id.glass_alpha_value);
        starfieldSwitch = overlayView.findViewById(R.id.starfield_switch);
        starfieldView = overlayView.findViewById(R.id.mod_menu_starfield);
        themeRow = overlayView.findViewById(R.id.theme_row);
        showNever = overlayView.findViewById(R.id.show_button_never);
        showMenus = overlayView.findViewById(R.id.show_button_menus);
        showAlways = overlayView.findViewById(R.id.show_button_always);
        tabQuickAccess = overlayView.findViewById(R.id.tab_quick_access);
        menuContainer = overlayView.findViewById(R.id.mod_menu_container);
        menuShell = overlayView.findViewById(R.id.mod_menu_shell);

        adapter = new ModMenuAdapter();
        adapter.setHasStableIds(true);
        adapter.setOnModActionListener(new ModMenuAdapter.OnModActionListener() {
            @Override public void onToggle(UnifiedMod mod, boolean enabled) {
                mod.applyEnabled(enabled);
                if (callback != null) callback.onModToggled(mod.getId(), enabled);
            }
            @Override public void onConfig(UnifiedMod mod) { openModuleSettings(mod); }
        });
        modsRecycler.setLayoutManager(new GridLayoutManager(activity, 2));
        modsRecycler.setAdapter(adapter);

        if (navClose != null) navClose.setOnClickListener(v -> hide());
        if (navHome != null) navHome.setOnClickListener(v -> showHomePage());
        if (navSettings != null) navSettings.setOnClickListener(v -> showClientSettingsPage());
        if (navHudEdit != null) navHudEdit.setOnClickListener(v -> openHudEditor());
        if (moduleSettingsClose != null) moduleSettingsClose.setOnClickListener(v -> closeModuleSettings());

        if (searchInput != null) searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                currentQuery = s == null ? "" : s.toString().trim().toLowerCase(Locale.US);
                applyFilters();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        setupClientSettingsControls();
        buildThemeSwatches();

        overlayView.setOnClickListener(v -> hide());
        if (menuShell != null) menuShell.setOnClickListener(v -> {});
        if (menuContainer != null) menuContainer.setOnClickListener(v -> {});
        if (moduleSettingsPanel != null) moduleSettingsPanel.setOnClickListener(v -> {});
    }

    private void setupClientSettingsControls() {
        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        if (opacitySeek != null) {
            int stored = manager.getModMenuButtonOpacity();
            opacitySeek.setProgress(stored);
            if (opacityValue != null) opacityValue.setText(String.format(Locale.US, "%.1f", stored / 100f));
            opacitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                    manager.setModMenuButtonOpacity(p);
                    if (opacityValue != null) opacityValue.setText(String.format(Locale.US, "%.1f", p / 100f));
                    if (callback != null) callback.onButtonOpacityChanged(p);
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
        }
        if (glassAlphaSeek != null) {
            int ga = manager.getUiGlassAlpha();
            glassAlphaSeek.setProgress(ga);
            if (glassAlphaValue != null) glassAlphaValue.setText(String.valueOf(ga));
            glassAlphaSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                    manager.setUiGlassAlpha(p);
                    if (glassAlphaValue != null) glassAlphaValue.setText(String.valueOf(p));
                    applyGlassAlpha(p);
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
        }
        if (starfieldSwitch != null) {
            starfieldSwitch.setChecked(manager.isStarfieldEnabled());
            applyStarfield(manager.isStarfieldEnabled());
            starfieldSwitch.setOnCheckedChangeListener((b, checked) -> {
                manager.setStarfieldEnabled(checked);
                applyStarfield(checked);
            });
        }
        bindShow(showNever, false, false);
        bindShow(showMenus, true, true);
        bindShow(showAlways, true, false);
        refreshShow();
    }

    private void buildThemeSwatches() {
        if (themeRow == null) return;
        themeRow.removeAllViews();
        float d = activity.getResources().getDisplayMetrics().density;
        int size = (int) (28 * d);
        int gap = (int) (8 * d);
        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        int current = manager.getUiThemeColor();
        for (int color : THEME_COLORS) {
            View swatch = new View(activity);
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(color);
            gd.setStroke((int) (2 * d), color == current ? Color.WHITE : Color.parseColor("#66FFFFFF"));
            swatch.setBackground(gd);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(0, 0, gap, 0);
            swatch.setLayoutParams(lp);
            final int c = color;
            swatch.setOnClickListener(v -> {
                manager.setUiThemeColor(c);
                themeColor = c;
                applyThemeColor(c);
                buildThemeSwatches();
            });
            themeRow.addView(swatch);
        }
    }

    private void applyThemeFromPrefs() {
        InbuiltModManager m = InbuiltModManager.getInstance(activity);
        themeColor = m.getUiThemeColor();
        applyThemeColor(themeColor);
        applyGlassAlpha(m.getUiGlassAlpha());
        applyStarfield(m.isStarfieldEnabled());
    }

    private void applyThemeColor(int color) {
        themeColor = color;
        if (tabQuickAccess != null) {
            GradientDrawable chip = new GradientDrawable();
            chip.setCornerRadius(12 * activity.getResources().getDisplayMetrics().density);
            int a = (color & 0x00FFFFFF) | 0xAA000000;
            chip.setColor(a);
            chip.setStroke(1, Color.parseColor("#88FFFFFF"));
            tabQuickAccess.setBackground(chip);
        }
    }

    private void applyGlassAlpha(int alphaPercent) {
        float a = 0.35f + (Math.max(20, Math.min(90, alphaPercent)) - 20) / 70f * 0.57f;
        if (menuContainer != null) menuContainer.setAlpha(a);
        if (moduleSettingsPanel != null && moduleSettingsPanel.getVisibility() == View.VISIBLE) {
            moduleSettingsPanel.setAlpha(Math.min(1f, a + 0.1f));
        }
    }

    private void applyStarfield(boolean enabled) {
        if (starfieldView != null) {
            starfieldView.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
    }

    private void bindShow(TextView tv, boolean enabled, boolean pauseOnly) {
        if (tv == null) return;
        tv.setOnClickListener(v -> {
            InbuiltModManager m = InbuiltModManager.getInstance(activity);
            m.setModMenuEnabled(enabled);
            m.setPauseMenuOnly(pauseOnly);
            refreshShow();
        });
    }

    private void refreshShow() {
        InbuiltModManager m = InbuiltModManager.getInstance(activity);
        selectSeg(showNever, !m.isModMenuEnabled());
        selectSeg(showMenus, m.isModMenuEnabled() && m.isPauseMenuOnly());
        selectSeg(showAlways, m.isModMenuEnabled() && !m.isPauseMenuOnly());
    }

    private void selectSeg(TextView tv, boolean selected) {
        if (tv == null) return;
        tv.setSelected(selected);
        tv.setAlpha(selected ? 1f : 0.55f);
    }

    private void showHomePage() {
        showingClientSettings = false;
        if (clientSettingsPage != null) clientSettingsPage.setVisibility(View.GONE);
        if (modsRecycler != null) modsRecycler.setVisibility(View.VISIBLE);
        if (navHome != null) navHome.setSelected(true);
        if (navSettings != null) navSettings.setSelected(false);
        applyFilters();
    }

    private void showClientSettingsPage() {
        showingClientSettings = true;
        closeModuleSettings();
        if (modsRecycler != null) modsRecycler.setVisibility(View.GONE);
        if (emptyStateText != null) emptyStateText.setVisibility(View.GONE);
        if (clientSettingsPage != null) clientSettingsPage.setVisibility(View.VISIBLE);
        if (navHome != null) navHome.setSelected(false);
        if (navSettings != null) navSettings.setSelected(true);
        refreshShow();
        buildThemeSwatches();
    }

    private void openHudEditor() {
        try {
            InbuiltOverlayManager.getInstance().setHudEditorMode(true);
            Toast.makeText(activity, "HUD Editor: drag overlays to move, select to resize", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "HUD editor", e);
        }
        hide();
    }

    private void openModuleSettings(UnifiedMod mod) {
        if (mod == null || !mod.hasConfig() || moduleSettingsPanel == null || moduleSettingsContent == null) {
            Toast.makeText(activity, "No settings", Toast.LENGTH_SHORT).show();
            return;
        }
        if (moduleSettingsTitle != null) moduleSettingsTitle.setText(mod.getName() + " settings");
        moduleSettingsContent.removeAllViews();
        try {
            ModConfigView.render(activity, moduleSettingsContent, mod, () -> {});
        } catch (Exception e) {
            TextView err = new TextView(activity);
            err.setText("Failed to load settings");
            err.setTextColor(0xFFFF6666);
            moduleSettingsContent.addView(err);
        }
        if (moduleSettingsPanel.getVisibility() != View.VISIBLE) {
            moduleSettingsPanel.setVisibility(View.VISIBLE);
            moduleSettingsPanel.setAlpha(0f);
            moduleSettingsPanel.setTranslationX(80f);
            moduleSettingsPanel.animate().alpha(1f).translationX(0f)
                    .setDuration(240).setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    private void closeModuleSettings() {
        if (moduleSettingsPanel == null || moduleSettingsPanel.getVisibility() != View.VISIBLE) return;
        moduleSettingsPanel.animate().alpha(0f).translationX(60f).setDuration(180)
                .withEndAction(() -> {
                    if (moduleSettingsPanel != null) {
                        moduleSettingsPanel.setVisibility(View.GONE);
                        moduleSettingsPanel.setTranslationX(0f);
                        moduleSettingsPanel.setAlpha(1f);
                    }
                }).start();
    }

    private void loadMods() {
        allMods.clear();
        try {
            List<UnifiedMod> inbuilt = InbuiltModuleProvider.load(activity);
            if (inbuilt != null) allMods.addAll(inbuilt);
            List<UnifiedMod> external = ExternalModuleProvider.load(activity);
            if (external != null) allMods.addAll(external);
        } catch (Exception e) { Log.e(TAG, "loadMods", e); }
    }

    public void refreshMods() {
        if (!isShowing) return;
        loadMods();
        applyFilters();
    }

    private void applyFilters() {
        if (showingClientSettings) return;
        List<UnifiedMod> filtered = new ArrayList<>();
        for (UnifiedMod mod : allMods) {
            if (!currentQuery.isEmpty()) {
                String n = mod.getName() == null ? "" : mod.getName().toLowerCase(Locale.US);
                String d = mod.getDescription() == null ? "" : mod.getDescription().toLowerCase(Locale.US);
                if (!n.contains(currentQuery) && !d.contains(currentQuery)) continue;
            }
            filtered.add(mod);
        }
        if (adapter != null) adapter.updateMods(filtered);
        if (emptyStateText != null) emptyStateText.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
