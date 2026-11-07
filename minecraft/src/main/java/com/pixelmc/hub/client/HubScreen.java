package com.pixelmc.hub.client;

import com.mojang.logging.LogUtils;
import com.pixelmc.hub.client.render.HubRenderer;
import com.pixelmc.hub.client.ui.HubView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** Native GPU composition and input bridge. Player requests use the authenticated optional channel. */
public final class HubScreen extends Screen {
    private final HubView view;
    private final String scope;
    private final Screen previous;
    private final boolean welcomeRequested;
    private HubRenderer renderer;
    private com.pixelmc.hub.client.render.HubRenderResources.Lease rendererLease;
    private boolean firstFrame = true;
    private boolean failed;
    private boolean disposed;
    private float panelX, panelY, panelScale = 1;

    private long firstFrameAt;
    private boolean automaticScreenshot;
    private boolean welcomeMarked;
    private final PanoramaRenderer menuPanorama = new PanoramaRenderer(
        new CubeMap(new ResourceLocation("textures/gui/title/background/panorama")));
    private final float[] contentScaleX = {1}, contentScaleY = {1};
    private long nextDpiCheck;
    private float displayScale = 1;
    private long handCursor, textCursor;
    private int activeCursor = GLFW.GLFW_ARROW_CURSOR;

    HubScreen(HubView view, String scope, boolean welcomeRequested, Screen previous) {
        super(Component.literal("PixelMC-Hub"));
        this.view = view;
        this.scope = scope;
        this.welcomeRequested = welcomeRequested;
        this.previous = previous;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long displayBegan=System.nanoTime();
        if (failed) {
            updateCursor("default");
            graphics.fill(0, 0, width, height, 0xe5182033);
            graphics.drawCenteredString(font, "PixelMC-Hub 渲染初始化失败", width / 2, height / 2 - 12, 0xffffff);
            graphics.drawCenteredString(font, "按 Esc 返回；详细原因已记录到 latest.log", width / 2, height / 2 + 8, 0xb9c8e7);
            return;
        }
        // The title-menu preview uses a real moving Minecraft scene so glass has a
        // meaningful backdrop. In a world, Minecraft has already drawn that world.
        if (minecraft.level == null) {
            graphics.flush();
            menuPanorama.render(partialTick, 1);
        } else {
            // Use the normal Screen background hook before Skija captures it.
            // Menu-effect mods (e.g. Modern UI) own world blur and its transition;
            // vanilla 1.20.1 supplies dimming when no such effect is installed.
            graphics.flush();
            super.renderBackground(graphics);
        }
        graphics.flush();
        int framebufferWidth = minecraft.getWindow().getWidth();
        int framebufferHeight = minecraft.getWindow().getHeight();
        if (framebufferWidth <= 0 || framebufferHeight <= 0) return;
        updatePanelScale(framebufferWidth, framebufferHeight);
        panelX = (framebufferWidth - 1260 * panelScale) / 2;
        panelY = (framebufferHeight - 850 * panelScale) / 2;
        view.setViewportBounds(-panelX / panelScale, -panelY / panelScale,
            framebufferWidth / panelScale, framebufferHeight / panelScale);
        float localX = localX(mouseX), localY = localY(mouseY);
        long acquiredAt=System.nanoTime();
        try {
            if(!HubClientGraphics.valid(rendererLease)){rendererLease=HubClientGraphics.acquire();renderer=rendererLease.renderer();disposed=false;}
            HubClientGraphics.requestRankingSkins(view.rankingPlayerNames());
            double acquireMs=(System.nanoTime()-acquiredAt)/1_000_000.0;
            renderer.render(framebufferWidth, framebufferHeight, panelX, panelY, panelScale,
                draw -> view.render(draw, localX, localY, System.nanoTime()));
            updateCursor(view.pointerCursor());
            if (firstFrame) {
                firstFrame = false;
                firstFrameAt = System.nanoTime();
                LogUtils.getLogger().info("PixelMC-Hub first displayed screen CPU submission size={}x{} scale={} acquireMs={} nativeMs={} totalMs={} warmupComplete={}",
                    framebufferWidth,framebufferHeight,panelScale,String.format(java.util.Locale.ROOT,"%.3f",acquireMs),String.format(java.util.Locale.ROOT,"%.3f",renderer.lastTiming().totalMs()),String.format(java.util.Locale.ROOT,"%.3f",(System.nanoTime()-displayBegan)/1_000_000.0),renderer.warmupProgress().complete());
            }
            if(!welcomeMarked&&view.isWelcomePlaying()) {
                welcomeMarked=true;
                ClientBootstrap.markWelcomeSeen(minecraft.player==null?scope:ClientBootstrap.activeScope());
            }
            if (Boolean.getBoolean("pixelmc.hub.capturePreview") && !automaticScreenshot && !view.isWelcomePlaying()
                    && System.nanoTime() - firstFrameAt > 4_000_000_000L) {
                automaticScreenshot = true;
                Screenshot.grab(minecraft.gameDirectory, "pixelmc-hub-" + System.currentTimeMillis() + ".png",
                    minecraft.getMainRenderTarget(), message -> LogUtils.getLogger().info("PixelMC-Hub automatic screenshot: {}", message.getString()));
            }
            if (view.consumeCloseRequest()) onClose();
        } catch (Throwable failure) {
            failed = true;
            try{HubClientGraphics.failed(rendererLease);}catch(Throwable cleanup){failure.addSuppressed(cleanup);}
            LogUtils.getLogger().error("PixelMC-Hub native preview rendering failed; the screen can be closed with Esc", failure);
        }
    }

