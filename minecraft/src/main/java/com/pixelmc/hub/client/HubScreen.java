package com.pixelmc.hub.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.pixelmc.hub.client.render.HubRenderer;
import com.pixelmc.hub.client.ui.HubHead;
import com.pixelmc.hub.client.ui.HubView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

/** Native GPU composition and input bridge; no browser, HTTP listener or gameplay mutations. */
public final class HubScreen extends Screen {
    private final HubView view;
    private final String scope;
    private final Screen previous;
    private final boolean welcomeRequested;
    private HubRenderer renderer;
    private boolean firstFrame = true;
    private boolean failed;
    private boolean disposed;
    private float panelX, panelY, panelScale = 1;
    private ResourceLocation capturedSkin;
    private long lastSkinAttempt;
    private long firstFrameAt;
    private boolean automaticScreenshot;

    HubScreen(HubView view, String scope, boolean welcomeRequested, Screen previous) {
        super(Component.literal("PixelMC-Hub"));
        this.view = view;
        this.scope = scope;
        this.welcomeRequested = welcomeRequested;
        this.previous = previous;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (failed) {
            graphics.fill(0, 0, width, height, 0xe5182033);
            graphics.drawCenteredString(font, "PixelMC-Hub 渲染初始化失败", width / 2, height / 2 - 12, 0xffffff);
            graphics.drawCenteredString(font, "按 Esc 返回；详细原因已记录到 latest.log", width / 2, height / 2 + 8, 0xb9c8e7);
            return;
        }
        if (minecraft.level == null) graphics.fillGradient(0, 0, width, height, 0xffc4dbef, 0xffcbbbe9);
        graphics.flush();
        int framebufferWidth = minecraft.getWindow().getWidth();
        int framebufferHeight = minecraft.getWindow().getHeight();
        if (framebufferWidth <= 0 || framebufferHeight <= 0) return;
        panelScale = Math.max(0.1f, Math.min((framebufferWidth - 48f) / 1260f, (framebufferHeight - 48f) / 850f));
        panelX = (framebufferWidth - 1260 * panelScale) / 2;
        panelY = (framebufferHeight - 850 * panelScale) / 2;
        float localX = localX(mouseX), localY = localY(mouseY);
        try {
            capturePlayerSkin();
            if (renderer == null) renderer = new HubRenderer();
            renderer.render(framebufferWidth, framebufferHeight, panelX, panelY, panelScale,
                draw -> view.render(draw, localX, localY, System.nanoTime()));
            if (firstFrame) {
                firstFrame = false;
                firstFrameAt = System.nanoTime();
                if (welcomeRequested && !view.reducedMotion()) ClientBootstrap.markWelcomeSeen(scope);
                LogUtils.getLogger().info("PixelMC-Hub native frame rendered: {}x{}, logical scale {}", framebufferWidth, framebufferHeight, panelScale);
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
            LogUtils.getLogger().error("PixelMC-Hub native preview rendering failed; the screen can be closed with Esc", failure);
        }
    }

    private float localX(double guiX) { return (float) ((guiX * minecraft.getWindow().getWidth() / width - panelX) / panelScale); }
    private float localY(double guiY) { return (float) ((guiY * minecraft.getWindow().getHeight() / height - panelY) / panelScale); }

    @Override public boolean mouseClicked(double x,double y,int button) {
        return !failed && view.mouseClicked(localX(x),localY(y),button) || super.mouseClicked(x,y,button);
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
        if (!failed && view.textInputFocused() && Screen.isPaste(keyCode)) {
            view.pasteText(minecraft.keyboardHandler.getClipboard());
            return true;
        }
        return !failed && view.keyPressed(keyCode,scanCode,modifiers) || super.keyPressed(keyCode,scanCode,modifiers);
    }
    @Override public boolean charTyped(char codePoint,int modifiers) {
        return !failed && view.charTyped(codePoint,modifiers) || super.charTyped(codePoint,modifiers);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { ClientBootstrap.persist(view); minecraft.setScreen(previous); }
    @Override public void removed() {
        ClientBootstrap.persist(view);
        if (!disposed && renderer != null) {
            disposed = true;
            try { renderer.close(); }
            catch (Throwable failure) { LogUtils.getLogger().warn("PixelMC-Hub renderer cleanup failed", failure); }
        }
    }

    private void capturePlayerSkin() {
        if (minecraft.player == null) return;
        long now = System.nanoTime();
        if (now - lastSkinAttempt < 1_000_000_000L) return;
        lastSkinAttempt = now;
        ResourceLocation location = minecraft.player.getSkinTextureLocation();
        if (location.equals(capturedSkin)) return;
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousPackAlignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        try {
            var texture = minecraft.getTextureManager().getTexture(location);
            RenderSystem.bindTexture(texture.getId());
            int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (w < 64 || w > 4096 || h < 32 || h > 4096) return;
            try (NativeImage image = new NativeImage(w, h, false)) {
                image.downloadTexture(0, false);
                HubHead.setSkinPng(image.asByteArray());
                capturedSkin = location;
            }
        } catch (Exception exception) {
            LogUtils.getLogger().debug("PixelMC-Hub could not capture local skin yet", exception);
        } finally {
            RenderSystem.bindTexture(previousTexture);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, previousPackAlignment);
        }
    }
}
