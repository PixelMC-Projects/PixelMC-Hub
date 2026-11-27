/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.pixelmc.hub.client.render.HubDraw;

import io.github.humbleui.skija.Matrix33;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Lifecycle, navigation, rendering, and input contract for external sibling pages hosted inside
 * {@link HubView}.
 */
public interface HubExternalPage {
    String id();

    HubView.NavigationEntry navigationEntry();

    boolean navigationVisible();

    default void refresh() {}

    default void prefetchIfDue() {}

    default void cancelPendingOpen() {}

    default boolean coverHandbook(long now, boolean reducedMotion) {
        return false;
    }

    default float handbookAlpha(long now, boolean reducedMotion) {
        return 1f;
    }

    default boolean routeVisible(long now, boolean reducedMotion) {
        return active();
    }

    boolean active();

    void render(
            HubDraw draw,
            Matrix33 logical,
            float mx,
            float my,
            long now,
            boolean dark,
            boolean reducedMotion,
            String focusId);

    default void paintOverlayItems(
            GuiGraphics graphics,
            float panelX,
            float panelY,
            float scaleX,
            float scaleY,
            int mouseX,
            int mouseY) {}

    default void onPanelVisibility(boolean open) {}

    default boolean escape() {
        return false;
    }

    default boolean pointerDown(float x, float y, boolean shift) {
        return false;
    }

    default boolean pointerUp() {
        return false;
    }

    default boolean pointerDrag(float x, float y) {
        return false;
    }

    default boolean scroll(float x, float y, double amount) {
        return false;
    }

    default boolean keyPressed(int key, int scanCode, int mods, String focusId) {
        return false;
    }

    default boolean charTyped(char c, int mods, String focusId) {
        return false;
    }

    default boolean textFocused(String focusId) {
        return false;
    }

    default String selectedText() {
        return "";
    }

    default String cutText() {
        return "";
    }

    default void pasteText(String value) {}

    /**
     * Returns the cursor token ({@code "text"}, {@code "pointer"}, {@code "default"}) when this
     * page claims the pointer at {@code (mx, my)}, or {@code null} to fall back to the host.
     */
    default String pointerCursor(float mx, float my) {
        return null;
    }
}