    private void updatePanelScale(int framebufferWidth, int framebufferHeight) {
        long now = System.nanoTime();
        if (now >= nextDpiCheck) {
            GLFW.glfwGetWindowContentScale(minecraft.getWindow().getWindow(), contentScaleX, contentScaleY);
            float dpi = Math.max(contentScaleX[0], contentScaleY[0]);
            displayScale = Float.isFinite(dpi) && dpi > 0 ? Math.max(1, Math.min(1.75f, dpi)) : 1;
            nextDpiCheck = now + 1_000_000_000L;
        }
        // Match the reference's max-width:1260px/max-height:850px in display pixels.
        // Fullscreen must not continually enlarge all type/cards to fill the monitor.
        // On a smaller window, leave breathing room and shrink the entire coordinate
        // system uniformly; drawing and pointer conversion use this same scale.
        panelScale = Math.max(.1f, Math.min(displayScale,
            Math.min(framebufferWidth * .90f / 1260f, framebufferHeight * .84f / 850f)));
    }

    private void updateCursor(String cursor) {
        int shape = switch (cursor) {
            case "pointer" -> GLFW.GLFW_HAND_CURSOR;
            case "text" -> GLFW.GLFW_IBEAM_CURSOR;
            default -> GLFW.GLFW_ARROW_CURSOR;
        };
        if (shape == activeCursor) return;
        long handle = 0;
        if (shape == GLFW.GLFW_HAND_CURSOR) {
            if (handCursor == 0) handCursor = GLFW.glfwCreateStandardCursor(shape);
            handle = handCursor;
        } else if (shape == GLFW.GLFW_IBEAM_CURSOR) {
            if (textCursor == 0) textCursor = GLFW.glfwCreateStandardCursor(shape);
            handle = textCursor;
        }
        GLFW.glfwSetCursor(minecraft.getWindow().getWindow(), handle);
        activeCursor = shape;
    }

    private float localX(double guiX) { return (float) ((guiX * minecraft.getWindow().getWidth() / width - panelX) / panelScale); }
    private float localY(double guiY) { return (float) ((guiY * minecraft.getWindow().getHeight() / height - panelY) / panelScale); }

    @Override public boolean mouseClicked(double x,double y,int button) {
        return !failed && view.mouseClicked(localX(x),localY(y),button,Screen.hasShiftDown()) || super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseReleased(double x,double y,int button) {
        return !failed && view.mouseReleased(localX(x),localY(y),button) || super.mouseReleased(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        if (failed) return false;
        return view.mouseDragged(localX(x),localY(y),button,
            (float) (dx * minecraft.getWindow().getWidth() / width / panelScale),
            (float) (dy * minecraft.getWindow().getHeight() / height / panelScale));
    }
    @Override public boolean mouseScrolled(double x,double y,double amount) {
        return !failed && view.mouseScrolled(localX(x),localY(y),amount);
    }
    @Override public boolean keyPressed(int keyCode,int scanCode,int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (failed || !view.dismissOverlay()) onClose();
            return true;
        }
        if(!failed&&view.textInputFocused()&&Screen.isCopy(keyCode)){minecraft.keyboardHandler.setClipboard(view.selectedInputText());return true;}
        if(!failed&&view.textInputFocused()&&Screen.isCut(keyCode)){minecraft.keyboardHandler.setClipboard(view.cutInputText());return true;}
        if (!failed && view.textInputFocused() && Screen.isPaste(keyCode)) {
            view.pasteText(minecraft.keyboardHandler.getClipboard());
            return true;
        }
        return !failed && view.keyPressed(keyCode,scanCode,modifiers) || super.keyPressed(keyCode,scanCode,modifiers);
    }
    @Override public boolean charTyped(char codePoint,int modifiers) {
        return !failed && view.charTyped(codePoint,modifiers) || super.charTyped(codePoint,modifiers);
    }
    @Override public boolean keyReleased(int keyCode,int scanCode,int modifiers) {
        return !failed && view.keyReleased(keyCode,scanCode,modifiers) || super.keyReleased(keyCode,scanCode,modifiers);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { ClientBootstrap.persist(view); minecraft.setScreen(previous); }
    @Override public void removed() {
        // Connection-owned data stays warm while this screen and its GPU resources close.
        ClientHubSession.panelClosed();
        ClientBootstrap.persist(view);
        view.releaseCommunityResources();
        updateCursor("default");
        if (handCursor != 0) { GLFW.glfwDestroyCursor(handCursor); handCursor = 0; }
        if (textCursor != 0) { GLFW.glfwDestroyCursor(textCursor); textCursor = 0; }
        if (!disposed && renderer != null) {
            disposed = true;
            try { HubClientGraphics.release(rendererLease); }
            catch (Throwable failure) { LogUtils.getLogger().warn("PixelMC-Hub renderer cleanup failed", failure); }
        }
    }

}
