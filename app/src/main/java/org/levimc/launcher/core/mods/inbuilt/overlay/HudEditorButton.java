package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.widget.ImageButton;

import org.levimc.launcher.R;
import org.levimc.launcher.core.mods.inbuilt.model.ModIds;

public class HudEditorButton extends BaseOverlayButton {
    public HudEditorButton(Activity activity) {
        super(activity);
    }

    @Override
    protected String getModId() {
        return ModIds.TOGGLE_HUD;
    }

    @Override
    protected int getIconResource() {
        return R.drawable.ic_toggle_hud_normal;
    }

    @Override
    protected void onButtonClick() {
        InbuiltOverlayManager mgr = InbuiltOverlayManager.getInstance();
        boolean nowActive = !mgr.isHudEditorModeActive();
        mgr.setHudEditorMode(nowActive);
        updateButtonState(nowActive);
    }

    private void updateButtonState(boolean active) {
        if (overlayView instanceof ImageButton) {
            ((ImageButton) overlayView).setAlpha(getButtonOpacity());
        }
    }
}
