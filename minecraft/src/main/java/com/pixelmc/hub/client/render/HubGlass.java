/*
 * GlassSurface displacement construction and GlassIcons shader/pose are adapted from
 * src/vendor/react-bits/{GlassSurface,GlassIcons}.{jsx,css}, copyright David Haz (2026).
 * These adapted portions retain MIT + Commons Clause, NOT Apache-2.0. Full license:
 * assets/pixelmc_hub/licenses/React-Bits-LICENSE.md. The GPU snapshot/Skija bridge is
 * original Apache-2.0 application integration. This is not a standalone component product.
 */
package com.pixelmc.hub.client.render;

import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.IRect;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** GPU-only backdrop sampling. Every child shader samples an immutable snapshot, never its draw attachment. */
final class HubGlass implements AutoCloseable {
    private static final int MAX_CAPTURES = 32;
    private static final long MAX_CAPTURE_PIXELS=16_000_000;
    private final Paint paint = new Paint();
    private final RuntimeEffect surfaceEffect = RuntimeEffect.makeForShader(SURFACE_SHADER);
    private final RuntimeEffect iconEffect = RuntimeEffect.makeForShader(ICON_SHADER);
    private final RuntimeEffect decorationEffect = RuntimeEffect.makeForShader(DECORATION_SHADER);
    private final Map<MapKey, Image> maps = new LinkedHashMap<>(8, .75f, true);
    private long mapPixels;
    private final List<Backdrop> frameBackdrops = new ArrayList<>();
    private Canvas canvas;
    private Surface surface;
    private Backdrop game;
    private Backdrop current;
    private int captures;
    private float originX, originY;
    private long capturePixels;
    private boolean budgetWarning;
    private final Map<FilterSize,Surface> filterPool=new LinkedHashMap<>(8,.75f,true);
    private long filterPixels;
    private int frameWidth,frameHeight;

    void begin(Canvas value, Surface target) {
        if(frameWidth!=target.getWidth()||frameHeight!=target.getHeight()) {
            filterPool.values().forEach(Surface::close);filterPool.clear();filterPixels=0;
            maps.values().forEach(Image::close);maps.clear();mapPixels=0;
            frameWidth=target.getWidth();frameHeight=target.getHeight();
        }
        canvas = value; surface = target; captures = 0; capturePixels=0; originX = 0; originY = 0;
        game = new Backdrop(target.makeImageSnapshot(), 0, 0);
        frameBackdrops.add(game); current = game;
    }
    void capture() {
        if (surface == null) throw new IllegalStateException("No active Hub GPU frame");
        long pixels=(long)surface.getWidth()*surface.getHeight();
        if(!allowCapture(pixels))return;
        captures++;capturePixels+=pixels;
        current = new Backdrop(surface.makeImageSnapshot(), originX, originY);
        frameBackdrops.add(current);
    }
    void capture(float x,float y,float width,float height) {
        if(surface==null)throw new IllegalStateException("No active Hub GPU frame");
        float[] m=canvas.getLocalToDeviceAsMatrix33().getMat();
        float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX;
        // The sample halo covers CSS blur kernels and the maximum default refraction reach.
        for(float xx:new float[]{x-96,x+width+96})for(float yy:new float[]{y-96,y+height+96}) {
            float z=m[6]*xx+m[7]*yy+m[8],px=(m[0]*xx+m[1]*yy+m[2])/z,py=(m[3]*xx+m[4]*yy+m[5])/z;
            minX=Math.min(minX,px);minY=Math.min(minY,py);maxX=Math.max(maxX,px);maxY=Math.max(maxY,py);
        }
        int l=Math.max(0,(int)Math.floor(minX)),t=Math.max(0,(int)Math.floor(minY));
        int r=Math.min(surface.getWidth(),(int)Math.ceil(maxX)),b=Math.min(surface.getHeight(),(int)Math.ceil(maxY));
        if(r<=l||b<=t)return;
        long pixels=(long)(r-l)*(b-t);
        if(!allowCapture(pixels))return;
        Image image=surface.makeImageSnapshot(IRect.makeLTRB(l,t,r,b));
        captures++;capturePixels+=pixels;
        current=new Backdrop(image,originX+l,originY+t);frameBackdrops.add(current);
    }
    void useGame() { current = game; }
    /** Reuses the existing game snapshot and filter pool. Only 1/16-resolution pixels are filtered. */
    void sceneBlur(float physicalSigma) {
        if(game==null||physicalSigma<=0)return;
        int width=Math.max(1,(game.image.getWidth()+3)/4),height=Math.max(1,(game.image.getHeight()+3)/4);
        if(game.sceneBlur==null) {
            Surface filtered=filterTarget(width,height);
            float sigma=Math.max(.25f,physicalSigma/4);
            try(ImageFilter blur=ImageFilter.makeBlur(sigma,sigma,FilterTileMode.CLAMP);Paint filter=new Paint().setImageFilter(blur)) {
                Canvas reduced=filtered.getCanvas();reduced.clear(0);
                reduced.drawImageRect(game.image,Rect.makeWH(game.image.getWidth(),game.image.getHeight()),Rect.makeWH(width,height),SamplingMode.LINEAR,filter,false);
                game.sceneBlur=filtered.makeImageSnapshot();
            }
        }
        int save=canvas.save();
        try {canvas.resetMatrix();canvas.drawImageRect(game.sceneBlur,Rect.makeWH(width,height),Rect.makeWH(game.image.getWidth(),game.image.getHeight()),SamplingMode.LINEAR,paint.reset(),false);}
        finally {canvas.restoreToCount(save);paint.reset();}
    }
    private boolean allowCapture(long pixels) {
        if(captures<MAX_CAPTURES&&capturePixels+pixels<=MAX_CAPTURE_PIXELS)return true;
        if(!budgetWarning) {
            budgetWarning=true;
            System.getLogger(HubGlass.class.getName()).log(System.Logger.Level.WARNING,
                "Hub glass backdrop budget reached; retaining the previous immutable snapshot. Use component ROI captures.");
        }
        return false;
    }
    void target(Canvas value, Surface target, float x, float y) { canvas=value; surface=target; originX=x; originY=y; }
    Backdrop selection() { return current; }
    void selection(Backdrop value) { current=value; }
    void end() {
        frameBackdrops.forEach(Backdrop::close); frameBackdrops.clear();
        canvas = null; surface = null; current = null; game = null;
    }

