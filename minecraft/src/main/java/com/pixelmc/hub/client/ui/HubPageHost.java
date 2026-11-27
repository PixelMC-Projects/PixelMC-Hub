/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.pixelmc.hub.client.render.HubDraw;

import io.github.humbleui.skija.Matrix33;

/**
 * Host capability contract exposed by {@link HubView} to registered {@link HubExternalPage}
 * implementations.
 */
public interface HubPageHost {
    HubDraw draw();

    Matrix33 logical();

    float mx();

    float my();

    long now();

    boolean dark();

    boolean winter();

    boolean reducedMotion();

    String focusId();

    void clearNextHits();

    void hit(String id, String label, float x, float y, float w, float h, Runnable action);

    void navigateBuiltIn(int page);

    boolean navigatePage(String id);

    void drawExtensionNavigation(HubView.NavigationEntry entry, int slot, boolean visible);

    float extensionHover(String id, boolean hovered);

    float extensionButtonMove(String id, boolean enabled, boolean hovered);

    void beginAlpha(float alpha, float x, float y, float w, float h);

    void endAlpha();

    void push(Matrix33 transform);

    void pop();

    void focus(String id);

    void feedback(String value);

    void feedbackSuccess(String value);

    void feedbackError(String value);

    void clearKeyboardFocus();

    void setKeyboardFocus(boolean focused);

    long windowHandle();
}
