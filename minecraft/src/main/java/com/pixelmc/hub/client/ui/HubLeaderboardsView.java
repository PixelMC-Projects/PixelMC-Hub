/* Original integration: Apache-2.0. Motion/material parameters follow the licensed
 * React Bits integrations retained in assets/pixelmc_hub/licenses/React-Bits-LICENSE.md. */
package com.pixelmc.hub.client.ui;

import com.google.gson.*;
import com.pixelmc.hub.client.render.GlassStyle;
import com.pixelmc.hub.client.render.HubDraw;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.*;

/** Native counterpart of the approved Web leaderboard, including retained live transitions. */
final class HubLeaderboardsView {
    interface Host {
        void hit(String id, String label, float x, float y, float w, float h, Runnable action);
        void request(String action, JsonObject payload);
        void focus(String id);
        default void feedback(String message) {}
        default void openProfile(String playerId) {}
    }
    private static final float LEFT=236, WIDTH=996, PODIUM_TOP=216, CARD_TOP=328;
    private static final float LIST_TOP=405, LIST_HEIGHT=351, ROW_TOP=446, ROW_HEIGHT=50, VIEW_HEIGHT=305;
    private static final int MAX_PAGES=5;
    private static final NumberFormat NUMBER=NumberFormat.getIntegerInstance(Locale.CHINA);
    private static final NumberFormat COMPACT=NumberFormat.getCompactNumberInstance(Locale.CHINA,NumberFormat.Style.SHORT);
    static { COMPACT.setMaximumFractionDigits(1); }
    private static final GlassStyle PODIUM_GLASS=new GlassStyle(.07f,72,.65f,8,0,.13f,1.15f,-15,
            0,0,0,false,0xffffffff,1,12,true);
    private static final String[] LABELS={"美食品鉴","签笺收藏","在线时长"};
    private static final String[] ICONS={"utensils","book-open","clock-3"};
    private static final String[] METRICS={"已品尝食物","已收藏签种","累计在线"};
    private static final String[] DESCRIPTIONS={"本存档内，记录你尝过的每一种风味","在旅途中收进藏签匣的不同签种","本存档内，记录相伴的时光"};

    private static final class Scalar {
        double from, target;
        long at, duration;
        Scalar(double value) { from=target=value; }
        double value(long now, boolean reduced) {
            if(reduced || duration==0)return target;
            float progress=clamp((float)(now-at)/duration,0,1);
            return from+(target-from)*HubMotion.bezier(progress,.22f,1,.36f,1);
        }
        void to(double value,long now,long millis,boolean reduced) {
            if(value==target)return;
            from=value(now,reduced);target=value;at=now;duration=millis*1_000_000L;
        }
    }
    private static final class Pose {
        HubRankingData.Row row;
        final Scalar y,score,ratio;
        final Scalar opacity=new Scalar(0);
        boolean revealed,leaving;
        void reveal(long now,long delay,boolean reduced) {
            if(revealed)return;
            revealed=true;opacity.to(1,now+delay,300,reduced);
        }
        Pose(HubRankingData.Row row,float y,double ratio) {
            this.row=row;this.y=new Scalar(y);score=new Scalar(row.value()==null?0:row.value());this.ratio=new Scalar(ratio);
        }
        void update(HubRankingData.Row next,float position,double fraction,long now,boolean reduced) {
            row=next;y.to(position,now,420,reduced);score.to(next.value()==null?0:next.value(),now,460,reduced);ratio.to(fraction,now,320,reduced);
        }
    }
    private static final class PodiumPose {
        HubRankingData.Row row;
        final Scalar x,width,leading,opacity,offset;
        int fromInk,fromWash,fromGlow,fromInkDark,fromWashDark,fromGlowDark;
        long colorAt;
        boolean leaving;
        PodiumPose(HubRankingData.Row row,float x,float width,boolean leading,boolean initial,long now,boolean reduced) {
            this.row=row;this.x=new Scalar(x);this.width=new Scalar(width);this.leading=new Scalar(leading?1:0);
            opacity=new Scalar(initial?1:0);offset=new Scalar(initial?0:8);
            fromInk=rankColor(row.rank());fromWash=rankWash(row.rank());fromGlow=rankGlow(row.rank());
            fromInkDark=rankColorDark(row.rank());fromWashDark=rankWashDark(row.rank());fromGlowDark=rankGlowDark(row.rank());
            opacity.to(1,now,320,reduced);offset.to(0,now,320,reduced);
        }
        float colorProgress(long now,boolean reduced) { return reduced?1:HubMotion.ease(clamp((now-colorAt)/320_000_000f,0,1)); }
        void update(HubRankingData.Row next,float x,float width,boolean leading,long now,boolean reduced) {
            if(!Objects.equals(row.rank(),next.rank())) {
                float mix=colorProgress(now,reduced);
                fromInk=mixColor(fromInk,rankColor(row.rank()),mix);
                fromWash=mixColor(fromWash,rankWash(row.rank()),mix);
                fromGlow=mixColor(fromGlow,rankGlow(row.rank()),mix);
                fromInkDark=mixColor(fromInkDark,rankColorDark(row.rank()),mix);
                fromWashDark=mixColor(fromWashDark,rankWashDark(row.rank()),mix);
                fromGlowDark=mixColor(fromGlowDark,rankGlowDark(row.rank()),mix);colorAt=now;
            }
            row=next;this.x.to(x,now,420,reduced);this.width.to(width,now,420,reduced);
            this.leading.to(leading?1:0,now,420,reduced);
            opacity.to(1,now,320,reduced);offset.to(0,now,320,reduced);leaving=false;
        }
    }
    private static final class State {
        HubRankingData.Snapshot header, pending;
        final NavigableMap<Integer,List<HubRankingData.Row>> pages=new TreeMap<>(), refreshed=new TreeMap<>();
        final Deque<Integer> refreshPages=new ArrayDeque<>();
        final Map<String,Pose> poses=new LinkedHashMap<>();
        final Map<String,PodiumPose> podium=new LinkedHashMap<>();
        final Map<String,Scalar> scores=new HashMap<>();
        Scalar scrollMotion;
        boolean podiumInitialized;
        final Map<String,HubMotion.Tilt> tilts=new HashMap<>();
        final Map<String,RankGlyph> ranks=new HashMap<>();
        String error="", selected="", locate="";
        float scroll;
        int loadingPage=-1;
        boolean loading, around;
        List<HubRankingData.Row> rows() {
            List<HubRankingData.Row> rows=new ArrayList<>();
            pages.forEach((page, values)->rows.addAll(page==0?values.subList(Math.min(3,values.size()),values.size()):values));
            return rows;
        }
        List<HubRankingData.Row> leaders() { return header==null?List.of():header.rows().subList(0,Math.min(3,header.rows().size())); }
        double leader() { return leaders().isEmpty() || leaders().get(0).value()==null?0:leaders().get(0).value(); }
        boolean hasData() { return header!=null && header.confirmed(); }
    }
    private static final class RankGlyph {
        Long previous,current;
        long at;
        RankGlyph(Long rank) { current=rank; }
        void update(Long rank,long now) {
            if(Objects.equals(rank,current))return;
            previous=current;current=rank;at=now;
        }
    }
    private final Host host;
    private final Map<String,State> states=new LinkedHashMap<>();
    private HubDraw draw;
    private String selectedBoard="food",displayedBoard="food", focus;
    private boolean live,active,reduced,dragging;
    private float mouseX,mouseY,dt=1f/60,bodyOffset,dragGrab;
    private long now,lastFrame,switchAt,enterAt,locateAt;
    private JsonArray previews=new JsonArray();
    private JsonArray previewFollowing=new JsonArray();
    private int previewIndex;