    void surface(float x, float y, float w, float h, float radius, GlassStyle style) {
        if (w <= 0 || h <= 0 || style.alpha() <= 0) return;
        boolean refractive = style.refraction();
        float sigma = refractive ? 0 : style.backdropBlur();
        float saturation = style.saturation() * (!refractive && style.decorations() ? 1.4f : 1);
        float brightness = !refractive && style.decorations() ? 1.04f : 1;
        Sample input = blurred(sigma, x, y, w, h);
        Image mapImage=refractive?displacement(w,h,radius,style):null;
        try (Shader backdrop = input.image.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR, Matrix33.IDENTITY);
             Shader map = refractive ? mapImage.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP, SamplingMode.LINEAR,
                 Matrix33.makeScale(w/mapImage.getWidth(),h/mapImage.getHeight())) : null;
             RuntimeEffectBuilder builder = new RuntimeEffectBuilder(surfaceEffect)) {
            builder.setChild("backdrop", backdrop);
            // Skia requires all declared children to be bound, even when a uniform disables a branch.
            builder.setChild("displacement", map != null ? map : backdrop);
            builder.setUniform("rect", x, y, w, h);
            Matrix33 sampleTransform = Matrix33.makeTranslate(originX-input.originX, originY-input.originY)
                .makeConcat(canvas.getLocalToDeviceAsMatrix33());
            float[] m=sampleTransform.getMat();
            // SkMatrix is row-major, while SkSL matrix uniform storage is column-major.
            builder.setUniform("localToDevice",new float[]{m[0],m[3],m[6],m[1],m[4],m[7],m[2],m[5],m[8]});
            builder.setUniform("scales", style.distortionScale() + style.redOffset(), style.distortionScale() + style.greenOffset(), style.distortionScale() + style.blueOffset());
            builder.setUniform("refractive", refractive ? 1 : 0);
            builder.setUniform("tone", saturation, brightness);
            float frost = refractive || !style.decorations() ? style.backgroundOpacity() : .12f + style.backgroundOpacity() * .5f;
            int tint = style.tint();
            builder.setUniform("tint", ((tint >> 16) & 255)/255f, ((tint >> 8) & 255)/255f, (tint & 255)/255f,
                frost * ((tint >>> 24) / 255f));
            builder.setUniform("opacity", Math.min(1, style.alpha()));
            int count = canvas.save();
            canvas.clipRRect(RRect.makeXYWH(x, y, w, h, radius), true);
            try (Shader shader = builder.makeShader();
                 ImageFilter blur = refractive && style.displace() > 0 ? ImageFilter.makeBlur(style.displace(), style.displace(), FilterTileMode.CLAMP) : null) {
                paint.reset().setAntiAlias(true).setShader(shader).setImageFilter(blur);
                canvas.drawRect(Rect.makeXYWH(x, y, w, h), paint);
                paint.reset();
            } finally { canvas.restoreToCount(count); }
        }
        if (style.decorations()) decorations(x, y, w, h, radius, style.alpha(), !refractive);
    }

    void blur(float x, float y, float w, float h, float radius, float sigma, float alpha) {
        GlassStyle plain = new GlassStyle(0, 50, 0, 0, 0, 0, 1, 0,
            0, 0, 0, false, 0, alpha, sigma, false);
        surface(x, y, w, h, radius, plain);
    }

    private Sample blurred(float logicalSigma, float x,float y,float width,float height) {
        if (current == null) throw new IllegalStateException("Glass helpers require HubRenderer's active GPU frame");
        if (logicalSigma <= .01f) return new Sample(current.image,current.originX,current.originY);
        Matrix33 ctm=canvas.getLocalToDeviceAsMatrix33();float[] m=ctm.getMat();
        float sigma = logicalSigma * deviceScale(ctm, x+width/2, y+height/2);
        // 1/64 physical pixel quantization prevents redundant filter images for equivalent transforms.
        float quantized = Math.max(1f / 64, Math.round(sigma * 64) / 64f);
        float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX;
        for(float xx:new float[]{x,x+width})for(float yy:new float[]{y,y+height}) {
            float z=m[6]*xx+m[7]*yy+m[8];
            float px=(m[0]*xx+m[1]*yy+m[2])/z+originX-current.originX;
            float py=(m[3]*xx+m[4]*yy+m[5])/z+originY-current.originY;
            minX=Math.min(minX,px);minY=Math.min(minY,py);maxX=Math.max(maxX,px);maxY=Math.max(maxY,py);
        }
        int pad=(int)Math.ceil(quantized*3)+2;
        int left=Math.max(0,(int)Math.floor(minX)-pad),top=Math.max(0,(int)Math.floor(minY)-pad);
        int right=Math.min(current.image.getWidth(),(int)Math.ceil(maxX)+pad),bottom=Math.min(current.image.getHeight(),(int)Math.ceil(maxY)+pad);
        if(right<=left||bottom<=top)return new Sample(current.image,current.originX,current.originY);
        BlurKey key = new BlurKey(quantized,left,top,right,bottom);
        Sample existing = current.blurImages.get(key);
        if (existing != null) return existing;
        Surface filtered=filterTarget(right-left,bottom-top);
        try (ImageFilter blur = ImageFilter.makeBlur(quantized, quantized, FilterTileMode.CLAMP);
             Paint filterPaint = new Paint().setImageFilter(blur)) {
            filtered.getCanvas().clear(0);
            filtered.getCanvas().drawImage(current.image, -left, -top, filterPaint);
            Sample result = new Sample(filtered.makeImageSnapshot(),current.originX+left,current.originY+top);
            current.blurImages.put(key, result);
            if (current.blurImages.size() > 6) {
                var iterator = current.blurImages.entrySet().iterator(); iterator.next().getValue().image.close(); iterator.remove();
            }
            return result;
        }
    }

    private Surface filterTarget(int width,int height) {
        FilterSize key=new FilterSize(width,height);
        Surface result=filterPool.get(key);
        if(result!=null)return result;
        result=surface.makeSurface(width,height);filterPool.put(key,result);filterPixels+=(long)width*height;
        var iterator=filterPool.entrySet().iterator();
        while((filterPool.size()>12||filterPixels>8_000_000)&&filterPool.size()>1&&iterator.hasNext()) {
            Surface old=iterator.next().getValue();if(old==result)continue;
            filterPixels-=(long)old.getWidth()*old.getHeight();old.close();iterator.remove();
        }
        return result;
    }

    private Image displacement(float w, float h, float radius, GlassStyle style) {
        MapKey key = new MapKey(w, h, radius, style.borderWidth(), style.brightness(), style.opacity(), style.blur());
        Image existing = maps.get(key);
        if (existing != null) return existing;
        int width = Math.max(2, (int) Math.ceil(w)), height = Math.max(2, (int) Math.ceil(h));
        try (Surface target = surface.makeSurface(width, height); Paint p = new Paint().setAntiAlias(true)) {
            Canvas c = target.getCanvas(); c.clear(0xFF000000);
            c.scale(width / w, height / h);
            RRect bounds = RRect.makeXYWH(0, 0, w, h, radius);
            try (Shader red = Shader.makeLinearGradient(w, 0, 0, 0, new int[]{0x00FF0000, 0xFFFF0000});
                 Shader blue = Shader.makeLinearGradient(0, 0, 0, h, new int[]{0x000000FF, 0xFF0000FF});
                 ImageFilter blur = ImageFilter.makeBlur(Math.max(0, style.blur()), Math.max(0, style.blur()), FilterTileMode.DECAL)) {
                c.drawRRect(bounds, p.setShader(red));
                c.drawRRect(bounds, p.setShader(blue).setBlendMode(BlendMode.DIFFERENCE));
                int level = Math.round(Math.max(0, Math.min(100, style.brightness())) * 2.55f);
                int color = Math.round(Math.max(0, Math.min(1, style.opacity())) * 255) << 24 | level << 16 | level << 8 | level;
                float edge = Math.min(w, h) * style.borderWidth() * .5f;
                c.drawRRect(RRect.makeXYWH(edge, edge, Math.max(0, w - edge * 2), Math.max(0, h - edge * 2), radius),
                    p.reset().setAntiAlias(true).setColor(color).setImageFilter(blur));
            }
            Image result = target.makeImageSnapshot(); maps.put(key, result); mapPixels += (long)width * height;
            while ((maps.size() > 12 || mapPixels > 8_000_000) && maps.size() > 1) {
                var iterator = maps.entrySet().iterator(); Image old = iterator.next().getValue();
                mapPixels -= (long)old.getWidth() * old.getHeight(); old.close(); iterator.remove();
            }
            return result;
        }
    }

    private void decorations(float x, float y, float w, float h, float radius, float alpha, boolean frosted) {
        try (RuntimeEffectBuilder builder = new RuntimeEffectBuilder(decorationEffect)) {
            builder.setUniform("rect", x, y, w, h).setUniform("radius", radius).setUniform("opacity", alpha)
                .setUniform("frosted", frosted ? 1 : 0);
            try (Shader shader = builder.makeShader()) {
                canvas.drawRRect(RRect.makeXYWH(x, y, w, h, radius), paint.reset().setAntiAlias(true).setShader(shader));
                paint.reset();
            }
        }
    }

    void icon(float x, float y, float size, int top, int bottom, IconPose pose, float alpha) {
        try (RuntimeEffectBuilder b = new RuntimeEffectBuilder(iconEffect)) {
            b.setUniform("center", x + size/2, y + size/2).setUniform("size", size)
                // Float uniforms must use the float overload: integer overloads write raw integer bits.
                .setUniform("radius", (float)Math.round(size * .5f * .65f)).setUniform("bevel", (float)Math.max(2, Math.round(size * .26f * .6f)))
                .setUniform("strength", size * .3f * .8f * pose.bend()).setUniform("split", 0f)
                .setUniform("soft", 1 + .1f * size * .15f).setUniform("shine", .9f * pose.glow()).setUniform("shade", .22f)
                .setUniform("light", pose.lightX(), pose.lightY()).setUniform("shift", pose.shiftX(), pose.shiftY())
                .setUniform("offset", pose.offsetX(), pose.offsetY()).setUniform("plateScale", pose.plateScale())
                .setUniform("lift", 0, pose.liftY()).setUniform("glassScale", pose.glassScale())
                .setUniform("glassAlpha", pose.glassAlpha()).setUniform("plateAlpha", pose.plateAlpha())
                .setUniform("pixel", 1 / deviceScale(canvas.getLocalToDeviceAsMatrix33(), x + size/2, y + size/2))
                .setUniform("opacity", alpha);
            double turn = Math.toRadians(-pose.angle());
            b.setUniform("turn", (float)Math.cos(turn), (float)Math.sin(turn));
            b.setUniform("top", channel(top,16), channel(top,8), channel(top,0));
            b.setUniform("bottom", channel(bottom,16), channel(bottom,8), channel(bottom,0));
            try (Shader shader = b.makeShader()) {
                float extent = size * 1.08f;
                canvas.drawRect(Rect.makeXYWH(x + size/2 - extent, y + size/2 - extent, extent*2, extent*2),
                    paint.reset().setAntiAlias(true).setShader(shader)); paint.reset();
            }
        }
    }

    static float deviceScale(Matrix33 matrix, float x, float y) {
        float[] m = matrix.getMat();
        float w = m[6]*x + m[7]*y + m[8];
        if (Math.abs(w) < .0001f) return 1;
        float nx = m[0]*x+m[1]*y+m[2], ny=m[3]*x+m[4]*y+m[5];
        double dx = Math.hypot((m[0]*w-nx*m[6])/(w*w), (m[3]*w-ny*m[6])/(w*w));
        double dy = Math.hypot((m[1]*w-nx*m[7])/(w*w), (m[4]*w-ny*m[7])/(w*w));
        return Math.max(.01f, (float)Math.sqrt(dx*dy));
    }
    private static float channel(int color, int shift) { return ((color >> shift) & 255) / 255f; }
    @Override public void close() {
        end(); maps.values().forEach(Image::close); maps.clear();
        filterPool.values().forEach(Surface::close);filterPool.clear();filterPixels=0;
        paint.close(); surfaceEffect.close(); iconEffect.close(); decorationEffect.close();
    }
    static final class Backdrop implements AutoCloseable {
        Image sceneBlur;
        final Image image;
        final float originX, originY;
        final Map<BlurKey, Sample> blurImages = new LinkedHashMap<>();
        Backdrop(Image value, float x, float y) { image = value; originX=x; originY=y; }
        public void close() { blurImages.values().forEach(value->value.image.close()); blurImages.clear();if(sceneBlur!=null){sceneBlur.close();sceneBlur=null;} image.close(); }
    }
    private record Sample(Image image,float originX,float originY) {}
    private record BlurKey(float sigma,int left,int top,int right,int bottom) {}
    private record FilterSize(int width,int height) {}
    private record MapKey(float width,float height,float radius,float borderWidth,float brightness,float opacity,float blur) {}

    private static final String SURFACE_SHADER = """
        uniform shader backdrop;
        uniform shader displacement;
        uniform float4 rect;
        uniform float3x3 localToDevice;
        uniform float3 scales;
        uniform int refractive;
        uniform float2 tone;
        uniform float4 tint;
        uniform float opacity;
        float2 project(float2 p) { float3 q=localToDevice*float3(p,1); return q.xy/q.z; }
        half4 main(float2 p) {
            half4 c;
            if (refractive != 0) {
                float2 shift=float2(displacement.eval(p-rect.xy).rg)-.5;
                half4 r=backdrop.eval(project(p+shift*scales.r));
                half4 g=backdrop.eval(project(p+shift*scales.g));
                half4 b=backdrop.eval(project(p+shift*scales.b));
                c=half4(r.r,g.g,b.b,(r.a+g.a+b.a)/3);
            } else { c=backdrop.eval(project(p)); }
            float3 rgb=c.a>0 ? float3(c.rgb)/float(c.a) : float3(0);
            float luma=dot(rgb,float3(.213,.715,.072));
            rgb=clamp(mix(float3(luma),rgb,tone.x)*tone.y,0,1);
            float outAlpha=float(c.a)+(1-float(c.a))*tint.a;
            float3 premul=rgb*float(c.a)*(1-tint.a)+tint.rgb*tint.a;
            return half4(premul,outAlpha)*half(opacity);
        }
        """;

    private static final String DECORATION_SHADER = """
        uniform float4 rect;
        uniform float radius;
        uniform float opacity;
        uniform int frosted;
        float sdf(float2 p,float2 halfSize,float r) {
            float2 q=abs(p)-(halfSize-r); return length(max(q,0))+min(max(q.x,q.y),0)-r;
        }
        half4 main(float2 p) {
            float2 v=p-rect.xy;
            float inside=max(0.,-sdf(v-rect.zw*.5,rect.zw*.5,radius));
            float t=clamp((v.x+v.y)/(rect.z+rect.w),0,1);
            float sheen=frosted!=0 ? (t<.42 ? mix(.42,0.,t/.42) : t>.64 ? mix(0.,.18,(t-.64)/.36) : 0.) : 0.;
            float tr=clamp((dot(v-rect.zw*.5,float2(.5,.8660254)))/dot(rect.zw,float2(.5,.8660254))+.5,0,1);
            float rim=frosted!=0 && inside<3 ? (tr<.45 ? mix(.7,.1,tr/.45) : tr<.6 ? mix(.1,.08,(tr-.45)/.15) : mix(.08,.42,(tr-.6)/.4)) : 0.;
            // Container box-shadow is intentionally supplied by the UI's higher-specificity CSS port.
            float white=(1-(1-sheen)*(1-rim))*opacity;
            return half4(float4(white));
        }
        """;

    private static final String ICON_SHADER = """
        uniform float2 center;
        uniform float size;
        uniform float radius;
        uniform float bevel;
        uniform float strength;
        uniform float split;
        uniform float soft;
        uniform float shine;
        uniform float shade;
        uniform float2 light;
        uniform float2 turn;
        uniform float2 shift;
        uniform float2 offset;
        uniform float plateScale;
        uniform float2 lift;
        uniform float glassScale;
        uniform float glassAlpha;
        uniform float plateAlpha;
        uniform float pixel;
        uniform float3 top;
        uniform float3 bottom;
        uniform float opacity;
        float roundBox(float2 p,float reach,float r) {
            float2 q=abs(p)-float2(reach-r); return length(max(q,0))+min(max(q.x,q.y),0)-r;
        }
        float2 roundNormal(float2 p,float reach,float r) {
            float2 q=abs(p)-float2(reach-r);
            float2 s=float2(p.x<0?-1:1,p.y<0?-1:1);
            if(q.x>0&&q.y>0)return normalize(q)*s;
            return q.x>q.y?float2(s.x,0):float2(0,s.y);
        }
        float4 plate(float2 p,float softness) {
            float2 origin=float2(size*.5);
            float2 q=(p-origin-shift)/plateScale;
            q=float2(turn.x*q.x-turn.y*q.y,turn.y*q.x+turn.x*q.y)+origin-offset;
            float d=roundBox(q,size*.5,radius);
            float a=clamp(.5-d/softness,0,1);
            float2 direction=float2(.70710678,.70710678);
            float t=clamp(dot(q,direction)/(size*1.41421356)+.5,0,1);
            return float4(mix(top,bottom,t)*a,a);
        }
        half4 main(float2 coord) {
            float2 p=coord-center;
            float reach=size*.5;
            float2 g=(p-lift)/glassScale;
            float dg=roundBox(g,reach,radius);
            float glass=clamp(.5-dg*glassScale/pixel,0,1)*glassAlpha;
            float4 color=plate(p,pixel)*plateAlpha;
            if(color.a>0) {
                float drop=roundBox(g-float2(0,size*.1),reach,radius);
                color.rgb*=1-(1-smoothstep(-size*.12,size*.14,drop))*.28*glassAlpha;
            }
            if(glass>0) {
                float inside=max(0.,-dg);
                float t=1-clamp(inside/bevel,0,1);
                float2 n=roundNormal(g,reach,radius);
                float2 bend=n*pow(t,2.2);
                float4 r=plate(p-bend*(strength+split),soft);
                float4 green=plate(p-bend*strength,soft);
                float4 b=plate(p-bend*(strength-split),soft);
                float4 seen=float4(r.r,green.g,b.b,green.a)*plateAlpha;
                float luma=dot(seen.rgb,float3(.2126,.7152,.0722));
                seen.rgb=clamp(mix(float3(luma),seen.rgb,1.25)*1.04,0,1);
                seen.rgb=min(mix(seen.rgb,float3(seen.a),.16),float3(seen.a));
                float facing=dot(n,light);
                float rim=clamp(inside/1.2,0,1);
                float dark=(max(-facing,0.)*.75+.25)*t*t*rim*shade;
                float lit=clamp((max(facing,0.)*.95+max(-facing,0.)*.35)*t*t*t*rim*shine,0,1);
                seen=seen*(1-dark)+float4(0,0,0,dark);
                seen=seen*(1-lit)+float4(lit);
                color=mix(color,seen,glass);
            }
            return half4(color*opacity);
        }
        """;
}
