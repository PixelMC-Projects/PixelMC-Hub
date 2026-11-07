/*
 * Original Skija bridge: Apache-2.0.
 * tiltGlare's CSS gradient stops are adapted from React Bits TiltedCard.css,
 * copyright David Haz (2026), MIT + Commons Clause. Their original terms remain
 * in assets/pixelmc_hub/licenses/React-Bits-LICENSE.md; those portions are not relicensed.
 */
package com.pixelmc.hub.client.render;

import io.github.humbleui.skija.*;
import io.github.humbleui.skija.shaper.Shaper;
import io.github.humbleui.types.Rect;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Point;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Deque;

/** Logical, top-left drawing helpers. Native resources belong to the owning renderer. */
public final class HubDraw implements AutoCloseable {
    private static final String ROOT = "/assets/pixelmc_hub/";
    private static final int MAX_TEXT_LINES = 768;
    private static final SamplingMode SMOOTH_SAMPLING=new FilterMipmap(FilterMode.LINEAR,MipmapMode.LINEAR);
    private static final String[] BODY_FAMILIES={"Segoe UI Variable Text","Segoe UI","Microsoft YaHei UI","sans-serif"};
    private static final String[] POEM_FAMILIES={"Noto Serif SC","Source Han Serif SC","SimSun","serif"};
    private static final String[] CJK_FAMILIES={"Microsoft YaHei UI","Microsoft YaHei","Noto Sans CJK SC","PingFang SC","sans-serif"};
    private final Paint paint = new Paint();
    private final FontMgr fontManager = FontMgr.getDefault();
    private final Shaper shaper = Shaper.makeBestAvailable(fontManager);
    private final Typeface regular;
    private final Typeface bold;
    private final Typeface serifRegular;
    private final Typeface serifBold;
    private final Typeface cjkRegular;
    private final Typeface cjkBold;
    private final Map<FontKey, Font> fonts = new LinkedHashMap<>();
    private final Map<TextKey, TextLine> lines = new LinkedHashMap<>(128, 0.75f, true);
    private final Map<String, Image> images = new LinkedHashMap<>();
    private final Map<RunKey, List<FontRunText>> sansRuns = new LinkedHashMap<>(128, 0.75f, true);
    private final Map<GlyphKey, Boolean> primaryGlyphs = new LinkedHashMap<>();
    private final Map<FaceKey,Typeface> weightedFaces=new LinkedHashMap<>(16,.75f,true);
    private final Map<TrackedKey,TrackedLine> trackedLines=new LinkedHashMap<>(128,.75f,true);
    private Canvas canvas;
    private int baseSaveCount;
    private boolean closed;
    private HubGlass glass;
    private Surface targetSurface;
    private float surfaceOriginX, surfaceOriginY;
    private final Deque<AlphaLayer> alphaLayers = new ArrayDeque<>();
    private final Map<LayerKey,Surface> layerPool=new LinkedHashMap<>(12,.75f,true);
    private long layerPixels;
    private int rootWidth,rootHeight;
    private java.util.List<String> warmAssets;
    private boolean dark, literalColors, supersampling;
    private final HubCorners corners=new HubCorners();
    private final Map<String,Surface> headLayers=new LinkedHashMap<>(8,.75f,true);
    public void setDark(boolean value){dark=value;}
    public boolean dark(){return dark;}
    /** Explicit CSS palettes and provider glyphs may opt out of the legacy light-token aliases. */
    public boolean literalColors(boolean value){boolean previous=literalColors;literalColors=value;return previous;}
    private int foreground(int color){return dark&&!literalColors?HubTheme.foreground(color):color;}
    private int surfaceColor(int color){return dark&&!literalColors?HubTheme.surface(color):color;}
    private int borderColor(int color){return dark&&!literalColors?HubTheme.border(color):color;}
    private int[] surfaceColors(int[] colors){return dark&&!literalColors?HubTheme.gradient(colors):colors;}


    HubDraw() {
        regular = typeface("fonts/Hub-Regular.ttf", FontStyle.NORMAL);
        bold = typeface("fonts/Hub-Bold.ttf", FontStyle.BOLD);
        serifRegular = serifTypeface(FontStyle.NORMAL);
        serifBold = serifTypeface(FontStyle.BOLD);
        cjkRegular = cjkTypeface(FontStyle.NORMAL);
        cjkBold = cjkTypeface(FontStyle.BOLD);
    }

    private Typeface serifTypeface(FontStyle style) {
        Typeface result = fontManager.matchFamiliesStyleCharacter(
            POEM_FAMILIES,
            style, new String[]{"zh-CN", "en"}, '签');
        if (result == null) result = fontManager.matchFamilyStyle("serif", style);
        if (result == null) throw new IllegalStateException("No usable local serif font is available");
        return result;
    }