    HubLeaderboardsView(Host host) { this.host=host;reset(); }
    private void reset() {
        states.clear();for(String board:HubRankingData.BOARDS)states.put(board,new State());
        selectedBoard=displayedBoard="food";switchAt=enterAt=0;dragging=false;
    }
    void live() { live=true;reset(); }
    void demo() {
        live=false;reset();
        try(var input=HubLeaderboardsView.class.getResourceAsStream("/assets/pixelmc_hub/rankings-preview.json")) {
            if(input==null)throw new IllegalStateException("Ranking preview asset missing");
            JsonObject fixture=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
            previews=fixture.getAsJsonArray("snapshots");previewFollowing=fixture.getAsJsonArray("followingPages");
            previewIndex=0;accept("snapshot",previews.get(0).getAsJsonObject(),new JsonObject());
        } catch(Exception error) { status("本地排行预览暂不可用"); }
    }
    void demoUpdate() {
        if(live || previews.isEmpty())return;
        previewIndex=(previewIndex+1)%previews.size();accept("snapshot",previews.get(previewIndex).getAsJsonObject(),new JsonObject());
    }
    boolean loaded() { return states.values().stream().anyMatch(state->state.header!=null); }
    int visibleMask() { return active?1<<HubRankingData.BOARDS.indexOf(selectedBoard):0; }
    JsonObject refreshQuery() {
        State state=states.get(selectedBoard);JsonObject query=new JsonObject();
        query.addProperty("board",selectedBoard);query.addProperty("page",state.pages.isEmpty()?0:state.pages.firstKey());
        query.addProperty("count",Math.max(1,state.pages.size()));state.loading=true;return query;
    }
    String description() { return DESCRIPTIONS[HubRankingData.BOARDS.indexOf(selectedBoard)]; }
    void activate() { active=true;continueRefresh(selectedBoard); }
    void deactivate() { active=false;dragging=false; }
    void status(String message) { for(State state:states.values()) { state.error=message;state.loading=false;state.loadingPage=-1;state.around=false; } }

    void accept(String action,JsonObject payload,JsonObject query) {
        long at=System.nanoTime();
        if(action.equals("window")) { acceptWindow(payload,query,at);return; }
        if(action.equals("snapshot")) {
            if(!payload.keySet().equals(Set.of("schemaVersion","boards")) || payload.get("schemaVersion").getAsInt()!=1
                    || !payload.getAsJsonObject("boards").keySet().equals(Set.copyOf(HubRankingData.BOARDS)))throw new IllegalArgumentException("排行快照无效");
            Map<String,HubRankingData.Snapshot> parsed=new LinkedHashMap<>();
            for(String board:HubRankingData.BOARDS) {
                HubRankingData.Snapshot snapshot=HubRankingData.parse(payload.getAsJsonObject("boards").getAsJsonObject(board));
                if(!snapshot.board().equals(board) || snapshot.page()!=0 || snapshot.size()!=20)throw new IllegalArgumentException("排行快照不匹配");
                parsed.put(board,snapshot);
            }
            Set<String> viewers=new HashSet<>();parsed.values().forEach(snapshot->viewers.add(snapshot.viewerId()));
            if(viewers.size()!=1)throw new IllegalArgumentException("排行玩家身份不一致");
            parsed.forEach((board,snapshot)->receiveSnapshot(board,snapshot,at));
            if(active)continueRefresh(selectedBoard);
            return;
        }
        HubRankingData.Snapshot snapshot=HubRankingData.parse(payload);
        if(!query.has("board") || !snapshot.board().equals(query.get("board").getAsString()))throw new IllegalArgumentException("排行响应不匹配");
        State state=states.get(snapshot.board());state.loading=false;state.loadingPage=-1;
        HubRankingData.Snapshot basis=state.pending==null?state.header:state.pending;
        if(basis==null || !basis.revision().equals(snapshot.revision()) || !basis.viewerId().equals(snapshot.viewerId()) || !basis.scope().equals(snapshot.scope())) {
            state.pending=null;state.refreshPages.clear();state.refreshed.clear();state.around=false;
            state.error="排行已更新，正在同步";host.request("snapshot",new JsonObject());return;
        }
        if(!snapshot.confirmed()){state.error="排行暂不可用";return;}
        if(state.pending!=null) {
            state.refreshed.put(snapshot.page(),snapshot.rows());
            if(!state.refreshPages.isEmpty() && state.refreshPages.peekFirst()==snapshot.page())state.refreshPages.removeFirst();
            if(state.refreshPages.isEmpty()) {
                state.pages.clear();state.pages.putAll(state.refreshed);state.refreshed.clear();
                state.header=state.pending;state.pending=null;retarget(state,at);
            }
            if(active)continueRefresh(selectedBoard);
        } else {
            int first=state.pages.isEmpty()?snapshot.page():state.pages.firstKey();
            boolean locate=action.equals("aroundMe") || state.around;
            if(locate)state.pages.clear();
            state.pages.put(snapshot.page(),snapshot.rows());
            if(!locate && snapshot.page()<first)state.scroll+=snapshot.rows().size()*ROW_HEIGHT;
            while(state.pages.size()>MAX_PAGES) {
                if(snapshot.page()>=state.pages.lastKey()) {var removed=state.pages.pollFirstEntry();state.scroll-=removed.getValue().size()*ROW_HEIGHT;}
                else state.pages.pollLastEntry();
            }
            state.around=false;retarget(state,at);
            if(locate)locateSelf(state);
        }
        state.error="";
    }

