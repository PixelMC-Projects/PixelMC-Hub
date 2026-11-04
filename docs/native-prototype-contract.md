# PixelMC-Hub native prototype contract

Scope: Forge 1.20.1 / Java 17 client-only native Skija experiment, under `minecraft/` in this repository. Existing web preview stays available as the visual reference. No production server, database, sync or installed play-instance mutations. Use an isolated local run directory. Original code Apache-2.0, preserve asset notices. No embedded browser. Product name PixelMC-Hub, mod ID `pixelmc_hub`, base package `com.pixelmc.hub`.

Parallel ownership:
- Renderer agent: `minecraft/` Gradle setup/wrapper and `com.pixelmc.hub.client.render` only. Supplies Skija context and draw helpers, packages required libraries safely for Forge. No UI or entrypoint changes.
- UI agent: `com.pixelmc.hub.client.ui` only (except `HubHead`, reserved for asset agent). Supplies all three pages, navigation, welcome and settings in the approved design. No Gradle or screen edits.
- Asset agent: native UI resources, `com.pixelmc.hub.client.ui.HubHead` and asset-generation scripts. No other Java or build files.
- Root: mod entrypoint, Minecraft Screen/input, settings/session lifecycle, integration, docs, verification and Git.

Coordinates: fixed logical panel 1260 x 850. Root centers the panel and caps its size using monitor content scale (1 to 1.75), with a maximum occupancy of 90% viewport width and 84% height. Fullscreen does not enlarge the panel to fill the monitor. UI mouse coordinates use the inverse of this same scale. HubDraw uses top-left shapes, text baseline coordinates, ARGB ints, floating point coordinates. Rendering runs on Minecraft render thread, canvas reset/GL state restore owned by renderer. A local menu preview renders vanilla Minecraft panorama; when in a world, glass samples the actual scene already drawn by Minecraft.

Lifecycle: unwind HubDraw's nested opacity targets before restoring the root canvas; restore root/flush/restore Minecraft GL state in independent finally blocks. Preserve program point size and draw-indirect buffer binding alongside framebuffer, vertex, texture, pixel-store, and blend state. At renderer close, invalidate Skia's cached GL state and synchronously submit pending work before releasing owned resources, the borrowed target wrapper and private FBO, and finally the Skia context. The wait is only at teardown, not every frame. Run `nativeLifecycleChecks` for a hidden OpenGL 4.6/4K reopen and exception-recovery check; it does not launch Minecraft or establish game compatibility.

Renderer public API (agent may add methods, notify coordinator before changing signatures):
```java
// com.pixelmc.hub.client.render
public final class HubRenderer implements AutoCloseable {
  public void render(int framebufferWidth, int framebufferHeight, float panelX, float panelY,
                     float scale, java.util.function.Consumer<HubDraw> content);
  public void close();
}
public final class HubDraw {
  public io.github.humbleui.skija.Canvas canvas();
  public void rect(float x,float y,float w,float h,int color);
  public void roundRect(float x,float y,float w,float h,float radius,int color);
  public void strokeRoundRect(float x,float y,float w,float h,float radius,float stroke,int color);
  public void gradientRoundRect(float x,float y,float w,float h,float radius,int topLeft,int bottomRight);
  public void circle(float cx,float cy,float radius,int color);
  public void line(float x1,float y1,float x2,float y2,float stroke,int color);
  public void text(String text,float x,float baseline,float size,int color,boolean bold);
  public float textWidth(String text,float size,boolean bold);
  public void image(String asset,float x,float y,float w,float h,float alpha);
  public io.github.humbleui.skija.Image imageResource(String asset); // cached, asset-relative to assets/pixelmc_hub/
  public void icon(String name,float x,float y,float size,int color); // white PNG textures/icons/<name>.png tinted
  public void save(); public void restore(); public void translate(float x,float y);
  public void clipRoundRect(float x,float y,float w,float h,float radius);
}
```

UI public API:
```java
// com.pixelmc.hub.client.ui
public final class HubView {
  public HubView();
  public void render(HubDraw draw,float mouseX,float mouseY,long nowNanos);
  public boolean mouseClicked(float x,float y,int button);
  public boolean mouseReleased(float x,float y,int button);
  public boolean mouseDragged(float x,float y,int button,float dx,float dy);
  public boolean mouseScrolled(float x,float y,double amount);
  public boolean keyPressed(int keyCode,int scanCode,int modifiers);
  public boolean keyReleased(int keyCode,int scanCode,int modifiers);
  public boolean charTyped(char codePoint,int modifiers);
  public String pointerCursor(); // default / pointer / text, after transformed hit testing
  public boolean dismissOverlay(); // Esc closes modal first, false lets Screen close
  public void open(boolean playWelcome); // always returns home, resets entrance timing
  public void setPlayerName(String name);
  public void setSettings(String welcomeMode,boolean reducedMotion);
  public String welcomeMode(); public boolean reducedMotion();
  public boolean consumeCloseRequest();
}
public final class HubHead {
  public static void draw(HubDraw draw,float x,float y,float size,float yawDegrees);
}
```

All state remains local demonstrator data, clearly labeled in UI. Modes: `once` left, `login` middle, `always` right. Default `always`. Settings controls are live; root persists to the test instance config, and honors actual game login sessions. Header skin/name may be real local player; other statistics must stay labeled demo. No credentials.

Visual source of truth: `src/App.jsx`, `src/glass-theme.css`, `src/styles.css`, `src/data.js`, `src/WelcomeSplash.jsx`, `src/welcome-splash.css`, `docs/design-spec.md`. Native art folders: `minecraft/src/main/resources/assets/pixelmc_hub/textures/ui/` and `textures/icons/`; fortune text/data can live under `data/`. Assets reuse original licenses; personal local skin must remain ignored/uncommitted.
