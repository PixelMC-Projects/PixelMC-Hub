/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.render;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.types.RRect;
import java.util.LinkedHashMap;
import java.util.Map;

/** CSS superellipse(1.35) for large surfaces, with the same path used for fill and clipping. */
final class HubCorners implements AutoCloseable {
    private record Bounds(float x,float y,float width,float height,float radius) {}
    private final Map<Bounds,Path> paths=new LinkedHashMap<>(32,.75f,true);
    private static final double POWER=2/Math.pow(2,1.35);

    private Path path(float x,float y,float width,float height,float radius) {
        float r=Math.min(radius,Math.min(width,height)*.5f);
        Bounds key=new Bounds(x,y,width,height,r);
        Path cached=paths.get(key);
        if(cached!=null)return cached;
        try(PathBuilder builder=new PathBuilder()) {
            builder.moveTo(x+r,y);builder.lineTo(x+width-r,y);
            for(int corner=0;corner<4;corner++) {
                float cx=x+(corner<2?width-r:r);
                float cy=y+(corner==0||corner==3?r:height-r);
                for(int step=1;step<=32;step++) {
                    double angle=(corner+step/32.0)*Math.PI*.5;
                    float px=(float)(cx+r*Math.copySign(Math.pow(Math.abs(Math.sin(angle)),POWER),Math.sin(angle)));
                    float py=(float)(cy-r*Math.copySign(Math.pow(Math.abs(Math.cos(angle)),POWER),Math.cos(angle)));
                    builder.lineTo(px,py);
                }
                if(corner==0)builder.lineTo(x+width,y+height-r);
                if(corner==1)builder.lineTo(x+r,y+height);
                if(corner==2)builder.lineTo(x,y+r);
            }
            builder.closePath();cached=builder.build();
        }
        paths.put(key,cached);
        if(paths.size()>128){var iterator=paths.entrySet().iterator();var old=iterator.next();iterator.remove();old.getValue().close();}
        return cached;
    }

    void draw(Canvas canvas,float x,float y,float width,float height,float radius,Paint paint) {
        draw(canvas,x,y,width,height,radius,paint,radius>=16);
    }
    void draw(Canvas canvas,float x,float y,float width,float height,float radius,Paint paint,boolean continuous) {
        if(!continuous){canvas.drawRRect(RRect.makeXYWH(x,y,width,height,radius),paint);return;}
        // Paint shaders are defined in the caller's coordinates, just like drawRRect.
        canvas.drawPath(path(x,y,width,height,radius),paint);
    }
    void clip(Canvas canvas,float x,float y,float width,float height,float radius) {
        clip(canvas,x,y,width,height,radius,ClipMode.INTERSECT);
    }
    void clip(Canvas canvas,float x,float y,float width,float height,float radius,ClipMode mode) {
        if(radius<16){canvas.clipRRect(RRect.makeXYWH(x,y,width,height,radius),mode,true);return;}
        canvas.clipPath(path(x,y,width,height,radius),mode,true);
    }
    void append(PathBuilder builder,float x,float y,float width,float height,float radius,boolean continuous) {
        if(continuous)builder.addPath(path(x,y,width,height,radius));
        else builder.addRRect(RRect.makeXYWH(x,y,width,height,radius));
    }
    @Override public void close(){paths.values().forEach(Path::close);paths.clear();}
}
