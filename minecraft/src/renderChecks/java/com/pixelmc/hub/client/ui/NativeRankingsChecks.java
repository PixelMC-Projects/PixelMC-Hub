package com.pixelmc.hub.client.ui;

import com.google.gson.*;
import com.pixelmc.hub.client.render.HubRenderer;
import com.pixelmc.hub.network.HubProtocol;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/** Hidden synthetic GPU and cache checks. This never launches Minecraft or connects to a game. */
public final class NativeRankingsChecks {
    private static int checks,framebuffer,texture;
    private static Path output;
    private static ByteBuffer background;
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;}
    private static Object get(Object target,String name)throws Exception {
        Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);
    }
    private static void set(Object target,String name,Object value)throws Exception {
        Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
    private static long activeRows(Object state)throws Exception {
        long count=0;
        for(Object pose:((Map<?,?>)get(state,"poses")).values())if(!(boolean)get(pose,"leaving"))count++;
        return count;
    }
    private static void navigate(HubView view,int page)throws Exception {
        Method navigate=HubView.class.getDeclaredMethod("navigate",int.class);navigate.setAccessible(true);navigate.invoke(view,page);
    }
    private static void scene() {
        glDisable(GL_SCISSOR_TEST);glBindTexture(GL_TEXTURE_2D,texture);
        if(background!=null)glTexSubImage2D(GL_TEXTURE_2D,0,0,0,1440,1000,GL_RGBA,GL_UNSIGNED_BYTE,background);
        else {glClearColor(.83f,.86f,.94f,1);glClear(GL_COLOR_BUFFER_BIT);}
    }
    private static void frame(HubRenderer renderer,HubView view,String name)throws Exception {
        scene();renderer.render(1440,1000,90,75,1,draw->view.render(draw,-100,-100,System.nanoTime()));
        check(glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)==framebuffer,"Game framebuffer was not restored");
        check(glGetError()==GL_NO_ERROR,"Native leaderboard raised a GL error");
        if(name!=null) screenshot(name);
    }

    private static BufferedImage screenshot(String name) throws Exception {
        ByteBuffer pixels=MemoryUtil.memAlloc(1440*1000*4);
        try {
            glReadPixels(0,0,1440,1000,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
            BufferedImage image=new BufferedImage(1440,1000,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<1000;y++)for(int x=0;x<1440;x++) {
                int index=((999-y)*1440+x)*4;
                image.setRGB(x,y,0xff000000|Byte.toUnsignedInt(pixels.get(index))<<16|Byte.toUnsignedInt(pixels.get(index+1))<<8|Byte.toUnsignedInt(pixels.get(index+2)));
            }
            ImageIO.write(image,"PNG",output.resolve(name+".png").toFile());
            return image;
        } finally {MemoryUtil.memFree(pixels);}
    }

    private static BufferedImage winterRowFrame(
            HubRenderer renderer, HubLeaderboardsView view, boolean dark, boolean reduced,
            float mouseX, float mouseY, String pressed, long now, String tag) throws Exception {
        scene();
        renderer.render(1440, 1000, 90, 75, 1, draw -> {
            draw.setDark(dark);
            draw.setWinter(true);
            view.render(draw, mouseX, mouseY, null, pressed, reduced, now);
        });
        check(glGetError() == GL_NO_ERROR, "Winter row rendered without a GL error");
        return screenshot("row-" + (dark ? "dark-" : "light-") + tag);
    }

    private static void winterRows(HubRenderer renderer, JsonObject snapshot, boolean dark)
            throws Exception {
        Map<String, Runnable> hits = new HashMap<>();
        Map<String, Float> centers = new HashMap<>();
        Map<String, float[]> boxes = new HashMap<>();
        List<String> opened = new ArrayList<>();
        HubLeaderboardsView rankings = new HubLeaderboardsView(new HubLeaderboardsView.Host() {
            public void hit(String id, String label, float x, float y, float w, float h, Runnable run) {
                hits.put(id, run);
                centers.put(id,x+w/2);
                boxes.put(id,new float[]{x,y,w,h});
            }
            public void request(String action, JsonObject payload) {}
            public void focus(String id) {}
            public void openProfile(String player) { opened.add(player); }
        });
        JsonObject data = snapshot.deepCopy();
        JsonObject board = data.getAsJsonObject("boards").getAsJsonObject("food");
        JsonArray rows = board.getAsJsonArray("rows");
        JsonObject ordinary = rows.get(3).getAsJsonObject(), own = rows.get(4).getAsJsonObject();
        String rowId = "rankings-row-" + ordinary.get("playerId").getAsString();
        for (String kind : HubRankingData.BOARDS) {
            JsonObject candidate = data.getAsJsonObject("boards").getAsJsonObject(kind);
            candidate.getAsJsonObject("viewer").add("playerId", own.get("playerId").deepCopy());
            candidate.getAsJsonObject("viewer").add("name", own.get("name").deepCopy());
            candidate.add("self", JsonNull.INSTANCE);
            for (JsonElement player : candidate.getAsJsonArray("rows")) {
                if (player.getAsJsonObject().get("playerId").equals(own.get("playerId"))) {
                    candidate.add("self", player.deepCopy());
                    break;
                }
            }
        }
        rankings.live();
        rankings.accept("snapshot", data, new JsonObject());
        long now = System.nanoTime();
        BufferedImage idle = winterRowFrame(renderer, rankings, dark, true, -1, -1, null, now, "idle");
        BufferedImage hover = winterRowFrame(renderer, rankings, dark, true, 560, 438, null, now, "hover");
        BufferedImage press = winterRowFrame(renderer, rankings, dark, true, 560, 438, rowId, now, "press");
        int x = 90 + 560, edge = 75 + 422, fill = edge + 10;
        int sheet = idle.getRGB(x, fill);
        check(idle.getRGB(x, edge - 1) != sheet,
                "Header rule separates the heading from the paper rows");
        check(hover.getRGB(x, fill) != sheet && hover.getRGB(x, edge) == hover.getRGB(x, fill),
                "Hover changes only the fill");
        check(press.getRGB(x, fill) != hover.getRGB(x, fill)
                        && press.getRGB(x, edge) == press.getRGB(x, fill),
                "Press supersedes hover fill without adding a rim");
        hits.get(rowId).run();
        BufferedImage selected = winterRowFrame(renderer, rankings, dark, true, -1, -1, null, now, "selected");
        check(selected.getRGB(x, edge) == idle.getRGB(x, edge)
                        && selected.getRGB(x, fill) == idle.getRGB(x, fill),
                "Summit stylesheet hides the selection rim and tile after row selection");
        check(selected.getRGB(90 + 302, 75 + 444) != selected.getRGB(x, fill),
                "Selected row avatar remains visible on the paper");
        hits.get("rankings-locate").run();
        long locateAt = System.nanoTime();
        BufferedImage reduced = winterRowFrame(renderer, rankings, dark, true, -1, -1, null, locateAt, "locate-reduced");
        BufferedImage located = winterRowFrame(renderer, rankings, dark, false, -1, -1, null,
                locateAt + 80_000_000, "located");
        BufferedImage expired = winterRowFrame(renderer, rankings, dark, false, -1, -1, null,
                locateAt + 1_700_000_000L, "locate-expired");
        int ownEdge = edge + 45, ownFill = fill + 45;
        check(reduced.getRGB(x, ownEdge) == idle.getRGB(x, ownEdge)
                        && reduced.getRGB(x, ownFill) == idle.getRGB(x, ownFill),
                "Reduced motion suppresses the target pulse");
        check(located.getRGB(x, ownEdge) != reduced.getRGB(x, ownEdge)
                        && located.getRGB(x, ownFill) != reduced.getRGB(x, ownFill),
                "Locate adds a temporary border and tint to the target row");
        check(expired.getRGB(x, ownEdge) == reduced.getRGB(x, ownEdge)
                        && expired.getRGB(x, ownFill) == reduced.getRGB(x, ownFill),
                "Locate fades back to the original self row without a persistent outline");
        String first=rows.get(0).getAsJsonObject().get("playerId").getAsString();
        String second=rows.get(1).getAsJsonObject().get("playerId").getAsString();
        String third=rows.get(2).getAsJsonObject().get("playerId").getAsString();
        check(centers.get("rankings-profile-head-"+first)==723f
                        && centers.get("rankings-profile-head-"+second)<723f
                        && centers.get("rankings-profile-head-"+third)>723f,
                "Summit centers the winner between second and third");
        // Measured from Chromium's 1260 x 850 winter shell at a 1920 x 1080 viewport.
        box(boxes,"rankings-tab-food",949,89.13f,91,32);
        box(boxes,"rankings-tab-online",1139,89.13f,91,32);
        box(boxes,"rankings-profile-head-"+first,665.09f,191.39f,115.78f,130.72f);
        box(boxes,"rankings-profile-head-"+second,415.81f,215.69f,99.56f,112.42f);
        box(boxes,"rankings-profile-head-"+third,930.59f,217.69f,99.56f,112.42f);
        box(boxes,rowId,224,421.75f,995,45.45f);
        box(boxes,"rankings-profile-row-"+ordinary.get("playerId").getAsString(),290,431.47f,279.3f,25);
        box(boxes,"rankings-locate",1157,763.70f,56,28);
        check(Math.abs(boxes.get("rankings-profile-self")[0]-374)<.8f,
                "Bookmark avatar follows the measured self-rank column");
        hits.get("rankings-profile-head-"+third).run();
        hits.get("rankings-profile-name-"+second).run();
        hits.get("rankings-profile-row-"+ordinary.get("playerId").getAsString()).run();
        hits.get("rankings-profile-self").run();
        check(opened.equals(List.of(third,second,ordinary.get("playerId").getAsString(),own.get("playerId").getAsString())),
                "Body, name, list avatar and bookmark open their displayed public player");
        hits.get("rankings-tab-online").run();
        winterRowFrame(renderer, rankings, dark, true,-1,-1,null,System.nanoTime(),"online");
        check(get(rankings,"displayedBoard").equals("online"), "Winter tabs switch metric and body");
        check(rankings.key(268,"rankings-tab-online"), "Home returns to first board");
        for(int count=2;count>=1;count--){
            JsonObject shortened=board.deepCopy();
            JsonArray subset=new JsonArray();
            for(int i=0;i<count;i++)subset.add(rows.get(i).deepCopy());
            if(count==2){
                JsonObject tied=subset.get(1).getAsJsonObject(), winner=subset.get(0).getAsJsonObject();
                tied.add("rank",winner.get("rank").deepCopy());
                tied.add("value",winner.get("value").deepCopy());
                tied.add("rankDelta",JsonNull.INSTANCE);
            }
            shortened.add("rows",subset);
            shortened.add("self",JsonNull.INSTANCE);
            shortened.addProperty("total",count);
            JsonObject query = new JsonObject();query.addProperty("board","food");
            JsonArray pages=new JsonArray();pages.add(shortened);
            JsonObject window=new JsonObject();window.add("header",shortened);window.add("pages",pages);
            rankings.accept("window",window,query);
            centers.clear();hits.clear();
            winterRowFrame(renderer, rankings, dark, true,-1,-1,null,System.nanoTime(),"count-"+count);
            float cx=centers.get("rankings-profile-head-"+first);
            check(centers.keySet().stream().filter(k->k.startsWith("rankings-profile-head-")).count()==count,"Only present winners are clickable");
            check(count==1 ? cx==723f : Math.abs((cx+centers.get("rankings-profile-head-"+second))/2-723f)<.01f,
                    "A single winner or tied pair centers as a group");
        }
        for(String status:List.of("loading","unsupported","unavailable","error","empty")){
            rankings.live();
            JsonObject sample=data.deepCopy();
            JsonObject stateBoard=sample.getAsJsonObject("boards").getAsJsonObject("food");
            stateBoard.addProperty("status",status);
            stateBoard.addProperty("supported",!status.equals("unsupported"));
            stateBoard.add("rows",new JsonArray());
            stateBoard.add("self",JsonNull.INSTANCE);
            stateBoard.add("comparisonRevision",JsonNull.INSTANCE);
            if(status.equals("empty"))stateBoard.addProperty("total",0);
            else {stateBoard.add("total",JsonNull.INSTANCE);stateBoard.add("updatedAt",JsonNull.INSTANCE);}
            rankings.accept("snapshot",sample,new JsonObject());
            hits.clear();
            winterRowFrame(renderer,rankings,dark,true,-1,-1,null,System.nanoTime(),"state-"+status);
            check(hits.containsKey("rankings-retry")==List.of("error","unavailable").contains(status),
                    "Retry is offered only for failure states: "+status);
            check(hits.keySet().stream().noneMatch(id->id.startsWith("rankings-profile-")),
                    "No simulated players appear in provider state "+status);
            rankings.accept("snapshot",data,new JsonObject());
            hits.clear();
            winterRowFrame(renderer,rankings,dark,true,-1,-1,null,System.nanoTime(),"restore-"+status);
            check(hits.containsKey("rankings-profile-head-"+first),
                    "Same-revision ready data recovers the stage from "+status);
        }
    }
    private static void box(Map<String,float[]> boxes, String id, float... expected) {
        float[] actual=Objects.requireNonNull(boxes.get(id),id);
        for(int i=0;i<4;i++)
            check(Math.abs(actual[i]-expected[i])<1f,id+" deviates from CSS at "+i+": "+actual[i]+" vs "+expected[i]);
    }
    private static void click(HubView view,String id)throws Exception {
        for(Object hit:(List<?>)get(view,"hits")) {
            Method name=hit.getClass().getDeclaredMethod("id");name.setAccessible(true);
            if(!name.invoke(hit).equals(id))continue;
            Method x=hit.getClass().getDeclaredMethod("x"),y=hit.getClass().getDeclaredMethod("y");x.setAccessible(true);y.setAccessible(true);
            float px=((Number)x.invoke(hit)).floatValue()+5,py=((Number)y.invoke(hit)).floatValue()+5;
            view.mouseClicked(px,py,0);view.mouseReleased(px,py,0);return;
        }
        throw new AssertionError("Missing normal input target "+id);
    }
    private static void rejected(Runnable action,String reason) {
        boolean rejected=false;try{action.run();}catch(RuntimeException expected){rejected=true;}check(rejected,reason);
    }
    // These hashes are the 288 x 296 RGBA buffers produced by summitMesh.js using summit-traveler.png.
    private static void summitGeometry() throws Exception {
        String[] reference = {
                "a335344ac1df52465ef7036c56cb693fde1e76822b5cdee6e7a756588e2a56f6",
                "06ecd4b3b810f7c057518bbf29f405bc2f3244a3ef365f2e811cbfb3492427a5",
                "7f438b20429ed7818ce547c46cbecd93b9731b1c7930236fe5184960e7166029"
        };
        try(var stream=NativeRankingsChecks.class.getResourceAsStream("/assets/pixelmc_hub/images/winter/summit-traveler.png");
            var texture=io.github.humbleui.skija.Image.makeFromEncoded(Objects.requireNonNull(stream).readAllBytes())) {
            for(int rank=1;rank<=3;rank++) {
                try(var frame=SummitPlayer.render(texture,rank);
                    var bitmap=io.github.humbleui.skija.Bitmap.makeFromImage(frame)) {
                    var info=new io.github.humbleui.skija.ImageInfo(288,296,io.github.humbleui.skija.ColorType.RGBA_8888,
                            io.github.humbleui.skija.ColorAlphaType.UNPREMUL,io.github.humbleui.skija.ColorSpace.getSRGB());
                    byte[] rgba=bitmap.readPixels(info,288*4L,0,0);
                    String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rgba));
                    check(digest.equals(reference[rank-1]),"Every pixel of the podium pose matches the Web reference for rank "+rank);
                }
            }
        }
    }
    @SuppressWarnings("unchecked")
    public static void main(String[] args)throws Exception {
        output=Path.of(args[0]);Files.createDirectories(output);
        JsonObject fixtures;
        try(var input=NativeRankingsChecks.class.getResourceAsStream("/assets/pixelmc_hub/rankings-preview.json")) {
            fixtures=JsonParser.parseReader(new java.io.InputStreamReader(Objects.requireNonNull(input),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        JsonArray snapshots=fixtures.getAsJsonArray("snapshots"),following=fixtures.getAsJsonArray("followingPages");
        for(JsonElement raw:snapshots)for(String board:HubRankingData.BOARDS) {
            var parsed=HubRankingData.parse(raw.getAsJsonObject().getAsJsonObject("boards").getAsJsonObject(board));
            check(parsed.rows().size()==20,"Shared Web fixture did not match native DTO");
        }
        JsonObject invalid=snapshots.get(0).getAsJsonObject().getAsJsonObject("boards").getAsJsonObject("food").deepCopy();
        invalid.addProperty("privateEmail","forbidden");rejected(()->HubRankingData.parse(invalid),"Private DTO fields accepted");
        rejected(()->HubProtocol.payload("rankings","page","{\"board\":\"food\",\"page\":0,\"player\":\"override\"}"),"Player identity override accepted");
        rejected(()->HubProtocol.payload("rankings","window","{\"board\":\"food\",\"page\":0,\"count\":6}"),"Unbounded window accepted");
        HubProtocol.payload("rankings","window","{\"board\":\"food\",\"page\":0,\"count\":5}");

        GLFWErrorCallback callback=GLFWErrorCallback.createPrint(System.err);callback.set();check(glfwInit(),"GLFW unavailable");
        glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,4);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,6);glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window=glfwCreateWindow(640,480,"Hidden leaderboard checks",0,0);check(window!=0,"Hidden GPU context unavailable");
        glfwMakeContextCurrent(window);GL.createCapabilities();int vao=glGenVertexArrays();glBindVertexArray(vao);
        texture=glGenTextures();glBindTexture(GL_TEXTURE_2D,texture);glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,1440,1000,0,GL_RGBA,GL_UNSIGNED_BYTE,(ByteBuffer)null);
        framebuffer=glGenFramebuffers();glBindFramebuffer(GL_FRAMEBUFFER,framebuffer);glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);glViewport(0,0,1440,1000);
        Path backdrop=output.getParent().resolve("web-background-1440x1000.png");
        if(Files.exists(backdrop)) {
            BufferedImage image=ImageIO.read(backdrop.toFile());background=MemoryUtil.memAlloc(1440*1000*4);
            for(int y=999;y>=0;y--)for(int x=0;x<1440;x++){int color=image.getRGB(x,y);background.put((byte)(color>>>16)).put((byte)(color>>>8)).put((byte)color).put((byte)255);}background.flip();
        }
        try(HubRenderer renderer=new HubRenderer()) {
            summitGeometry();
            winterRows(renderer, snapshots.get(0).getAsJsonObject(), false);
            winterRows(renderer, snapshots.get(0).getAsJsonObject(), true);
            HubView view=new HubView();List<JsonObject> requests=new ArrayList<>();
            view.live((module,action,payload)->{JsonObject request=payload.deepCopy();request.addProperty("action",action);requests.add(request);});
            view.setSettings("login",true);view.open(false);view.setViewportBounds(-90,-75,1440,1000);
            view.acceptLive("rankings","snapshot",snapshots.get(0).getAsJsonObject());navigate(view,4);
            frame(renderer,view,"native-food");
            HubLeaderboardsView rankings=(HubLeaderboardsView)get(view,"rankings");
            Map<String,Object> states=(Map<String,Object>)get(rankings,"states");Object food=states.get("food");
            check(((Map<?,?>)get(food,"poses")).size()==17,"Initial list size incorrect");
            click(view,"rankings-tab-online");frame(renderer,view,"native-online");check(get(rankings,"displayedBoard").equals("online"),"Tab input did not switch board");
            click(view,"rankings-tab-fortunes");frame(renderer,view,"native-fortunes");
            click(view,"rankings-tab-food");frame(renderer,view,null);
            view.mouseScrolled(700,600,-3);float before=(float)get(food,"scroll");check(before>0,"Native list did not scroll");
            Object retained=((Map<?,?>)get(food,"poses")).get("7f1b0000-0000-4000-8000-000000000004");
            view.setSettings("login",false);view.acceptLive("rankings","snapshot",snapshots.get(1).getAsJsonObject());frame(renderer,view,"native-update-start");
            check(get(food,"scroll").equals(before),"Snapshot reset scroll position");
            check(((Map<?,?>)get(food,"podium")).size()>3,"Departing podium player vanished before exit finished");
            check(((Map<?,?>)get(food,"poses")).get("7f1b0000-0000-4000-8000-000000000004")==retained,"Player animation identity was replaced");
            for(int frame=0;frame<36;frame++){Thread.sleep(16);frame(renderer,view,null);}frame(renderer,view,"native-update-settled");
            check(((Map<?,?>)get(food,"podium")).size()==3,"Departed podium player retained stale position");
            view.mouseScrolled(700,650,-100);frame(renderer,view,null);
            check(requests.get(requests.size()-1).get("action").getAsString().equals("page"),"Scroll did not request the next bounded page");
            JsonObject page=following.get(1).getAsJsonObject().getAsJsonObject("food").deepCopy();
            page.add("_hubRequest",requests.get(requests.size()-1).deepCopy());page.getAsJsonObject("_hubRequest").remove("action");
            view.acceptLive("rankings","page",page);frame(renderer,view,null);check(activeRows(food)==21,"Next page was not merged");
            float scrolled=(float)get(food,"scroll");int requestCount=requests.size();
            view.acceptLive("rankings","snapshot",snapshots.get(2).getAsJsonObject());
            check(requests.size()==requestCount+1 && requests.get(requests.size()-1).get("action").getAsString().equals("window"),"Refresh did not request one atomic window");
            check(activeRows(food)==21 && get(food,"scroll").equals(scrolled),"Pending window blanked existing rows");
            JsonObject refreshed=new JsonObject();refreshed.add("header",snapshots.get(2).getAsJsonObject().getAsJsonObject("boards").get("food"));
            JsonArray pages=new JsonArray();pages.add(following.get(2).getAsJsonObject().get("food"));refreshed.add("pages",pages);
            JsonObject query=requests.get(requests.size()-1).deepCopy();query.remove("action");refreshed.add("_hubRequest",query);
            view.acceptLive("rankings","window",refreshed);frame(renderer,view,null);check(get(food,"scroll").equals(scrolled),"Atomic window changed scroll position");
            view.moduleStatus("rankings","网络暂不可用");check(activeRows(food)==21,"Transient failure erased cached rows");
            view.setSettings("login",true);
            JsonObject shortBoard=snapshots.get(0).getAsJsonObject().getAsJsonObject("boards").getAsJsonObject("food").deepCopy();
            JsonArray two=new JsonArray();two.add(shortBoard.getAsJsonArray("rows").get(0));two.add(shortBoard.getAsJsonArray("rows").get(1));
            shortBoard.add("rows",two);shortBoard.addProperty("total",2);
            JsonObject queryShort=new JsonObject();queryShort.addProperty("board","food");queryShort.addProperty("page",0);queryShort.addProperty("count",1);
            JsonObject shortWindow=new JsonObject();shortWindow.add("header",shortBoard);JsonArray shortPages=new JsonArray();shortPages.add(shortBoard);shortWindow.add("pages",shortPages);shortWindow.add("_hubRequest",queryShort);
            view.acceptLive("rankings","window",shortWindow);frame(renderer,view,"native-two-players");check(activeRows(food)==0,"Two-player board drew phantom list rows");
            view.live((m,a,p)->{});check(!view.hasModuleSnapshot("rankings"),"New connection reused another session's leaderboard");
            for(int index=1;index<=5;index++) {
                String player=String.format("00000000-0000-4000-8000-%012d",index);
                byte[] pixels=new byte[1024*1024*4];Arrays.fill(pixels,(byte)255);
                renderer.withIsolated(()->HubHead.setPlayerSkinRgba(player,1024,1024,pixels));
            }
            check(!HubHead.hasPlayerSkin("00000000-0000-4000-8000-000000000001")&&HubHead.hasPlayerSkin("00000000-0000-4000-8000-000000000005"),"Peer-skin eviction presence query is stale");
            renderer.withIsolated(HubHead::clearSkin);
        } finally {
            if(background!=null)MemoryUtil.memFree(background);
            glDeleteFramebuffers(framebuffer);glDeleteTextures(texture);glDeleteVertexArrays(vao);glfwDestroyWindow(window);glfwTerminate();callback.free();
        }
        Files.writeString(output.resolve("checks.txt"),"PASS "+checks+" assertions; hidden GPU and synthetic protocol/cache checks only.\n");
        System.out.println("PASS native leaderboard assertions: "+checks);
    }
}
