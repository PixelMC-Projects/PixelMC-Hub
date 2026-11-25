/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.pixelmc.hub.client.render.HubRenderer;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.opengl.GL11.*;
import static com.pixelmc.hub.client.ui.WinterStationChecks.*;

final class WinterAlbumInkChecks {
    // The final browser-computed colors of the actual preview-name span in both themes.
    private static final String[] IDS={"dew","star","wood","moon","harvest","mountain","dawn","cloud"};
    private static final int[][] LIGHT={{0x168878,0x69a648},{0xa97929,0xbf854a,0x8a69bb},{0xa36942},{0x7e5eb6},{0xa68322},{0x537786},{0xc17c43},{0x647dba}};
    private static final int[][] DARK={{0x7acdbb,0xb7cf83},{0xdfbf7c,0xdfb18c,0xc9abe9},{0xdbaf8c},{0xbba5ed},{0xdfc66f},{0xa0c9dd},{0xe5b08a},{0xa7bce9}};
    private static final int X=355,Y=202,W=184,H=49;

    private static BufferedImage readName() {
        ByteBuffer pixels=MemoryUtil.memAlloc(W*H*4);
        try {
            glReadPixels(X,WinterStationChecks.H-Y-H,W,H,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
            var image=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
            for(int y=0;y<H;y++) for(int x=0;x<W;x++){
                int off=((H-1-y)*W+x)*4;
                image.setRGB(x,y,((pixels.get(off)&255)<<16)|((pixels.get(off+1)&255)<<8)|(pixels.get(off+2)&255));
            }
            return image;
        } finally {MemoryUtil.memFree(pixels);}
    }

    private static int interpolated(int[] stops,float fraction) {
        float t=fraction*(stops.length-1);
        int start=(int)t,end=Math.min(start+1,stops.length-1);
        return HubWinterSheet.mix(stops[start] | 0xff000000,stops[end] | 0xff000000,t-start);
    }

    private static void assertInk(BufferedImage image,float fraction,int expected,int tolerance,String label) {
        int matched=0;
        int pos=Math.round(176*fraction);
        for(int y=5;y<H-1;y++) for(int x=Math.max(0,pos-8);x<Math.min(W,pos+9);x++) {
            int actual=image.getRGB(x,y);
            int distance=0;
            for(int shift=0;shift<=16;shift+=8) distance=Math.max(distance,Math.abs((actual>>shift&255)-(expected>>shift&255)));
            if(distance<=tolerance)matched++;
        }
        check(matched>=4,label+" lacks "+Integer.toHexString(expected)+" at "+fraction+" ("+matched+" pixels)");
    }

    static void run(HubRenderer r,boolean dark) throws Exception {
        var view=new HubView();
        view.setWinter(true);view.setDark(dark);view.setSettings("always",true);view.open(false);
        epoch=System.nanoTime();
        frame(r,view,1000,null);
        WinterCollectionChecks.clickId(view,"nav-1"); frame(r,view,1000,null);
        WinterCollectionChecks.clickId(view,"filter-1"); frame(r,view,1000,null);
        var sheet=new BufferedImage(W,H*(IDS.length+1),BufferedImage.TYPE_INT_RGB);
        var g=sheet.createGraphics();
        try {
            for(int i=0;i<IDS.length;i++) {
                if(i==6){WinterCollectionChecks.clickId(view,"album-next");frame(r,view,1000,null);}
                WinterCollectionChecks.clickId(view,"album-pin-"+IDS[i]);frame(r,view,1000,null);
                var image=readName();
                g.drawImage(image,0,i*H,null);
                int[] stops=(dark?DARK:LIGHT)[i];
                for(float f:new float[]{.10f,.50f,.90f}) assertInk(image,f,interpolated(stops,f),stops.length==1?1:9,IDS[i]+"/"+dark);
            }

            // A server may give an unrelated title the very same id and RGB values as a demo preset.
            // A raw segment and each stop of the user-defined gradient must survive both themes.
            PlayerDataChecks.populate(view);
            var snapshot=PlayerDataChecks.titles();
            var title=snapshot.getAsJsonArray("titles").get(0).getAsJsonObject();
            title.addProperty("id","dew");snapshot.addProperty("equippedTitle","dew");
            title.add("name", JsonParser.parseString("""
                    {"segments":[{"text":"晨风入林","color":5470086,"bold":true,"italic":false,"underline":true,"strike":false}],
                     "gradients":[{"positions":[1,2,3],"from":1476728,"to":6923848}]}
                    """));
            view.acceptLive("titles","snapshot",snapshot);
            view.catalogueComplete();
            frame(r,view,1000,null);
            WinterCollectionChecks.clickId(view,"album-pin-dew");frame(r,view,1000,null);
            var provider=readName();
            g.drawImage(provider,0,IDS.length*H,null);
            ImageIO.write(sheet,"PNG",output.resolve("album-name-ink-"+(dark?"dark":"light")+".png").toFile());
            assertInk(provider,.125f,5470086,1,"provider raw segment");
            assertInk(provider,.375f,1476728,1,"provider gradient first stop");
            assertInk(provider,.625f,interpolated(new int[]{1476728,6923848},.5f),1,"provider gradient interpolation");
            assertInk(provider,.875f,6923848,1,"provider gradient final stop");
        } finally {g.dispose();}
    }
}