    private void acceptWindow(JsonObject payload,JsonObject query,long at) {
        if(!payload.keySet().equals(Set.of("header","pages")))throw new IllegalArgumentException("排行窗口无效");
        HubRankingData.Snapshot header=HubRankingData.parse(payload.getAsJsonObject("header"));
        if(!header.board().equals(query.get("board").getAsString()) || header.page()!=0 || header.size()!=20)
            throw new IllegalArgumentException("排行窗口不匹配");
        JsonArray raw=payload.getAsJsonArray("pages");
        if(raw.size()>5)throw new IllegalArgumentException("排行窗口超出范围");
        NavigableMap<Integer,List<HubRankingData.Row>> pages=new TreeMap<>();
        int previous=-1;
        for(JsonElement element:raw) {
            HubRankingData.Snapshot page=HubRankingData.parse(element.getAsJsonObject());
            if(!page.board().equals(header.board()) || !page.revision().equals(header.revision()) || !page.scope().equals(header.scope())
                    || !page.viewerId().equals(header.viewerId()) || page.size()!=20 || previous>=0 && page.page()!=previous+1)
                throw new IllegalArgumentException("排行窗口修订不一致");
            pages.put(page.page(),page.rows());previous=page.page();
        }
        State state=states.get(header.board());
        if(state.header!=null && !state.header.viewerId().equals(header.viewerId()))throw new IllegalArgumentException("排行身份不一致");
        if(!header.confirmed() || header.status().equals("empty")) { receiveSnapshot(header.board(),header,at);return; }
        if(pages.isEmpty())throw new IllegalArgumentException("排行窗口缺少内容");
        int first=state.pages.isEmpty()?0:state.pages.firstKey();
        if(first==0 && pages.firstKey()>0)pages.put(0,header.rows());
        if(state.header!=null && !state.header.scope().equals(header.scope())) {state=new State();states.put(header.board(),state);first=pages.firstKey();}
        state.header=header;state.pending=null;state.refreshPages.clear();state.refreshed.clear();
        state.pages.clear();state.pages.putAll(pages);state.loading=false;state.loadingPage=-1;state.error="";
        state.scroll+=(first-pages.firstKey())*20*ROW_HEIGHT;retarget(state,at);
    }

    private void receiveSnapshot(String board,HubRankingData.Snapshot snapshot,long at) {
        State state=states.get(board);
        if(state.header!=null && (!state.header.scope().equals(snapshot.scope()) || !state.header.viewerId().equals(snapshot.viewerId()))) {
            state=new State();states.put(board,state);
        }
        state.loading=false;state.loadingPage=-1;state.around=false;
        if(!snapshot.confirmed()) {
            state.pending=null;state.refreshed.clear();state.refreshPages.clear();
            state.error=snapshot.status().equals("unsupported")?"这个存档还未开启此排行":"排行暂不可用";
            if(snapshot.status().equals("unsupported") || !state.hasData()) {state.header=snapshot;state.pages.clear();state.poses.clear();}
            return;
        }
        state.error="";
        if(snapshot.status().equals("empty")) {
            state.header=snapshot;state.pending=null;state.pages.clear();state.refreshed.clear();state.refreshPages.clear();
            state.poses.clear();state.scores.clear();state.ranks.clear();state.scroll=0;return;
        }
        if(state.header!=null && state.header.revision().equals(snapshot.revision()))return;
        List<Integer> pages=state.pages.keySet().stream().filter(page->page!=0).toList();
        state.refreshPages.clear();state.refreshed.clear();state.pending=null;
        if(!pages.isEmpty()) {
            state.pending=snapshot;
            if(state.pages.containsKey(0))state.refreshed.put(0,snapshot.rows());
            state.refreshPages.addAll(pages);
        } else {
            state.header=snapshot;state.pages.clear();state.pages.put(0,snapshot.rows());retarget(state,at);
        }
    }
    private void continueRefresh(String board) {
        State state=states.get(board);
        if(!active || state.loading || state.pending==null || state.refreshPages.isEmpty())return;
        if(!live) {requestPage(board,state.refreshPages.peekFirst(),state.pending.revision(),false);return;}
        JsonObject query=new JsonObject();query.addProperty("board",board);query.addProperty("page",state.refreshPages.peekFirst());
        query.addProperty("count",state.refreshPages.size());state.loading=true;host.request("window",query);
    }
    private void requestPage(String board,int page,String revision,boolean around) {
        State state=states.get(board);if(state.loading)return;
        JsonObject query=new JsonObject();query.addProperty("board",board);query.addProperty("revision",revision);
        if(!around)query.addProperty("page",page);
        state.loading=true;state.loadingPage=page;state.around=around;
        if(!live) {
            JsonObject fixture=page==0?previews.get(previewIndex).getAsJsonObject().getAsJsonObject("boards"):
                    previewFollowing.get(previewIndex).getAsJsonObject();
            if(page<=1)accept(around?"aroundMe":"page",fixture.getAsJsonObject(board),query);
            else {state.loading=false;state.loadingPage=-1;}
            return;
        }
        host.request(around?"aroundMe":"page",query);
    }
    private void retarget(State state,long at) {
        state.poses.values().removeIf(pose->pose.leaving && pose.opacity.value(at,reduced)<=0);
        state.podium.values().removeIf(pose->pose.leaving && pose.opacity.value(at,reduced)<=0);
        List<HubRankingData.Row> rows=state.rows();Set<String> keep=new HashSet<>();double leader=state.leader();
        for(int index=0;index<rows.size();index++) {
            HubRankingData.Row row=rows.get(index);keep.add(row.playerId());double ratio=leader>0?row.value()/leader:0;
            state.ranks.computeIfAbsent(row.playerId(),id->new RankGlyph(row.rank())).update(row.rank(),at);
            Pose pose=state.poses.get(row.playerId());
            if(pose==null)state.poses.put(row.playerId(),new Pose(row,index*ROW_HEIGHT,ratio));
            else {
                pose.update(row,index*ROW_HEIGHT,ratio,at,reduced);
                if(pose.leaving){pose.leaving=false;pose.opacity.to(1,at,300,reduced);}
            }
            state.scores.computeIfAbsent(row.playerId(),id->new Scalar(row.value())).to(row.value(),at,460,reduced);
        }
        for(var entry:state.poses.entrySet())if(!keep.contains(entry.getKey())) {
            entry.getValue().leaving=true;entry.getValue().opacity.to(0,at,200,reduced);
        }
        List<HubRankingData.Row> leaders=state.leaders();
        Set<String> podiumIds=new HashSet<>();
        int[] positions=leaders.size()==3?new int[]{1,0,2}:new int[]{0,1,2};
        float[] widths={304.5f,341,304.5f},xs={242,563.5f,921.5f};
        for(int slot=0;slot<leaders.size();slot++) {
            int index=positions[slot];var row=leaders.get(index);podiumIds.add(row.playerId());
            PodiumPose pose=state.podium.get(row.playerId());
            if(pose==null)state.podium.put(row.playerId(),new PodiumPose(row,xs[slot],widths[slot],index==0,!state.podiumInitialized,at,reduced));
            else pose.update(row,xs[slot],widths[slot],index==0,at,reduced);
        }
        for(var entry:state.podium.entrySet())if(!podiumIds.contains(entry.getKey())) {
            PodiumPose pose=entry.getValue();pose.leaving=true;pose.opacity.to(0,at,320,reduced);pose.offset.to(-8,at,320,reduced);
        }
        state.podiumInitialized=true;
        for(HubRankingData.Row row:state.leaders()) {
            state.ranks.computeIfAbsent(row.playerId(),id->new RankGlyph(row.rank())).update(row.rank(),at);
            keep.add(row.playerId());state.scores.computeIfAbsent(row.playerId(),id->new Scalar(row.value())).to(row.value(),at,460,reduced);
        }
        if(state.header.self()!=null && state.header.self().value()!=null) {
            var self=state.header.self();keep.add(self.playerId());state.scores.computeIfAbsent(self.playerId(),id->new Scalar(self.value())).to(self.value(),at,460,reduced);
            state.ranks.computeIfAbsent(self.playerId(),id->new RankGlyph(self.rank())).update(self.rank(),at);
        }
        state.scores.keySet().retainAll(keep);state.tilts.keySet().retainAll(keep);state.ranks.keySet().retainAll(keep);

        clampScroll(state);
    }
    private void choose(String board) {
        if(board.equals(selectedBoard))return;
        State previous=states.get(selectedBoard);previous.loading=false;previous.loadingPage=-1;previous.around=false;
        selectedBoard=board;switchAt=System.nanoTime();dragging=false;host.focus("rankings-tab-"+board);continueRefresh(board);
    }

