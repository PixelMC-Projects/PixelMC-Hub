/* Original integration: Apache-2.0. AnimatedList motion numbers and ClickSpark behavior
 * adapted from React Bits, copyright David Haz (2026), MIT + Commons Clause.
 * Those portions retain assets/pixelmc_hub/licenses/React-Bits-LICENSE.md. */
package com.pixelmc.hub.client.ui;

import com.google.gson.*;
import com.pixelmc.hub.client.render.HubDraw;
import io.github.humbleui.skija.Matrix33;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Native counterpart of the approved ChangelogPage/Article. All live data is connection-scoped. */
final class HubUpdatesView {
    interface Host {
        void hit(String id,String label,float x,float y,float w,float h,Runnable action);
        void request(String action,JsonObject payload);
        void feedback(String text);
        void focus(String id);
    }
    private record Run(String text,int weight) {}
    private record Block(String kind,List<Run> runs,int depth) {}
    private record Glyph(String text,int weight,float x) {}
    private record Line(List<Glyph> glyphs) {}
    private static final int LIST_WINDOW_PAGES=20,PACK_WINDOW_PAGES=3,COMMENT_WINDOW_PAGES=2,REPLY_WINDOW_PAGES=2;
    private record Box(float y,float height) {}
    private static final class Article {
        JsonObject entry;
        final NavigableMap<Integer,List<JsonObject>> pages=new TreeMap<>();
        final Map<String,NavigableMap<Integer,List<JsonObject>>> replies=new HashMap<>();
        int total;
        Article(JsonObject entry){this.entry=entry;}
        List<JsonObject> comments(){
            List<JsonObject> out=new ArrayList<>();Set<String> seen=new HashSet<>();
            for(var page:pages.values())for(JsonObject source:page){String id=s(source,"id","");if(!seen.add(id))continue;JsonObject copy=source.deepCopy();var windows=replies.get(id);if(windows!=null){JsonArray values=new JsonArray();Set<String> replyIds=new HashSet<>();for(var window:windows.descendingMap().values())for(JsonObject reply:window)if(replyIds.add(s(reply,"id",reply.toString())))values.add(reply);copy.add("replies",values);copy.addProperty("repliesPage",windows.lastKey());copy.addProperty("newestRepliesPage",windows.firstKey());}out.add(copy);}
            return out;
        }
    }
    private final Set<String> staleArticles=new HashSet<>();
    private final LinkedHashMap<String,Article> articleCache=new LinkedHashMap<>(8,.75f,true);
    private final NavigableMap<Integer,List<JsonObject>> listPages=new TreeMap<>(),packPages=new TreeMap<>();
    private final Map<String,Box> rowBoxes=new HashMap<>(),threadBoxes=new HashMap<>();
    private String displayedId="",latestId="",listAnchor="",readerAnchor="";
    private float listAnchorOffset,readerAnchorOffset;
    private boolean exitingArticle;
    private long exitAt,enterAt;
    private int unfilteredTotal,listLoadingPage=-1,packLoadingPage=-1;
    private final NavigableMap<Integer,List<JsonObject>> listRefreshData=new TreeMap<>();
    private final Deque<Integer> listRefreshQueue=new ArrayDeque<>();
    private boolean listRefreshActive;
    private final NavigableMap<Integer,List<JsonObject>> packRefreshData=new TreeMap<>();
    private final Deque<Integer> packRefreshQueue=new ArrayDeque<>();
    private boolean packRefreshActive;
    private final Deque<JsonObject> readRefreshQueue=new ArrayDeque<>();
    private String readRefreshId="",readRequestKey="";
    private boolean listResetRequested,readInFlight;
    private float ringY,ringHeight,ringVY,ringVH;
    private boolean ringReady;
    private long ringAt;
    private final Map<String,HubMotion.Transition> controlMotion=new HashMap<>();
    private final HubTextEditor searchEditor=new HubTextEditor(false,120),composeEditor=new HubTextEditor(true,1000);
    private record InputBox(float x,float y,float width,float height) {boolean contains(float xx,float yy){return xx>=x&&yy>=y&&xx<x+width&&yy<y+height;}}
    private InputBox searchInput,composeInput;
    private HubTextEditor draggingEditor;
    private record Rail(float x,float y,float height,float content,float scroll,float thumbY,float thumbHeight) {}
    private final Map<Integer,Rail> rails=new HashMap<>();
    private int pendingArrowDirection;
    private int dragRail;
    private float railGrab,railTrackY,railTrackHeight,railContentHeight;
    private final Host host;
    private JsonObject settings=new JsonObject(),entry;
    private final List<JsonObject> packs=new ArrayList<>(),rows=new ArrayList<>(),comments=new ArrayList<>();
    private final Map<String,String> drafts=new HashMap<>(),operations=new HashMap<>();
    private final Map<String,Integer> replyPages=new HashMap<>();
    private String packId="",selected="",search="",status="正在读取更新日志…",focus="",deleting="";
    private boolean loaded,live,active,packMenu,pending,searchAll,composeAll,listReady,animateNextList=true;
    private int packPage,packTotal;
    private float packScroll;
    private JsonObject selectedPack,demoSnapshot;
    private final Map<String,JsonObject> demoEntries=new LinkedHashMap<>();
    private record LayoutKey(List<Run> runs,float width,float size,int weight) {}
    private record MetricKey(int codepoint,float size,int weight) {}
    private final Map<LayoutKey,List<Line>> layouts=new LinkedHashMap<>(128,.75f,true);
    private final Map<MetricKey,Float> metrics=new LinkedHashMap<>(256,.75f,true);
    private List<Block> bodyBlocks=List.of();
    private int layoutWeight;
    private boolean readerDrawing;
    private void clearLayout(){layouts.clear();metrics.clear();layoutWeight=0;bodyBlocks=List.of();}
    private int listPage,total,commentsPage,commentsTotal,commentLimit=1000,searchCaret,composeCaret;
    private float listScroll,readerScroll,listHeight,readerHeight;
    private long selectedAt,listedAt,sparkAt,countAt;
    private float sparkX,sparkY,sparkScroll;
    private float pointerX=Float.NaN,pointerY=Float.NaN;
    private record Spark(float angle,float reach,float scale,float life,float spin,int color) {}
    private List<Spark> sparks=List.of();
    private final Map<String,Box> replyBoxes=new HashMap<>();
    private HubDraw d,editorDraw;
    private float mx,my;
    private boolean reduced;
    private String pressed="";
    private final HubMotion.Transition likeHover=new HubMotion.Transition(),likePress=new HubMotion.Transition();
    static final float X=236,Y=174,LW=252,GAP=20,RX=508,RW=724,H=630;
    HubUpdatesView(Host host){this.host=host;}
    void demo(){
        clear();live=false;
        try(var input=HubUpdatesView.class.getResourceAsStream("/assets/pixelmc_hub/data/updates-demo.json")){
            if(input==null)throw new IllegalStateException();JsonObject fixture=JsonParser.parseString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            if(!b(fixture,"demonstratorOnly",false))throw new IllegalStateException();demoSnapshot=obj(fixture,"snapshot");obj(fixture,"entries").entrySet().forEach(e->demoEntries.put(e.getKey(),e.getValue().getAsJsonObject().deepCopy()));accept("snapshot",demoSnapshot.deepCopy());
        }catch(java.io.IOException|RuntimeException error){status="本地演示数据暂不可用";}
    }
    private void perform(String action,JsonObject payload){
        if(live){host.request(action,payload);return;}
        if(action.equals("snapshot")||action.equals("refresh")){if(demoSnapshot!=null)accept("snapshot",demoSnapshot.deepCopy());return;}
        if(action.equals("list")){
            JsonObject page=new JsonObject();JsonArray filtered=new JsonArray();String q=s(payload,"search","").toLowerCase(Locale.ROOT);for(var value:demoEntries.values()){JsonObject item=obj(value,"entry");if(s(item,"packId","").equals(s(payload,"packId",""))&&(s(item,"title","")+version(item)+s(item,"summary","")).toLowerCase(Locale.ROOT).contains(q))filtered.add(item.deepCopy());}page.add("rows",filtered);page.addProperty("total",filtered.size());JsonObject counts=new JsonObject();long published=demoEntries.values().stream().filter(v->s(obj(v,"entry"),"packId","").equals(s(payload,"packId",""))).count();counts.addProperty("all",published);counts.addProperty("published",published);page.add("counts",counts);String latest=demoEntries.values().stream().map(v->obj(v,"entry")).filter(v->s(v,"packId","").equals(s(payload,"packId",""))).map(v->s(v,"id","")).findFirst().orElse("");page.addProperty("latestId",latest);page.addProperty("page",0);page.add("_hubRequest",payload.deepCopy());accept("list",page);return;
        }
        JsonObject value=demoEntries.get(s(payload,"id",""));if(value==null)return;
        if(action.equals("like")){JsonObject item=obj(value,"entry");boolean wanted=b(payload,"liked",false),old=b(item,"liked",false);item.addProperty("liked",wanted);item.addProperty("likesCount",Math.max(0,n(item,"likesCount",0)+(wanted==old?0:wanted?1:-1)));accept("like",item.deepCopy());host.feedback("本地演示 · "+(wanted?"感谢你的支持":"已取消点赞"));return;}
        if(action.equals("comment")){JsonObject c=new JsonObject();c.addProperty("id",UUID.randomUUID().toString());c.addProperty("text",s(payload,"text",""));c.addProperty("createdAt",System.currentTimeMillis());c.add("replies",new JsonArray());JsonArray a=new JsonArray();a.add(c);a.addAll(obj(value,"comments").getAsJsonArray("rows"));obj(value,"comments").add("rows",a);host.feedback("本地演示 · 留言已发送");}
        if(action.equals("deleteComment")){JsonArray a=new JsonArray();for(JsonObject c:objects(obj(value,"comments"),"rows"))if(!s(c,"id","").equals(s(payload,"commentId","")))a.add(c);obj(value,"comments").add("rows",a);host.feedback("本地演示 · 留言已删除");}
        obj(value,"comments").addProperty("total",obj(value,"comments").getAsJsonArray("rows").size());obj(value,"entry").addProperty("commentsCount",n(obj(value,"comments"),"total",0));JsonObject result=value.deepCopy();result.add("_hubRequest",payload.deepCopy());accept(action,result);
    }
    void live(){clear();live=true;status="正在读取更新日志…";}
    void clear(){editorDraw=null;controlMotion.clear();replyBoxes.clear();rails.clear();searchInput=composeInput=null;sparks=List.of();pointerX=pointerY=Float.NaN;clearLayout();staleArticles.clear();articleCache.clear();listPages.clear();packPages.clear();rowBoxes.clear();threadBoxes.clear();displayedId="";latestId="";listAnchor=readerAnchor="";exitingArticle=false;readInFlight=false;readRefreshQueue.clear();readRefreshId="";readRequestKey="";listRefreshData.clear();listRefreshQueue.clear();listRefreshActive=false;packRefreshData.clear();packRefreshQueue.clear();packRefreshActive=false;unfilteredTotal=0;listLoadingPage=packLoadingPage=-1;ringReady=false;dragRail=0;draggingEditor=null;searchEditor.setText("");composeEditor.setText("");settings=new JsonObject();entry=null;demoEntries.clear();demoSnapshot=null;packs.clear();rows.clear();comments.clear();drafts.clear();operations.clear();replyPages.clear();packId="";selected="";search="";deleting="";loaded=false;listReady=false;animateNextList=true;packPage=packTotal=0;packScroll=0;selectedPack=null;active=false;pending=false;packMenu=false;total=0;listPage=0;commentsPage=0;commentsTotal=0;readerScroll=listScroll=0;searchCaret=composeCaret=0;}
    boolean loaded(){return loaded;}
    int total(){return unfilteredTotal;}
    void status(String value){status=value;if(!value.isBlank()){listLoadingPage=packLoadingPage=-1;readInFlight=false;readRequestKey="";listRefreshActive=false;listRefreshQueue.clear();readRefreshQueue.clear();readRefreshId="";}}
    void pending(boolean value){pending=value;}
    void failure(String action,JsonObject payload,String message){readInFlight=false;
        if(message.equals("更新日志入口暂未开放")){settings.addProperty("updatesEnabled",false);purgeArticles();entry=null;rows.clear();comments.clear();selected="";total=0;}
        else if(message.equals("只接受已入服玩家本人的更新日志请求")||message.equals("记录不存在或不可见")&&selected.equals(s(payload,"id",""))){articleCache.remove(s(payload,"id",""));staleArticles.remove(s(payload,"id",""));displayedId="";clearLayout();entry=null;comments.clear();selected="";commentsTotal=0;rows.clear();total=0;}
    }
    void activate(){active=true;if(!loaded){perform("snapshot",new JsonObject());return;}ensureRead(false);}
    void deactivate(){active=false;packMenu=false;editorDraw=null;}
    private static String s(JsonObject o,String k,String fallback){return HubData.string(o,k,fallback);}
    private static int n(JsonObject o,String k,int fallback){try{return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsInt():fallback;}catch(RuntimeException e){return fallback;}}
    private static boolean b(JsonObject o,String k,boolean fallback){try{return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsBoolean():fallback;}catch(RuntimeException e){return fallback;}}
    private static JsonObject obj(JsonObject o,String k){return o!=null&&o.has(k)&&o.get(k).isJsonObject()?o.getAsJsonObject(k):new JsonObject();}
    private static List<JsonObject> objects(JsonObject o,String k){List<JsonObject> a=new ArrayList<>();if(o.has(k)&&o.get(k).isJsonArray())for(JsonElement e:o.getAsJsonArray(k))if(e.isJsonObject())a.add(e.getAsJsonObject().deepCopy());return a;}
    boolean enabled(){return b(settings,"updatesEnabled",true);}
    private String editingId(){return displayedId.isEmpty()?selected:displayedId;}
    private String draft(){return drafts.getOrDefault(editingId(),"");}
    private JsonObject query(){JsonObject p=new JsonObject();p.addProperty("packId",packId);p.addProperty("page",listPage);p.addProperty("search",search);return p;}
    private void list(){list(true);}
    private void list(boolean animate){if(animate)animateNextList=true;if(!packId.isBlank()){listLoadingPage=listPage;listResetRequested=animate&&listPage==0;perform("list",query());}}
    private void packPage(){JsonObject p=new JsonObject();p.addProperty("page",packPage);packLoadingPage=packPage;perform("packs",p);}
    private void ensureRead(boolean refresh){
        if(!active||selected.isEmpty())return;
        if(refresh&&articleCache.containsKey(selected)){beginArticleRefresh();return;}
        if(refresh||staleArticles.contains(selected)||!articleCache.containsKey(selected))read();else queueArticle(selected);
    }
    private void read(){
        if(selected.isEmpty())return;JsonObject payload=new JsonObject();payload.addProperty("id",selected);payload.addProperty("commentsPage",commentsPage);if(!replyPages.isEmpty()){JsonObject pages=new JsonObject();replyPages.forEach(pages::addProperty);payload.add("replyPages",pages);}
        if(readInFlight&&payload.toString().equals(readRequestKey))return;readInFlight=true;readRequestKey=payload.toString();perform("read",payload);
    }
    private void beginArticleRefresh(){
        Article article=articleCache.get(selected);if(article==null){read();return;}readRefreshQueue.clear();readRefreshId=selected;
        for(var page:article.pages.entrySet()){
            if(page.getKey()>0&&page.getKey()*12>=article.total)continue;JsonObject latest=new JsonObject(),oldest=new JsonObject();boolean extra=false;
            for(JsonObject c:page.getValue()){String id=s(c,"id","");var windows=article.replies.get(id);int first=windows==null?0:windows.firstKey(),last=windows==null?0:windows.lastKey();latest.addProperty(id,first);oldest.addProperty(id,last);if(first!=last)extra=true;}
            JsonObject request=new JsonObject();request.addProperty("commentsPage",page.getKey());request.add("replyPages",latest);readRefreshQueue.add(request);if(extra){JsonObject older=new JsonObject();older.addProperty("commentsPage",page.getKey());older.add("replyPages",oldest);readRefreshQueue.add(older);}
        }
        if(readRefreshQueue.isEmpty()){commentsPage=0;replyPages.clear();read();}else nextArticleRefresh();
    }
    private void nextArticleRefresh(){JsonObject request=readRefreshQueue.removeFirst();commentsPage=n(request,"commentsPage",0);replyPages.clear();obj(request,"replyPages").entrySet().forEach(e->replyPages.put(e.getKey(),e.getValue().getAsInt()));read();}
    private void beginPackRefresh(JsonObject snapshot){
        if(packPages.isEmpty())return;packRefreshData.clear();packRefreshQueue.clear();packRefreshActive=true;
        for(int page:packPages.keySet())if(page==0)packRefreshData.put(0,objects(snapshot,"packs"));else if(page*100<packTotal)packRefreshQueue.add(page);
        if(packRefreshQueue.isEmpty())finishPackRefresh();else{packPage=packRefreshQueue.removeFirst();packPage();}
    }
    private void finishPackRefresh(){
        String anchor="";float anchorOffset=0;if(!packs.isEmpty()){int at=Math.max(0,Math.min(packs.size()-1,(int)(packScroll/55)));anchor=s(packs.get(at),"id","");anchorOffset=packScroll-at*55;}
        packPages.clear();packPages.putAll(packRefreshData);packRefreshData.clear();packRefreshActive=false;packs.clear();for(var chunk:packPages.values())packs.addAll(chunk);
        for(int i=0;i<packs.size();i++)if(s(packs.get(i),"id","").equals(anchor)){packScroll=i*55+anchorOffset;break;}
        selectedPack=packs.stream().filter(pack->packId.equals(s(pack,"id",""))).findFirst().orElse(selectedPack);
    }
    private void beginListRefresh(JsonObject firstPage){
        if(listPages.isEmpty())return;rememberListAnchor();listRefreshData.clear();listRefreshQueue.clear();listRefreshActive=true;
        for(int page:listPages.keySet())if(page==0&&firstPage!=null)listRefreshData.put(0,objects(firstPage,"rows"));else if(page==0||page*12<total)listRefreshQueue.add(page);
        if(listRefreshQueue.isEmpty())finishListRefresh();else{listPage=listRefreshQueue.removeFirst();list(false);}
    }
    private void finishListRefresh(){listPages.clear();listPages.putAll(listRefreshData);listRefreshData.clear();listRefreshActive=false;flattenList(false);}
    private void select(String id){
        if(selected.equals(id))return;readRefreshQueue.clear();readRefreshId="";selected=id;commentsPage=0;replyPages.clear();deleting="";
        if(id.isEmpty()){entry=null;displayedId="";comments.clear();bodyBlocks=List.of();return;}
        if(articleCache.containsKey(id)&&!staleArticles.contains(id))queueArticle(id);else{exitingArticle=false;ensureRead(false);}
    }
    private void queueArticle(String id){
        if(!articleCache.containsKey(id))return;
        if(displayedId.equals(id)){showArticle(id,false);return;}
        if(displayedId.isEmpty()||reduced){showArticle(id,true);return;}
        if(!exitingArticle){exitingArticle=true;exitAt=System.nanoTime();}
    }
    private void showArticle(String id,boolean entrance){
        Article article=articleCache.get(id);if(article==null)return;
        boolean changed=!displayedId.equals(id);displayedId=id;entry=article.entry;comments.clear();comments.addAll(article.comments());commentsTotal=article.total;bodyBlocks=document(entry);
        if(changed){readerScroll=0;clearLayout();bodyBlocks=document(entry);composeEditor.setText(draft());composeEditor.select(draft().length(),draft().length());threadBoxes.clear();}
        if(changed||id.equals(selected))exitingArticle=false;if(entrance)enterAt=System.nanoTime();
    }
    private void advanceArticle(long now){if(exitingArticle&&(reduced||now-exitAt>=200_000_000L)){if(articleCache.containsKey(selected))showArticle(selected,true);else exitingArticle=false;}}
    private void flattenList(boolean resetSelection){
        rows.clear();Set<String> ids=new HashSet<>();for(var page:listPages.values())for(JsonObject row:page)if(ids.add(s(row,"id","")))rows.add(row);
        if(!listReady||animateNextList)listedAt=System.nanoTime();listReady=true;animateNextList=false;
        if(selected.isEmpty()||resetSelection&&rows.stream().noneMatch(row->selected.equals(s(row,"id",""))))select(rows.isEmpty()?"":s(rows.get(0),"id",""));
    }
    private void rememberListAnchor(){for(JsonObject row:rows){String id=s(row,"id","");Box box=rowBoxes.get(id);if(box!=null&&box.y+box.height>=listScroll){listAnchor=id;listAnchorOffset=box.y-listScroll;return;}}}
    private void rememberReaderAnchor(){for(var reply:replyBoxes.entrySet()){Box box=reply.getValue();if(box.y+box.height>=readerScroll&&box.y<=readerScroll+H){readerAnchor="reply:"+reply.getKey();readerAnchorOffset=box.y-readerScroll;return;}}for(JsonObject c:comments){String id=s(c,"id","");Box box=threadBoxes.get(id);if(box!=null&&box.y+box.height>=readerScroll){readerAnchor=id;readerAnchorOffset=box.y-readerScroll;return;}}}
    private void mergeRead(String action,JsonObject value,JsonObject request){
        JsonObject next=obj(value,"entry");String id=s(next,"id","");if(id.isEmpty())return;staleArticles.remove(id);readInFlight=false;readRequestKey="";
        Article article=articleCache.computeIfAbsent(id,key->new Article(next.deepCopy()));article.entry=next.deepCopy();JsonObject page=obj(value,"comments");int pageIndex=n(page,"page",0);article.total=n(page,"total",0);
        if(action.equals("deleteComment")){String removed=s(request,"commentId","");for(var commentsPage:article.pages.values())commentsPage.removeIf(c->s(c,"id","").equals(removed));article.replies.remove(removed);}
        if(id.equals(displayedId))rememberReaderAnchor();List<JsonObject> incoming=objects(page,"rows");
        for(JsonObject c:incoming){String cid=s(c,"id","");var windows=article.replies.computeIfAbsent(cid,key->new TreeMap<>());windows.put(n(c,"repliesPage",0),objects(c,"replies"));int replyTotal=n(c,"replyCount",objects(c,"replies").size());windows.entrySet().removeIf(window->window.getKey()>0&&window.getKey()*20>=replyTotal);while(windows.size()>REPLY_WINDOW_PAGES){int requested=n(c,"repliesPage",0);if(requested==windows.firstKey())windows.pollLastEntry();else windows.pollFirstEntry();}}
        article.pages.put(pageIndex,incoming);article.pages.entrySet().removeIf(chunk->chunk.getKey()>0&&chunk.getKey()*12>=article.total);while(article.pages.size()>COMMENT_WINDOW_PAGES){if(pageIndex==article.pages.firstKey())article.pages.pollLastEntry();else article.pages.pollFirstEntry();}
        Set<String> liveComments=new HashSet<>();for(var commentsPage:article.pages.values())for(var c:commentsPage)liveComments.add(s(c,"id",""));article.replies.keySet().retainAll(liveComments);
        trimArticles();replaceSummary(article.entry);if(selected.equals(id))queueArticle(id);else if(displayedId.equals(id))showArticle(id,false);
        if(action.equals("read")&&id.equals(readRefreshId)){if(readRefreshQueue.isEmpty())readRefreshId="";else nextArticleRefresh();}
    }
    private long articleWeight(Article article){long chars=s(article.entry,"body","").length()+obj(article.entry,"bodyDocument").toString().length();for(var page:article.pages.values())for(JsonObject comment:page)chars+=s(comment,"text","").length();for(var windows:article.replies.values())for(var replies:windows.values())for(JsonObject reply:replies)chars+=s(reply,"text","").length();return chars;}
    private void trimArticles(){long weight=articleCache.values().stream().mapToLong(this::articleWeight).sum();while((articleCache.size()>4||weight>4_000_000)&&articleCache.size()>1){String oldest=articleCache.keySet().iterator().next();if(oldest.equals(selected)){Article keep=articleCache.remove(oldest);articleCache.put(oldest,keep);continue;}weight-=articleWeight(articleCache.remove(oldest));staleArticles.remove(oldest);}}
    void accept(String action,JsonObject value){
        JsonObject request=obj(value,"_hubRequest");
        if(action.equals("snapshot")){
            boolean first=!loaded;readInFlight=false;readRequestKey="";listLoadingPage=packLoadingPage=-1;listRefreshActive=false;listRefreshQueue.clear();packRefreshActive=false;packRefreshQueue.clear();readRefreshQueue.clear();staleArticles.addAll(articleCache.keySet());settings=obj(value,"settings").deepCopy();commentLimit=Math.max(1,Math.min(2000,n(settings,"commentLimit",1000)));composeEditor.setMaxLength(commentLimit);
            packTotal=n(value,"packsTotal",objects(value,"packs").size());if(first||packPages.isEmpty()){packPages.clear();packPages.put(0,objects(value,"packs"));packs.clear();packs.addAll(packPages.get(0));}else beginPackRefresh(value);
            if(first){packId=s(value,"selectedPackId","");search="";searchEditor.setText("");listPage=0;}
            selectedPack=packs.stream().filter(p->packId.equals(s(p,"id",""))).findFirst().orElse(selectedPack);loaded=true;status="";
            if(!enabled()){purgeArticles();rows.clear();selected="";total=unfilteredTotal=0;return;}
            JsonObject page=obj(value,"list");if(first||packId.equals(s(value,"selectedPackId",""))&&search.isBlank()){
                if(first){listPages.clear();listPages.put(0,objects(page,"rows"));}
                total=n(page,"total",0);unfilteredTotal=n(obj(page,"counts"),"published",n(selectedPack==null?new JsonObject():selectedPack,"entriesCount",total));latestId=s(page,"latestId",latestId);if(first)flattenList(true);else beginListRefresh(page);
            }else {if(listPages.isEmpty()){listPage=0;list(false);}else beginListRefresh(null);}
            if(active)ensureRead(true);
        }else if(action.equals("packs")){
            int page=n(value,"page",0);if(page!=packLoadingPage)return;packLoadingPage=-1;
            if(packRefreshActive){packRefreshData.put(page,objects(value,"rows"));packTotal=n(value,"total",packTotal);while(!packRefreshQueue.isEmpty()&&packRefreshQueue.peekFirst()*100>=packTotal)packRefreshQueue.removeFirst();if(packRefreshQueue.isEmpty())finishPackRefresh();else{packPage=packRefreshQueue.removeFirst();packPage();}return;}int previousFirst=packPages.isEmpty()?page:packPages.firstKey();packPages.put(page,objects(value,"rows"));if(page<previousFirst)packScroll+=objects(value,"rows").size()*55;packTotal=n(value,"total",packs.size());
            if(packPages.size()>PACK_WINDOW_PAGES){if(page==packPages.firstKey())packPages.pollLastEntry();else {packPages.pollFirstEntry();packScroll=Math.max(0,packScroll-100*55);}}
            packs.clear();for(var chunk:packPages.values())packs.addAll(chunk);
        }else if(action.equals("list")){
            if(request.size()>0&&(!packId.equals(s(request,"packId",""))||!search.equals(s(request,"search",""))))return;
            int page=n(value,"page",0);if(listLoadingPage>=0&&page!=listLoadingPage)return;listLoadingPage=-1;
            if(listRefreshActive){listRefreshData.put(page,objects(value,"rows"));total=n(value,"total",total);unfilteredTotal=n(obj(value,"counts"),"published",unfilteredTotal);latestId=s(value,"latestId",latestId);while(!listRefreshQueue.isEmpty()&&listRefreshQueue.peekFirst()>0&&listRefreshQueue.peekFirst()*12>=total)listRefreshQueue.removeFirst();if(listRefreshQueue.isEmpty())finishListRefresh();else{listPage=listRefreshQueue.removeFirst();list(false);}return;}rememberListAnchor();
            boolean reset=listResetRequested&&page==0;if(reset)listPages.clear();listPages.put(page,objects(value,"rows"));
            if(listPages.size()>LIST_WINDOW_PAGES){if(page==listPages.firstKey())listPages.pollLastEntry();else listPages.pollFirstEntry();}
            total=n(value,"total",0);unfilteredTotal=n(obj(value,"counts"),"published",n(selectedPack==null?new JsonObject():selectedPack,"entriesCount",total));latestId=s(value,"latestId",latestId);flattenList(reset);if(active)ensureRead(false);
        }else if(action.equals("read")||action.equals("comment")||action.equals("deleteComment")){
            mergeRead(action,value,request);String id=s(obj(value,"entry"),"id","");
            if(action.equals("comment")){String submitted=s(request,"text","");if(submitted.equals(drafts.getOrDefault(id,""))){drafts.remove(id);operations.remove(id);if(id.equals(editingId()))composeEditor.setText("");}deleting="";}
            if(action.equals("deleteComment"))deleting="";status="";
        }else if(action.equals("like")){
            String id=s(value,"id","");int oldCount=entry!=null&&id.equals(displayedId)?n(entry,"likesCount",0):-1;for(JsonObject row:rows)if(id.equals(s(row,"id","")))copyReaction(row,value);Article article=articleCache.get(id);if(article!=null)copyReaction(article.entry,value);
            if(entry!=null&&id.equals(displayedId)){copyReaction(entry,value);if(oldCount!=n(value,"likesCount",oldCount))countAt=System.nanoTime();}status="";
        }
    }
    void acceptCachedUpdate(String id,JsonObject value){if(!id.equals(s(obj(value,"entry"),"id","")))throw new IllegalArgumentException("缓存记录标识不一致");mergeRead("read",value,new JsonObject());}
    void purgeCachedUpdates(java.util.Set<String> visibleIds,java.util.Set<String> visiblePacks){
        if(visibleIds.isEmpty()&&visiblePacks.isEmpty()){purgeArticles();listPages.clear();rows.clear();listReady=false;listLoadingPage=-1;readInFlight=false;selected="";total=unfilteredTotal=0;loaded=false;return;}
        articleCache.entrySet().removeIf(item->!visibleIds.contains(item.getKey())||!visiblePacks.contains(s(item.getValue().entry,"packId","")));
        for(var page:listPages.values())page.removeIf(row->!visibleIds.contains(s(row,"id",""))||!visiblePacks.contains(s(row,"packId","")));
        if(!displayedId.isEmpty()&&!articleCache.containsKey(displayedId)){entry=null;comments.clear();displayedId="";exitingArticle=false;clearLayout();}if(!selected.isEmpty()&&!visibleIds.contains(selected))selected="";flattenList(false);
    }
    private void purgeArticles(){staleArticles.clear();articleCache.clear();entry=null;comments.clear();displayedId="";exitingArticle=false;clearLayout();}
    private static void copyReaction(JsonObject target,JsonObject value){for(String key:List.of("liked","likesCount"))if(value.has(key))target.add(key,value.get(key).deepCopy());}
    private void replaceSummary(JsonObject value){
        JsonObject summary=value.deepCopy();summary.remove("body");summary.remove("bodyDocument");
        for(var page:listPages.values())for(int i=0;i<page.size();i++)if(s(page.get(i),"id","").equals(s(value,"id","")))page.set(i,summary.deepCopy());
        for(int i=0;i<rows.size();i++)if(s(rows.get(i),"id","").equals(s(value,"id","")))rows.set(i,summary.deepCopy());
    }
    private void acceptList(JsonObject value){listPages.clear();listPages.put(0,objects(value,"rows"));total=n(value,"total",rows.size());unfilteredTotal=n(obj(value,"counts"),"published",total);latestId=s(value,"latestId",listPages.get(0).isEmpty()?"":s(listPages.get(0).get(0),"id",""));flattenList(true);}
    String selectedId(){return selected;}
    private JsonObject chosen(){if(entry!=null&&!displayedId.isEmpty())return entry;return rows.stream().filter(row->selected.equals(s(row,"id",""))).findFirst().orElse(null);}
    void render(HubDraw draw,float mouseX,float mouseY,String focused,String pressedId,boolean reducedMotion,String player,long now){
        if(editorDraw!=draw){editorDraw=draw;searchEditor.invalidateLayout();composeEditor.invalidateLayout();}d=draw;rails.clear();mx=mouseX;my=mouseY;focus=focused==null?"":focused;pressed=pressedId==null?"":pressedId;reduced=reducedMotion;
        if(!loaded){empty(X,Y,996,H,status.isBlank()?"更新日志暂不可用":status,"服务器未提供更新日志时，不显示示例内容。");button("updates-retry","重新读取",X+424,Y+350,148,34,()->perform("refresh",new JsonObject()),true);d=null;return;}
        if(!enabled()){empty(X,Y,996,H,"更新日志暂未开放","发布后的更新会出现在这里。");d=null;return;}
        packPicker();searchBox();renderList(now);renderReader(player,now);if(packMenu)packMenu();d=null;
    }
    private void empty(float x,float y,float w,float h,String title,String hint){d.roundRect(x,y,w,h,20,c(0x88ffffff,0x09a3b4d8));d.strokeRoundRect(x,y,w,h,20,1,c(0xffffffff,0x20c3cfed));icon("file-text",x+w/2-16,y+h/2-58,32,0xffa4aabe);center(title,x+w/2,y+h/2,16,0xff697a98,600);center(hint,x+w/2,y+h/2+29,12,0xffa4aabe,400);}
    private String packLabel(JsonObject pack){return s(pack,"shortName",s(pack,"name",""))+s(pack,"version","");}
    private void packPicker(){if(packTotal<2)return;JsonObject current=selectedPack!=null?selectedPack:packs.isEmpty()?new JsonObject():packs.get(0);String name=packLabel(current);float w=Math.min(250,d.textWidth(name,12,400)+32);text(fit(name,w-26,12,400),1055-w,105,12,0xff697695,400);icon("chevron-down",1038,94,14,0xff697695);hit("updates-packs","选择整合包",1050-w,82,w,32,()->packMenu=!packMenu);}
    private void packMenu(){
        float x=875,y=119,w=280,viewport=Math.min(550,packs.size()*55),height=viewport+12;
        d.boxShadow(x,y,w,height,12,0,10,40,0,0x2b727ba7);d.roundRect(x,y,w,height,12,c(0xeeffffff,0xef2b344b));d.strokeRoundRect(x,y,w,height,12,1,c(0xffffffff,0x33c3d3f2));
        packScroll=clamp(packScroll,0,Math.max(0,packs.size()*55-viewport));d.save();d.clipRoundRect(x+6,y+6,w-12,viewport,7);
        for(int i=0;i<packs.size();i++){JsonObject p=packs.get(i);float yy=y+6+i*55-packScroll;if(yy+53<y+6||yy>y+6+viewport)continue;
            if(inside(x+6,yy,w-12,53))d.roundRect(x+6,yy,w-12,53,7,c(0xffedeafa,0x1cc0a5ee));text(fit(packLabel(p),w-50,12,400),x+16,yy+20,12,0xff697695,400);text(b(p,"current",false)?"当前整合包":"历史整合包",x+16,yy+38,10,0xff8b95aa,400);if(packId.equals(s(p,"id","")))icon("check",x+w-28,yy+17,14,0xff8065b5);
            clippedHit("updates-pack-"+i,"选择"+packLabel(p),x+6,yy,w-12,53,y+6,y+6+viewport,()->{selectedPack=p.deepCopy();packId=s(p,"id","");packMenu=false;search="";searchEditor.setText("");listPage=0;listScroll=0;list();});
        }d.restore();scrollbar(3,x+w-4,y+7,3,viewport-2,packs.size()*55,packScroll);
        if(packLoadingPage>=0)text("正在载入…",x+12,y+viewport-6,10,0xffa0a5ba,400);
    }
    private void searchBox(){
        d.roundRect(X,Y,LW,38,11,c(0x6bffffff,0x611b243a));d.strokeRoundRect(X,Y,LW,38,11,1,focus.equals("updates-search")?c(0xffbaa9e9,0x66c0a6f2):c(0xc9ffffff,0x2bb0c0e0));icon("search",X+12,Y+11,15,0xff939db3);
        searchInput=new InputBox(X+36,Y+9,LW-47,20);searchEditor.setText(search);searchEditor.layout(value->editorWidth(value,11),searchInput.width,searchInput.height,20);drawEditor(searchEditor,searchInput,11,0xff596c8e,"搜索标题或版本…",0xff99a3b9,focus.equals("updates-search"));
        hit("updates-search","搜索更新日志",X,Y,LW,38,()->host.focus("updates-search"));
    }
    private float editorWidth(String text,float size){return editorDraw!=null&&!editorDraw.isClosed()?editorDraw.textWidth(text,size,400):text.codePointCount(0,text.length())*size*.6f;}
    private void drawEditor(HubTextEditor editor,InputBox box,float size,int color,String placeholder,int placeholderColor,boolean focused){
        d.save();d.clipRoundRect(box.x,box.y,box.width,box.height,3);
        if(focused)for(HubTextEditor.Rect rect:editor.selectionRects())d.roundRect(box.x+rect.x(),box.y+rect.y(),rect.width(),rect.height(),2,0x33765bd4);
        if(editor.text().isEmpty())text(placeholder,box.x,box.y+(box.height>=22?17:15),size,placeholderColor,400);
        else for(HubTextEditor.Line line:editor.lines()){float y=box.y+line.y()-editor.scrollY()+(box.height>=22?17:15);if(y>=box.y-size&&y<=box.y+box.height+size)text(line.text(),box.x-editor.scrollX(),y,size,color,400);}
        if(focused&&System.nanoTime()/500_000_000L%2==0){HubTextEditor.Rect caret=editor.caretRect();d.line(box.x+caret.x(),box.y+caret.y()+2,box.x+caret.x(),box.y+caret.y()+caret.height()-2,1,0xff765bd4);}d.restore();
    }
    private float rowHeight(JsonObject row){return 15+15+9+measure(s(row,"title",""),LW-38,13,650,19.5f,0)+5+Math.min(34,measure(s(row,"summary",""),LW-38,10,400,17,0))+12+13+15;}
    private void updateRing(float targetY,float targetHeight,long now){
        if(reduced||!ringReady){ringY=targetY;ringHeight=targetHeight;ringVY=ringVH=0;ringReady=true;ringAt=now;return;}
        float dt=Math.min(.05f,Math.max(0,(now-ringAt)/1_000_000_000f));ringAt=now;int steps=Math.max(1,(int)Math.ceil(dt*240));float h=dt/steps;
        for(int i=0;i<steps;i++){ringVY+=(900*(targetY-ringY)-46.8f*ringVY)*h;ringVH+=(900*(targetHeight-ringHeight)-46.8f*ringVH)*h;ringY+=ringVY*h;ringHeight+=ringVH*h;}
        if(Math.abs(targetY-ringY)<.05f&&Math.abs(targetHeight-ringHeight)<.05f&&Math.abs(ringVY)+Math.abs(ringVH)<.5f){ringY=targetY;ringHeight=targetHeight;ringVY=ringVH=0;}
    }
    private void renderList(long now){
        float top=Y+50,height=H-50;rowBoxes.clear();float contentY=2;for(JsonObject row:rows){float rowHeight=rowHeight(row);rowBoxes.put(s(row,"id",""),new Box(contentY,rowHeight));contentY+=rowHeight+10;}listHeight=contentY;
        if(!listAnchor.isEmpty()){Box anchor=rowBoxes.get(listAnchor);if(anchor!=null)listScroll=anchor.y-listAnchorOffset;listAnchor="";}listScroll=clamp(listScroll,0,Math.max(0,listHeight-height));
        d.save();d.clipRoundRect(X,top,LW,height,0);
        if(rows.isEmpty()){icon("file-text",X+112,top+160,24,0xffa4aabe);center("没有匹配的记录",X+126,top+208,12,0xffa4aabe,400);if(!search.isEmpty())button("updates-clear","清除搜索",X+66,top+226,120,30,()->{search="";searchEditor.setText("");listPage=0;list();},true);}
        for(int i=0;i<rows.size();i++){
            JsonObject row=rows.get(i);String id=s(row,"id","");Box box=rowBoxes.get(id);float rh=box.height,yy=top+box.y-listScroll;
            float elapsed=(now-listedAt-i*45_000_000L)/1_000_000f;float enter=reduced?1:HubMotion.bezier(clamp(elapsed/300,0,1),.22f,1,.36f,1),motion=reduced?1:listSpring(clamp(elapsed/620,0,1)),ry=yy+(1-motion)*28;
            boolean on=id.equals(selected),hover=inside(X+2,ry,LW-8,rh);float brightness=pressed.equals("updates-row-"+id)?.92f:hover&&!on?.97f:1;
            if(ry+rh<top||ry>top+height)continue;
            d.saveLayerAlpha(X,ry,LW,rh,enter);d.saveBrightnessLayer(X-4,ry-4,LW+8,rh+10,brightness);d.save();d.canvas().concat(Matrix33.makeTranslate(X+LW/2,ry+rh/2).makeConcat(Matrix33.makeScale(.98f+.02f*motion)).makeConcat(Matrix33.makeTranslate(-X-LW/2,-ry-rh/2)));
            d.boxShadow(X+2,ry,LW-8,rh,14,0,2,8,0,0x087183aa);d.linearGradientRoundRect(X+2,ry,LW-8,rh,14,135,new int[]{0xd6ffffff,0xa6ffffff},new float[]{0,1});if(on)d.linearGradientRoundRect(X+2,ry,LW-8,rh,14,120,new int[]{c(0x77e7ecff,0x258a9ed2),c(0x88ede5ff,0x26b28cde)},new float[]{0,1});d.strokeRoundRect(X+2,ry,LW-8,rh,14,1,c(0xffffffff,0x22c2d1ef));
            float x=X+18,y=ry+25;text(fit(version(row),LW-84,10,600),x,y,10,on?0xff8064b6:0xff8985a7,600);right(time(row,"MM.dd"),X+LW-22,y,10,0xff929db4,400);y+=24;y=plain(s(row,"title",""),x,y,LW-38,13,650,0xff4b5874,19.5f,0)+5;plain(s(row,"summary",""),x,y,LW-38,10,400,0xff97a0b4,17,2);
            int color=listCategoryColor(s(row,"category","功能更新"));d.circle(x+2,ry+rh-20,2,listCategoryDot(s(row,"category","功能更新")));text(s(row,"category","功能更新"),x+10,ry+rh-16,10,color,400);if(b(row,"pinned",false)){icon("pin",X+LW-59,ry+rh-26,11,0xffa498ba);text("置顶",X+LW-45,ry+rh-16,10,0xffa498ba,400);}else if(id.equals(latestId))text("最新",X+LW-45,ry+rh-16,10,0xffa498ba,400);else icon("arrow-right",X+LW-32,ry+rh-27,12,0xffa498ba);d.restore();d.restore();d.restore();
            clippedHit("updates-row-"+id,s(row,"title",""),X+2,ry,LW-8,rh,top,top+height,()->select(id));
        }
        Box target=rowBoxes.get(selected);if(target!=null){updateRing(target.y,target.height,now);float ry=top+ringY-listScroll;d.boxShadow(X+2,ry,LW-8,ringHeight,14,0,3,13,0,0x158e76b9);d.roundRect(X+2,ry,LW-8,ringHeight,14,c(0x10c4b5ef,0x0cc4b5ef));d.strokeRoundRect(X+2,ry,LW-8,ringHeight,14,1,c(0x78ac98d4,0x73c0a4ed));}else ringReady=false;
        if(listLoadingPage>=0)text("正在载入…",X+18,Math.min(top+height-8,top+listHeight-listScroll+14),10,0xffa0a5ba,400);d.restore();scrollbar(1,X+LW-3,top,3,height,listHeight,listScroll);
    }
    private record Cover(float kickerHeight,float pillWidth,float pillHeight,float categoryY,boolean categoryOnNext,
                         float titleTop,float titleHeight,float summaryTop,float summaryHeight,float metaTop,float metaHeight,float height) {}
    private Cover cover(JsonObject current){
        float w=RW-56,kickerWidth=w-50;List<Line> versionLines=wrap(List.of(new Run(version(current),650)),kickerWidth-18,11,650);float versionWidth=0;
        for(Line line:versionLines)for(Glyph glyph:line.glyphs)versionWidth=Math.max(versionWidth,glyph.x+d.textWidth(glyph.text,11,glyph.weight));
        float pillWidth=Math.min(kickerWidth,versionWidth+18),pillHeight=versionLines.size()*19.8f+4;
        float extra=d.textWidth(s(current,"category","功能更新"),11,400)+(b(current,"pinned",false)?47:0);boolean next=pillWidth+10+extra>kickerWidth;
        float categoryY=next?pillHeight+10:0,kickerHeight=next?categoryY+20:Math.max(pillHeight,20),titleTop=26+kickerHeight+15,titleHeight=measure(s(current,"title",""),w-36,23,700,33.35f,0);
        boolean summary=!s(current,"summary","").isEmpty();float summaryTop=titleTop+titleHeight+(summary?8:0),summaryHeight=summary?measure(s(current,"summary",""),w*.92f,12,400,21.6f,0):0;
        float metaTop=summaryTop+summaryHeight+19,dateWidth=d.textWidth(time(current,"yyyy.MM.dd HH:mm"),10,400)+19,packWidth=Math.max(50,w-dateWidth-32),metaHeight=Math.max(18,measure(s(current,"packName","")+s(current,"packVersion",""),packWidth,10,400,18,0));
        return new Cover(kickerHeight,pillWidth,pillHeight,categoryY,next,titleTop,titleHeight,summaryTop,summaryHeight,metaTop,metaHeight,metaTop+metaHeight+22);
    }
    private void renderReader(String player,long now){
        advanceArticle(now);JsonObject current=chosen();composeInput=null;
        if(current==null){empty(RX,Y,RW,H,"这里还没有更新记录",search.isEmpty()?"发布后的更新会出现在这里。":"换个关键词试试。");if(!status.isBlank())button("updates-empty-retry","重新读取",RX+RW/2-55,Y+H/2+62,110,32,()->perform("refresh",new JsonObject()),!pending);return;}
        boolean actionable=entry!=null&&displayedId.equals(selected)&&!exitingArticle;
        d.boxShadow(RX,Y,RW,H,20,0,6,24,0,0x0b65729b);d.save();d.clipRoundRect(RX,Y,RW,H,20);d.roundRect(RX,Y,RW,H,20,c(0xffffffff,0xff293349));readerDrawing=true;
        readerScroll=clamp(readerScroll,0,Math.max(0,readerHeight-H));float animation=1,move=0;
        if(!reduced){if(exitingArticle){float p=HubMotion.bezier(clamp((now-exitAt)/200_000_000f,0,1),0,0,.58f,1);animation=1-p;move=-5*p;}else {float p=HubMotion.bezier(clamp((now-enterAt)/200_000_000f,0,1),0,0,.58f,1);animation=p;move=10*(1-p);}}
        float y=Y-readerScroll+move;d.saveLayerAlpha(RX,Y,RW,H,animation);Cover cover=cover(current);float x=RX+28,w=RW-56;
        d.linearGradientRoundRect(RX,y,RW,cover.height,0,122,new int[]{c(0xfff4f8ff,0xff30405c),c(0xfff8f6ff,0xff383750)},new float[]{0,1});d.radialGradientRoundRect(RX,y,RW,cover.height,0,RX+RW*.94f,y,RW*.65f,cover.height,new int[]{c(0x80dcd6ff,0x299780c7),c(0x00dcd6ff,0x009780c7)},new float[]{0,1});
        d.roundRect(x,y+26,cover.pillWidth,cover.pillHeight,6,c(0xaeffffff,0x1ac4ace2));d.strokeRoundRect(x,y+26,cover.pillWidth,cover.pillHeight,6,1,c(0xffffffff,0x2bccbbeb));plain(version(current),x+9,y+26+13,cover.pillWidth-18,11,650,0xff7258b4,19.8f,0);
        float categoryX=cover.categoryOnNext?x:x+cover.pillWidth+10,categoryBaseline=y+26+cover.categoryY+14;text(s(current,"category","功能更新"),categoryX,categoryBaseline,11,0xff7c719f,400);if(b(current,"pinned",false)){float pinX=categoryX+d.textWidth(s(current,"category","功能更新"),11,400)+10;icon("pin",pinX,categoryBaseline-11,12,0xff7c719f);text("置顶",pinX+15,categoryBaseline,11,0xff7c719f,400);}
        categoryArt(s(current,"category","功能更新"),RX+RW-75,y+33);plain(s(current,"title",""),x,y+cover.titleTop+23,w-36,23,700,c(0xff26344f,0xffe0e8f7),33.35f,0);if(cover.summaryHeight>0)plain(s(current,"summary",""),x,y+cover.summaryTop+12,w*.92f,12,400,0xff73819a,21.6f,0);
        float metaY=y+cover.metaTop+10;icon("calendar-days",x,metaY-11,13,0xff8791a6);text(time(current,"yyyy.MM.dd HH:mm"),x+19,metaY,10,0xff8791a6,400);float tx=x+19+d.textWidth(time(current,"yyyy.MM.dd HH:mm"),10,400)+16;d.line(tx,metaY-8,tx,metaY+1,1,0xffcfd5e6);plain(s(current,"packName","")+s(current,"packVersion",""),tx+16,metaY,w-(tx-x)-16,10,400,0xff8791a6,18,0);d.line(RX,y+cover.height,RX+RW,y+cover.height,1,0xffe8ebf5);
        y+=cover.height+7;
        if(entry==null){text(status.isBlank()?"正在读取正文…":status,x,y+30,12,0xff8994aa,400);y+=100;}
        else for(Block block:bodyBlocks){boolean heading=block.kind.equals("heading"),bullet=block.kind.equals("bullet"),continuation=block.kind.equals("listParagraph");float size=heading?13:12,line=heading?19.5f:bullet||continuation?22.8f:23.4f;y+=heading?22:bullet?4:continuation?0:16;float indent=bullet||continuation?18+block.depth*14:0;if(heading){d.linearGradientRoundRect(x,y+2,3,12,2,180,new int[]{0xff6f9de1,0xffa889d9},new float[]{0,1});indent=11;}if(bullet)d.circle(x+indent-8,y+8,1.8f,0xffa295ca);y=rich(block.runs,x+indent,y+size,w-indent,size,heading?650:400,heading?0xff384466:0xff66748e,line);}
        y+=24;float reactionY=y;d.line(RX,y,RX+RW,y,1,0xffeceef7);y+=19;text("这次更新怎么样？",x,y+12,12,0xff5b6881,550);text("一个赞，就是一份支持。",x,y+30,10,0xffa0a7b8,400);
        boolean liked=b(current,"liked",false),likesEnabled=b(settings,"likesEnabled",true);float bw=118,bx=RX+RW-28-bw;
        float hover=likeHover.sample(likesEnabled&&inside(bx,y,bw,36)?1:0,now,180_000_000L,reduced),press=likePress.sample(pressed.equals("updates-like")?1:0,now,180_000_000L,reduced);
        d.saveLayerAlpha(bx,y,bw,36,likesEnabled?1:.45f);d.save();d.canvas().concat(Matrix33.makeTranslate(bx+bw/2,y+18-hover).makeConcat(Matrix33.makeScale(1-.04f*press)).makeConcat(Matrix33.makeTranslate(-bx-bw/2,-y-18)));
        d.linearGradientRoundRect(bx,y,bw,36,10,110,new int[]{c(liked?0xfff0ecfc:mix(0xfffaf9ff,0xfff0eafb,hover),liked?0x29aa83dd:mix(0x17b59bd7,0x29c1a4e8,hover)),c(liked?0xfff7eefb:mix(0xfffaf9ff,0xfff0eafb,hover),liked?0x1fc48acb:mix(0x17b59bd7,0x29c1a4e8,hover))},new float[]{0,1});d.strokeRoundRect(bx,y,bw,36,10,1,liked?c(0xffe2d7f3,0x40d2a9ed):c(0xffe8e3f5,0x29c2a8e1));icon(liked?"heart-filled":"heart",bx+12,y+9,17,liked?0xff9c76c3:0xff9b8caf);text(liked?"已点赞":"点赞",bx+36,y+23,11,liked?0xff9c76c3:0xff9b8caf,400);d.line(bx+80,y+11,bx+80,y+25,1,0xffd9cfe8);
        float countProgress=reduced?1:1-(float)Math.pow(1-clamp((now-countAt)/200_000_000f,0,1),3);d.saveLayerAlpha(bx+82,y+5,34,29,.4f+.6f*countProgress);text(fit(Integer.toString(n(current,"likesCount",0)),26,11,400),bx+87,y+23+3*(1-countProgress),11,0xff9b8caf,400);d.restore();d.restore();d.restore();float buttonY=y;
        clippedHit("updates-like",liked?"取消点赞":"为这次更新点赞",bx,y,bw,36,Y,Y+H,()->{if(!liked&&!reduced)spawnSpark(bx,buttonY,bw,36);JsonObject payload=new JsonObject();payload.addProperty("id",displayedId);payload.addProperty("liked",!liked);perform("like",payload);},likesEnabled&&!pending&&actionable);
        drawSparks(now);
        y=reactionY+76;d.linearGradientRoundRect(RX,y,RW,Math.max(H,readerHeight),0,180,new int[]{c(0xfff9faff,0xff252f45),c(0xffffffff,0xff293349)},new float[]{0,.25f});d.line(RX,y,RX+RW,y,1,0xffedeef6);y+=24;icon("message-circle",x,y,16,0xff66728d);text("我的留言",x+23,y+13,13,0xff66728d,600);text(Integer.toString(commentsTotal),x+86,y+13,10,0xffa0a5ba,400);
        float privacyWidth=d.textWidth("仅你与管理员可见",10,400);icon("lock-keyhole",RX+RW-28-privacyWidth-16,y+2,12,0xff9c9bb2);right("仅你与管理员可见",RX+RW-28,y+13,10,0xff9c9bb2,400);y+=30;
        if(b(settings,"commentsEnabled",true)){
            float inputW=w-172;composeEditor.setMaxLength(commentLimit);composeEditor.setText(draft());composeEditor.layout(value->editorWidth(value,12),inputW-10,160,22);float ih=Math.min(168,Math.max(30,composeEditor.contentHeight()+8)),ch=ih+14;
            d.roundRect(x,y,w,ch,12,c(0xffffffff,0x991b263b));d.strokeRoundRect(x,y,w,ch,12,1,focus.equals("updates-compose")?c(0xffbca8df,0x66c5a8ed):c(0xffe1e5f1,0x30adc1e5));composeInput=new InputBox(x+13,y+11,inputW-10,ih-8);composeEditor.layout(value->editorWidth(value,12),composeInput.width,composeInput.height,22);drawEditor(composeEditor,composeInput,12,0xff56627d,"写下想法，或遇到的问题…",0xffa0a9bc,focus.equals("updates-compose"));
            clippedHit("updates-compose","给管理员留言",x+8,y+7,inputW,ih,Y,Y+H,()->host.focus("updates-compose"),actionable);text(draft().length()+" / "+commentLimit,x+w-158,y+ch-17,9,0xffacb3c3,400);buttonClipped("updates-send","发送留言",x+w-89,y+ch-37,79,29,()->submit(),!pending&&!draft().isBlank()&&actionable);y+=ch+2;
        }else y=plain("管理员已暂停接收新留言，已有的对话仍然保留。",x,y+14,w,11,400,0xff979cb0,22,0)+4;
        positionThreads(y+readerScroll-Y,w);if(!readerAnchor.isEmpty()){Box anchor=readerAnchor.startsWith("reply:")?replyBoxes.get(readerAnchor.substring(6)):threadBoxes.get(readerAnchor);if(anchor!=null){float next=anchor.y-readerAnchorOffset,delta=next-readerScroll;readerScroll=Math.max(0,next);y-=delta;}readerAnchor="";}
        Article article=articleCache.get(displayedId);if(article!=null&&!article.pages.isEmpty()&&article.pages.firstKey()>0){text("向上滚动载入较新的留言",x,y+17,10,0xffa7adbf,400);y+=24;}
        for(JsonObject c:comments){
            String cid=s(c,"id","");y+=20;
            if(y+25>Y&&y<Y+H){d.roundRect(x,y,25,25,5,0xffe9edf6);d.save();d.clipRoundRect(x,y,25,25,5);HubHead.draw2D(d,x,y,25);d.restore();d.strokeRoundRect(x,y,25,25,5,1,0x22a9b2cd);}
            text(player,x+32,y+16,11,0xff67708a,600);text("我",x+38+d.textWidth(player,11,600),y+16,9,0xffa7adbf,400);right(time(c,"yyyy.MM.dd HH:mm"),RX+RW-55,y+15,9,0xffa7adc0,400);boolean deleteHover=inside(RX+RW-46,y,24,24);icon("trash-2",RX+RW-41,y+5,13,deleteHover?0xffb86f81:0xffb1b4c4);clippedHit("updates-delete-"+cid,"删除我的留言",RX+RW-46,y,24,24,Y,Y+H,()->deleting=cid,!pending&&actionable);y+=38;y=plain(s(c,"text",""),x,y,w,12,400,0xff77809a,22.2f,0);
            List<JsonObject> replies=objects(c,"replies");int replyCount=n(c,"replyCount",replies.size()),oldestPage=n(c,"repliesPage",0),newestPage=n(c,"newestRepliesPage",0);
            if((oldestPage+1)*20<replyCount){text(readInFlight?"正在载入较早回复…":"向上滚动载入较早回复",x+26,y+20,10,0xffa7adbf,400);y+=27;}
            for(JsonObject reply:replies){y+=12;float rh=measure(s(reply,"text",""),w-39,11,400,20.35f,0)+38;if(y+rh>Y&&y<Y+H){d.roundRect(x+11,y,w-11,rh,9,c(0xfff6f5fb,0x10b9a0e3));d.rect(x+11,y,2,rh,c(0xffd9d1ec,0x4ab89ae4));}text("管理员回复",x+26,y+20,10,0xff9282b0,400);right(time(reply,"yyyy.MM.dd HH:mm"),RX+RW-41,y+20,9,0xffaca9bd,400);plain(s(reply,"text",""),x+26,y+40,w-39,11,400,0xff7a7490,20.35f,0);y+=rh;}
            if(newestPage>0){text("向下滚动载入较新回复",x+26,y+20,10,0xffa7adbf,400);y+=27;}
            if(replies.isEmpty()){text("已送达，等待管理员回复",x,y+19,10,0xffacb1c0,400);y+=25;}
            if(deleting.equals(cid)){y+=10;d.roundRect(x,y,w,38,8,c(0xfffcf3f5,0x1aca7695));text("删除这条留言及回复？",x+10,y+24,11,0xffa57683,400);buttonClipped("updates-delete-cancel","取消",x+w-134,y+4,52,30,()->deleting="",true);buttonClipped("updates-delete-confirm","确认删除",x+w-77,y+4,67,30,()->{JsonObject payload=new JsonObject();payload.addProperty("id",displayedId);payload.addProperty("commentId",cid);perform("deleteComment",payload);},!pending&&actionable);y+=38;}y+=19;d.line(x,y,x+w,y,1,0xffecedf5);
        }
        if(comments.isEmpty()){y+=30;icon("lock-keyhole",x+15,y-3,15,0xffa6aec0);text("你的留言和回复会留在这里。其他玩家看不到。",x+39,y+9,11,0xffa6aec0,400);y+=38;}
        if(article!=null&&!article.pages.isEmpty()&&(article.pages.lastKey()+1)*12<commentsTotal){text(readInFlight?"正在载入较早留言…":"继续向下滚动载入较早留言",x,y+22,10,0xffa7adbf,400);y+=32;}
        if(!status.isBlank()){y+=14;y=plain(status,x,y+12,w,11,400,0xffa06c80,20,0);buttonClipped("updates-read-retry","重新读取",x,y+4,100,30,()->ensureRead(true),!pending);y+=38;}
        y+=28;readerHeight=y+readerScroll-Y-move;d.restore();d.restore();d.strokeRoundRect(RX,Y,RW,H,20,1,c(0xffffffff,0x2bd0dfff));scrollbar(2,RX+RW-4,Y+8,3,H-16,readerHeight,readerScroll);readerDrawing=false;
    }
    private void positionThreads(float y,float width){
        threadBoxes.clear();replyBoxes.clear();Article article=articleCache.get(displayedId);if(article!=null&&!article.pages.isEmpty()&&article.pages.firstKey()>0)y+=24;
        for(JsonObject c:comments){String cid=s(c,"id","");float start=y;y+=20+38+measure(s(c,"text",""),width,12,400,22.2f,0);List<JsonObject> replies=objects(c,"replies");if((n(c,"repliesPage",0)+1)*20<n(c,"replyCount",replies.size()))y+=27;
            for(JsonObject reply:replies){y+=12;float h=measure(s(reply,"text",""),width-39,11,400,20.35f,0)+38;replyBoxes.put(s(reply,"id",reply.toString()),new Box(y,h));y+=h;}
            if(n(c,"newestRepliesPage",0)>0)y+=27;if(replies.isEmpty())y+=25;if(deleting.equals(cid))y+=48;y+=19;threadBoxes.put(cid,new Box(start,y-start));
        }
    }
    private void submit(){
        if(pending||exitingArticle||!selected.equals(displayedId)||entry==null)return;String id=editingId(),text=draft().trim();if(text.isEmpty())return;String operation=operations.computeIfAbsent(id,key->UUID.randomUUID().toString());JsonObject payload=new JsonObject();payload.addProperty("id",id);payload.addProperty("text",text);payload.addProperty("operationId",operation);drafts.put(id,text);composeEditor.setText(text);perform("comment",payload);
    }
    private void spawnSpark(float x,float y,float width,float height){
        sparkX=Float.isFinite(pointerX)&&pointerX>=x&&pointerX<=x+width?pointerX:x+width/2;sparkY=Float.isFinite(pointerY)&&pointerY>=y&&pointerY<=y+height?pointerY:y+height/2;sparkScroll=readerScroll;sparkAt=System.nanoTime();List<Spark> values=new ArrayList<>();Random random=new Random(sparkAt);int offset=random.nextInt(3);int[] colors={0xff7699e6,0xffa884dd,0xffd3bbef};
        for(int i=0;i<7;i++)values.add(new Spark((float)(-Math.PI/2+i*Math.PI*2/7+(random.nextFloat()-.5)*.2*Math.PI*2/7*.9),1+(random.nextFloat()-.5f)*.14f,1+(random.nextFloat()-.5f)*.16f,1+(random.nextFloat()-.5f)*.1f,random.nextFloat()-.5f,colors[(i+offset)%3]));sparks=values;
    }
    private void drawSparks(long now){if(reduced||sparkAt==0)return;for(Spark spark:sparks){float p=(now-sparkAt)/(480_000_000f*spark.life);if(p<0||p>=1)continue;float e=1-(float)Math.pow(1-p,3),fade=p<.7f?1:1-smooth((p-.7f)/.3f),radius=3*spark.scale*(float)Math.pow(Math.sin(Math.PI*Math.min(1,p*1.1)),.7);d.sparkStar(sparkX+(float)Math.cos(spark.angle)*23*spark.reach*e,sparkY+sparkScroll-readerScroll+(float)Math.sin(spark.angle)*23*spark.reach*e,radius,spark.angle+(float)Math.PI/4+spark.spin*e*1.8f,withAlpha(spark.color,fade));}}
    private static float smooth(float value){float p=clamp(value,0,1);return p*p*(3-2*p);}
    private void categoryArt(String category,float x,float y){int color=categoryColor(category);d.save();d.canvas().concat(Matrix33.makeRotate(9,x+24,y+27));d.save();d.canvas().concat(Matrix33.makeRotate(-16,x+24,y+27));d.linearGradientRoundRect(x-7,y+3,47,56,10,135,new int[]{0x79b8c9f7,categoryWash(category)},new float[]{0,1});d.restore();d.boxShadow(x,y,48,54,12,2,6,15,0,0x247b65b5);d.linearGradientRoundRect(x,y,48,54,12,150,new int[]{c(0xb5ffffff,0x24c7d7ff),c(0x36ffffff,0x0ab7c3ed)},new float[]{0,1});d.strokeRoundRect(x,y,48,54,12,1,0xffffffff);icon("stroke-1.15/"+categoryIcon(category),x+7,y+10,34,c(color,mix(color,0xffe8eeff,.38f)));d.restore();}
    private static int listCategoryColor(String c){return switch(c){case "问题修复"->0xff7ca394;case "体验优化"->0xffa28bb8;default->0xff8190b5;};}
    private static int listCategoryDot(String c){return switch(c){case "问题修复"->0xff83b69c;case "体验优化"->0xffb19ac4;default->0xff8e9fd3;};}
    private static int categoryWash(String c){return switch(c){case "体验优化"->0x6ba8d0f4;case "问题修复"->0x6b9bdbc6;case "整合包更新"->0x6befd29c;case "通知"->0x6be7b7da;default->0x6bcdbbf6;};}
    private static int categoryColor(String c){return switch(c){case "体验优化"->0xff608cb7;case "问题修复"->0xff519986;case "整合包更新"->0xffb48b59;case "通知"->0xffb078a1;default->0xff8b73c5;};}
    private static String categoryIcon(String c){return switch(c){case "体验优化"->"wand-sparkles";case "问题修复"->"wrench";case "整合包更新"->"package-open";case "通知"->"megaphone";default->"sparkles";};}
    private String version(JsonObject row){String pack=s(row,"packName","")+s(row,"packVersion","");return pack+(pack.isEmpty()?"":"-")+s(row,"updateVersion","");}
    private String time(JsonObject o,String pattern){String key=o.has("publishedAt")?"publishedAt":"createdAt";try{long at=o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsLong():0;return at<=0?"时间未记录":Instant.ofEpochMilli(at).atZone(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern(pattern));}catch(RuntimeException ignored){return "时间未记录";}}
    private void scrollbar(int id,float x,float y,float w,float h,float content,float scroll){
        if(content<=h)return;float thumb=Math.max(28,h*h/content),sy=y+(h-thumb)*clamp(scroll/Math.max(1,content-h),0,1);boolean hover=dragRail==id||id==1&&inside(X,Y+50,LW,H-50)||id==2&&inside(RX,Y,RW,H)||id==3&&menuContains(mx,my);d.roundRect(x,sy,w,thumb,2,hover?c(0x739aa7c2,0x70acbee4):c(0x3d9aa7c2,0x3da2b2d6));rails.put(id,new Rail(x,y,h,content,scroll,sy,thumb));
    }
    private record Span(int start,int end,int weight) {}
    private List<Line> wrap(List<Run> runs,float width,float size,int defaultWeight){
        LayoutKey key=new LayoutKey(runs,width,size,defaultWeight);List<Line> cached=layouts.get(key);if(cached!=null)return cached;
        StringBuilder joined=new StringBuilder();List<Span> spans=new ArrayList<>();for(Run run:runs){int start=joined.length();joined.append(run.text);spans.add(new Span(start,joined.length(),run.weight==0?defaultWeight:run.weight));}String text=joined.toString();
        java.text.BreakIterator breaks=java.text.BreakIterator.getLineInstance(Locale.ROOT);breaks.setText(text);Set<Integer> opportunities=new HashSet<>();for(int at=breaks.first();at!=java.text.BreakIterator.DONE;at=breaks.next())opportunities.add(at);
        List<Line> lines=new ArrayList<>();int start=0;
        while(start<text.length()){
            int newline=text.indexOf('\n',start),limit=newline<0?text.length():newline;
            if(limit==start){lines.add(new Line(List.of()));start++;continue;}
            int at=start,lastBreak=-1,spanIndex=0;float advance=0;
            while(spanIndex+1<spans.size()&&spans.get(spanIndex).end<=at)spanIndex++;
            while(at<limit){int cp=text.codePointAt(at),next=at+Character.charCount(cp);while(spanIndex+1<spans.size()&&spans.get(spanIndex).end<=at)spanIndex++;int weight=spans.isEmpty()?defaultWeight:spans.get(spanIndex).weight;MetricKey mk=new MetricKey(cp,size,weight);Float known=metrics.get(mk);float cw;if(known==null){cw=d.textWidth(new String(Character.toChars(cp)),size,weight);metrics.put(mk,cw);if(metrics.size()>8192)metrics.remove(metrics.keySet().iterator().next());}else cw=known;if(advance+cw>width&&at>start)break;advance+=cw;at=next;if(opportunities.contains(at))lastBreak=at;}
            int end=at<limit&&lastBreak>start?lastBreak:at;if(end<=start)end=start+Character.charCount(text.codePointAt(start));
            List<Glyph> glyphs=lineGlyphs(text,spans,start,end,size);while(end>start+1&&!glyphs.isEmpty()){Glyph last=glyphs.get(glyphs.size()-1);if(last.x+d.textWidth(last.text,size,last.weight)<=width+.01f)break;int previous=text.offsetByCodePoints(end,-1);if(previous<=start)break;end=previous;glyphs=lineGlyphs(text,spans,start,end,size);}
            lines.add(new Line(List.copyOf(glyphs)));start=end;if(start==limit&&newline>=0){start++;if(start==text.length())lines.add(new Line(List.of()));}
        }
        if(lines.isEmpty())lines.add(new Line(List.of()));int weight=text.length();layouts.put(key,lines);layoutWeight+=weight;var iterator=layouts.entrySet().iterator();while((layouts.size()>8192||layoutWeight>2_000_000)&&iterator.hasNext()){var old=iterator.next();layoutWeight-=old.getKey().runs.stream().mapToInt(run->run.text.length()).sum();iterator.remove();}return lines;
    }
    private List<Glyph> lineGlyphs(String text,List<Span> spans,int start,int end,float size){List<Glyph> out=new ArrayList<>();float x=0;for(Span span:spans){int left=Math.max(start,span.start),right=Math.min(end,span.end);if(right<=left)continue;String part=text.substring(left,right);out.add(new Glyph(part,span.weight,x));x+=d.textWidth(part,size,span.weight);}return out;}
    private float rich(List<Run> runs,float x,float baseline,float width,float size,int weight,int color,float line){List<Line> lines=wrap(runs,width,size,weight);int start=readerDrawing?Math.max(0,(int)Math.floor((Y-baseline-size)/line)):0,end=readerDrawing?Math.min(lines.size(),(int)Math.ceil((Y+H-baseline+size)/line)):lines.size();for(int i=start;i<end;i++){Line row=lines.get(i);for(Glyph g:row.glyphs)text(g.text,x+g.x,baseline+i*line,size,g.weight==600?0xff435675:color,g.weight);}return baseline+lines.size()*line-size;}
    private float measure(String text,float width,float size,int weight,float line,int max){if(text==null||text.isEmpty())return 0;int count=wrap(List.of(new Run(text,weight)),width,size,weight).size();return (max>0?Math.min(max,count):count)*line;}
    private float plain(String text,float x,float baseline,float width,float size,int weight,int color,float line,int max){if(text==null||text.isEmpty())return baseline;List<Line> lines=wrap(List.of(new Run(text,weight)),width,size,weight);int count=max>0?Math.min(max,lines.size()):lines.size();int start=readerDrawing?Math.max(0,(int)Math.floor((Y-baseline-size)/line)):0,end=readerDrawing?Math.min(count,(int)Math.ceil((Y+H-baseline+size)/line)):count;for(int i=start;i<end;i++){Line row=lines.get(i);for(Glyph g:row.glyphs)text(max>0&&i==count-1&&lines.size()>count?fit(g.text+"…",width-g.x,size,weight):g.text,x+g.x,baseline+i*line,size,color,g.weight);}return baseline+count*line;}
    private static List<Block> document(JsonObject entry){List<Block> blocks=new ArrayList<>();JsonObject doc=obj(entry,"bodyDocument");if(doc.size()>0)parseBlocks(doc,blocks,0,0);else for(String line:s(entry,"body","").split("\n",-1)){if(line.isBlank())continue;boolean heading=line.matches("^#{1,3} .*"),bullet=line.matches("^[-*] .*");String value=heading?line.replaceFirst("^#{1,3} ",""):bullet?line.substring(2):line;blocks.add(new Block(heading?"heading":bullet?"bullet":"paragraph",inlineLegacy(value),0));}return blocks;}
    private static List<Run> inlineLegacy(String text){List<Run> runs=new ArrayList<>();java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\*\\*([^*]+)\\*\\*").matcher(text);int at=0;while(m.find()){if(m.start()>at)runs.add(new Run(text.substring(at,m.start()),0));runs.add(new Run(m.group(1),600));at=m.end();}if(at<text.length())runs.add(new Run(text.substring(at),0));return runs;}
    private static void parseBlocks(JsonObject node,List<Block> out,int depth,int listDepth){
        if(depth>16||out.size()>40000)return;String type=s(node,"type","");
        if(type.equals("paragraph")||type.equals("heading")){List<Run> runs=new ArrayList<>();parseInline(node,runs,0);out.add(new Block(type.equals("heading")?"heading":"paragraph",runs,0));return;}
        if(type.equals("listItem")){boolean first=true;for(JsonObject child:objects(node,"content")){String childType=s(child,"type","");if(childType.equals("paragraph")){List<Run> runs=new ArrayList<>();parseInline(child,runs,0);out.add(new Block(first?"bullet":"listParagraph",runs,Math.max(0,listDepth-1)));first=false;}else parseBlocks(child,out,depth+1,listDepth);}return;}
        if(!Set.of("doc","bulletList").contains(type))return;for(JsonObject child:objects(node,"content"))parseBlocks(child,out,depth+1,listDepth+(type.equals("bulletList")?1:0));
    }
    private static void parseInline(JsonObject node,List<Run> out,int depth){if(depth>16||out.size()>30000)return;String type=s(node,"type","");if(type.equals("text")){boolean bold=false;for(JsonObject mark:objects(node,"marks"))if(s(mark,"type","").equals("bold"))bold=true;out.add(new Run(s(node,"text",""),bold?600:0));}else if(type.equals("hardBreak"))out.add(new Run("\n",0));else if(type.equals("paragraph")||type.equals("heading"))for(JsonObject child:objects(node,"content"))parseInline(child,out,depth+1);}
    private boolean menuContains(float x,float y){return x>=875&&x<1155&&y>=119&&y<131+Math.min(550,packs.size()*55);}
    boolean scroll(float x,float y,double amount){
        if(packMenu&&menuContains(x,y)){packScroll-=amount*48;if(packLoadingPage<0&&!packPages.isEmpty()){if(amount<0&&packScroll+550>=packs.size()*55-160&&(packPages.lastKey()+1)*100<packTotal){packPage=packPages.lastKey()+1;packPage();}else if(amount>0&&packScroll<160&&packPages.firstKey()>0){packPage=packPages.firstKey()-1;packPage();}}return true;}
        if(composeInput!=null&&composeInput.contains(x,y)&&focus.equals("updates-compose")&&composeEditor.contentHeight()>composeInput.height){composeEditor.scrollBy(0,(float)-amount*22);return true;}
        if(x>=X&&x<X+LW&&y>=Y+50&&y<=Y+H){listScroll-=amount*48;loadListBoundary(amount);return true;}
        if(x>=RX&&x<=RX+RW&&y>=Y&&y<=Y+H){readerScroll-=amount*48;loadReaderBoundary(amount);return true;}return false;
    }
    private void loadListBoundary(double direction){if(listLoadingPage>=0||listPages.isEmpty())return;if(direction<0&&listScroll+H-50>=listHeight-180&&(listPages.lastKey()+1)*12<total){listPage=listPages.lastKey()+1;list(false);}else if(direction>0&&listScroll<180&&listPages.firstKey()>0){listPage=listPages.firstKey()-1;list(false);}}
    private void loadReaderBoundary(double direction){
        if(readInFlight||!selected.equals(displayedId))return;Article article=articleCache.get(displayedId);if(article==null||article.pages.isEmpty())return;
        if(direction<0){for(JsonObject c:comments){String cid=s(c,"id","");Box box=threadBoxes.get(cid);var windows=article.replies.get(cid);if(box!=null&&windows!=null&&windows.firstKey()>0&&box.y+box.height>=readerScroll&&box.y+box.height<=readerScroll+H+180){commentsPage=pageOf(article,cid);replyPages.clear();replyPages.put(cid,windows.firstKey()-1);read();return;}}}
        if(direction<0&&readerScroll+H>=readerHeight-180&&(article.pages.lastKey()+1)*12<article.total){commentsPage=article.pages.lastKey()+1;read();return;}
        if(direction>0){
            for(JsonObject c:comments){String id=s(c,"id","");Box box=threadBoxes.get(id);var windows=article.replies.get(id);if(box!=null&&windows!=null&&box.y+90>=readerScroll&&box.y<readerScroll+H&& (windows.lastKey()+1)*20<n(c,"replyCount",objects(c,"replies").size())){commentsPage=pageOf(article,id);replyPages.clear();replyPages.put(id,windows.lastKey()+1);read();return;}}
            if(readerScroll<180&&article.pages.firstKey()>0){commentsPage=article.pages.firstKey()-1;replyPages.clear();read();}
        }
    }
    private int pageOf(Article article,String comment){for(var page:article.pages.entrySet())for(JsonObject c:page.getValue())if(s(c,"id","").equals(comment))return page.getKey();return 0;}
    void pointerAt(float x,float y){pointerX=x;pointerY=y;}
    boolean pointerDown(float x,float y,boolean shift){
        pointerAt(x,y);
        if(packMenu&&!menuContains(x,y)&&!(y>=82&&y<=114&&x>=780&&x<=1058))packMenu=false;
        for(var item:rails.entrySet()){Rail rail=item.getValue();if(x>=rail.x-4&&x<=rail.x+7&&y>=rail.y&&y<=rail.y+rail.height){dragRail=item.getKey();railTrackY=rail.y;railTrackHeight=rail.height;railContentHeight=rail.content;railGrab=y>=rail.thumbY&&y<=rail.thumbY+rail.thumbHeight?y-rail.thumbY:rail.thumbHeight/2;dragTo(y);return true;}}
        if(searchInput!=null&&x>=X&&x<X+LW&&y>=Y&&y<Y+38){host.focus("updates-search");draggingEditor=searchEditor;searchEditor.pointerDown(x-searchInput.x,y-searchInput.y,shift);return true;}
        if(composeInput!=null&&composeInput.contains(x,y)&&selected.equals(displayedId)&&!exitingArticle){host.focus("updates-compose");draggingEditor=composeEditor;composeEditor.pointerDown(x-composeInput.x,y-composeInput.y,shift);return true;}return false;
    }
    private void dragTo(float y){float thumb=Math.max(28,railTrackHeight*railTrackHeight/railContentHeight),scroll=clamp((y-railTrackY-railGrab)/Math.max(1,railTrackHeight-thumb),0,1)*(railContentHeight-railTrackHeight);if(dragRail==1){double direction=scroll>listScroll?-1:1;listScroll=scroll;loadListBoundary(direction);}else if(dragRail==2){double direction=scroll>readerScroll?-1:1;readerScroll=scroll;loadReaderBoundary(direction);}else if(dragRail==3)packScroll=scroll;}
    boolean pointerDrag(float x,float y){if(dragRail!=0){dragTo(y);return true;}if(draggingEditor!=null){InputBox box=draggingEditor==searchEditor?searchInput:composeInput;if(box!=null)draggingEditor.pointerDrag(x-box.x,y-box.y);return true;}return false;}
    boolean pointerUp(){boolean handled=dragRail!=0||draggingEditor!=null;dragRail=0;if(draggingEditor!=null)draggingEditor.pointerUp();draggingEditor=null;return handled;}
    boolean pointerOnRail(float x,float y){for(Rail rail:rails.values())if(x>=rail.x-4&&x<=rail.x+7&&y>=rail.y&&y<=rail.y+rail.height)return true;return false;}
    boolean dismiss(){if(packMenu){packMenu=false;return true;}if(!deleting.isEmpty()){deleting="";return true;}return false;}
    boolean textFocused(String id){return id!=null&&(id.equals("updates-search")||id.equals("updates-compose"));}
    private HubTextEditor editor(String id){return "updates-compose".equals(id)?composeEditor:searchEditor;}
    private void edited(String id,String before){HubTextEditor editor=editor(id);if(before.equals(editor.text()))return;if(id.equals("updates-compose")){drafts.put(editingId(),editor.text());operations.remove(editingId());}else{search=editor.text();listPage=0;listScroll=0;list();}}
    boolean key(int key,int modifiers,String id){
        if(!textFocused(id)){
            boolean index=id!=null&&id.startsWith("updates-row-");if(index&&(key==265||key==264||key==268||key==269)){int current=0;for(int i=0;i<rows.size();i++)if(s(rows.get(i),"id","").equals(selected))current=i;int target=key==268?0:key==269?rows.size()-1:Math.max(0,Math.min(rows.size()-1,current+(key==265?-1:1)));if(!rows.isEmpty()){String next=s(rows.get(target),"id","");select(next);host.focus("updates-row-"+next);Box box=rowBoxes.get(next);if(box!=null){if(box.y<listScroll)listScroll=box.y;else if(box.y+box.height>listScroll+H-50)listScroll=box.y+box.height-H+50;}}return true;}
            if(key==266||key==267||key==265||key==264||key==268||key==269){float delta=key==266?-H*.8f:key==267?H*.8f:key==265?-38:key==264?38:0;readerScroll=key==268?0:key==269?readerHeight:readerScroll+delta;loadReaderBoundary(delta>0?-1:1);return true;}return false;
        }
        HubTextEditor editor=editor(id);String before=editor.text();boolean ctrl=(modifiers&2)!=0,shift=(modifiers&1)!=0;
        if(ctrl&&key==65)editor.selectAll();else if(key==259)editor.backspace(ctrl);else if(key==261)editor.deleteForward(ctrl);else if(key==263)editor.moveLeft(ctrl,shift);else if(key==262)editor.moveRight(ctrl,shift);else if(key==268)editor.moveHome(ctrl,shift);else if(key==269)editor.moveEnd(ctrl,shift);else if(key==265)editor.moveVertical(-1,shift);else if(key==264)editor.moveVertical(1,shift);else if(key==257||key==335){if(id.equals("updates-compose")){if(ctrl){if(!pending)submit();}else editor.insert("\n");}}else if(key!=32)return false;edited(id,before);return true;
    }
    void paste(String value,String id){if(value==null||!textFocused(id))return;HubTextEditor editor=editor(id);String before=editor.text();editor.insert(value);edited(id,before);}
    void type(char value,String id){if(!textFocused(id))return;HubTextEditor editor=editor(id);String before=editor.text();editor.type(value);edited(id,before);}
    String selectedText(String id){return textFocused(id)?editor(id).selectedText():"";}
    String cutText(String id){if(!textFocused(id))return "";HubTextEditor editor=editor(id);String before=editor.text(),copy=editor.cutSelection();edited(id,before);return copy;}
    private void hit(String id,String label,float x,float y,float w,float h,Runnable action){host.hit(id,label,x,y,w,h,action);}
    private void clippedHit(String id,String label,float x,float y,float w,float h,float top,float bottom,Runnable action){clippedHit(id,label,x,y,w,h,top,bottom,action,true);}
    private void clippedHit(String id,String label,float x,float y,float w,float h,float top,float bottom,Runnable action,boolean enabled){float yy=Math.max(y,top),hh=Math.min(y+h,bottom)-yy;if(enabled&&hh>0)hit(id,label,x,yy,w,hh,action);}
    private void buttonClipped(String id,String label,float x,float y,float w,float h,Runnable action,boolean enabled){buttonDraw(id,label,x,y,w,h,enabled);clippedHit(id,label,x,y,w,h,Y,Y+H,action,enabled);}
    private void button(String id,String label,float x,float y,float w,float h,Runnable action,boolean enabled){buttonDraw(id,label,x,y,w,h,enabled);if(enabled)hit(id,label,x,y,w,h,action);}
    private void buttonDraw(String id,String label,float x,float y,float w,float h,boolean enabled){
        HubMotion.Transition hover=controlMotion.computeIfAbsent(id+"-hover",key->new HubMotion.Transition()),press=controlMotion.computeIfAbsent(id+"-press",key->new HubMotion.Transition());float over=hover.sample(enabled&&inside(x,y,w,h)?1:0,System.nanoTime(),180_000_000L,reduced),down=press.sample(enabled&&pressed.equals(id)?1:0,System.nanoTime(),180_000_000L,reduced);
        boolean deletion=id.equals("updates-delete-cancel")||id.equals("updates-delete-confirm");if(deletion){center(label,x+w/2,y+(h+10)/2+down,10,id.endsWith("cancel")?0xff8890a4:mix(0xffa57683,0xffb86f81,over),400);return;}
        d.saveLayerAlpha(x-10,y-10,w+20,h+30,enabled?1:.45f);d.save();d.translate(0,down>0?down:-over);d.boxShadow(x,y,w,h,8,0,3+3*over,8+8*over,0,mix(0x1a9d8bca,0x4d7558d1,over));d.linearGradientRoundRect(x,y,w,h,8,over>0?130:110,new int[]{c(mix(0xff8d9dd1,0xff987af5,over),mix(0xff7588bb,0xff9c81de,over)),c(mix(0xffa28bd0,0xff694acb,over),mix(0xff9577bb,0xff7c60c5,over))},new float[]{0,1});d.insetBoxShadow(x,y,w,h,8,0,1,0,0,mix(0x61ffffff,0x70ffffff,over));d.strokeRoundRect(x,y,w,h,8,1,0xff8c71e6);
        if(id.equals("updates-send")){float content=d.textWidth(label,10,500)+18;icon("send",x+(w-content)/2,y+(h-13)/2,13,0xffffffff);text(label,x+(w-content)/2+18,y+(h+10)/2,10,0xffffffff,500);}else center(label,x+w/2,y+(h+10)/2,10,0xffffffff,500);d.restore();d.restore();
    }
    private int c(int light,int dark){return d.dark()?dark:light;}
    private void text(String text,float x,float y,float size,int color,int weight){if(readerDrawing&&(y<Y-size*2||y>Y+H+size*2))return;d.text(text,x,y,size,color,weight);}
    private void center(String text,float x,float y,float size,int color,int weight){text(text,x-d.textWidth(text,size,weight)/2,y,size,color,weight);}
    private void right(String text,float x,float y,float size,int color,int weight){text(text,x-d.textWidth(text,size,weight),y,size,color,weight);}
    private void icon(String icon,float x,float y,float size,int color){if(readerDrawing&&(y+size<Y||y>Y+H))return;d.icon(icon,x,y,size,color);}
    private String fit(String value,float width,float size,int weight){if(d.textWidth(value,size,weight)<=width)return value;int end=value.length();while(end>0&&d.textWidth(value.substring(0,end)+"…",size,weight)>width)end=value.offsetByCodePoints(end,-1);return value.substring(0,end)+"…";}
    private boolean inside(float x,float y,float w,float h){return mx>=x&&my>=y&&mx<x+w&&my<y+h;}
    private static float listSpring(float p){float[] samples={0,.0603f,.2033f,.3821f,.5633f,.7258f,.8584f,.9574f,1.0242f,1.0634f,1.0809f,1.0828f,1.0747f,1.0613f,1.046f,1.0313f,1.0186f,1.0085f,1.0012f,.9965f,.994f,.9931f,.9933f,.9941f,.9953f,.9966f,.9978f,.9988f,1};float at=p*(samples.length-1);int i=Math.min(samples.length-2,(int)at);return samples[i]+(samples[i+1]-samples[i])*(at-i);}
    private static float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}
    private static int mix(int from,int to,float f){int color=0;for(int shift=0;shift<=24;shift+=8){int a=(from>>>shift)&255,b=(to>>>shift)&255;color|=Math.round(a+(b-a)*clamp(f,0,1))<<shift;}return color;}
    private static int withAlpha(int c,float alpha){return (Math.round((c>>>24)*clamp(alpha,0,1))<<24)|(c&0xffffff);}
}
