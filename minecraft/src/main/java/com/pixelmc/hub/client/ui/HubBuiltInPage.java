/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.pixelmc.hub.client.render.HubDraw;

/** Lightweight internal page contract for built-in handbook pages managed by {@link HubView}. */
public interface HubBuiltInPage {
    int index();

    String id();

    String label();

    default String section() {
        return "我的空间";
    }

    HubView.NavigationEntry navigationEntry();

    default HubMotion.Navigation navigationState() {
        return null;
    }

    default boolean visible() {
        return true;
    }

    default boolean unread() {
        return false;
    }

    default void onEnter(long now) {}

    default void onLeave(int nextDestination) {}

    default void onPanelVisibility(boolean open) {}

    void render(
            HubDraw draw,
            float mx,
            float my,
            long now,
            boolean dark,
            boolean winter,
            boolean reducedMotion,
            String focusId);

    default boolean escape() {
        return false;
    }

    default boolean dismissOverlay() {
        return false;
    }

    default boolean pointerDown(float x, float y, float pageX, float pageY, boolean shift) {
        return false;
    }

    default boolean pointerUp(float pageX, float pageY) {
        return false;
    }

    default boolean pointerDrag(float pageX, float pageY) {
        return false;
    }

    default boolean scroll(float pageX, float pageY, double amount) {
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

    default String selectedText(String focusId) {
        return "";
    }

    default String cutText(String focusId) {
        return "";
    }

    default void pasteText(String value, String focusId) {}

    default String pointerCursor(float pageX, float pageY) {
        return null;
    }
}