    void render(HubDraw draw,float mouseX,float mouseY,String focus,boolean reduced,long now) {
        this.draw=draw;this.mouseX=mouseX;this.mouseY=mouseY;this.focus=focus;this.reduced=reduced;this.now=now;
        dt=lastFrame==0?1f/60:Math.min(.05f,(now-lastFrame)/1_000_000_000f);lastFrame=now;
        tabs();
        float alpha=1;bodyOffset=0;
        if(!selectedBoard.equals(displayedBoard)) {
            float progress=reduced?1:clamp((now-switchAt)/180_000_000f,0,1);
            if(progress>=1){displayedBoard=selectedBoard;enterAt=now;}
            else {alpha=1-HubMotion.ease(progress);bodyOffset=-6*(1-alpha);}
        }
        if(selectedBoard.equals(displayedBoard) && enterAt>0) {
            float progress=reduced?1:clamp((now-enterAt)/180_000_000f,0,1);
            alpha=HubMotion.ease(progress);bodyOffset=9*(1-alpha);if(progress>=1)enterAt=0;
        }
        draw.saveLayerAlpha(LEFT-8,205,WIDTH+16,629,alpha);
        State state=states.get(displayedBoard);
        state.poses.values().removeIf(pose->pose.leaving && pose.opacity.value(now,reduced)<=0);
        state.podium.values().removeIf(pose->pose.leaving && pose.opacity.value(now,reduced)<=0);
        if(state.scrollMotion!=null) {
            state.scroll=(float)state.scrollMotion.value(now,reduced);clampScroll(state);
            if(reduced || now-state.scrollMotion.at>=state.scrollMotion.duration)state.scrollMotion=null;
        }
        if(state.hasData() && state.header.status().equals("ready")) {podium(state);list(state);self(state);}
        else empty(state);
        draw.restore();this.draw=null;
    }
    private void tabs() {
        draw.roundRect(LEFT,158,330,42,13,c(0x80e7e8f4,0x38141e32));draw.strokeRoundRect(LEFT+.5f,158.5f,329,41,13,1,c(0xe6ffffff,0x1fadc1e3));
        float x=LEFT+4;
        for(int index=0;index<3;index++) {
            String board=HubRankingData.BOARDS.get(index);boolean chosen=board.equals(selectedBoard),hover=inside(x,162,104,34);
            if(chosen) {draw.boxShadow(x,162,104,34,9,0,2,8,0,0x166b648d);if(draw.dark())draw.linearGradientRoundRect(x,162,104,34,9,135,new int[]{0xff4b4467,0xff3b3c59},new float[]{0,1});else draw.roundRect(x,162,104,34,9,0xffffffff);}
            else if(hover)draw.roundRect(x,162,104,34,9,c(0x80ffffff,0x14c0b2e7));
            int color=chosen?new int[]{0xffa77a49,0xff7757ab,0xff578c97}[index]:0xff7a839d;
            draw.icon(ICONS[index],x+13,171.5f,15,color);text(LABELS[index],x+35,184,12,color,400);
            if(Objects.equals(focus,"rankings-tab-"+board))draw.strokeRoundRect(x-2,160,108,38,11,2,0xffa594d1);
            host.hit("rankings-tab-"+board,LABELS[index],x,162,104,34,()->choose(board));x+=108;
        }
        String scope=selectedBoard.equals("fortunes")?"全局收藏":"当前存档";
        float width=draw.textWidth(scope,11,400);draw.circle(1230-width-10,180,2.5f,0xffa99bc7);right(scope,1230,184,11,0xff8690a6,400);
        State state=states.get(selectedBoard);
        if(!state.error.isEmpty() && state.hasData()) {
            right("读取失败，重试",1210-width-12,184,10,0xffa68a9d,400);
            host.hit("rankings-retry","重新读取排行",1070,160,100,36,()->host.request("snapshot",new JsonObject()));
        }
    }
    private void podium(State state) {
        for(PodiumPose pose:state.podium.values()) {
            HubRankingData.Row row=pose.row;
            float leading=(float)pose.leading.value(now,reduced),width=(float)pose.width.value(now,reduced);
            float x=(float)pose.x.value(now,reduced),top=PODIUM_TOP+20*(1-leading),height=156+20*leading;
            float cx=x+width/2,head=56+14*leading,cardY=CARD_TOP+bodyOffset;
            boolean hover=!pose.leaving && inside(x,top,width,height);
            draw.saveLayerAlpha(x-35,PODIUM_TOP-45,width+70,260,(float)pose.opacity.value(now,reduced));
            draw.translate(0,(float)pose.offset.value(now,reduced));
            HubMotion.Tilt tilt=state.tilts.computeIfAbsent(row.playerId(),id->new HubMotion.Tilt(false));
            tilt.update(dt,reduced,hover,false,false,clamp((mouseX-cx)/(width/2),-1,1),clamp((mouseY-top-height/2)/(height/2),-1,1),3);
            draw.save();draw.perspective(cx,top+height/2,800,tilt.rx.value,tilt.ry.value,0,tilt.scale(1.015f),0,0);
            float colorProgress=pose.colorProgress(now,reduced);
            int ink=mixColor(c(pose.fromInk,pose.fromInkDark),c(rankColor(row.rank()),rankColorDark(row.rank())),colorProgress);
            int wash=mixColor(c(pose.fromWash,pose.fromWashDark),c(rankWash(row.rank()),rankWashDark(row.rank())),colorProgress);
            int glow=mixColor(c(pose.fromGlow,pose.fromGlowDark),c(rankGlow(row.rank()),rankGlowDark(row.rank())),colorProgress);
            boolean previousColors=draw.literalColors(true);
            float auraWidth=204+26*leading,auraHeight=104+23*leading;
            draw.radialGradientRoundRect(cx-auraWidth/2,cardY-8-auraHeight,auraWidth,auraHeight,auraHeight/2,
                    cx,cardY-8-auraHeight/2,auraWidth*.70710678f,auraHeight*.70710678f,
                    new int[]{alpha(glow,.34f+.04f*leading),0x00ffffff},new float[]{0,.69f});
            float headY=cardY-6-head*1.175f;
            HubHead.drawPlayer(draw,row.playerId(),row.playerId().equals(state.header.viewerId()),cx-head/2,headY,head,-23,-10);
            float cardX=x+11,cardWidth=width-22;
            draw.boxShadow(cardX,cardY,cardWidth,64,17,0,5,17,0,c(0x0c7884ac,0x330a132c));
            draw.captureGlassBackdrop(cardX,cardY,cardWidth,64);draw.glassBackdrop(cardX,cardY,cardWidth,64,17,PODIUM_GLASS);
            draw.linearGradientRoundRect(cardX,cardY,cardWidth,64,17,125,draw.dark()?new int[]{0xd939445d,0xbf303a52}:new int[]{0xf0ffffff,0x8fffffff},new float[]{0,1});
            draw.radialGradientRoundRect(cardX,cardY,cardWidth,64,17,cardX+cardWidth,cardY,cardWidth*1.41421356f,64*1.41421356f,
                    new int[]{alpha(wash,draw.dark()?1:.85f),wash&0xffffff},new float[]{0,.8f});
            draw.glassDecorations(cardX,cardY,cardWidth,64,17,PODIUM_GLASS);
            draw.insetBoxShadow(cardX,cardY,cardWidth,64,17,0,1,1,0,c(0xffffffff,0x14ebf0ff));
            draw.strokeRoundRect(cardX+.5f,cardY+.5f,cardWidth-1,63,17,1,c(0xfaffffff,0x2ed9e1fb));
            draw.literalColors(previousColors);
            float located=locateOpacity(state,row,1_500_000_000L);
            if(located>0) {
                draw.strokeRoundRect(cardX-1,cardY-1,cardWidth+2,66,18,2,alpha(0x1cad97d2,located));
                draw.strokeRoundRect(cardX,cardY,cardWidth,64,17,1,alpha(0xffb8a3d8,located));
            }
            rank(row,state,cardX+16,cardY+32,72,true,ink);
            float copyX=cardX+100,copyWidth=cardWidth-116;
            String name=fit(row.name(),copyWidth-(row.playerId().equals(state.header.viewerId())?17:0),14,600);
            text(name,copyX,cardY+23,14,0xff63708a,600);
            if(row.playerId().equals(state.header.viewerId()))text("我",copyX+draw.textWidth(name,14,600)+6,cardY+22,9,0xffa397b3,400);
            score(state,row,copyX,cardY+48,25,ink,false);
            float glare=tilt.glare(.42f,3);if(glare>0)draw.tiltGlare(cardX,cardY,cardWidth,64,17,.5f+tilt.ry.value/3*.46f,.5f-tilt.rx.value/3*.46f,glare);
            draw.restore();draw.restore();
            if(!pose.leaving&&selectedBoard.equals(displayedBoard)){
                host.hit("rankings-profile-head-"+row.playerId(),"查看"+row.name()+"的公开资料",cx-head*.75f,headY-head*.15f,head*1.5f,head*1.35f,()->host.openProfile(row.playerId()));
                host.hit("rankings-profile-name-"+row.playerId(),"查看"+row.name()+"的公开资料",copyX,cardY+7,copyWidth,23,()->host.openProfile(row.playerId()));
            }
        }
    }
    private void list(State state) {
        float y=LIST_TOP+bodyOffset;
        draw.boxShadow(LEFT,y,WIDTH,LIST_HEIGHT,18,0,5,20,0,0x097181a4);
        draw.roundRect(LEFT,y,WIDTH,LIST_HEIGHT,18,c(0xa6ffffff,0xc9283247));draw.strokeRoundRect(LEFT+.5f,y+.5f,WIDTH-1,LIST_HEIGHT-1,18,1,c(0xffffffff,0x2bd0dfff));
        text("排名",266,y+23,10,0xff939cb1,400);text("玩家",313,y+23,10,0xff939cb1,400);
        right(METRICS[HubRankingData.BOARDS.indexOf(displayedBoard)],1210,y+23,10,0xff939cb1,400);
        draw.line(LEFT,y+36,LEFT+WIDTH,y+36,1,c(0x73e7eaf3,0x16becbeb));
        draw.save();draw.clipRoundRect(LEFT+1,y+37,WIDTH-2,LIST_HEIGHT-38,17);
        int entranceOrder=0;
        if(state.rows().isEmpty())center("本榜已有 "+state.header.total()+" 位旅人留下记录",LEFT+WIDTH/2,y+LIST_HEIGHT/2+18,11,0xffa2aabc,400);
        for(Pose pose:state.poses.values()) {
            HubRankingData.Row row=pose.row;
            float rowY=ROW_TOP+bodyOffset+(float)pose.y.value(now,reduced)-state.scroll;
            if(rowY+ROW_HEIGHT<y+36 || rowY>y+LIST_HEIGHT)continue;
            float overlap=Math.min(y+LIST_HEIGHT,rowY+48)-Math.max(y+37,rowY);
            if(!pose.leaving && overlap>=24)pose.reveal(now,entranceOrder++*25_000_000L,reduced);
            draw.saveLayerAlpha(246,rowY,973,48,(float)pose.opacity.value(now,reduced));
            boolean hover=!pose.leaving && inside(246,rowY,973,48),selected=row.playerId().equals(state.selected);
            if(row.playerId().equals(state.header.viewerId()))draw.linearGradientRoundRect(246,rowY,973,48,10,110,new int[]{c(0x85ece7fd,0x21a483dc),c(0x40f0edff,0x10a892d5)},new float[]{0,1});
            if(hover)draw.roundRect(246,rowY,973,48,10,c(0x67eeedf7,0x0ec1b0e1));
            if(selected)draw.strokeRoundRect(246.5f,rowY+.5f,972,47,10,1,c(0x5cb5a7d1,0x57c0a4e6));
            float located=locateOpacity(state,row,1_600_000_000L);
            if(located>0) {
                draw.roundRect(246,rowY,973,48,10,alpha(c(0xffebe1ff,0x338e70c7),located));
                draw.strokeRoundRect(246.5f,rowY+.5f,972,47,10,1,alpha(c(0xffac94d2,0xffc1a0e6),located));
            }
            rank(row,state,252,rowY+24,48,false,0xff9ca4b6);
            draw.roundRect(313,rowY+11,26,26,7,0xffe9edf6);
            draw.save();draw.clipRoundRect(313,rowY+11,26,26,7);
            HubHead.drawPlayer2D(draw,row.playerId(),row.playerId().equals(state.header.viewerId()),313,rowY+11,26);draw.restore();
            boolean own=row.playerId().equals(state.header.viewerId());
            String name=fit(row.name(),own?210:225,12,500);
            text(name,349,rowY+29,12,own?0xff8670b6:0xff758099,500);
            if(own)text("我",349+draw.textWidth(name,12,500)+10,rowY+29,9,0xffa095b6,400);
            draw.roundRect(604,rowY+21,484,6,3,c(0x80e5eaf4,0x1499afda));
            float fraction=(float)Math.max(0,Math.min(1,pose.ratio.value(now,reduced)));
            if(fraction>0)draw.linearGradientRoundRect(604,rowY+21,484*fraction,6,3,90,new int[]{c(0xad9fbce0,0xa67fa5d1),c(0xada7afe0,0xa6979ed6),c(0xb8b29cda,0xb3b098d7)},new float[]{0,.48f,1});
            score(state,row,1207,rowY+29,14,0xff75829b,true);
            float hitY=Math.max(rowY,y+37),hitBottom=Math.min(rowY+48,y+LIST_HEIGHT-1);
            if(!pose.leaving && hitBottom>hitY && selectedBoard.equals(displayedBoard))host.hit("rankings-row-"+row.playerId(),row.name(),246,hitY,973,hitBottom-hitY,()->{
                state.selected=row.playerId();host.focus("rankings-list");
            });
            if(!pose.leaving&&hitBottom>hitY&&selectedBoard.equals(displayedBoard))host.hit("rankings-profile-row-"+row.playerId(),"查看"+row.name()+"的公开资料",313,hitY,270,hitBottom-hitY,()->host.openProfile(row.playerId()));
            draw.restore();
        }
        draw.restore();scrollbar(state);
    }
    private void rank(HubRankingData.Row row,State state,float x,float center,float width,boolean large,int color) {
        if(row.rank()==null){text("未入榜",x,center+4,12,0xffa69bb7,400);return;}
        boolean tied=tied(state,row),movement=!large && row.delta()!=null && row.delta()!=0 && !state.header.comparison().isEmpty();
        if(large) {
            String prefix=tied?"并列第":"第";float size=width==72?32:26;
            text(prefix,x,center+13,10,color,400);float start=x+draw.textWidth(prefix,10,400)+3;
            rankDigits(state,row,start,center+13,size,color,600,false);
            text("名",start+draw.textWidth(Long.toString(row.rank()),size,600)+3,center+13,10,color,400);return;
        }
        float numberCenter=x+(movement?11:width/2),baseline=center+(tied?-1:4);
        rankDigits(state,row,numberCenter,baseline,13,color,500,true);
        if(tied)center("并列",numberCenter,center+10,8,0xffb1a8bd,400);
        if(movement) {
            boolean up=row.delta()>0;float badgeY=center-(tied?12:7.5f),badgeX=x+25;
            draw.roundRect(badgeX,badgeY,23,15,5,up?0xffedf7f2:0xfffcf1f3);
            draw.strokeRoundRect(badgeX+.5f,badgeY+.5f,22,14,5,1,up?0xffd2e8dc:0xffefd8df);
            draw.icon(up?"arrow-up":"arrow-down",badgeX+2,badgeY+2.5f,10,up?0xff548d77:0xffb87680);
            text(fit(Long.toString(Math.abs(row.delta())),10,9,500),badgeX+12,badgeY+11,9,up?0xff548d77:0xffb87680,500);
        }
    }
    private void rankDigits(State state,HubRankingData.Row row,float x,float y,float size,int color,int weight,boolean centered) {
        RankGlyph glyph=state.ranks.get(row.playerId());
        float progress=glyph==null || glyph.previous==null || reduced?1:clamp((now-glyph.at)/220_000_000f,0,1);
        if(progress<1) {
            draw.saveLayerAlpha(x-size*5,y-size*2,size*10,size*4,1-progress);
            String previous=Long.toString(glyph.previous);
            text(previous,centered?x-draw.textWidth(previous,size,weight)/2:x,y-4*progress,size,color,weight);draw.restore();
        }
        draw.saveLayerAlpha(x-size*5,y-size*2,size*10,size*4,progress);
        String current=Long.toString(row.rank());
        text(current,centered?x-draw.textWidth(current,size,weight)/2:x,y+4*(1-progress),size,color,weight);draw.restore();
    }
    private boolean tied(State state,HubRankingData.Row row) {
        if(row.rank()==null)return false;
        Set<String> players=new HashSet<>();
        for(var value:state.header.rows())if(row.rank().equals(value.rank()))players.add(value.playerId());
        for(var value:state.rows())if(row.rank().equals(value.rank()))players.add(value.playerId());
        return players.size()>1;
    }
    private void score(State state,HubRankingData.Row row,float x,float baseline,float size,int color,boolean alignRight) {
        if(row.value()==null){text("—",x,baseline,size,color,600);return;}
        Pose pose=state.poses.get(row.playerId());Scalar scalar=state.scores.get(row.playerId());
        double shown=scalar!=null?scalar.value(now,reduced):pose!=null?pose.score.value(now,reduced):row.value();
        long value=Math.round(shown),minutes=value/60000;
        long shownNumber=displayedBoard.equals("online")?minutes/60:value;
        String digits=shownNumber>=1_000_000?COMPACT.format(shownNumber):NUMBER.format(shownNumber);
        String unit=displayedBoard.equals("online")?"小时":"种";
        String detail=displayedBoard.equals("online")?NUMBER.format(minutes%60)+" 分钟":"";
        float small=10,width=draw.textWidth(digits,size,600)+4+draw.textWidth(unit,small,400);
        if(!detail.isEmpty())width+=4+draw.textWidth(detail,9,400);
        float left=alignRight?x-width:x;
        text(digits,left,baseline,size,color,600);left+=draw.textWidth(digits,size,600)+4;
        text(unit,left,baseline,small,0xff8e94a7,400);left+=draw.textWidth(unit,small,400)+4;
        if(!detail.isEmpty())text(detail,left,baseline,9,0xff8e94a7,400);
    }
    private void self(State state) {
        float top=769+bodyOffset;
        draw.linearGradientRoundRect(LEFT,top,WIDTH,57,16,110,new int[]{c(0x95ede7fc,0x26a486d6),c(0xaaf5f1ff,0x21a18bcf),c(0x7ae9edfb,0x1c789bd2)},new float[]{0,.65f,1});
        draw.strokeRoundRect(LEFT+.5f,top+.5f,WIDTH-1,56,16,1,c(0xffffffff,0x33cdb6f4));
        text("我的位置",253,top+33,11,0xff8a7aa7,400);
        HubRankingData.Row row=state.header.self();
        if(row==null)row=new HubRankingData.Row(state.header.viewerId(),state.header.viewerName(),null,null,null);
        rank(row,state,312,top+28.5f,56,true,0xff8a72b3);
        float rankWidth=row.rank()==null?56:Math.max(56,draw.textWidth(tied(state,row)?"并列第":"第",10,400)
                +draw.textWidth(Long.toString(row.rank()),26,600)+draw.textWidth("名",10,400)+6);
        float avatarX=312+rankWidth+15;
        draw.save();draw.clipRoundRect(avatarX,top+15,27,27,7);HubHead.drawPlayer2D(draw,row.playerId(),true,avatarX,top+15,27);draw.restore();
        text(fit(row.name(),350,12,600),avatarX+36,top+33,12,0xff7f719b,600);
        String selfId=row.playerId();
        host.hit("rankings-profile-self","查看我的公开资料",avatarX,top+13,350,31,()->host.openProfile(selfId));
        if(row.value()!=null)score(state,row,row.rank()==null?1008:1140,top+33,14,0xff8a76ac,true);
        if(row.rank()!=null) {
            boolean hover=inside(1155,top+13,60,31);draw.roundRect(1155,top+13,60,31,8,hover?c(0xffffffff,0x2bd4c0f3):c(0x80ffffff,0x16d0bcf3));
            draw.strokeRoundRect(1155.5f,top+13.5f,59,30,8,1,c(0xa1ffffff,0x2bd7c2f3));text("定位",1164,top+33,10,0xff9b88b9,400);
            draw.icon("arrow-down",1189,top+21,13,0xff9b88b9);
            host.hit("rankings-locate","定位我的排名",1155,top+13,60,31,()->locateSelf(state));
        } else right(displayedBoard.equals("food")?"品尝食物后，留下你的记录":displayedBoard.equals("fortunes")?"收藏签笺后，留下你的记录":"下次更新后再来看看",1215,top+33,10,0xffa49aaf,400);
    }
    private void locateSelf(State state) {
        if(state.header==null || state.header.self()==null || state.header.self().rank()==null)return;
        String player=state.header.viewerId();state.locate=player;locateAt=System.nanoTime();
        if(state.leaders().stream().anyMatch(row->row.playerId().equals(player))) {
            host.feedback(state.header.self().rank()==1?"你位于榜首":"你位于第 "+state.header.self().rank()+" 名");return;
        }
        List<HubRankingData.Row> rows=state.rows();
        for(int index=0;index<rows.size();index++)if(rows.get(index).playerId().equals(player)) {
            float target=clamp(index*ROW_HEIGHT-(VIEW_HEIGHT-ROW_HEIGHT)/2,0,Math.max(0,rows.size()*ROW_HEIGHT-VIEW_HEIGHT));
            state.scrollMotion=new Scalar(state.scroll);state.scrollMotion.to(target,System.nanoTime(),380,reduced);
            if(reduced){state.scroll=target;state.scrollMotion=null;}host.focus("rankings-list");return;
        }
        requestPage(selectedBoard,0,state.header.revision(),true);
    }
    private float locateOpacity(State state,HubRankingData.Row row,long duration) {
        if(!row.playerId().equals(state.locate))return 0;
        float progress=clamp((now-locateAt)/(float)duration,0,1);
        if(progress>=1)return 0;
        return reduced?1:1-HubMotion.ease(clamp((progress-.35f)/.65f,0,1));
    }
    private void empty(State state) {
        float top=214+bodyOffset;
        draw.linearGradientRoundRect(LEFT,top,WIDTH,612,21,140,new int[]{0xbdffffff,0x63ffffff},new float[]{0,1});
        draw.strokeRoundRect(LEFT+.5f,top+.5f,WIDTH-1,611,21,1,0xdcffffff);
        String status=state.header==null?"loading":state.header.status();
        if(!state.error.isEmpty() && !status.equals("unsupported"))status="unavailable";
        boolean loading=status.equals("loading"),unavailable=status.equals("unavailable")||status.equals("error");
        draw.roundRect(701,top+190,66,66,22,c(0x8ae9e3f7,0x24af91d6));
        draw.save();
        if(loading && !reduced)draw.perspective(734,top+223,800,0,0,(now%1_400_000_000L)/1_400_000_000f*360,1,0,0);
        draw.icon(loading?"loader-circle":unavailable?"wifi-off":"trophy",719.5f,top+208.5f,29,0xffaaa0bd);draw.restore();
        String title=switch(status){case "unsupported"->"这个存档还未开启此排行";case "empty"->"榜上还没有记录";case "loading"->state.error.isEmpty()?"正在读取排行":"暂时无法读取排行";default->"暂时无法读取排行";};
        center(title,734,top+292,17,0xff7b809c,600);
        String detail=status.equals("empty")?"第一份记录，会从一位旅人开始。":status.equals("unsupported")?"对应模块安装并开启后，就能看到大家的记录。":state.error.isEmpty()?(loading?"稍等片刻，旅途的记录马上就到。":"连接恢复后，可以重新试试。"):state.error;
        center(fit(detail,430,12,400),734,top+327,12,0xffa0a4b6,400);
        if(unavailable) {
            draw.roundRect(679,top+350,110,34,9,0x99ffffff);center("重新读取",734,top+373,12,0xff8670b6,400);
            host.hit("rankings-retry","重新读取排行",679,top+350,110,34,()->host.request("snapshot",new JsonObject()));
        }
    }
    private void scrollbar(State state) {
        float content=state.rows().size()*ROW_HEIGHT;if(content<=VIEW_HEIGHT)return;
        float height=Math.max(28,VIEW_HEIGHT*VIEW_HEIGHT/content),y=ROW_TOP+bodyOffset+state.scroll/(content-VIEW_HEIGHT)*(VIEW_HEIGHT-height);
        draw.roundRect(1228,y,3,height,2,inside(LEFT,LIST_TOP+36,WIDTH,LIST_HEIGHT-36)?0x709ba6c1:0x3b9ba6c1);
    }
    boolean scroll(float x,float y,double amount) {
        if(!insideRect(x,y,LEFT,LIST_TOP+36,WIDTH,LIST_HEIGHT-36))return false;
        State state=states.get(selectedBoard);state.scrollMotion=null;state.scroll-=amount*50;clampScroll(state);boundary(state,amount);return true;
    }
    private void boundary(State state,double direction) {
        if(!state.hasData() || state.loading || state.pending!=null || state.pages.isEmpty())return;
        if(direction<0 && state.scroll+VIEW_HEIGHT>=state.rows().size()*ROW_HEIGHT-100 && (state.pages.lastKey()+1)*20<state.header.total())
            requestPage(selectedBoard,state.pages.lastKey()+1,state.header.revision(),false);
        else if(direction>0 && state.scroll<100 && state.pages.firstKey()>0)requestPage(selectedBoard,state.pages.firstKey()-1,state.header.revision(),false);
    }
    boolean pointerDown(float x,float y) {
        State state=states.get(selectedBoard);float content=state.rows().size()*ROW_HEIGHT;
        if(content<=VIEW_HEIGHT || !insideRect(x,y,1224,ROW_TOP,8,VIEW_HEIGHT))return false;
        float height=Math.max(28,VIEW_HEIGHT*VIEW_HEIGHT/content),top=ROW_TOP+state.scroll/(content-VIEW_HEIGHT)*(VIEW_HEIGHT-height);
        dragGrab=y>=top && y<=top+height?y-top:height/2;dragging=true;drag(y);host.focus("rankings-list");return true;
    }
    boolean pointerDrag(float y) { if(!dragging)return false;drag(y);return true; }
    private void drag(float y) {
        State state=states.get(selectedBoard);float content=state.rows().size()*ROW_HEIGHT,height=Math.max(28,VIEW_HEIGHT*VIEW_HEIGHT/Math.max(1,content));
        state.scrollMotion=null;float before=state.scroll;state.scroll=clamp((y-ROW_TOP-dragGrab)/Math.max(1,VIEW_HEIGHT-height),0,1)*Math.max(0,content-VIEW_HEIGHT);
        boundary(state,state.scroll>before?-1:1);
    }
    boolean pointerUp() { boolean was=dragging;dragging=false;return was; }
    boolean pointerOnRail(float x,float y) { return insideRect(x,y,1224,ROW_TOP,8,VIEW_HEIGHT); }
    boolean key(int key,String focus) {
        if(focus!=null && focus.startsWith("rankings-tab-")) {
            int index=HubRankingData.BOARDS.indexOf(selectedBoard);
            if(key==262)choose(HubRankingData.BOARDS.get((index+1)%3));
            else if(key==263)choose(HubRankingData.BOARDS.get((index+2)%3));
            else if(key==268)choose("food");else if(key==269)choose("online");else return false;
            return true;
        }
        if(!Objects.equals(focus,"rankings-list") && (focus==null || !focus.startsWith("rankings-row-")))return false;
        State state=states.get(selectedBoard);List<HubRankingData.Row> rows=state.rows();if(rows.isEmpty())return false;
        int index=0;for(int i=0;i<rows.size();i++)if(rows.get(i).playerId().equals(state.selected))index=i;
        if(key==264)index=Math.min(rows.size()-1,index+1);else if(key==265)index=Math.max(0,index-1);
        else if(key==268)index=0;else if(key==269)index=rows.size()-1;else return false;
        state.scrollMotion=null;state.selected=rows.get(index).playerId();
        if(index*ROW_HEIGHT<state.scroll)state.scroll=index*ROW_HEIGHT;
        else if((index+1)*ROW_HEIGHT>state.scroll+VIEW_HEIGHT)state.scroll=(index+1)*ROW_HEIGHT-VIEW_HEIGHT;
        clampScroll(state);boundary(state,key==264||key==269?-1:1);return true;
    }
    Map<String,String> playerNames() {
        Map<String,String> result=new LinkedHashMap<>();
        State state=states.get(selectedBoard);
        if(state.header==null)return result;
        for(var row:state.leaders())result.put(row.playerId(),row.name());
        List<HubRankingData.Row> rows=state.rows();
        int first=Math.max(0,(int)(state.scroll/ROW_HEIGHT)-1),last=Math.min(rows.size(),first+(int)(VIEW_HEIGHT/ROW_HEIGHT)+4);
        for(var row:rows.subList(first,last))result.put(row.playerId(),row.name());
        return result;
    }
    private void clampScroll(State state) { state.scroll=clamp(state.scroll,0,Math.max(0,state.rows().size()*ROW_HEIGHT-VIEW_HEIGHT)); }
    private boolean inside(float x,float y,float w,float h) { return insideRect(mouseX,mouseY,x,y,w,h); }
    private static boolean insideRect(float px,float py,float x,float y,float w,float h) { return px>=x && py>=y && px<x+w && py<y+h; }
    private static float clamp(float value,float min,float max) { return Math.max(min,Math.min(max,value)); }
    private static int alpha(int color,float opacity) { return (color&0xffffff)|Math.round(((color>>>24)&255)*opacity)<<24; }
    private static int mixColor(int from,int to,float progress) {
        int color=0;
        for(int shift=0;shift<=24;shift+=8)color|=Math.round(((from>>>shift)&255)*(1-progress)+((to>>>shift)&255)*progress)<<shift;
        return color;
    }
    private static int rankColor(Long rank) { return rank!=null && rank==1?0xffad8745:rank!=null && rank==3?0xff639b90:0xff7d83b0; }
    private static int rankWash(Long rank) { return rank!=null && rank==1?0xfff6e8c8:rank!=null && rank==3?0xffd9f0e7:0xffe4e9fb; }
    private static int rankGlow(Long rank) { return rank!=null && rank==1?0xffead093:rank!=null && rank==3?0xffb4dbcf:0xffc5cdf4; }
    private static int rankColorDark(Long rank) { return rank!=null && rank==1?0xffe1bf7f:rank!=null && rank==3?0xff91ccbb:0xffb4bbe9; }
    private static int rankWashDark(Long rank) { return rank!=null && rank==1?0xff504839:rank!=null && rank==3?0xff304d4c:0xff41476a; }
    private static int rankGlowDark(Long rank) { return rank!=null && rank==1?0xffbb9859:rank!=null && rank==3?0xff6aaa98:0xff9a9cd8; }
    private int c(int light,int dark){return draw.dark()?dark:light;}
    private void text(String value,float x,float y,float size,int color,int weight) { draw.text(value,x,y,size,color,weight); }
    private void center(String value,float x,float y,float size,int color,int weight) { text(value,x-draw.textWidth(value,size,weight)/2,y,size,color,weight); }
    private void right(String value,float x,float y,float size,int color,int weight) { text(value,x-draw.textWidth(value,size,weight),y,size,color,weight); }
    private String fit(String value,float width,float size,int weight) {
        if(draw.textWidth(value,size,weight)<=width)return value;
        int end=value.length();while(end>0 && draw.textWidth(value.substring(0,end)+"…",size,weight)>width)end=value.offsetByCodePoints(end,-1);
        return value.substring(0,end)+"…";
    }
}