    private Typeface typeface(String bundled, FontStyle style) {
        try (InputStream stream = HubDraw.class.getResourceAsStream(ROOT + bundled)) {
            if (stream != null) {
                try (Data data = Data.makeFromBytes(stream.readAllBytes())) {
                    Typeface result = fontManager.makeFromData(data);
                    if (result != null) return result;
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read bundled Hub font " + bundled, exception);
        }
        // Match the reference CSS's first family for Latin; unsupported CJK glyphs use explicit runs below.
        Typeface result = fontManager.matchFamiliesStyle(BODY_FAMILIES, style);
        if (result == null) result = fontManager.matchFamilyStyle("sans-serif", style);
        if (result == null) throw new IllegalStateException("No usable local font is available");
        return result;
    }

    private Typeface cjkTypeface(FontStyle style) {
        Typeface result = fontManager.matchFamiliesStyleCharacter(
            CJK_FAMILIES,
            style, new String[]{"zh-CN", "en"}, '中');
        if (result == null) result = fontManager.matchFamilyStyle("sans-serif", style);
        if (result == null) throw new IllegalStateException("No usable local CJK font is available");
        return result;
    }

    void begin(Canvas value) {
        if (closed) throw new IllegalStateException("HubDraw was closed");
        canvas = value;
        baseSaveCount = canvas.getSaveCount();
    }

    void begin(Canvas value, Surface target) {
        begin(value);
        if(rootWidth!=target.getWidth()||rootHeight!=target.getHeight()) {
            layerPool.values().forEach(Surface::close); layerPool.clear(); layerPixels=0;
            rootWidth=target.getWidth(); rootHeight=target.getHeight();
        }
        targetSurface=target; surfaceOriginX=0; surfaceOriginY=0;
        if (glass==null) glass=new HubGlass();
        glass.begin(value,target);
    }

    void end() {
        while(!alphaLayers.isEmpty()) restoreAlphaLayer();
        if(glass!=null) glass.end();
        canvas=null; targetSurface=null;
    }

    public boolean isClosed(){return closed;}
    public Canvas canvas() {
        if (canvas == null) throw new IllegalStateException("HubDraw can only draw inside HubRenderer.render");
        return canvas;
    }

    private Paint fill(int color) { return paint.reset().setAntiAlias(true).setColor(color); }

    public void rect(float x, float y, float w, float h, int color) {
        color=surfaceColor(color);
        if (w > 0 && h > 0) canvas().drawRect(Rect.makeXYWH(x, y, w, h), fill(color));
    }

    public void roundRect(float x, float y, float w, float h, float radius, int color) {
        color=surfaceColor(color);
        if (w > 0 && h > 0) corners.draw(canvas(),x,y,w,h,radius,fill(color));
    }

    public void strokeRoundRect(float x, float y, float w, float h, float radius, float stroke, int color) {
        color=borderColor(color);
        if (w > 0 && h > 0 && stroke > 0)
            corners.draw(canvas(),x,y,w,h,radius,fill(color).setMode(PaintMode.STROKE).setStrokeWidth(stroke));
    }

    /** CSS border-radius:50% uses independent horizontal/vertical radii, not a capsule. */
    public void oval(float x, float y, float w, float h, int color) {
        color=surfaceColor(color);
        if (w > 0 && h > 0) canvas().drawOval(Rect.makeXYWH(x,y,w,h),fill(color));
    }

    public void strokeOval(float x, float y, float w, float h, float stroke, int color) {
        color=borderColor(color);
        if (w > 0 && h > 0 && stroke > 0)
            canvas().drawOval(Rect.makeXYWH(x,y,w,h),fill(color).setMode(PaintMode.STROKE).setStrokeWidth(stroke));
    }

    public void ovalShadow(float x,float y,float w,float h,float offsetX,float offsetY,float blur,int color) {
        if(w<=0||h<=0)return;
        int count=canvas().save();
        // CSS outer shadows are clipped outside the border box, even with a translucent fill.
        canvas.clipRRect(RRect.makeXYWH(x,y,w,h,w*.5f,h*.5f),ClipMode.DIFFERENCE,true);
        try(ImageFilter shadow=ImageFilter.makeDropShadowOnly(offsetX,offsetY,Math.max(0,blur)*.5f,Math.max(0,blur)*.5f,color)) {
            canvas.drawOval(Rect.makeXYWH(x,y,w,h),fill(0xFFFFFFFF).setImageFilter(shadow));
        } finally {paint.reset();canvas.restoreToCount(count);}
    }

    public void insetOvalShadow(float x,float y,float w,float h,float offsetX,float offsetY,float blur,int color) {
        if(w<=0||h<=0)return;
        int count=canvas().save();canvas.clipRRect(RRect.makeXYWH(x,y,w,h,w*.5f,h*.5f),true);
        float pad=Math.max(16,blur*3+Math.abs(offsetX)+Math.abs(offsetY));
        try(PathBuilder builder=new PathBuilder(PathFillMode.EVEN_ODD);
            MaskFilter filter=blur>0?MaskFilter.makeBlur(FilterBlurMode.NORMAL,blur*.5f):null) {
            builder.addRect(Rect.makeXYWH(x-pad,y-pad,w+pad*2,h+pad*2));
            builder.addOval(Rect.makeXYWH(x+offsetX,y+offsetY,w,h));
            try(Path path=builder.build()) {canvas.drawPath(path,fill(color).setMaskFilter(filter));}
        } finally {paint.reset();canvas.restoreToCount(count);}
    }

    public void gradientRoundRect(float x, float y, float w, float h, float radius, int topLeft, int bottomRight) {
        if (w <= 0 || h <= 0) return;
        if(dark&&!literalColors&&(topLeft&0xffffff)==0xffffff&&(bottomRight&0xffffff)!=0xffffff) {
            linearGradientRoundRect(x,y,w,h,radius,140,HubTheme.calendarGradient(bottomRight),new float[]{.08f,.4f,.69f,1});
            return;
        }
        try (Shader shader = Shader.makeLinearGradient(x, y, x + w, y + h, surfaceColors(new int[]{topLeft, bottomRight}))) {
            corners.draw(canvas(),x,y,w,h,radius,fill(0xFFFFFFFF).setShader(shader));
            paint.setShader(null);
        }
    }

    public void linearGradientRoundRect(float x,float y,float w,float h,float radius,float angleDeg,int[] colors,float[] stops) {
        colors=surfaceColors(colors);
        double radians=Math.toRadians(angleDeg);
        float dx=(float)Math.sin(radians),dy=-(float)Math.cos(radians);
        float extent=(Math.abs(w*dx)+Math.abs(h*dy))*.5f,cx=x+w*.5f,cy=y+h*.5f;
        try(Shader shader=Shader.makeLinearGradient(cx-dx*extent,cy-dy*extent,cx+dx*extent,cy+dy*extent,colors,stops)) {
            corners.draw(canvas(),x,y,w,h,radius,fill(0xFFFFFFFF).setShader(shader)); paint.setShader(null);
        }
    }

    public void radialGradientRoundRect(float x,float y,float w,float h,float radius,float cx,float cy,float rx,float ry,int[] colors,float[] stops) {
        colors=surfaceColors(colors);
        if(rx<=0||ry<=0)return;
        Matrix33 local=Matrix33.makeTranslate(cx,cy).makeConcat(Matrix33.makeScale(rx,ry));
        try(Shader shader=Shader.makeRadialGradient(0,0,1,colors,stops,GradientStyle.DEFAULT.withLocalMatrix(local))) {
            corners.draw(canvas(),x,y,w,h,radius,fill(0xFFFFFFFF).setShader(shader)); paint.setShader(null);
        }
    }

    public void boxShadow(float x,float y,float w,float h,float radius,float offsetX,float offsetY,float blur,float spread,int color) {
        if(w+spread*2<=0||h+spread*2<=0)return;
        int saved=canvas().save();corners.clip(canvas(),x,y,w,h,radius,ClipMode.DIFFERENCE);
        try(ImageFilter shadow=ImageFilter.makeDropShadowOnly(offsetX,offsetY,Math.max(0,blur)*.5f,Math.max(0,blur)*.5f,color)) {
            corners.draw(canvas(),x-spread,y-spread,w+spread*2,h+spread*2,Math.max(0,radius+spread),
                fill(0xFFFFFFFF).setImageFilter(shadow),radius>=16);paint.setImageFilter(null);
        } finally {canvas().restoreToCount(saved);}
    }

    public void insetBoxShadow(float x,float y,float w,float h,float radius,float offsetX,float offsetY,float blur,float spread,int color) {
        int count=canvas().save();corners.clip(canvas(),x,y,w,h,radius);
        float pad=Math.max(16,blur*3+Math.abs(offsetX)+Math.abs(offsetY)+Math.abs(spread));
        try(PathBuilder builder=new PathBuilder(PathFillMode.EVEN_ODD);
            MaskFilter filter=blur>0?MaskFilter.makeBlur(FilterBlurMode.NORMAL,blur*.5f):null) {
            builder.addRect(Rect.makeXYWH(x-pad,y-pad,w+pad*2,h+pad*2));
            corners.append(builder,x+spread+offsetX,y+spread+offsetY,Math.max(0,w-spread*2),Math.max(0,h-spread*2),Math.max(0,radius-spread),radius>=16);
            try(Path path=builder.build()) {canvas.drawPath(path,fill(color).setMaskFilter(filter));paint.reset();}
        } finally {canvas.restoreToCount(count);}
    }

    public void tiltGlare(float x,float y,float w,float h,float radius,float gx,float gy,float strength) {
        float cx=x+gx*w,cy=y+gy*h;
        float farX=Math.max(Math.abs(cx-x),Math.abs(cx-x-w)),farY=Math.max(Math.abs(cy-y),Math.abs(cy-y-h));
        float reach=(float)Math.hypot(farX,farY),amount=Math.max(0,Math.min(1,strength));
        float[] opacities={.34f,.331f,.285f,.202f,.108f,.035f,.003f,0};
        int[] colors=new int[opacities.length];for(int i=0;i<colors.length;i++)colors[i]=Math.round(opacities[i]*amount*255)<<24|0xFFFFFF;
        try(Shader shader=Shader.makeRadialGradient(cx,cy,Math.max(1,reach),colors,new float[]{0,.09f,.18f,.27f,.36f,.45f,.54f,.6f})) {
            corners.draw(canvas(),x,y,w,h,radius,fill(0xFFFFFFFF).setShader(shader).setBlendMode(BlendMode.SCREEN));paint.reset();
        }
        float[] edgeAlpha={.85f,.71f,.43f,.16f,.02f,0};
        int[] edgeColors=new int[edgeAlpha.length];for(int i=0;i<edgeColors.length;i++)edgeColors[i]=Math.round(edgeAlpha[i]*amount*255)<<24|0xFFFFFF;
        try(Shader edge=Shader.makeRadialGradient(cx,cy,Math.max(1,Math.max(farX,farY)),edgeColors,new float[]{0,.18f,.36f,.54f,.72f,.85f})) {
            corners.draw(canvas(),x+.5f,y+.5f,w-1,h-1,Math.max(0,radius-.5f),
                fill(0xFFFFFFFF).setShader(edge).setMode(PaintMode.STROKE).setStrokeWidth(1),radius>=16);paint.reset();
        }
    }

    public void glassSurface(float x,float y,float w,float h,float radius,GlassStyle style) {
        requireGlass();
        GlassStyle themed=style.decorations()?style:style.withTint(surfaceColor(style.tint()),style.backgroundOpacity());
        glass.backdrop(x,y,w,h,radius,themed,dark,true);
        glassDecorations(x,y,w,h,radius,style);
    }

    /** CSS backdrop-filter before an element's own, overriding background layers. */
    public void glassBackdrop(float x,float y,float w,float h,float radius,GlassStyle style) {
        requireGlass();glass.backdrop(x,y,w,h,radius,style,dark,false);
    }

    /** CSS sheen and rim above the element background, sharing its continuous contour. */
    public void glassDecorations(float x,float y,float w,float h,float radius,GlassStyle style) {
        requireGlass();
        if(!dark){if(style.decorations())glass.decorations(x,y,w,h,radius,style.alpha(),!style.refraction());return;}
        if(style.decorations()) {
            save();clipRoundRect(x,y,w,h,radius);
            linearGradientRoundRect(x,y,w,h,radius,135,new int[]{0x0ed2e5ff,0x00d2e5ff,0x00d6d0ff,0x05d6d0ff},new float[]{0,.42f,.64f,1});
            if(style.tint()!=0x00ffffff) {
                float dx=.5f,dy=.8660254f,extent=(Math.abs(w*dx)+Math.abs(h*dy))*.5f,cx=x+w*.5f,cy=y+h*.5f;
                try(Shader rim=Shader.makeLinearGradient(cx-dx*extent,cy-dy*extent,cx+dx*extent,cy+dy*extent,
                        new int[]{0x29dce8ff,0x05cddfff,0x03ced5ff,0x14dacfff},new float[]{0,.45f,.6f,1})) {
                    // CSS content-box mask leaves a three-pixel inner rim, never a full fill.
                    corners.draw(canvas(),x,y,w,h,radius,fill(0xffffffff).setShader(rim).setMode(PaintMode.STROKE).setStrokeWidth(6));paint.reset();
                }
                insetBoxShadow(x,y,w,h,radius,0,1,2,0,0x0ddceaff);
                insetBoxShadow(x,y,w,h,radius,0,-1,3,0,0x1a111a33);
            }
            restore();
        }
    }
    public void blurBackdrop(float x,float y,float w,float h,float radius,float blurPx,float alpha) {
        requireGlass(); glass.blur(x,y,w,h,radius,blurPx,alpha);
    }
    public void blurGameScene(float physicalSigma){requireGlass();glass.sceneBlur(physicalSigma);}
    public void captureGlassBackdrop() { requireGlass(); glass.capture(); }
    public void captureGlassBackdrop(float x,float y,float w,float h) { requireGlass();glass.capture(x,y,w,h); }
    public void useGameBackdrop() { requireGlass(); glass.useGame(); }
    public void glassIcon(float x,float y,float size,int top,int bottom,IconPose pose,float alpha) {
        requireGlass(); glass.icon(x,y,size,top,bottom,pose,alpha);
    }
    public static IconPose iconPose(float size,float enter,float hover,float press,float pointerX,float pointerY,boolean reducedMotion) {
        return IconPose.navigation(size,enter,hover,press,pointerX,pointerY,reducedMotion);
    }
    private void requireGlass() {
        canvas(); if(glass==null||targetSurface==null)throw new IllegalStateException("Glass requires an active GPU HubRenderer frame");
    }

    public static Matrix33 cssPerspective(float cx,float cy,float distance,float rx,float ry,float rz,float scale,float tx,float ty) {
        return HubTransforms.perspective(cx,cy,distance,rx,ry,rz,scale,tx,ty);
    }
    public void perspective(float cx,float cy,float distance,float rx,float ry,float rz,float scale,float tx,float ty) {
        canvas().concat(cssPerspective(cx,cy,distance,rx,ry,rz,scale,tx,ty));
    }
    public static float[] inversePoint(Matrix33 transform,float x,float y) { return HubTransforms.inverse(transform,x,y); }

    /** A cropped GPU group, so backdrop snapshots can see content before group opacity is composited. */
    public void saveLayerAlpha(float x,float y,float w,float h,float alpha) {
        requireGlass();
        if(alphaLayers.size()>=8)throw new IllegalStateException("More than eight nested Hub opacity layers");
        if(alpha>=.99999f) {
            int count=canvas.getSaveCount();canvas.save();
            alphaLayers.push(new AlphaLayer(canvas,targetSurface,surfaceOriginX,surfaceOriginY,baseSaveCount,
                null,0,0,1,glass.selection(),count));
            baseSaveCount=canvas.getSaveCount();
            return;
        }
        Matrix33 matrix=canvas.getLocalToDeviceAsMatrix33();
        float[] m=matrix.getMat();
        float padding=96;
        float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX;
        for(float xx:new float[]{x-padding,x+w+padding})for(float yy:new float[]{y-padding,y+h+padding}) {
            float z=m[6]*xx+m[7]*yy+m[8];
            float px=(m[0]*xx+m[1]*yy+m[2])/z,py=(m[3]*xx+m[4]*yy+m[5])/z;
            minX=Math.min(minX,px); minY=Math.min(minY,py); maxX=Math.max(maxX,px); maxY=Math.max(maxY,py);
        }
        int left=Math.max(0,(int)Math.floor(minX)),top=Math.max(0,(int)Math.floor(minY));
        int right=Math.min(targetSurface.getWidth(),(int)Math.ceil(maxX)),bottom=Math.min(targetSurface.getHeight(),(int)Math.ceil(maxY));
        // Empty or fully clipped groups still need a balanced restore.
        int width=Math.max(1,right-left),height=Math.max(1,bottom-top);
        LayerKey key=new LayerKey(alphaLayers.size(),width,height);
        Surface layer=layerPool.get(key);
        if(layer==null) {
            layer=targetSurface.makeSurface(width,height); layerPool.put(key,layer); layerPixels+=(long)width*height;
        }
        Canvas child=layer.getCanvas(); child.restoreToCount(1);child.resetMatrix();child.clear(0);
        child.setMatrix(Matrix33.makeTranslate(-left,-top).makeConcat(matrix));
        alphaLayers.push(new AlphaLayer(canvas,targetSurface,surfaceOriginX,surfaceOriginY,baseSaveCount,
            layer,left,top,Math.max(0,Math.min(1,alpha)),glass.selection(),0));
        canvas=child; targetSurface=layer; surfaceOriginX+=left; surfaceOriginY+=top;
        baseSaveCount=child.getSaveCount(); glass.target(child,layer,surfaceOriginX,surfaceOriginY);
        trimLayerPool();
    }

    private void restoreAlphaLayer() {
        AlphaLayer layer=alphaLayers.pop();
        if(layer.layer==null) {
            canvas.restoreToCount(layer.parentSaveCount);baseSaveCount=layer.baseSaveCount;
            glass.selection(layer.backdrop);return;
        }
        try(Image image=layer.layer.makeImageSnapshot()) {
            canvas=layer.parent; targetSurface=layer.parentSurface;
            surfaceOriginX=layer.originX; surfaceOriginY=layer.originY; baseSaveCount=layer.baseSaveCount;
            int count=canvas.save();
            try { canvas.resetMatrix(); canvas.drawImage(image,layer.x,layer.y,fill(0xFFFFFFFF).setAlphaf(layer.alpha)); }
            finally { canvas.restoreToCount(count); }
        } finally {
            glass.target(canvas,targetSurface,surfaceOriginX,surfaceOriginY); glass.selection(layer.backdrop);
        }
    }

    private void trimLayerPool() {
        var iterator=layerPool.entrySet().iterator();
        while((layerPool.size()>16||layerPixels>16_000_000)&&iterator.hasNext()) {
            var entry=iterator.next(); Surface value=entry.getValue();
            if(alphaLayers.stream().anyMatch(layer->layer.layer==value))continue;
            layerPixels-=(long)value.getWidth()*value.getHeight();value.close();iterator.remove();
        }
    }

    /** CSS brightness on an isolated painted tile; alpha and the scene underneath stay intact. */
    public void saveBrightnessLayer(float x,float y,float w,float h,float brightness){
        try(ColorFilter color=ColorFilter.makeMatrix(new ColorMatrix(brightness,0,0,0,0,0,brightness,0,0,0,0,0,brightness,0,0,0,0,0,1,0));Paint layer=new Paint().setColorFilter(color)){
            canvas().saveLayer(Rect.makeXYWH(x,y,w,h),layer);
        }
    }
    /** Four-point filled star, with the source ClickSpark quadratic pinch. */
    public void sparkStar(float x,float y,float radius,float rotation,int color){
        if(radius<=0)return;StringBuilder svg=new StringBuilder("M").append(x+(float)Math.cos(rotation)*radius).append(',').append(y+(float)Math.sin(rotation)*radius);
        for(int i=1;i<=4;i++){double a=rotation+i*Math.PI/2,mid=a-Math.PI/4;svg.append(" Q").append(x+(float)Math.cos(mid)*radius*.16f).append(',').append(y+(float)Math.sin(mid)*radius*.16f).append(' ').append(x+(float)Math.cos(a)*radius).append(',').append(y+(float)Math.sin(a)*radius);}svg.append(" Z");
        try(Path path=Path.makeFromSVGString(svg.toString())){canvas().drawPath(path,fill(color));}
    }
    public void circle(float cx, float cy, float radius, int color) {
        if (radius > 0) canvas().drawCircle(cx, cy, radius, fill(color));
    }

    public void line(float x1, float y1, float x2, float y2, float stroke, int color) {
        color=dark&&!literalColors?HubTheme.line(color):color;
        if (stroke > 0) canvas().drawLine(x1, y1, x2, y2, fill(color).setStrokeWidth(stroke).setStrokeCap(PaintStrokeCap.ROUND));
    }

    private TextLine textLine(String text, float size, boolean heavy) { return textLine(text, size, heavy, false); }

    private TextLine textLine(String text, float size, boolean heavy, boolean serif) { return textLine(text, size, heavy, serif, false); }

    private TextLine textLine(String text, float size, boolean heavy, boolean serif, boolean cjk) {
        return textLineWeight(text,size,heavy?700:400,serif,cjk);
    }

    private Typeface face(int weight,boolean serif,boolean cjk) {
        if(weight==400)return serif?serifRegular:cjk?cjkRegular:regular;
        if(weight==700)return serif?serifBold:cjk?cjkBold:bold;
        FaceKey key=new FaceKey(weight,serif,cjk);
        Typeface result=weightedFaces.get(key);
        if(result!=null)return result;
        FontStyle style=FontStyle.NORMAL.withWeight(Math.max(1,Math.min(1000,weight)));
        result=serif?serifTypeface(style):cjk?cjkTypeface(style):fontManager.matchFamiliesStyle(BODY_FAMILIES,style);
        if(result==null)throw new IllegalStateException("Cannot match local font weight "+weight);
        weightedFaces.put(key,result);
        if(weightedFaces.size()>64){var iterator=weightedFaces.entrySet().iterator();iterator.next().getValue().close();iterator.remove();}
        return result;
    }

    private TextLine textLineWeight(String text,float size,int weight,boolean serif,boolean cjk) {
        TextKey key = new TextKey(text, size, weight, serif, cjk);
        TextLine existing = lines.get(key);
        if (existing != null) return existing;
        Typeface face = face(weight,serif,cjk);
        Font font = fonts.computeIfAbsent(new FontKey(size, weight, serif, cjk), k ->
            new Font(face, size).setSubpixel(true).setEdging(FontEdging.ANTI_ALIAS)
                .setHinting(FontHinting.SLIGHT).setBaselineSnapped(false));
        // HarfBuzz shaping and FontMgr fallback keep Chinese, punctuation and mixed-script metrics aligned.
        TextLine created = shaper.shapeLine(text, font);
        lines.put(key, created);
        if (fonts.size() > 96) {
            var iterator = fonts.entrySet().iterator();
            iterator.next().getValue().close();
            iterator.remove();
        }
        if (lines.size() > MAX_TEXT_LINES) {
            var iterator = lines.entrySet().iterator();
            iterator.next().getValue().close();
            iterator.remove();
        }
        return created;
    }

    public void text(String text, float x, float baseline, float size, int color, boolean bold) {
        text(text,x,baseline,size,color,bold?700:400);
    }

    public void text(String text,float x,float baseline,float size,int color,int weight) {
        color=foreground(color);
        if (text == null || text.isEmpty() || size <= 0) return;
        for (FontRunText run : sansRuns(text, weight)) {
            TextLine line = textLineWeight(run.text, size, weight, false, run.cjk);
            canvas().drawTextLine(line, x, baseline, fill(color));
            x += line.getWidth();
        }
    }

    public float textWidth(String text, float size, boolean bold) {
        return textWidth(text,size,bold?700:400);
    }

    public float textWidth(String text,float size,int weight) {
        if (text == null || text.isEmpty() || size <= 0) return 0;
        float width = 0;
        for (FontRunText run : sansRuns(text, weight)) width += textLineWeight(run.text, size, weight, false, run.cjk).getWidth();
        return width;
    }

    /** Read-only diagnostic of actual locally matched faces; no font files are copied or distributed. */
    public String fontSummary() {
        return "body400="+regular.getFamilyName()+"/"+regular.getFontStyle().getWeight()
            +", body700="+bold.getFamilyName()+"/"+bold.getFontStyle().getWeight()
            +", cjk400="+cjkRegular.getFamilyName()+"/"+cjkRegular.getFontStyle().getWeight()
            +", poem400="+serifRegular.getFamilyName()+"/"+serifRegular.getFontStyle().getWeight()
            +", poem600="+face(600,true,false).getFamilyName()+"/"+face(600,true,false).getFontStyle().getWeight();
    }

    private List<FontRunText> sansRuns(String text, int weight) {
        RunKey key = new RunKey(text, weight);
        List<FontRunText> cached = sansRuns.get(key);
        if (cached != null) return cached;
        List<FontRunText> result = new ArrayList<>();
        int runStart = 0;
        boolean lastCjk = false;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            GlyphKey glyphKey = new GlyphKey(codePoint,weight);
            boolean cjk = !primaryGlyphs.computeIfAbsent(glyphKey, ignored -> face(weight,false,false).getUTF32Glyph(codePoint) != 0);
            // Keep combining marks and joiners with their preceding shaping run.
            int type = Character.getType(codePoint);
            if (offset > 0 && (type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK || codePoint == 0x200D)) cjk = lastCjk;
            if (offset > 0 && cjk != lastCjk) {
                result.add(new FontRunText(text.substring(runStart, offset), lastCjk));
                runStart = offset;
            }
            lastCjk = cjk;
            offset += Character.charCount(codePoint);
        }
        result.add(new FontRunText(text.substring(runStart), lastCjk));
        sansRuns.put(key, result);
        if (sansRuns.size() > 768) { var iterator = sansRuns.entrySet().iterator(); iterator.next(); iterator.remove(); }
        if (primaryGlyphs.size() > 4096) primaryGlyphs.clear();
        return result;
    }

    /** Local Song/serif typography for fortune poetry; no remote font is requested. */
    public void textSerif(String text, float x, float baseline, float size, int color, boolean bold) {
        textSerif(text,x,baseline,size,color,bold?700:400);
    }
    public void textSerif(String text,float x,float baseline,float size,int color,int weight) {
        color=foreground(color);
        if (text == null || text.isEmpty() || size <= 0) return;
        canvas().drawTextLine(textLineWeight(text, size, weight, true,false), x, baseline, fill(color));
    }

    public float textWidthSerif(String text, float size, boolean bold) {
        return textWidthSerif(text,size,bold?700:400);
    }
    public float textWidthSerif(String text,float size,int weight) {
        if (text == null || text.isEmpty() || size <= 0) return 0;
        return textLineWeight(text, size, weight, true,false).getWidth();
    }

    public float textWidthTracked(String text,float size,int weight,float tracking,boolean serif) {
        if(text==null||text.isEmpty()||size<=0)return 0;
        float width=serif?textWidthSerif(text,size,weight):textWidth(text,size,weight);
        return width+tracking*Math.max(0,text.codePointCount(0,text.length())-1);
    }

    public void textTracked(String text,float x,float baseline,float size,int color,int weight,float tracking,boolean serif) {
        color=foreground(color);
        if(text==null||text.isEmpty()||size<=0)return;
        canvas().drawTextBlob(tracked(text,size,weight,tracking,serif).blob,x,baseline,fill(color));
    }

    /** Minecraft RichText italic is an oblique glyph; bold uses the actual weighted font. */
    public void richGlyph(String text,float x,float baseline,float size,int color,boolean bold,boolean italic,boolean underline,boolean strike) {
        richGlyph(text,x,baseline,size,color,bold,italic,underline,strike,0);
    }

    /** Optional thin glyph contour keeps literal pale provider text readable on light artwork. */
    public void richGlyph(String text,float x,float baseline,float size,int color,boolean bold,boolean italic,boolean underline,boolean strike,int outlineColor) {
        boolean previousLiteral=literalColors;literalColors=true;
        int weight=bold?700:400;float width=textWidth(text,size,weight);
        try {
            canvas().save();
            try {
                canvas().translate(x,baseline);if(italic)canvas().skew(-.2f,0);
                if(outlineColor!=0) {
                    float cursor=0;
                    for(FontRunText run:sansRuns(text,weight)) {
                        TextLine line=textLineWeight(run.text,size,weight,false,run.cjk);
                        canvas().drawTextLine(line,cursor,0,fill(outlineColor).setMode(PaintMode.STROKE)
                            .setStrokeWidth(1.3f).setStrokeJoin(PaintStrokeJoin.ROUND));
                        cursor+=line.getWidth();
                    }
                }
                text(text,0,0,size,color,weight);
            } finally {canvas().restore();}
            float stroke=Math.max(.8f,size/16);
            if(outlineColor!=0) {
                if(underline)line(x,baseline+size*.12f,x+width,baseline+size*.12f,stroke+1.3f,outlineColor);
                if(strike)line(x,baseline-size*.3f,x+width,baseline-size*.3f,stroke+1.3f,outlineColor);
            }
            if(underline)line(x,baseline+size*.12f,x+width,baseline+size*.12f,stroke,color);
            if(strike)line(x,baseline-size*.3f,x+width,baseline-size*.3f,stroke,color);
        } finally {literalColors=previousLiteral;}
    }

    public void textGradient(String text,float x,float baseline,float size,int weight,float tracking,float angleDeg,int[] colors,float[] stops,boolean serif) {
        colors=dark&&!literalColors?HubTheme.gradient(colors):colors;
        if(text==null||text.isEmpty()||size<=0)return;
        float width=textWidthTracked(text,size,weight,tracking,serif),height=size*1.2f;
        double angle=Math.toRadians(angleDeg);float dx=(float)Math.sin(angle),dy=-(float)Math.cos(angle);
        float extent=(Math.abs(width*dx)+Math.abs(height*dy))*.5f,cx=x+width*.5f,cy=baseline-size+height*.5f;
        try(Shader shader=Shader.makeLinearGradient(cx-dx*extent,cy-dy*extent,cx+dx*extent,cy+dy*extent,colors,stops)) {
            canvas().drawTextBlob(tracked(text,size,weight,tracking,serif).blob,x,baseline,fill(0xFFFFFFFF).setShader(shader));paint.reset();
        }
    }

    private TrackedLine tracked(String text,float size,int weight,float tracking,boolean serif) {
        TrackedKey key=new TrackedKey(text,size,weight,tracking,serif);
        TrackedLine existing=trackedLines.get(key);if(existing!=null)return existing;
        List<FontRunText> runs=serif?List.of(new FontRunText(text,false)):sansRuns(text,weight);
        float advance=0;int consumed=0;
        try(TextBlobBuilder builder=new TextBlobBuilder()) {
            for(FontRunText run:runs) {
                TextLine line=textLineWeight(run.text,size,weight,serif,run.cjk);
                short[] glyphs=line.getGlyphs();float[] positions=line.getPositions();Point[] points=new Point[glyphs.length];
                for(int i=0;i<glyphs.length;i++) {
                    int offset=Math.max(0,Math.min(run.text.length(),line.getLeftOffsetAtCoord(positions[i*2]+.001f)));
                    int cluster=run.text.codePointCount(0,offset);
                    points[i]=new Point(advance+positions[i*2]+tracking*(consumed+cluster),positions[i*2+1]);
                }
                try(Font font=new Font(face(weight,serif,run.cjk),size).setSubpixel(true).setEdging(FontEdging.ANTI_ALIAS)
                    .setHinting(FontHinting.SLIGHT).setBaselineSnapped(false)) {
                    builder.appendRunPos(font,glyphs,points);
                }
                advance+=line.getWidth();consumed+=run.text.codePointCount(0,run.text.length());
            }
            TrackedLine result=new TrackedLine(builder.build());trackedLines.put(key,result);
            if(trackedLines.size()>384){var iterator=trackedLines.entrySet().iterator();iterator.next().getValue().blob.close();iterator.remove();}
            return result;
        }
    }

    private java.util.List<String> warmAssets(){
        if(warmAssets!=null)return warmAssets;java.util.LinkedHashSet<String> names=new java.util.LinkedHashSet<>();
        for(String manifest:new String[]{"ASSET-MAPPING.json","UPDATES-ASSET-MAPPING.json"}){
            try(InputStream input=HubDraw.class.getResourceAsStream(ROOT+manifest)){
                if(input==null)continue;
                var object=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for(String group:new String[]{"artwork","icons"})if(object.has(group))for(var item:object.getAsJsonArray(group)){String target=item.getAsJsonObject().get("target").getAsString();if(target.startsWith("textures/")&&target.endsWith(".png"))names.add(target);}
            }catch(IOException error){throw new IllegalStateException("Cannot read bundled public asset list",error);}
        }
        warmAssets=java.util.List.copyOf(names);return warmAssets;
    }
    public int warmAssetCount(){return warmAssets().size();}
    int warmAssetBatch(int start,int maximum){int end=Math.min(warmAssets().size(),start+maximum);for(int i=start;i<end;i++)image(warmAssets().get(i),8+(i-start)%6*29,10+(i-start)/6*29,24,24,1);return end;}
    /** Drop per-panel private strings, server icons, immutable frame copies and large temporary surfaces. */
    public void releasePanelResources(){
        if(canvas!=null||!alphaLayers.isEmpty())throw new IllegalStateException("Cannot suspend during a Hub frame");
        lines.values().forEach(TextLine::close);lines.clear();
        trackedLines.values().forEach(value->value.blob.close());trackedLines.clear();
        sansRuns.clear();primaryGlyphs.clear();
        var runtime=images.entrySet().iterator();while(runtime.hasNext()){var item=runtime.next();if(item.getKey().startsWith("memory:")){item.getValue().close();runtime.remove();}}
        layerPool.values().forEach(Surface::close);layerPool.clear();layerPixels=0;
        headLayers.values().forEach(Surface::close);headLayers.clear();
        if(glass!=null)glass.releaseFrameResources();
        rootWidth=rootHeight=0;
    }

    /** Cached classpath image, borrowed by the caller: do not close it. */
    public Image imageResource(String asset) {
        if (closed) throw new IllegalStateException("HubDraw was closed");
        String path = asset.replace('\\', '/');
        if (path.startsWith("/") || path.contains("..")) throw new IllegalArgumentException("Asset must be relative to " + ROOT);
        return images.computeIfAbsent(path, name -> {
            try (InputStream stream = HubDraw.class.getResourceAsStream(ROOT + name)) {
                if (stream == null) throw new IllegalArgumentException("Missing Hub image: " + name);
                return Image.makeFromEncoded(stream.readAllBytes());
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot decode Hub image " + name, exception);
            }
        });
    }

    public void image(String asset, float x, float y, float w, float h, float alpha) {
        if (w <= 0 || h <= 0 || alpha <= 0) return;
        Image image = imageResource(asset);
        canvas().drawImageRect(image, Rect.makeWH(image.getWidth(), image.getHeight()), Rect.makeXYWH(x, y, w, h),
            asset.equals("textures/ui/fortune-barrel.png") || asset.equals("textures/ui/collection-chest.png") ? SamplingMode.DEFAULT : SMOOTH_SAMPLING,
            fill(0xFFFFFFFF).setAlphaf(Math.min(1, alpha)), false);
    }
    public void serverIcon(byte[] png,float x,float y,float size) {
        if(png==null||png.length<24||png.length>65536)return;
        java.nio.ByteBuffer header=java.nio.ByteBuffer.wrap(png);
        if(header.getLong()!=0x89504e470d0a1a0aL||header.getInt(16)!=64||header.getInt(20)!=64)return;
        Image image=images.computeIfAbsent("memory:server-icon",ignored->Image.makeFromEncoded(png));
        canvas().drawImageRect(image,Rect.makeWH(64,64),Rect.makeXYWH(x,y,size,size),SMOOTH_SAMPLING,fill(0xffffffff),false);
    }

    public void icon(String name, float x, float y, float size, int color) {
        color=foreground(color);
        if (size <= 0) return;
        if(name.equals("personal")||name.endsWith("/personal"))name=name.substring(0,name.length()-8)+"user-round";
        Image image = imageResource("textures/icons/" + name + ".png");
        try (ColorFilter tint = ColorFilter.makeBlend(color, BlendMode.SRC_IN)) {
            canvas().drawImageRect(image, Rect.makeWH(image.getWidth(), image.getHeight()), Rect.makeXYWH(x, y, size, size),
                // Full-image fast constraint permits Skia's GPU mip chain. Strict source-rect sampling
                // disables mipmaps and aliases 192px thin-stroke icons at 12-17px display sizes.
                SMOOTH_SAMPLING, fill(0xFFFFFFFF).setColorFilter(tint), false);
            paint.setColorFilter(null);
        }
    }

    /** Device-pixel supersampling keeps geometric edges smooth at every GUI/DPI scale. */
    public boolean supersample(float x,float y,float width,float height,int factor,Runnable content) {
        if(supersampling||targetSurface==null||width<=0||height<=0||factor<2||factor>3)return false;
        Matrix33 transform=canvas.getLocalToDeviceAsMatrix33();
        float[] matrix=transform.getMat();
        float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX;
        for(float px:new float[]{x,x+width})for(float py:new float[]{y,y+height}) {
            float z=matrix[6]*px+matrix[7]*py+matrix[8];
            if(!Float.isFinite(z)||z<=.0001f)return false;
            float deviceX=(matrix[0]*px+matrix[1]*py+matrix[2])/z;
            float deviceY=(matrix[3]*px+matrix[4]*py+matrix[5])/z;
            minX=Math.min(minX,deviceX);maxX=Math.max(maxX,deviceX);
            minY=Math.min(minY,deviceY);maxY=Math.max(maxY,deviceY);
        }
        int left=Math.max(0,(int)Math.floor(minX)),top=Math.max(0,(int)Math.floor(minY));
        int deviceWidth=Math.min(targetSurface.getWidth(),(int)Math.ceil(maxX))-left;
        int deviceHeight=Math.min(targetSurface.getHeight(),(int)Math.ceil(maxY))-top;
        if(deviceWidth<=0||deviceHeight<=0)return false;
        int pixelsWide=deviceWidth*factor,pixelsHigh=deviceHeight*factor;
        if(pixelsWide>1536||pixelsHigh>1536)return false;
        int pooledWidth=(pixelsWide+15)/16*16,pooledHeight=(pixelsHigh+15)/16*16;
        String key=pooledWidth+"x"+pooledHeight;
        Surface layer=headLayers.get(key);
        if(layer==null) {
            try{layer=targetSurface.makeSurface(pooledWidth,pooledHeight);}catch(RuntimeException unavailable){return false;}
            if(layer==null)return false;
            headLayers.put(key,layer);
            while(headLayers.size()>8){var iterator=headLayers.entrySet().iterator();var old=iterator.next();iterator.remove();old.getValue().close();}
        }
        Canvas parent=canvas;Surface parentSurface=targetSurface;int parentBase=baseSaveCount;
        Canvas child=layer.getCanvas();child.restoreToCount(1);child.resetMatrix();child.clear(0);
        child.scale(factor,factor);child.translate(-left,-top);child.concat(transform);
        supersampling=true;canvas=child;targetSurface=layer;baseSaveCount=child.getSaveCount();
        try{content.run();}
        finally{canvas=parent;targetSurface=parentSurface;baseSaveCount=parentBase;supersampling=false;}
        try(Image snapshot=layer.makeImageSnapshot()) {
            int saved=parent.save();
            try {
                parent.resetMatrix();
                parent.drawImageRect(snapshot,Rect.makeWH(pixelsWide,pixelsHigh),Rect.makeXYWH(left,top,deviceWidth,deviceHeight),SMOOTH_SAMPLING,fill(0xffffffff),false);
            } finally {parent.restoreToCount(saved);}
        }
        return true;
    }

    public void save() { canvas().save(); }
    public void restore() {
        if (canvas().getSaveCount() <= baseSaveCount) {
            if(!alphaLayers.isEmpty()) { restoreAlphaLayer(); return; }
            throw new IllegalStateException("Unbalanced HubDraw.restore");
        }
        canvas.restore();
    }
    public void translate(float x, float y) { canvas().translate(x, y); }
    public void clipRoundRect(float x, float y, float w, float h, float radius) {
        corners.clip(canvas(),x,y,Math.max(0,w),Math.max(0,h),radius);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        corners.close();headLayers.values().forEach(Surface::close);headLayers.clear();
        canvas = null;
        lines.values().forEach(TextLine::close);
        fonts.values().forEach(Font::close);
        images.values().forEach(Image::close);
        if(glass!=null)glass.close();
        layerPool.values().forEach(Surface::close); layerPool.clear(); layerPixels=0;
        weightedFaces.values().forEach(Typeface::close);weightedFaces.clear();
        trackedLines.values().forEach(value->value.blob.close());trackedLines.clear();
        lines.clear(); fonts.clear(); images.clear(); sansRuns.clear(); primaryGlyphs.clear();
        paint.close(); shaper.close(); bold.close(); regular.close(); serifBold.close(); serifRegular.close(); cjkRegular.close(); cjkBold.close();
        // FontMgr.getDefault() is a borrowed singleton and must remain alive for the process.
    }

    private record FontKey(float size, int weight, boolean serif, boolean cjk) {}
    private record TextKey(String text, float size, int weight, boolean serif, boolean cjk) {}
    private record RunKey(String text, int weight) {}
    private record FaceKey(int weight,boolean serif,boolean cjk) {}
    private record GlyphKey(int codePoint,int weight) {}
    private record FontRunText(String text, boolean cjk) {}
    private record AlphaLayer(Canvas parent,Surface parentSurface,float originX,float originY,int baseSaveCount,
                              Surface layer,int x,int y,float alpha,HubGlass.Backdrop backdrop,int parentSaveCount) {}
    private record LayerKey(int depth,int width,int height) {}
    private record TrackedKey(String text,float size,int weight,float tracking,boolean serif) {}
    private record TrackedLine(TextBlob blob) {}
}
