/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.pixelmc.hub.client.render.HubDraw;
import com.pixelmc.hub.client.render.GlassStyle;
import com.pixelmc.hub.client.render.IconPose;
import io.github.humbleui.skija.Matrix33;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.text.Collator;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import com.google.gson.JsonObject;

/** Native rendering of the approved handbook, with explicitly separate live and demo data. */
public final class HubView {
    private static final int INK=0xff26344f, MUTED=0xff73809a, PURPLE=0xff765bd4;
    private static final float LEFT=236, WIDTH=996, RIGHT=922;
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("yyyy / MM / dd");
    private final HubData data=new HubData();
    private final List<Hit> hits=new ArrayList<>();
    private final List<Hit> nextHits=new ArrayList<>();
    private final Map<String,HubMotion.Transition> transitions=new HashMap<>();
    private final Map<String,HubMotion.Tilt> tilts=new HashMap<>();
    private final HubMotion.Navigation[] navigation={new HubMotion.Navigation(),new HubMotion.Navigation(),new HubMotion.Navigation()};
    private final Deque<Matrix33> matrixStack=new ArrayDeque<>();
    private final Deque<Float> alphaStack=new ArrayDeque<>();
    private Matrix33 logical=Matrix33.IDENTITY;
    private Matrix33 titleClipTransform=Matrix33.IDENTITY,pageTransform=Matrix33.IDENTITY,sliderTransform=Matrix33.IDENTITY;
    private HubDraw d;
    private float mx,my,opacity=1,offsetY,interactiveAlpha=1,dt=1f/60;
    private long now=System.nanoTime(),openedAt=now,pageAt=now,monthAt=now,lastFrame=now,modalAt,navAt=now;
    private int page,filter,sort,fortuneState=3,searchCaret;
    private String playerName="zhan_cpu",equipped="dew",selectedTitle="dew",query="",welcomeMode="login";
    private boolean visible=true,reducedMotion,closeRequest,playWelcome,compact,history,sliderDragging,searchSelectAll;
    private float titleScroll,historyScroll;
    private YearMonth month=YearMonth.from(data.today);
    private int monthDirection=1;
    private LocalDate selectedDate=data.today,displayDate=data.today;
    private String overlay,focusId,restoreFocus,pressedId;
    private final HubToast toast=new HubToast();
    private Hit pressedHit;
    private boolean keyboardFocus,pressMoved,keyboardPress;
    private float pressX,pressY;
    private HubData.Entry reading;
    private float modalX,modalY,modalW,modalH;
    private float viewportX,viewportY,viewportW=1260,viewportH=850;
    @FunctionalInterface public interface PlayerAction {void request(String module,String action,JsonObject payload);}
    private PlayerAction actions;
    private final Map<String,String> moduleStates=new HashMap<>();
    private final java.util.Set<String> pendingModules=new java.util.HashSet<>();
    private boolean catalogueComplete=true;
    private JsonObject titleDraft;
    private boolean calendarLoading,chooseLatestOnCalendar;
    private String connectionName="当前存档";
    private byte[] serverIcon;
    public void setConnection(String name,byte[] icon){connectionName=name==null||name.isBlank()?"当前存档":name;serverIcon=icon==null?null:icon.clone();}
    public void live(PlayerAction actions) {
        this.actions=actions;data.clearLive();equipped=null;selectedTitle="";displayDate=null;fortuneState=0;
        month=YearMonth.from(data.today);selectedDate=data.today;catalogueComplete=false;titleDraft=null;calendarLoading=true;pendingModules.clear();
        for(String module:List.of("manager","titles","fortunes"))moduleStates.put(module,"正在读取玩家资料…");
    }
    public void moduleStatus(String module,String status){
        moduleStates.put(module,status);
        if(!status.isEmpty()){
            if(module.equals("titles"))titleDraft=null;
            if(hasSnapshot(module))feedbackError(status);
        }
    }
    public void pending(String module,boolean pending){if(pending)pendingModules.add(module);else pendingModules.remove(module);}
    public void feedback(String value){notify(value);}
    public void feedbackError(String value){toast.show(value,HubToast.Kind.ERROR,System.nanoTime());}
    public void feedbackSuccess(String value){toast.show(value,HubToast.Kind.SUCCESS,System.nanoTime());}
    public String currentMonth(){return month.toString();}
    public boolean needsCalendar(){return page==2;}
    public String dayIdentity(){return (data.fortunesLoaded?data.timezone:data.managerTimezone)+"|"+data.today;}
    public long nextMidnightEpochMillis(){return data.today.plusDays(1).atStartOfDay(data.fortunesLoaded?data.zone():data.profileZone()).toInstant().toEpochMilli();}
    public void advanceDay(long serverMillis){if(!data.fortunesLoaded)data.today=java.time.Instant.ofEpochMilli(serverMillis).atZone(data.profileZone()).toLocalDate();}
    public boolean compact(){return compact;}
    public void setCompact(boolean value){compact=value;}
    public void catalogueComplete(){if(titleDraft==null&&data.titlesLoaded)catalogueComplete=true;}
    public void catalogueInvalidated(){titleDraft=null;}
    public boolean isCatalogueComplete(){return catalogueComplete;}
    private boolean live(){return actions!=null;}
    private boolean hasSnapshot(String module){return switch(module){case "manager"->data.managerLoaded;case "titles"->data.titlesLoaded;case "fortunes"->data.fortunesLoaded;default->false;};}
    private boolean ready(String module){return !live()||hasSnapshot(module)||moduleStates.getOrDefault(module,"").isEmpty();}
    private boolean privacyKnown(){return !live()||catalogueComplete&&(equipped==null||equipped.isEmpty()||data.title(equipped)!=null);}
    private boolean canChangeVisibility(){return !live()||privacyKnown()&&ready("titles")&&equipped!=null&&!equipped.isEmpty()&&!pendingModules.contains("titles");}
    private void changeVisibility(){
        if(live()){if(!canChangeVisibility())return;JsonObject p=mutation("titles");p.addProperty("visible",!visible);request("titles","visibility",p);return;}
        visible=!visible;notify(visible?"已开启称号展示 · 本地演示":"展示已隐藏，佩戴状态保留");
    }
    private String moduleLabel(String module){return !live()?"演示":moduleStates.getOrDefault(module,"").isEmpty()?"已连接":moduleStates.get(module);}
    private int titleCount(){return (int)data.titles.stream().filter(HubData.Title::owned).count();}
    private int archiveCount(){return live()?data.archiveCount:archive().size();}
    private String timestamp(long at,String pattern){return at<=0?"暂无记录":java.time.Instant.ofEpochMilli(at).atZone(data.profileZone()).format(DateTimeFormatter.ofPattern(pattern));}
    private String titleDate(HubData.Title title){return live()?timestamp(title.acquiredAt(),"yyyy/MM/dd"):title.date();}
    private long journeyDays(){return data.firstJoinedAt<=0?0:Math.max(1,java.time.temporal.ChronoUnit.DAYS.between(java.time.Instant.ofEpochMilli(data.firstJoinedAt).atZone(data.profileZone()).toLocalDate(),data.today)+1);}
    private void request(String module,String action,JsonObject payload){if(actions!=null)actions.request(module,action,payload);}
    private JsonObject mutation(String module){JsonObject p=new JsonObject();p.addProperty("expectedRevision",data.revisions.getOrDefault(module,""));return p;}
    private void calendarRequest(){if(live()&&ready("fortunes")){calendarLoading=true;JsonObject p=new JsonObject();p.addProperty("month",month.toString());request("fortunes","calendar",p);}}
    private boolean calendarInitialLoading(){return live()&&calendarLoading&&!data.calendarMonths.contains(month);}
    public void acceptLive(String module,String action,JsonObject result) {
        if(module.equals("manager")){data.manager(result);setPlayerName(HubData.string(HubData.object(result,"player"),"name",playerName));}
        if(module.equals("titles")) {
            if(!action.equals("catalogue"))titleDraft=result.deepCopy();
            else {
                if(titleDraft==null||!HubData.string(titleDraft,"revision","").equals(HubData.string(result,"revision",""))) {
                    titleDraft=null;throw new IllegalStateException("称号资料在读取期间发生变化，请重新读取");
                }
                if(result.has("titles"))titleDraft.getAsJsonArray("titles").addAll(result.getAsJsonArray("titles"));
            }
            if(!HubData.string(result,"nextCursor","").isEmpty())return;
            data.titles(titleDraft,false);equipped=HubData.string(titleDraft,"equippedTitle","");titleDraft=null;catalogueComplete=true;
            HubData.Title worn=data.title(equipped);visible=worn!=null&&worn.publicDisplay();
            if(data.title(selectedTitle)==null&&!data.titles.isEmpty())selectedTitle=data.titles.get(0).id();
        }
        if(module.equals("fortunes")) {
            if(action.equals("calendar")) {
                data.calendar(result);if(month.toString().equals(HubData.string(result,"month",""))){calendarLoading=false;if(chooseLatestOnCalendar)selectedDate=archive().keySet().stream().filter(date->YearMonth.from(date).equals(month)).max(Comparator.naturalOrder()).orElse(month.atDay(1));chooseLatestOnCalendar=false;}
            } else if(action.equals("read")) {
                HubData.Entry record=data.record(result.has("record")?result.get("record"):result);if(record!=null)showReading(record);
            } else {
                boolean initial=!data.fortunesLoaded;data.fortunes(result);
                calendarLoading=true;
                fortuneState=List.of("undrawn","unread","read","archived").indexOf(HubData.string(result,"todayState","undrawn"));if(fortuneState<0)fortuneState=0;
                if(initial){month=YearMonth.from(data.today);selectedDate=data.today;}
                displayDate=data.featured==null?null:data.featured.date();
            }
        }
    }

    private record Hit(String id,String label,float x,float y,float w,float h,Matrix33 transform,Matrix33 viewport,Runnable action) {
        boolean contains(float px,float py) {
            if(viewport!=null){float[] clip=HubDraw.inversePoint(viewport,px,py);if(clip[0]<230||clip[1]<272||clip[0]>=904||clip[1]>=794)return false;}
            float[] p=HubDraw.inversePoint(transform,px,py);return p[0]>=x&&p[1]>=y&&p[0]<x+w&&p[1]<y+h;
        }
    }
    public HubView() {}
    public void setPlayerName(String name) { if(name!=null&&!name.isBlank()) playerName=name; }
    public void setViewportBounds(float x,float y,float w,float h){if(w>0&&h>0){viewportX=x;viewportY=y;viewportW=w;viewportH=h;}}
    public void setSettings(String mode,boolean reduced) {
        welcomeMode=List.of("once","login","always").contains(mode)?mode:"login";
        setReducedMotion(reduced);
    }
    public String welcomeMode() { return welcomeMode; }
    public boolean reducedMotion() { return reducedMotion; }
    public boolean consumeCloseRequest() { boolean value=closeRequest;closeRequest=false;return value; }
    public boolean isWelcomePlaying() { return playWelcome&&!reducedMotion&&System.nanoTime()-openedAt<3_060_000_000L; }
    public void open(boolean welcome) {
        toast.clear();
        page=0;overlay=null;reading=null;focusId=null;closeRequest=false;
        openedAt=System.nanoTime();navAt=openedAt;lastFrame=openedAt;playWelcome=welcome&&!reducedMotion;
        pageAt=openedAt+(playWelcome?3_060_000_000L:0);hits.clear();
        transitions.clear();tilts.clear();pressedId=null;
        for(HubMotion.Navigation state:navigation){state.enter.snap(reducedMotion?1:0);state.hover.snap(0);state.press.snap(0);state.x.snap(0);state.y.snap(0);}
    }

    public void render(HubDraw draw,float mouseX,float mouseY,long nowNanos) {
        d=draw;mx=mouseX;my=mouseY;now=nowNanos;dt=clamp((now-lastFrame)/1_000_000_000f,0,.05f);lastFrame=now;
        opacity=1;offsetY=0;interactiveAlpha=1;logical=Matrix33.IDENTITY;matrixStack.clear();alphaStack.clear();nextHits.clear();
        float shell=reducedMotion?1:HubMotion.bezier(progress(now-openedAt,360_000_000L),.2f,.8f,.2f,1);
        beginAlpha(shell);push(HubDraw.cssPerspective(630,425,800,0,0,0,.992f+.008f*shell,0,12*(1-shell)));
        d.boxShadow(0,0,1260,850,28,0,28,80,-28,0x60435882);
        d.boxShadow(0,0,1260,850,28,0,5,16,0,0x1253658e);
        d.save();d.clipRoundRect(0,0,1260,850,28);
        d.blurBackdrop(0,0,1260,850,28,24,1);
        d.roundRect(0,0,1260,850,28,0x99f4f6ff);
        d.insetBoxShadow(0,0,1260,850,28,0,1,0,0,0xffffffff);
        d.linearGradientRoundRect(208,0,1052,850,0,135,new int[]{0xadffffff,0x9cf5f6ff},new float[]{0,1});
        d.captureGlassBackdrop(0,0,208,850);
        sidebar();topbar();
        boolean welcomeActive=playWelcome&&!reducedMotion&&now-openedAt<3_060_000_000L;
        if(!welcomeActive) {
            if(page==0)home();else if(page==1)titles();else fortunes();
        }
        if(welcomeActive) { nextHits.clear();welcome(); }
        d.restore();
        d.strokeRoundRect(.5f,.5f,1259,849,28,1,0xe6ffffff);
        pop();endAlpha();
        if(!welcomeActive&&(overlay!=null||reading!=null))modal();
        toast.render(d,mx,my,now,reducedMotion,viewportX,viewportY,viewportW,viewportH);
        hits.clear();hits.addAll(nextHits);
        d=null;
    }

    private void sidebar() {
        d.glassSurface(0,0,208,850,0,GlassStyle.SIDEBAR);
        d.linearGradientRoundRect(0,0,208,850,0,145,new int[]{0x40ffffff,0x28e6edff,0x35e9e3ff},new float[]{0,.52f,1});
        d.linearGradientRoundRect(0,0,208,850,0,145,new int[]{0x3df8fcff,0x2beaf1ff,0x38eee9ff},new float[]{0,.5f,1});
        d.linearGradientRoundRect(5,5,203,840,0,118,new int[]{0x24ffffff,0x00ffffff,0x00ffffff,0x14bcc0e3},new float[]{0,.32f,.72f,1});
        d.insetBoxShadow(0,0,208,850,0,2,1,3,0,0xffffffff);
        d.insetBoxShadow(0,0,208,850,0,-3,0,7,0,0x42abb9e1);
        d.boxShadow(0,0,208,850,0,6,0,20,0,0x0c65719e);
        d.rect(5,5,1,840,0x88ffffff);d.rect(207,0,1,850,0x88b3bfdf);
        if(live()&&serverIcon!=null)d.serverIcon(serverIcon,22,27,44);else image("server-logo",22,27,44,44,1);
        tracked("旅人手册",77,46,18,1.2f,INK,650,false);text("PixelMC-Hub",77,65,10,0xff687491,false);
        hit("brand","旅人手册首页",18,20,174,61,()->navigate(0));
        text("我的空间",25,118,11,0xff677591,false);
        String[] labels={"个人主页","我的称号","我的签笺"};
        String[] icons={"home","tag","book-open"};
        int[] colors={0xff1949e6,0xff6f34d5,0xfff8651b};
        int[] bright={0xff569dfb,0xffbf72f3,0xffffb83d};
        for(int i=0;i<3;i++) {
            float y=145+i*74;final int destination=i;
            boolean hover=inside(21,y-8,172,64),current=page==i;
            float h=transition("nav-bg-"+i,hover?1:0,200);
            if(current)glass(20,y-8,173,64,18,0xb8ffffff,0x57ffffff);
            else if(h>0)d.roundRect(20,y-8,173,64,18,withAlpha(0x65ffffff,h));
            float[] mouse=HubDraw.inversePoint(logical,mx,my);
            float nx=clamp((mouse[0]-58)/23,-1,1),ny=clamp((mouse[1]-y-23)/23,-1,1);
            HubMotion.Navigation state=navigation[i];
            state.update(dt,reducedMotion,hover,Objects.equals(pressedId,"nav-"+i),nx,ny,now-navAt>=i*70_000_000L);
            IconPose pose=HubDraw.iconPose(46,state.enter.value,state.hover.value,state.press.value,state.x.value,state.y.value,reducedMotion);
            d.glassIcon(35,y,46,bright[i],colors[i],pose,1);
            beginAlpha(pose.faceAlpha(),35,y-8,46,62);
            push(HubDraw.cssPerspective(58,y+23,800,0,0,0,pose.faceScale(),0,pose.faceY()));
            icon(icons[i],48.8f,y+13.8f,18.4f,0xffffffff);pop();endAlpha();
            tracked(labels[i],99,y+28,14,.25f,current?0xff3a3590:0xff53627e,current?700:550,false);
            hit("nav-"+i,labels[i],20,y-8,173,64,()->navigate(destination));
        }
        glass(24,718,36,40,12,0xc9e7fff2,0x7ab4e9d7);icon("flower-2",32,728,21,0xff458c80);
        text(live()?fit(data.packName.isBlank()?connectionName:data.packName,112,12,true):"香草纪元",70,731,12,0xff52627b,true);
        d.circle(72,748,2,0xff6a9c8b);text(live()?(data.packVersion.isBlank()?"当前存档":fit(data.packVersion,105,10,false)):"当前存档 · 演示",80,752,10,0xff708098,false);
        d.line(24,777,184,777,1,0x55b7c2dc);
        float settingsHover=transition("settings-hover",inside(20,791,172,40)?1:0,140);
        if(settingsHover>0)d.roundRect(20,791,172,40,10,withAlpha(0x99ffffff,settingsHover));
        icon("settings",34,801,19,0xff65738c);text("界面设置",65,816,13,0xff65738c,false);
        hit("settings","界面设置",20,791,172,40,()->openOverlay("settings"));
    }
    private void topbar() {
        d.rect(208,0,1052,54,0x36ffffff);d.line(208,54,1260,54,1,0xb8ffffff);
        text("我的空间",236,33,12,0xff8a94aa,false);icon("chevron-right",301,22,13,0xffa1aac0);
        text(pageName(),326,33,12,0xff63718c,false);
        d.circle(1095,28,2.5f,0xff529c8c);text(live()?"玩家手册":"本地原型",1105,32,11,0xff64748b,false);
        d.line(1183,21,1183,35,1,0x7ac7cfe0);
        iconButton("close","收起手册","x",1200,12,32,()->closeRequest=true,true);
    }
    private void heading(String title,String subtitle,String module,int count) {
        group(0,340);
        tracked(title,LEFT,108,25,1,INK,720,false);
        if(count>=0){String number=Integer.toString(count);float x=LEFT+trackedWidth(title,25,1,720,false)+12,w=d.textWidth(number,13,500)+18;d.roundRect(x,84,w,28,10,0xffe9e1ff);d.strokeRoundRect(x,84,w,28,10,1,0xffffffff);d.text(number,x+9,103,13,0xff7361c3,500);}
        text(subtitle,LEFT,137,13,0xff6c7891,false);
        if(module!=null)textRight(fit(module,460,10,false),1232,105,10,0xff7c72a6,false);
        ungroup();
    }
    private String pageName(){return page==0?"个人主页":page==1?"我的称号":"我的签笺";}

    private void home() {
        heading("个人主页",live()?(journeyDays()>0?"很高兴又见到你，今天是旅程的第 "+journeyDays()+" 天。":"很高兴又见到你。") :"很高兴又见到你，今天是旅程的第 18 天。",null,-1);
        group(0,340);
        icon("sun",1062,88,18,0xffbf8a37);text(live()?data.today.format(DateTimeFormatter.ofPattern("M 月 d 日，EEEE",Locale.CHINA)):"11 月 3 日，星期二",1089,103,12,0xff6e7990,false);
        ungroup();
        group(50,440);
        d.boxShadow(LEFT,174,WIDTH,276,22,0,10,30,0,0x288980c5);
        d.linearGradientRoundRect(LEFT,174,WIDTH,276,22,110,new int[]{0xffd9f0ff,0xffe4e8ff,0xffdbc9f9},new float[]{0,.45f,1});
        d.radialGradientRoundRect(LEFT,174,WIDTH,276,22,LEFT+WIDTH*.78f,174+276*.34f,WIDTH*.5f,276*.5f,new int[]{0xffbcbbff,0x00bcbbff},new float[]{0,1});
        d.captureGlassBackdrop(LEFT,174,WIDTH,276);d.glassSurface(LEFT,174,WIDTH,276,22,GlassStyle.IDENTITY);
        d.insetBoxShadow(LEFT,174,WIDTH,276,22,0,1,2,0,0xffffffff);
        d.insetBoxShadow(LEFT,174,WIDTH,276,22,0,0,5,0,0x298c9ac3);
        d.boxShadow(LEFT,174,WIDTH,276,22,0,8,22,0,0x136c64aa);
        d.save();d.clipRoundRect(LEFT,174,WIDTH,276,22);
        d.rect(LEFT,366,WIDTH,84,a(0x9effffff));
        d.line(LEFT,366,1232,366,1,a(0xc7ffffff));d.restore();
        d.strokeRoundRect(LEFT,174,WIDTH,276,22,1,a(0xf2ffffff));
        text("你好",263,211,12,0xff586987,false);
        String name=fitTracked(playerName,390,35,-1,700,false);tracked(name,263,255,35,-1,0xff253657,700,false);
        float nameWidth=trackedWidth(name,35,-1,700,false);
        pill(live()?"我的角色":"本地角色",277+nameWidth,232,0xbceffff2,0xff25816a,true);
        HubData.Title worn=data.title(equipped);
        if(worn==null)text(live()&&!ready("titles")?"称号资料未连接":live()&&!catalogueComplete?"正在读取称号…":"暂未佩戴称号",263,287,14,MUTED,true);else titleLabel(worn,263,287,14,1.4f,false,false);
        if(privacyKnown()&&!visible&&worn!=null){icon("eye-off",live()?750:371,276,13,MUTED);text("展示已隐藏",live()?769:390,287,10,MUTED,false);}
        button("profile","个人资料  ↗",263,315,106,31,false,true,()->openOverlay("profile"));
        d.save();d.translate(1080,268);d.canvas().rotate(-29);
        d.strokeRoundRect(-120,-57,240,114,57,2,a(0x70ffffff));d.restore();
        push(Matrix33.makeRotate(-5,1082,267));
        HubMotion.Tilt medallion=beginTilt("medallion",987,187,190,160,10,1.04f,true,.16f,26);
        glass(987,187,190,160,26,0x66ffffff,0x24e7e5ff);
        HubHead.draw(d,1055,214,54,-23,-2);
        pill(live()?(journeyDays()>0?"✧ 旅程第 "+journeyDays()+" 天":"✧ 今天也有新旅程"):"✧ 旅程第 18 天",1020,309,0x6bffffff,0xff5d5491,false);
        endTilt(medallion,987,187,190,160,26,10,.75f);pop();
        text("✦",1190,222,27,0xffffffff,false);text("✧",967,321,21,0xffffffff,false);
        stat(263,391,"clock",live()?"当前存档 · 累计在线":"累计在线 · 演示",live()?(data.managerLoaded?Long.toString(data.onlineMillis(now)/3600000):"—"):"128",live()?"小时  "+(data.onlineMillis(now)/60000%60)+" 分钟":"小时  42 分钟",0xff6b7dd6,0xffe8eeff);
        d.line(566,388,566,430,1,a(0x4ab7c1e0));
        stat(590,391,"log-in",live()?"当前存档 · 登录次数":"登录次数 · 演示",live()?(data.managerLoaded?Long.toString(data.joinCount):"—"):"36","次",0xffaa75c6,0xfff1e9ff);
        d.line(887,388,887,430,1,a(0x4ab7c1e0));
        glass(910,392,33,33,11,0xfff8f0df,0xfff8f0df);icon("calendar-days",918,400,17,0xffb29047);
        text(live()?"上次登录":"上次登录 · 演示",955,400,11,0xff72809c,false);
        text(live()?timestamp(data.previousLoginAt,"yyyy/MM/dd"):"2026/11/02",955,424,16,0xff344566,true);text(live()?(data.previousLoginAt<=0?"":timestamp(data.previousLoginAt,"HH:mm:ss")):"20:26:14",1060,424,12,0xff71809b,false);
        ungroup();
        group(110,440);
        homeToday();homeKeepsakes();ungroup();
        group(170,340);
        icon("leaf",LEFT,811,13,0xff8a93a8);text("每一份收藏，都记得来时的路。",256,823,11,0xff8a93a8,false);
        textRight(live()?(ready("manager")?"当前存档的个人资料":moduleLabel("manager")):"样例统计与收藏 · F8 预览选项",1232,823,10,0xff8a93a8,false);ungroup();
    }
    private void stat(float x,float y,String name,String label,String main,String unit,int color,int background) {
        d.roundRect(x,y,33,33,11,a(background));icon(name,x+8,y+8,17,color);
        text(label,x+45,y+9,11,0xff72809c,false);text(main,x+45,y+34,21,0xff344566,true);
        text(unit,x+49+d.textWidth(main,21,600),y+33,10,0xff78869d,false);
    }
    private void homeToday() {
        glass(LEFT,470,515,310,22,0xfffffefa,0xfffff9ef);
        d.radialGradientRoundRect(LEFT,470,515,310,22,751,600,335,202,new int[]{0x9cffe9bf,0x00ffe9bf},new float[]{0,1});
        icon("leaf",259,493,19,0xffbc9345);text("今日签笺",287,510,16,INK,true);
        textRight("每日一签",728,508,10,MUTED,false);
        HubData.Fortune f=live()?(data.todayRecord==null?null:data.todayRecord.fortune()):data.fortune(1);
        if(live()&&!ready("fortunes")) {
            text("今日签笺暂不可用",259,593,23,0xff4e4f42,true);
            wrap(moduleLabel("fortunes"),259,630,280,12,23,0xff928675,false);
        } else if(fortuneState>=2&&f!=null) {
            text(f.grade()+"  ·  第"+f.id()+"签",259,551,11,HubData.gradeColor(f.grade()),false);
            tracked(f.title(),259,597,29,2,0xff4e4f42,600,true);
            if(!f.poem().isEmpty())tracked(f.poem().get(0),259,630,13,1.3f,0xff828069,400,true);if(f.poem().size()>1)tracked(f.poem().get(1),259,655,13,1.3f,0xff828069,400,true);
        } else {
            text("今日机缘",259,551,11,0xffae8132,false);
            text(fortuneState==0?"有一签，等你开启":"签已入手，静候解读",259,593,24,0xff4e4f42,true);
            wrap(fortuneState==0?"拿起签筒，为今天留下一份期许。":"带上未解的签，前往解签龛。",259,629,260,13,23,0xff928675,false);
        }
        d.circle(662,606,61,a(0x30ebcf87));
        float barrel=transition("fortune-barrel-hover",inside(LEFT,470,515,310)?1:0,350);
        if(reducedMotion)barrel=0;
        push(HubDraw.cssPerspective(661,611,800,0,0,-8+5*barrel,1,0,-4*barrel));image("fortune-barrel",598,541,126,140,1);pop();
        text("✦",716,556,12,0xffd5ae61,false);text("✧",586,675,16,0xffd5ae61,false);
        d.line(259,706,728,706,1,a(0x33cabc9b));
        icon(fortuneState==3?"check-check":"info",259,731,15,0xff7f8e81);
        text(stateLabel(),281,743,10,0xff7f8e81,false);
        goldButton("today-read",fortuneState>=2?"查看签文  →":fortuneState==0?"如何抽签  →":"如何解签  →",608,721,120,37,
            ()->{if(live()&&!ready("fortunes")){notify(moduleLabel("fortunes"));return;}if(fortuneState>=2)openReading(todayEntry());else openOverlay("fortune-help");});
    }
    private void homeKeepsakes() {
        glass(771,470,461,310,22,0xecffffff,0xabffffff);
        icon("bookmark",793,493,18,0xff8c74c4);text("最近收藏",820,510,16,INK,true);
        textRight("旅途的印记",1210,508,10,MUTED,false);
        List<HubData.Keepsake> recent=data.recentKeepsakes(live()?data.recentArchive:archive());
        for(int i=0;i<recent.size();i++) {
            HubData.Keepsake item=recent.get(i);float y=537+i*64;
            if(item.title()!=null) {
                HubData.Title t=item.title();keepsake(y,fit(t.name(),280,13,false),"获得称号 · "+titleDate(t),t.icon(),t.light(),t.color(),null,()->{selectedTitle=t.id();navigate(1);},t);
            } else {
                HubData.Entry entry=item.entry();keepsake(y,entry.fortune().title(),"收藏签笺 · "+shortDate(entry.date())+" "+entry.time(),"fortune-slip",0xfff8edda,HubData.gradeColor(entry.fortune().grade()),entry.fortune().grade(),()->openReading(entry));
            }
        }
        if(recent.isEmpty()) {
            boolean loading=live()&&(!data.titlesLoaded||!data.fortunesLoaded);
            String empty=loading?"正在读取收藏…":"还没有留下收藏";
            if(live()&&(!ready("titles")||!ready("fortunes")))empty="收藏资料尚未就绪";
            textCenter(empty,1001,619,14,MUTED,false);
        }
        d.line(793,731,1210,731,1,a(0xffe4e5f1));
        text(live()&&!catalogueComplete?"—":Integer.toString(titleCount()),793,760,17,0xff5c5790,true);text("枚称号",810,760,10,0xff80899f,false);
        d.line(866,746,866,762,1,a(0xffd9dcec));
        text(live()&&!data.fortunesLoaded?"—":Integer.toString(archiveCount()),884,760,17,0xff5c5790,true);text("张签笺",911,760,10,0xff80899f,false);
        iconButton("archive-link","打开签笺月历","arrow-up-right",1177,740,30,()->navigate(2),true);
    }
    private void keepsake(float y,String title,String subtitle,String art,int bg,int color,String grade,Runnable click) {
        keepsake(y,title,subtitle,art,bg,color,grade,click,null);
    }
    private void keepsake(float y,String title,String subtitle,String art,int bg,int color,String grade,Runnable click,HubData.Title style) {
        float hover=transition("keepsake-hover-"+y,inside(793,y-1,417,60)?1:0,140);
        if(hover>0)d.roundRect(793,y-1,417,60,0,withAlpha(0x80f5f2ff,hover));
        glass(793,y+5,36,39,12,bg,lighten(bg,.1f));
        if(art.equals("fortune-slip"))image(art,797,y+7,28,34,1);else icon(art,800,y+13,22,color);
        if(style==null)text(title,841,y+19,13,0xff4c5570,false);else titleLabel(style,841,y+19,13,0,false,false,340);
        if(grade!=null){float x=848+d.textWidth(title,13,false);d.strokeRoundRect(x,y+6,d.textWidth(grade,9,false)+10,17,4,.7f,a(0xffeadfc7));text(grade,x+5,y+18,9,color,false);}
        text(subtitle,841,y+40,10,0xff8490a5,false);icon("chevron-right",1195,y+19,16,0xffa5a9c0);
        if(y<665)d.line(793,y+59,1210,y+59,1,a(0x57e5e7f3));
        hit("keepsake-"+y,title,793,y-1,417,60,click);
    }

    private List<HubData.Title> titleList() {
        List<HubData.Title> list=new ArrayList<>(data.titles.stream().filter(t->
            (filter==1||(filter==0?t.owned():!t.owned()))&&(t.name()+t.category()).contains(query.strip())).toList());
        if(sort==0)list.sort(live()?Comparator.comparingLong(HubData.Title::acquiredAt).reversed():Comparator.comparing(HubData.Title::date).reversed());
        else {Collator collator=Collator.getInstance(Locale.CHINA);list.sort((a,b)->collator.compare(a.name(),b.name()));}
        return list;
    }
    private void titles() {
        heading("我的称号","选一个喜欢的称号，让名字多一点自己的颜色。","MemorialTitles · "+moduleLabel("titles"),live()&&!catalogueComplete?-1:titleCount());
        if(live()&&!ready("titles")){unavailable("titles");return;}
        group(30,350);
        glass(LEFT,174,234,36,13,0x5cdce0f0,0x5cdce0f0);
        String[] labels={"已拥有","全部","未获得"};
        for(int i=0;i<3;i++) {
            final int value=i;float x=LEFT+3+i*76;
            float chosen=stateTransition("title-filter-selected-"+i,filter==i?1:0,140);
            if(chosen>0){beginAlpha(chosen,x,177,74,30);glass(x,177,74,30,10,0xffffffff,0xffffffff);endAlpha();}
            textCenter(labels[i],x+37,197,11,mix(0xff78839b,0xff6650a7,chosen),filter==i);
            hit("filter-"+i,labels[i],x,177,74,30,()->{filter=value;titleScroll=0;});
        }
        iconButton("density",compact?"切换卡片显示":"切换紧凑显示",compact?"grid-2x2":"list",864,176,32,()->{compact=!compact;titleScroll=0;},true);
        glass(LEFT,223,518,36,11,0xb3ffffff,0xb3ffffff);
        icon("search",248,233,16,0xff929bb1);
        if(searchSelectAll&&!query.isEmpty()&&"search".equals(focusId))d.roundRect(273,230,Math.min(438,d.textWidth(query,12,false))+2,21,3,a(0xffd4c9ff));
        text(query.isEmpty()?"搜索称号":fit(query,439,12,false),274,247,12,query.isEmpty()?0xff949caf:INK,false);
        if("search".equals(focusId)) {
            d.boxShadow(LEFT,223,518,36,11,0,0,0,3,0x1fab92ef);
            d.strokeRoundRect(LEFT,223,518,36,11,1,a(0xffa391dc));
            if(!searchSelectAll&&(now/500_000_000L)%2==0) {
                float caret=274+d.textWidth(query.substring(0,Math.min(searchCaret,query.length())),12,false);
                if(caret<735)d.line(caret,232,caret,250,1,a(INK));
            }
        }
        hit("search","搜索称号",LEFT,223,518,36,()->{searchCaret=query.length();searchSelectAll=false;});
        if(!query.isEmpty())iconButton("clear-search","清空搜索","x",720,228,26,()->{query="";searchCaret=0;titleScroll=0;searchSelectAll=false;focusId="search";},true);
        button("sort",sort==0?"最近获得  ⇅":"名称排序  ⇅",766,223,132,36,false,true,()->{sort=1-sort;titleScroll=0;});
        List<HubData.Title> list=titleList();
        float contentHeight=compact?list.size()*80f:((list.size()+1)/2)*174f-16;
        float viewportHeight=510;
        titleScroll=Math.max(0,Math.min(titleScroll,Math.max(0,contentHeight-viewportHeight)));
        d.save();d.clipRoundRect(230,272,674,522,0);
        titleClipTransform=logical;
        for(int i=0;i<list.size();i++) {
            float x=compact?LEFT:LEFT+(i%2)*339;
            float y=278+(compact?i*80:(i/2)*174)-titleScroll;
            if(y+(compact?68:158)<274||y>790)continue;
            titleTile(list.get(i),x,y,compact?662:323,compact?68:158);
        }
        d.restore();
        if(contentHeight>viewportHeight) {
            float thumb=Math.max(45,viewportHeight*viewportHeight/contentHeight);
            d.roundRect(902,278+titleScroll/(contentHeight-viewportHeight)*(viewportHeight-thumb),3,thumb,2,a(0x99a6b0cf));
        }
        if(list.isEmpty()&&!catalogueComplete) {
            icon("tag",548,374,28,0xff9d90b4);textCenter("正在读取称号目录…",567,437,17,0xff8c8495,true);
        } else if(list.isEmpty()) {
            icon("search",548,374,28,0xff9d90b4);textCenter("没有找到这个称号",567,437,17,0xff8c8495,true);
            textCenter("换个关键词，或查看全部称号。",567,466,12,MUTED,false);
            button("reset-filter","查看全部",512,492,110,37,false,true,()->{filter=1;query="";titleScroll=0;});
        }
        text(list.size()+" 枚称号 · "+(!catalogueComplete?"目录正在加载，请稍候":filter==2?"还有新的旅程等你开启":"每一个名字，都有它的故事"),LEFT,816,11,0xff8993a9,false);
        titleDetail();ungroup();
    }
    private void titleTile(HubData.Title t,float x,float y,float w,float h) {
        boolean selected=selectedTitle.equals(t.id()),hover=inside(x,y,w,h);
        beginAlpha(t.owned()?1:.8f,x,y,w,h);
        HubMotion.Tilt tilt=beginTilt("title-"+t.id(),x,y,w,h,5,1.018f,false,.12f,18);
        shadow(x,y,w,h,18,selected?0x1f607097:0x0b607097);
        d.radialGradientRoundRect(x,y,w,h,18,x+w,y,w,h,new int[]{t.light(),0xd9ffffff},new float[]{0,.74f});
        d.strokeRoundRect(x,y,w,h,18,selected?1.5f:1,a(selected?mix(t.color(),0xff6452ad,.2f):0xfaffffff));
        if(compact) {
            emblem(t,x+16,y+13,42);
            titleLabel(t,x+75,y+30,17,1,false,!t.owned(),w-178);
            text(t.category(),x+75,y+50,10,0xff7c859a,false);
            if(Objects.equals(equipped,t.id()))pill("✓ 已佩戴",x+w-84,y+22,0xa3ffffff,0xff448772,false);
            else if(!t.owned())icon("lock",x+w-36,y+26,14,0xff8d97ad);
        } else {
            text(t.category(),x+20,y+32,10,0xff7c859a,false);
            titleLabel(t,x+20,y+66,20,1,false,!t.owned(),w-111);
            if(!t.rarity().isEmpty()){d.circle(x+23,y+88,2,a(t.rarityColor()));text(t.rarity(),x+32,y+92,10,t.rarityColor(),false);}
            emblem(t,x+w-81,y+43,60);
            if(Objects.equals(equipped,t.id()))pill("✓ 已佩戴",x+w-86,y+12,0xa3ffffff,0xff448772,false);
            else if(!t.owned())icon("lock",x+w-31,y+17,14,0xff8d97ad);
            d.line(x+20,y+124,x+w-20,y+124,1,a(mix(t.color(),0x00ffffff,.75f)));
            icon("calendar-days",x+20,y+136,12,0xff7b859c);
            text(t.owned()?titleDate(t):"尚未解锁",x+38,y+146,10,0xff7b859c,false);
            if(selected){icon("check",x+w-77,y+135,12,mix(t.color(),INK,.4f));text("已选中",x+w-60,y+146,10,mix(t.color(),INK,.4f),false);}
            else icon("arrow-up-right",x+w-34,y+135,14,mix(t.color(),INK,.4f));
        }
        // Clamp hit areas to the scroll viewport so clipped cards cannot steal input.
        hit("title-"+t.id(),t.name()+(t.owned()?"":"，未获得"),x,y,w,h,()->selectedTitle=t.id());
        endTilt(tilt,x,y,w,h,18,5,.5f);
        endAlpha();
    }
    private void emblem(HubData.Title t,float x,float y,float size) {
        float radius=size*.28f;
        d.save();d.translate(x+size/2,y+size/2);d.canvas().rotate(-9);
        d.save();d.canvas().rotate(12);d.translate(4,-5);
        int back=t.owned()?t.color():desaturate(t.color(),.35f);
        d.boxShadow(-size/2,-size/2,size,size,radius,0,9,18,0,withAlpha(back,.35f));
        d.linearGradientRoundRect(-size/2,-size/2,size,size,radius,145,new int[]{back,mix(back,0xff463169,.17f)},new float[]{0,1});d.restore();
        d.captureGlassBackdrop(-size/2,-size/2,size,size);d.blurBackdrop(-size/2,-size/2,size,size,radius,4,1);
        d.linearGradientRoundRect(-size/2,-size/2,size,size,radius,145,new int[]{0xa6ffffff,0x47ffffff},new float[]{0,1});
        d.strokeRoundRect(-size/2,-size/2,size,size,radius,1,a(0xe8ffffff));
        icon("stroke-1.5/"+t.icon(),-size*.27f,-size*.27f,size*.54f,mix(t.color(),0xff25364c,.32f));d.restore();
    }
    private void titleDetail() {
        HubData.Title t=data.title(selectedTitle);if(t==null&&!data.titles.isEmpty())t=data.titles.get(0);
        if(t==null){glass(RIGHT,174,310,626,22,0xecffffff,0xabffffff);textCenter("选择称号查看详情",1077,450,15,MUTED,false);return;}
        final HubData.Title selected=t;
        glass(RIGHT,174,310,626,22,0xecffffff,0xabffffff);
        text("佩戴预览",940,207,13,0xff4f5a73,true);
        boolean worn=Objects.equals(equipped,t.id());
        pill(worn?"✓ 已佩戴":"◉ 仅预览",1144,188,worn?0xffeff9f4:0xfff6f1ff,worn?0xff4c8573:0xff85709d,false);
        d.gradientRoundRect(940,225,274,226,18,a(t.light()),a(0xffe8e3ff));
        d.strokeRoundRect(940,225,274,226,18,1,a(0xffffffff));d.strokeRoundRect(948,233,258,210,11,1,a(0x8cffffff));
        d.save();d.clipRoundRect(940,225,274,226,18);d.translate(1077,338);d.canvas().rotate(-30);
        d.strokeRoundRect(-134,-49,268,98,49,1,a(0xb8ffffff));d.restore();
        icon("stroke-1.0/"+t.icon(),931,292,106,withAlpha(t.color(),.14f));
        // Web line boxes: top26 + height31.5 + margin5, then the 17px player-name line.
        // Measured baselines relative to the preview top are 50 and 76.5 (not glyph-top positions).
        titleLabel(t,1077,275,21,2,true,false);
        String previewPlayer=fit(playerName,230,13,true);
        float previewPlayerWidth=d.textWidthTracked(previewPlayer,13,500,.5f,false);
        d.textTracked(previewPlayer,1077-previewPlayerWidth*.5f,301.5f,13,a(0xff62718f),500,.5f,false);
        // .nameplate-preview::before + .preview-ground: two true ellipses form the source pedestal.
        d.ovalShadow(1007,377,140,42,0,5,12,a(0x277184b8));
        d.oval(1007,377,140,42,a(0x9cf6f8ff));
        d.insetOvalShadow(1008,378,138,40,0,-5,7,a(0x17b0a8d0));
        d.strokeOval(1007.5f,377.5f,139,41,1,a(0xffffffff));
        d.oval(1021.5f,379,111,26,a(0x4da3b4d5));
        // The projected cube's bottom meets the ground ellipse at y=392, as in the web preview.
        if(opacity>.04f)HubHead.draw(d,1051,335,51,-23);
        textCenter("游戏内效果示意",1077,419.5f,10,0xff7d87a1,false);
        icon(t.icon(),940,473,18,mix(t.color(),INK,.55f));titleLabel(t,966,488,14,0,false,false);hit("full-title","查看称号全文",940,466,274,28,()->openOverlay("title-name"));
        wrap(t.description(),940,516,274,12,22,0xff748098,false);
        glass(940,568,274,73,10,0xfff7f7fc,0xfff7f7fc);
        icon("gift",952,580,13,0xff8990a5);text(t.owned()?"获得方式":"解锁条件",973,591,10,0xff8990a5,false);
        wrap(t.condition(),952,615,250,11,18,0xff6d7890,false);
        if(t.owned()) {icon("calendar-days",942,655,13,MUTED);text(titleDate(t)+"  获得",962,667,10,MUTED,false);}
        String equipText=!t.owned()?"尚未获得":worn?"✓ 已佩戴 · 点击摘下":"佩戴这个称号  →";
        button("equip",pendingModules.contains("titles")?"等待服务器确认…":equipText,940,687,274,38,!worn,t.owned()&&!pendingModules.contains("titles")&&catalogueComplete,()->{
            if(live()){JsonObject p=mutation("titles");p.addProperty("title",Objects.equals(equipped,selected.id())?"":selected.id());request("titles","equip",p);return;}
            equipped=Objects.equals(equipped,selected.id())?null:selected.id();
            notify(equipped==null?"已摘下称号 · 本地演示":"已佩戴「"+selected.name()+"」· 本地演示");
        });
        text("展示给其他玩家",940,754,11,0xff65718b,false);
        if(live())iconButton("display-settings","称号展示位置","settings",1128,742,25,()->openOverlay("title-display"),!pendingModules.contains("titles"));
        String visibilityNote=!privacyKnown()?(catalogueComplete?"称号展示状态暂不可用":"正在读取称号展示状态…"):live()&&(equipped==null||equipped.isEmpty())?"先佩戴称号后可设置":visible?(live()?"仅修改当前佩戴称号的展示":"本地展示示意"):"已隐藏展示，佩戴状态保留";
        text(visibilityNote,940,774,10,0xff8a94aa,false);
        toggle("visibility",privacyKnown()&&visible,1174,746,this::changeVisibility,canChangeVisibility());
    }

    private Map<LocalDate,HubData.Entry> archive() {
        if(live())return data.archive;
        if(fortuneState==3)return data.archive;
        Map<LocalDate,HubData.Entry> copy=new java.util.LinkedHashMap<>(data.archive);copy.remove(data.today);return copy;
    }
    private HubData.Entry todayEntry(){return live()?data.todayRecord:new HubData.Entry(data.today,"20:26",data.fortune(1));}
    private void unavailable(String module) {
        group(30,350);glass(LEFT,174,WIDTH,626,22,0xecffffff,0xabffffff);
        icon("info",692,351,34,0xff9d90b4);textCenter(fit(moduleLabel(module),800,16,true),734,422,16,MUTED,true);
        textCenter("其他页面仍可使用",734,455,12,MUTED,false);
        button("retry-"+module,"重新读取",679,485,110,37,false,true,()->request(module,"snapshot",new JsonObject()));ungroup();
    }
    private void fortunes() {
        Map<LocalDate,HubData.Entry> records=archive();
        heading("我的签笺","一日一签，把沿途的机缘慢慢收藏。","DailyFortunes · "+moduleLabel("fortunes"),archiveCount());
        if(live()&&!ready("fortunes")){unavailable("fortunes");return;}
        group(35,360);
        glass(LEFT,174,662,626,22,0xecffffff,0xabffffff);
        int stored=(int)records.keySet().stream().filter(date->YearMonth.from(date).equals(month)).count();
        float monthProgress=progress(now-monthAt,260_000_000L);
        float monthHeading=reducedMotion?1:HubMotion.easeOut(monthProgress);
        float monthOffset=monthDirection*(28f/3)*(1-monthHeading);
        beginAlpha(monthHeading,LEFT,174,430,78);
        text(String.format(Locale.ROOT,"%02d",month.getMonthValue()),258+monthOffset,231,39,0xff67549b,true);
        text(month.getYear()+" 年 "+month.getMonthValue()+" 月",320+monthOffset,211,13,0xff505b76,true);
        icon("bookmark",321+monthOffset,223,12,0xff8a8fa5);text(calendarInitialLoading()?"正在读取本月收藏…":"本月收藏 "+stored+" 张",339+monthOffset,234,10,0xff8a8fa5,false);
        endAlpha();
        glass(704,195,171,37,11,0xfff0edf8,0xfff0edf8);
        textButton("current-month","本月",707,198,45,30,()->{monthDirection=1;month=YearMonth.from(data.today);selectedDate=data.today;chooseLatestOnCalendar=false;monthAt=System.nanoTime();historyScroll=0;calendarRequest();},true);
        iconButton("previous-month","上个月","chevron-left",752,198,29,()->shiftMonth(-1),true);
        iconButton("next-month","下个月","chevron-right",782,198,29,()->shiftMonth(1),month.isBefore(YearMonth.from(data.today)));
        d.line(816,205,816,225,1,a(0xffdbd7e7));
        iconButton("fortune-mode",history?"切换月历显示":"切换签笺列表",history?"grid-2x2":"list",829,198,30,()->{history=!history;historyScroll=0;monthAt=System.nanoTime();},true);
        d.save();d.clipRoundRect(255,252,624,498,0);
        float monthBody=reducedMotion?1:HubMotion.bezier(monthProgress,.18f,.75f,.22f,1);
        float slide=monthDirection*28*(1-monthBody);
        beginAlpha(monthBody);push(Matrix33.makeTranslate(slide,0));
        if(calendarInitialLoading())textCenter("正在读取本月收藏…",567,485,14,MUTED,false);else if(history)fortuneHistory(records,0);else calendar(records,0);
        pop();endAlpha();d.restore();
        String[] grades={"上上签","上吉签","中平签","小凶签","下下签"};
        for(int i=0;i<grades.length;i++) {float x=258+i*122;d.circle(x+3,775,3,a(HubData.gradeColor(grades[i])));text(grades[i],x+15,779,10,0xff808aa0,false);}
        fortuneSelection(records);ungroup();
    }
    private void calendar(Map<LocalDate,HubData.Entry> records,float slide) {
        String weekdays="日一二三四五六";
        for(int i=0;i<7;i++)textCenter(weekdays.substring(i,i+1),298+i*88,273,11,i==0||i==6?0xffae92af:0xff8c92a8,false);
        int start=month.atDay(1).getDayOfWeek().getValue()%7;
        int cells=((start+month.lengthOfMonth()+6)/7)*7,rows=cells/7;
        float gap=7,cellW=(618-gap*6)/7,cellH=(449-gap*(rows-1))/rows;
        for(int i=0;i<cells;i++) {
            int day=i-start+1;if(day<1||day>month.lengthOfMonth())continue;
            LocalDate date=month.atDay(day);HubData.Entry entry=records.get(date);
            float x=258+(i%7)*(cellW+gap),y=292+(i/7)*(cellH+gap);
            boolean future=date.isAfter(data.today),selected=date.equals(selectedDate),today=date.equals(data.today);
            int grade=entry==null?0xffa0a7bb:HubData.gradeColor(entry.fortune().grade());
            if(entry!=null)d.gradientRoundRect(x,y,cellW,cellH,11,a(0xffffffff),a(lighten(grade,.43f)));
            else d.roundRect(x,y,cellW,cellH,11,a(future?0xfff5f5fa:0xfff4f4fa));
            float selection=stateTransition("day-selected-"+date,selected?1:0,160);
            d.strokeRoundRect(x,y,cellW,cellH,11,1,mix(entry!=null?0xffe1d9d0:0xffe6e7f1,0xff8b6bb9,selection));
            if(selection>0){d.boxShadow(x,y,cellW,cellH,11,0,0,0,2,withAlpha(0x279a7bc3,selection));d.boxShadow(x,y,cellW,cellH,11,0,4,9,0,withAlpha(0x198876aa,selection));}
            float hover=transition("day-hover-"+date,!future&&inside(x+slide,y,cellW,cellH)?1:0,160);
            if(hover>0)d.strokeRoundRect(x,y,cellW,cellH,11,1,withAlpha(0xffb396d8,hover));
            text(Integer.toString(day),x+10,y+20,12,future?0xffacb2c2:0xff65728b,false);
            if(today)textRight("今天",x+cellW-7,y+18,9,0xff8f70ad,false);
            float slipH=Math.min(38,cellH-26),slipW=slipH*.83f;
            if(!future){d.save();d.translate(x+cellW/2,y+cellH/2+slipH/2-10);d.canvas().rotate(-8);image("fortune-slip",-slipW/2,-slipH/2,slipW,slipH,entry!=null?1:.14f);d.restore();}
            if(entry!=null)textRight(entry.fortune().grade(),x+cellW-5,y+cellH-6,9,grade,false);
            if(!future)hit("day-"+date,date+(entry==null?" 未收藏":" "+entry.fortune().title()),x+slide,y,cellW,cellH,()->selectedDate=date);
        }
    }
    private void fortuneHistory(Map<LocalDate,HubData.Entry> records,float slide) {
        List<HubData.Entry> entries=records.values().stream().filter(e->YearMonth.from(e.date()).equals(month)).sorted(Comparator.comparing(HubData.Entry::date).reversed()).toList();
        historyScroll=Math.max(0,Math.min(historyScroll,Math.max(0,entries.size()*80-488)));
        if(entries.isEmpty()){icon("book-open",547,382,30,0xffa49bb3);textCenter("这个月还没有签笺",567,443,17,0xff8c8495,true);textCenter("已收藏的签会按抽签日期出现在这里。",567,474,12,MUTED,false);return;}
        for(int i=0;i<entries.size();i++) {
            HubData.Entry entry=entries.get(i);float y=256+i*80-historyScroll;
            if(y+70<252||y>746)continue;
            boolean selected=entry.date().equals(selectedDate);
            d.roundRect(258,y,618,70,11,a(selected?0xffeee7fb:0x90ffffff));
            d.strokeRoundRect(258,y,618,70,11,1,a(0xffe7e5f2));
            image("fortune-slip",270,y+14,32,41,1);text(entry.fortune().title(),319,y+29,13,0xff55617b,true);
            text(entry.date().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))+"  "+entry.time(),319,y+51,10,0xff8d96ac,false);
            textRight(entry.fortune().grade(),835,y+37,12,HubData.gradeColor(entry.fortune().grade()),false);
            icon("chevron-right",848,y+26,16,0xff9989b2);
            float top=Math.max(252,y),bottom=Math.min(746,y+70);
            if(bottom>top)hit("history-"+entry.date(),entry.date()+" "+entry.fortune().title(),258+slide,top,618,bottom-top,()->selectedDate=entry.date());
        }
    }
    private void fortuneSelection(Map<LocalDate,HubData.Entry> records) {
        HubData.Entry entry=records.get(selectedDate);
        glass(RIGHT,174,310,450,22,0xf2fffdf9,0xc7ffffff);
        text(selectedDate.format(DATE),939,204,11,0xff85879a,false);
        if(selectedDate.equals(data.today))pill("今天",1160,187,0xbceffff2,0xff25816a,true);
        if(entry==null) {
            image("fortune-slip",1039,314,76,97,.3f);textCenter(calendarInitialLoading()?"正在读取这一天的签笺…":"这一天，没有留下签笺",1077,456,15,0xff8c8495,true);
            wrap(calendarInitialLoading()?"等待服务器确认收藏记录。":"打开藏签匣时，背包中属于你的已解签会按对应日期归档。",957,491,240,12,22,0xffa39bab,false);
            if(selectedDate.equals(data.today)&&fortuneState<3)textCenter(stateLabel()+" · 尚未归档",1077,562,11,0xffa39bab,false);
        } else {
            HubData.Fortune f=entry.fortune();
            paper(937,219,280,331);
            glass(1053,238,48,49,13,0x94f2deac,0xfffff7e6);image("fortune-slip",1062,244,29,37,1);
            textCenter(f.grade()+" · 第"+f.id()+"签",1077,310,11,HubData.gradeColor(f.grade()),false);
            trackedCenter(f.title(),1077,348,25,3,0xff615b4f,true,true);
            d.line(961,365,1193,365,1,a(0x36c9b78d));
            for(int i=0;i<f.poem().size();i++)trackedCenter(f.poem().get(i),1077,391+i*30.55f,13,1.6f,0xff817967,false,true);
            d.line(961,494,1193,494,1,a(0x36c9b78d));
            text(fit(playerName,190,9,false)+" 所得",960,526,9,0xffae9565,false);textRight(entry.time(),1195,526,9,0xffae9565,false);
            icon("check-check",940,582,14,0xff7f8f87);text("已收藏",962,594,10,0xff7f8f87,false);
            button("read-selected","阅读解签  →",1093,572,123,35,true,true,()->openReading(entry));
        }
        glass(RIGHT,640,310,160,19,0x99e6eaff,0xd1f6edff);
        glass(938,656,53,56,14,0xd4ffffff,0x45ffffff);image("collection-chest",936,653,57,61,1);
        HubData.Entry display=live()?data.featured:records.get(displayDate);
        text("手册展示",1004,668,10,0xff7b869b,false);
        if(live()&&data.featured!=null)iconButton("clear-feature","清除手册展示","x",1187,651,24,()->{JsonObject p=mutation("fortunes");p.addProperty("date","");request("fortunes","feature",p);},!pendingModules.contains("fortunes"));
        text(display==null?"还没有选择签笺":display.fortune().title(),1004,691,13,0xff625c80,true);
        text(display==null?"选择一张已收藏的签":shortDate(display.date())+" · 已收藏",1004,709,10,0xff9290aa,false);
        boolean current=Objects.equals(displayDate,selectedDate)&&entry!=null;
        button("display-selected",pendingModules.contains("fortunes")?"等待服务器确认…":current?"当前选择已展示  ✓":"展示选中的签  ↗",938,725,278,33,false,entry!=null&&!current&&!pendingModules.contains("fortunes"),()->displaySelected(entry));
        textCenter("仅用于手册，方块展示在游戏内设置",1077,783,9,0xff948ba6,false);
    }
    private void paper(float x,float y,float w,float h) {
        d.gradientRoundRect(x,y,w,h,14,a(0xfffff9ec),a(0xfffffef9));
        d.strokeRoundRect(x,y,w,h,14,1,a(0xffe9dcbf));
        d.strokeRoundRect(x+6,y+6,w-12,h-12,9,1,a(0x2bcdb57e));
    }
    private void shiftMonth(int amount) {
        YearMonth target=month.plusMonths(amount);if(target.isAfter(YearMonth.from(data.today)))return;
        month=target;monthDirection=Integer.signum(amount);monthAt=System.nanoTime();historyScroll=0;
        chooseLatestOnCalendar=true;
        selectedDate=archive().keySet().stream().filter(date->YearMonth.from(date).equals(month)).max(Comparator.naturalOrder()).orElse(month.atDay(1));
        calendarRequest();
    }
    private void displaySelected(HubData.Entry entry) {
        if(entry==null||!archive().containsKey(entry.date())||Objects.equals(displayDate,entry.date()))return;
        if(live()){JsonObject p=mutation("fortunes");p.addProperty("date",entry.date().toString());request("fortunes","feature",p);return;}
        displayDate=entry.date();notify("已更新手册展示的签笺 · 本地演示");
    }

    private void modal() {
        opacity=1;offsetY=0;nextHits.clear();
        d.captureGlassBackdrop(viewportX,viewportY,viewportW,viewportH);
        float backdrop=reducedMotion?1:HubMotion.ease(progress(now-modalAt,180_000_000L));
        beginAlpha(backdrop,viewportX,viewportY,viewportW,viewportH);d.blurBackdrop(viewportX,viewportY,viewportW,viewportH,0,9,1);
        d.rect(viewportX,viewportY,viewportW,viewportH,0x3634405c);
        boolean wide=reading!=null;
        modalW=wide?720:440;
        modalH=wide?147+Math.max(357,178.5f+trackedLines(reading.fortune().interpretation(),319.5f,15,1,400,true).size()*31.5f):"settings".equals(overlay)?520:"profile".equals(overlay)?590:"preview".equals(overlay)?430:355;
        modalX=(1260-modalW)/2;modalY=(850-modalH)/2;
        float enter=reducedMotion?1:HubMotion.bezier(progress(now-modalAt,220_000_000L),.2f,.8f,.2f,1);
        beginAlpha(enter,modalX,modalY,modalW,modalH);
        push(HubDraw.cssPerspective(630,425,800,0,0,0,.985f+.015f*enter,0,9*(1-enter)));
        d.boxShadow(modalX,modalY,modalW,modalH,25,0,28,90,0,0x4235416c);
        d.linearGradientRoundRect(modalX,modalY,modalW,modalH,25,135,new int[]{0xf5fcfdff,0xeff5f3ff},new float[]{0,1});
        d.strokeRoundRect(modalX,modalY,modalW,modalH,25,1,0xffffffff);
        String title=wide?"签笺详情":switch(overlay){case "settings"->"界面设置";case "profile"->"个人资料";case "preview"->"预览选项";case "title-name"->"称号全文";case "title-display"->"称号展示位置";default->fortuneState==0?"如何抽签":"如何解签";};
        String subtitle=wide?reading.date().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))+" · "+playerName+" 所得":switch(overlay){case "settings"->"让手册更符合你的习惯";case "profile"->"你在这段旅程中留下的足迹";case "preview"->"仅用于体验不同状态，不会修改游戏数据";case "title-name"->"完整文字与原始样式";case "title-display"->"个人偏好与服务器展示规则";default->"DailyFortunes · 玩法提示";};
        text(title,modalX+27,modalY+39,19,0xff35415e,true);
        text(subtitle,modalX+27,modalY+63,12,0xff8c8fa5,false);
        iconButton("modal-close","关闭弹窗","x",modalX+modalW-58,modalY+22,32,this::dismissOverlay,true);
        d.line(modalX+26,modalY+84,modalX+modalW-26,modalY+84,1,0xffe7e6f1);
        if(wide)readingModal();else switch(overlay) {
            case "settings"->settingsModal();case "profile"->profileModal();case "preview"->previewModal();case "title-name"->titleNameModal();case "title-display"->titleDisplayModal();default->fortuneHelp();
        }
        pop();endAlpha();endAlpha();
    }
    private void settingsModal() {
        float x=modalX+28,y=modalY+118;
        text("开屏动画",x,y,14,INK,true);textRight("从低到高",modalX+modalW-28,y,10,0xff9694ac,false);
        glass(x,y+22,384,94,14,0x96eaf0ff,0x8cf1eafe);
        int selected=welcomeIndex();float start=x+64,trackY=y+48;
        d.roundRect(start,trackY,256,6,4,0xffd9dced);
        if(selected>0)d.gradientRoundRect(start,trackY,selected*128,6,4,0xff819de0,0xff9575da);
        for(int i=0;i<3;i++){d.circle(start+i*128,trackY+3,3,0xffffffff);d.strokeRoundRect(start+i*128-3,trackY,6,6,3,1,0xffbdc4dd);}
        d.circle(start+selected*128,trackY+5,12,0x226b61ae);d.circle(start+selected*128,trackY+3,11,0xffffffff);
        d.circle(start+selected*128,trackY+3,8,selected==0?0xff96b4f1:selected==1?0xff97a0e8:0xff9172d8);
        hit("frequency","开屏动画频率",start-15,trackY-12,286,30,()->setWelcomeFromX(mx));
        String[] labels={"仅初次","每次登录","每次打开"};
        for(int i=0;i<3;i++) {
            final int index=i;float bx=x+5+i*125;
            if(i==selected)d.roundRect(bx,y+74,124,28,8,0x8fffffff);
            float hover=transition("frequency-label-"+i+"-hover",inside(bx,y+74,124,28)?1:0,180);
            if(hover>0)d.roundRect(bx,y+74,124,28,8,withAlpha(0x66ffffff,hover));
            textCenter(labels[i],bx+62,y+93,11,i==selected?0xff7056af:mix(0xff81869e,0xff67509d,hover),i==selected);
            hit("frequency-label-"+i,labels[i],bx,y+74,124,28,()->welcomeMode=List.of("once","login","always").get(index));
        }
        String explanation=reducedMotion?"减少动态效果已开启，开屏动画会跳过。":switch(welcomeMode){case "once"->"仅首次打开时播放；已经看过的记录会保留。";case "login"->"每次进入游戏后，首次打开手册时播放。";default->"每次打开手册时播放约 3 秒的手写问候。";};
        wrap(explanation,x,y+139,384,12,19.2f,0xff8c8fa5,false);
        d.line(x,y+169,x+384,y+169,1,0xffe7e6f1);
        text("减少动态效果",x,y+203,14,INK,true);text("关闭开屏动画、卡片倾斜与页面过渡",x,y+229,12,0xff8c8fa5,false);
        toggle("reduce-motion",reducedMotion,x+351,y+190,()->setReducedMotion(!reducedMotion));
        d.line(x,y+252,x+384,y+252,1,0xffe7e6f1);
        text("界面主题",x,y+284,14,INK,true);text("Liquid Glass · 浅色",x,y+309,12,0xff8c8fa5,false);
        glass(x+345,y+270,39,38,11,0xffe0e9ff,0xfff3e0ff);icon("check",x+357,y+282,14,0xff817393);
        icon("info",x,y+345,15,0xff8c8fa5);wrap(live()?"偏好按玩家与当前服务器／存档隔离保存。":"偏好保存在当前测试实例；统计与收藏为本地演示。",x+25,y+357,359,11,18,0xff8c8fa5,false);
    }
    private void profileModal() {
        float cx=630,top=modalY+115;
        glass(cx-46,top,92,92,24,0xffe5ecff,0xffeae1ff);HubHead.draw(d,cx-27,top+21,54,-23);
        textCenter(fit(playerName,480,24,true),cx,top+130,24,INK,true);
        HubData.Title worn=data.title(equipped);if(worn==null)textCenter("暂未佩戴称号",cx,top+160,14,MUTED,true);else titleLabel(worn,cx,top+160,14,1.4f,true,false);
        String[][] rows=live()?new String[][]{{"首次加入",timestamp(data.firstJoinedAt,"yyyy/MM/dd HH:mm:ss")},{"当前存档 · 累计在线",data.managerLoaded?data.onlineMillis(now)/3600000+" 小时 "+data.onlineMillis(now)/60000%60+" 分钟":"暂无记录"},{"当前存档 · 累计登录",data.managerLoaded?data.joinCount+" 次":"暂无记录"},{"玩家 UUID",data.playerUuid.isEmpty()?"正在读取":data.playerUuid}}:new String[][]{{"首次加入 · 演示","2026/10/17"},{"累计在线 · 演示","128 小时 42 分钟"},{"累计登录 · 演示","36 次"},{"玩家 UUID · 演示身份","7f1a0000-0000-4000-8000-000000000001"}};
        for(int i=0;i<rows.length;i++) {
            float y=top+207+i*48;d.line(modalX+28,y-21,modalX+modalW-28,y-21,1,0xffe7e6f1);
            text(rows[i][0],modalX+28,y,11,0xff8c8fa5,false);textRight(rows[i][1],modalX+modalW-28,y,i==3?10:12,0xff59657f,false);
        }
        textCenter(live()?"统计来自当前服务器／存档。":"名字与头像取自本地角色，其他资料均为样例。",cx,modalY+modalH-28,11,MUTED,false);
    }
    private void previewModal() {
        float x=modalX+28,y=modalY+120;
        text("今日抽签状态",x,y,13,INK,true);
        String[] labels={"尚未抽签","等待解签","已经解签","已收进藏签匣"};
        for(int i=0;i<4;i++) {
            final int state=i;float bx=x+(i%2)*197,by=y+22+(i/2)*55;
            button("preview-state-"+i,(fortuneState==i?"✓ ":"")+labels[i],bx,by,187,42,fortuneState==i,true,()->{fortuneState=state;notify("今日状态："+stateLabel()+" · 本地演示");});
        }
        wrap("未解签不会显示结果；已解但未归档的签不会出现在收藏和月历中。",x,y+164,384,12,22,0xff8c8fa5,false);
        button("reset-demo","还原演示数据",x,y+236,148,37,false,true,()->{equipped="dew";visible=true;fortuneState=3;displayDate=data.today;selectedDate=data.today;notify("样例已还原，界面偏好保留");});
        text("F8 打开此面板 · 界面偏好不参与演示还原",x,y+295,11,MUTED,false);
    }
    private void fortuneHelp() {
        float x=modalX+30,y=modalY+125;
        image(fortuneState==0?"fortune-barrel":"fortune-slip",x,y,94,106,1);
        wrap(fortuneState==0?"拿起签筒，为今天留一份期许。":"带上未解的签，前往解签龛。",x+119,y+20,235,13,21,0xff807789,true);
        wrap(fortuneState==0?"在游戏中手持签筒使用，即可抽取今日的签。抽到之后仍需解签，才会看见结果。":"签文尚未解读，因此手册不会提前展示内容。完成解签后，打开藏签匣即可归档收藏。",x+119,y+68,235,12,23,0xff807789,false);
        text(live()?"请在游戏内完成抽签、解签与归档。":"这里是本地界面原型，不会触发游戏抽签。",x,modalY+modalH-40,11,0xff9992a4,false);
    }
    private void readingModal() {
        HubData.Entry entry=reading;HubData.Fortune f=entry.fortune();
        float x=modalX+26,y=modalY+121,w=319.5f,h=modalH-147;
        d.linearGradientRoundRect(x,y,w,h,6,110,new int[]{0xfff7f0dd,0xfffffaf0},new float[]{0,1});
        d.strokeRoundRect(x,y,w,h,6,1,0xffe6dcc1);d.boxShadow(x,y,w,h,6,0,6,16,0,0x12867955);
        trackedCenter("第"+f.id()+"签 · "+f.grade(),x+w/2,y+39,11,.5f,HubData.gradeColor(f.grade()),false,false);
        trackedCenter(f.title(),x+w/2,y+89,29,4,0xff68604c,true,true);
        for(int i=0;i<f.poem().size();i++)trackedCenter(f.poem().get(i),x+w/2,y+133+i*35.2f,16,2,0xff84806c,false,true);
        d.line(x+8,y+8,x+26,y+8,1,0xffcbbc93);d.line(x+8,y+8,x+8,y+26,1,0xffcbbc93);
        d.line(x+w-8,y+h-8,x+w-26,y+h-8,1,0xffcbbc93);d.line(x+w-8,y+h-8,x+w-8,y+h-26,1,0xffcbbc93);
        push(Matrix33.makeRotate(-5,x+w/2,y+292));beginAlpha(.7f,x+w/2-11,y+281,22,22);
        d.strokeRoundRect(x+w/2-11,y+281,22,22,0,1,0xffb58b6b);serifCenter("签",x+w/2,y+297,12,0xffb08767,false);endAlpha();pop();
        trackedCenter("DAILY FORTUNES",x+w/2,y+333,10,2,0xffc1b693,false,false);
        float rx=x+w+29;
        icon("leaf",rx,y+8,15,0xff8f7da7);tracked("解签",rx+23,y+21,12,2,0xff8f7da7,500,false);
        trackedWrap(f.interpretation(),rx,y+57,w,15,31.5f,1,0xff7b7883,400,true);
        boolean archived=archive().containsKey(entry.date());
        d.line(rx,y+h-111,rx+w,y+h-111,1,0xffe6e2ef);
        icon("bookmark",rx,y+h-97,16,0xff8f91a2);
        text(archived?"已收藏于你的藏签匣":"已解签，尚未收藏",rx+24,y+h-84,10,0xff8f91a2,false);
        text(archived?"签笺一经收藏，便成为旅途的一部分。":"在游戏中打开藏签匣，即可收存这张签。",rx+24,y+h-66,10,0xff9997aa,false);
        boolean current=Objects.equals(displayDate,entry.date());
        button("reading-display",!archived?"收藏后可设为展示":current?"当前展示的签笺":"设为手册展示  ✧",rx,y+h-45,w,37,true,archived&&!current,()->displaySelected(entry));
    }

    private static final String HELLO_PATH="M24 111 C42 99 63 77 72 48 C83 14 67 11 58 29 C48 49 49 86 41 121 C48 94 60 77 73 81 C92 88 64 127 88 124 C101 122 111 111 120 101 C139 80 143 95 128 104 C99 122 112 139 139 123 C160 111 176 80 185 48 C194 13 177 12 170 33 C162 57 159 100 157 113 C154 140 178 126 194 105 C211 82 222 51 224 32 C228 11 210 14 206 38 C201 65 196 102 199 116 C201 136 220 125 235 108 C253 85 268 91 262 112 C256 136 231 136 229 116 C227 98 243 87 255 96 C270 107 284 109 303 93";
    private static final String FLOURISH_PATH="M97 145 C153 133 229 134 283 142 C302 145 316 143 329 136";
    private void welcome() {
        float seconds=(now-openedAt)/1_000_000_000f;
        float fade=HubMotion.bezier(clamp((seconds-2.6f)/.42f,0,1),.42f,0,.58f,1);
        beginAlpha(1-fade);
        d.roundRect(0,0,1260,850,28,0xfff7f8ff);
        d.radialGradientRoundRect(0,0,1260,850,28,352.8f,238,756,510,new int[]{0xc9d2ebff,0x00d2ebff},new float[]{0,.6f});
        d.radialGradientRoundRect(0,0,1260,850,28,1008,637.5f,819,552.5f,new int[]{0xb8e1d4f9,0x00e1d4f9},new float[]{0,.65f});
        d.save();d.translate(420,296);d.canvas().scale(420f/330,420f/330);d.translate(165,80);d.canvas().rotate(-5);d.translate(-165,-80);
        float write=HubMotion.bezier(clamp((seconds-.12f)/1.9f,0,1),.32f,.08f,.35f,1);
        strokeSvg(HELLO_PATH,write,6.8f,1);
        strokeSvg(FLOURISH_PATH,HubMotion.easeOut(clamp((seconds-1.81f)/.35f,0,1)),2.1f,.58f);
        d.restore();
        float signature=HubMotion.ease(clamp((seconds-1.55f)/.5f,0,1));
        beginAlpha(signature,420,525,420,30);trackedCenter("PIXELMC",632.5f,535+5*(1-signature),10,5,0xff9292ad,false,false);endAlpha();
        endAlpha();
    }
    private void strokeSvg(String svg,float fraction,float width,float alpha) {
        if(fraction<=0)return;
        try(io.github.humbleui.skija.Path full=io.github.humbleui.skija.Path.makeFromSVGString(svg);
            io.github.humbleui.skija.PathMeasure measure=new io.github.humbleui.skija.PathMeasure(full,false,4);
            io.github.humbleui.skija.PathBuilder builder=new io.github.humbleui.skija.PathBuilder();
            io.github.humbleui.skija.Paint paint=new io.github.humbleui.skija.Paint()) {
            measure.getSegment(0,measure.getLength()*fraction,builder,true);
            try(io.github.humbleui.skija.Path segment=builder.build();
                io.github.humbleui.skija.Shader shader=io.github.humbleui.skija.Shader.makeLinearGradient(
                    full.getBounds().getLeft(),0,full.getBounds().getRight(),0,
                    new int[]{0xff5d84d6,0xff8970d8,0xffb184bf},new float[]{0,.55f,1})) {
                paint.setAntiAlias(true).setMode(io.github.humbleui.skija.PaintMode.STROKE).setStrokeWidth(width)
                    .setStrokeCap(io.github.humbleui.skija.PaintStrokeCap.ROUND).setStrokeJoin(io.github.humbleui.skija.PaintStrokeJoin.ROUND)
                    .setColor(0xffffffff).setAlphaf(alpha).setShader(shader);
                d.canvas().drawPath(segment,paint);
            }
        }
    }

    private void group(int delayMs,int durationMs) {
        float p;
        if(page!=0){p=reducedMotion?1:HubMotion.ease(progress(now-pageAt,240_000_000L));offsetY=5*(1-p);}
        else {
            float time=progress(now-pageAt-delayMs*1_000_000L,durationMs*1_000_000L);
            p=reducedMotion?1:durationMs==440?HubMotion.bezier(time,.2f,.7f,.2f,1):HubMotion.easeOut(time);
            offsetY=12*(1-p);
        }
        beginAlpha(p);push(Matrix33.makeTranslate(0,offsetY));pageTransform=logical;offsetY=0;
    }
    private void ungroup(){pop();endAlpha();opacity=1;offsetY=0;}
    private HubMotion.Tilt beginTilt(String id,float x,float y,float w,float h,float amplitude,float hoverScale,boolean intro,float shadowStrength,float radius) {
        HubMotion.Tilt state=tilts.computeIfAbsent(id,ignored->new HubMotion.Tilt(intro&&!reducedMotion));
        boolean hover=inside(x,y,w,h);float[] mouse=HubDraw.inversePoint(logical,mx,my);
        float nx=clamp((mouse[0]-x-w/2)/(w/2),-1,1),ny=clamp((mouse[1]-y-h/2)/(h/2),-1,1);
        state.update(dt,reducedMotion,hover,keyboardFocus&&Objects.equals(focusId,id),Objects.equals(pressedId,id)&&!pressMoved,nx,ny,amplitude);
        float scale=state.scale(hoverScale),rx=(float)Math.toRadians(state.rx.value),ry=(float)Math.toRadians(state.ry.value);
        float spread=scale*(.86f+.04f*state.lift.value),sw=w*spread,sh=h*spread;
        float dx=-(float)Math.sin(ry)*22,dy=12+16*state.lift.value+(float)Math.sin(rx)*18-state.ty.value*.6f;
        int shadow=withAlpha(0x8c000000,shadowStrength*state.shown()*(.6f+.4f*state.lift.value));
        d.boxShadow(x+(w-sw)/2,y+(h-sh)/2,sw,sh,radius,dx,dy,40,0,shadow);
        beginAlpha(state.shown(),x,y,w,h);push(HubDraw.cssPerspective(x+w/2,y+h/2,800,state.rx.value,state.ry.value,0,scale,0,state.ty.value));
        return state;
    }
    private void endTilt(HubMotion.Tilt state,float x,float y,float w,float h,float radius,float amplitude,float glare) {
        float gx=.5f+state.ry.value/Math.max(amplitude,1)*.46f,gy=.5f-state.rx.value/Math.max(amplitude,1)*.46f;
        d.tiltGlare(x,y,w,h,radius,gx,gy,state.glare(glare,amplitude));pop();endAlpha();
    }
    private void push(Matrix33 matrix){matrixStack.push(logical);logical=logical.makeConcat(matrix);d.save();d.canvas().concat(matrix);}
    private void pop(){d.restore();logical=matrixStack.pop();}
    private void beginAlpha(float alpha){alphaStack.push(interactiveAlpha);interactiveAlpha*=alpha;d.saveLayerAlpha(-128,-128,1516,1106,alpha);}
    private void beginAlpha(float alpha,float x,float y,float w,float h){alphaStack.push(interactiveAlpha);interactiveAlpha*=alpha;d.saveLayerAlpha(x,y,w,h,alpha);}
    private void endAlpha(){d.restore();interactiveAlpha=alphaStack.pop();}
    private float transition(String id,float target,int milliseconds){return transitions.computeIfAbsent(id,ignored->new HubMotion.Transition()).sample(target,now,milliseconds*1_000_000L,reducedMotion);}
    private float stateTransition(String id,float target,int milliseconds){return transitions.computeIfAbsent(id,ignored->{HubMotion.Transition c=new HubMotion.Transition();c.value=c.target=c.from=target;c.initialized=true;c.started=now;return c;}).sample(target,now,milliseconds*1_000_000L,reducedMotion);}
    private void shadow(float x,float y,float w,float h,float r,int color) {
        d.boxShadow(x,y,w,h,r,0,6,22,0,color);
    }
    private void glass(float x,float y,float w,float h,float radius,int from,int to) {
        shadow(x,y,w,h,radius,0x104b5d85);
        d.linearGradientRoundRect(x,y,w,h,radius,135,new int[]{from,to},new float[]{0,1});
        d.strokeRoundRect(x+.5f,y+.5f,w-1,h-1,radius,1,a(0xe9ffffff));
        if(h>28)d.line(x+radius,y+1,x+w-radius,y+1,1,a(0xd9ffffff));
    }
    private void text(String s,float x,float baseline,float size,int color,boolean bold){d.text(s,x,baseline,size,a(color),bold?600:400);}
    private void trackedCenter(String s,float x,float baseline,float size,float tracking,int color,boolean bold,boolean serif) {
        float width=trackedWidth(s,size,tracking,bold?600:400,serif);tracked(s,x-width/2,baseline,size,tracking,color,bold?600:400,serif);
    }
    private float trackedWidth(String s,float size,float tracking,int weight,boolean serif) {
        return d.textWidthTracked(s,size,weight,tracking,serif);
    }
    private void tracked(String s,float x,float baseline,float size,float tracking,int color,int weight,boolean serif) {
        d.textTracked(s,x,baseline,size,color,weight,tracking,serif);
    }
    private String fitTracked(String s,float width,float size,float tracking,int weight,boolean serif) {
        if(trackedWidth(s,size,tracking,weight,serif)<=width)return s;
        int end=s.length();while(end>0&&trackedWidth(s.substring(0,end)+"…",size,tracking,weight,serif)>width)end=s.offsetByCodePoints(end,-1);
        return s.substring(0,end)+"…";
    }
    private void titleLabel(HubData.Title title,float x,float baseline,float size,float tracking,boolean center,boolean locked) {
        titleLabel(title,x,baseline,size,tracking,center,locked,center?250:x>=RIGHT?246:450);
    }
    private void titleLabel(HubData.Title title,float x,float baseline,float size,float tracking,boolean center,boolean locked,float maxWidth) {
        if(live()) {
            List<HubData.Glyph> glyphs=title.glyphs();float width=richWidth(glyphs,size,tracking);
            float actual=Math.max(11,Math.min(size,size*maxWidth/Math.max(1,width)));
            width=richWidth(glyphs,actual,tracking);boolean clipped=width>maxWidth;
            float start=center?x-Math.min(width,maxWidth)*.5f:x;
            d.save();d.clipRoundRect(start-2,baseline-actual*1.3f,maxWidth+4,actual*1.7f,0);
            float cursor=start;
            for(HubData.Glyph glyph:glyphs) {
                float advance=d.textWidth(glyph.text(),actual,glyph.bold()?700:400);
                if(clipped&&cursor+advance>start+maxWidth-actual){text("…",cursor,baseline,actual,INK,false);break;}
                d.richGlyph(glyph.text(),cursor,baseline,actual,a(glyph.color()),glyph.bold(),glyph.italic(),glyph.underline(),glyph.strike());cursor+=advance+tracking;
            }
            d.restore();return;
        }
        String label=title.name();if(center)x-=trackedWidth(label,size,tracking,600,false)/2;
        if(locked){tracked(label,x,baseline,size,tracking,0xff78859b,600,false);return;}
        if(title.id().equals("dew"))d.textGradient(label,x,baseline,size,600,tracking,95,new int[]{0xff168878,0xff69a648},new float[]{0,1},false);
        else if(title.id().equals("star"))d.textGradient(label,x,baseline,size,600,tracking,100,new int[]{0xffa97929,0xffbf854a,0xff8a69bb},new float[]{0,.5f,1},false);
        else tracked(label,x,baseline,size,tracking,HubData.titleText(title),600,false);
    }
    private float richWidth(List<HubData.Glyph> glyphs,float size,float tracking){float width=0;for(HubData.Glyph glyph:glyphs)width+=d.textWidth(glyph.text(),size,glyph.bold()?700:400)+tracking;return Math.max(0,width-tracking);}
    private void titleNameModal() {
        HubData.Title title=data.title(selectedTitle);if(title==null)return;
        float left=modalX+28,right=modalX+modalW-28,x=left,y=modalY+137;
        for(HubData.Glyph glyph:title.glyphs()) {
            float advance=d.textWidth(glyph.text(),18,glyph.bold()?700:400);
            if(x+advance>right){x=left;y+=29;}
            d.richGlyph(glyph.text(),x,y,18,glyph.color(),glyph.bold(),glyph.italic(),glyph.underline(),glyph.strike());x+=advance+1;
        }
    }
    private void titleDisplayModal() {
        String[] keys={"head","chat","tab"},labels={"头顶称号","聊天称号","玩家列表称号"};
        for(int i=0;i<keys.length;i++) {
            String key=keys[i];float y=modalY+122+i*66;boolean allowed=HubData.bool(data.globalDisplay,key,true),shown=HubData.bool(data.displayPreferences,key,true);
            text(labels[i],modalX+28,y,14,INK,true);text(allowed?"使用个人展示偏好":"管理员已关闭此位置",modalX+28,y+22,11,MUTED,false);
            toggle("title-location-"+key,shown,modalX+modalW-61,y-12,()->{
                if(pendingModules.contains("titles")){notify("正在等待服务器确认");return;}
                JsonObject p=mutation("titles");for(String location:keys)p.addProperty(location,location.equals(key)?!shown:HubData.bool(data.displayPreferences,location,true));request("titles","displayPreferences",p);
            });
        }
    }
    private List<String> trackedLines(String s,float width,float size,float tracking,int weight,boolean serif) {
        List<String> result=new ArrayList<>();StringBuilder line=new StringBuilder();
        for(int offset=0;offset<s.length();) {
            int cp=s.codePointAt(offset);String glyph=new String(Character.toChars(cp));
            if(!line.isEmpty()&&trackedWidth(line+glyph,size,tracking,weight,serif)>width&&"，。！？；：、）》】」』…,.!?;:".indexOf(cp)<0){result.add(line.toString());line.setLength(0);}
            line.append(glyph);offset+=Character.charCount(cp);
        }
        if(!line.isEmpty())result.add(line.toString());return result;
    }
    private void trackedWrap(String s,float x,float baseline,float width,float size,float lineHeight,float tracking,int color,int weight,boolean serif) {
        List<String> lines=trackedLines(s,width,size,tracking,weight,serif);
        for(int i=0;i<lines.size();i++)tracked(lines.get(i),x,baseline+i*lineHeight,size,tracking,color,weight,serif);
    }
    private void serif(String s,float x,float baseline,float size,int color,boolean bold){d.textSerif(s,x,baseline,size,a(color),bold);}
    private void serifCenter(String s,float x,float baseline,float size,int color,boolean bold){serif(s,x-d.textWidthSerif(s,size,bold)/2,baseline,size,color,bold);}
    private void textCenter(String s,float x,float baseline,float size,int color,boolean bold){text(s,x-d.textWidth(s,size,bold?600:400)/2,baseline,size,color,bold);}
    private void textRight(String s,float right,float baseline,float size,int color,boolean bold){text(s,right-d.textWidth(s,size,bold?600:400),baseline,size,color,bold);}
    private void icon(String name,float x,float y,float size,int color){d.icon(name,x,y,size,a(color));}
    private void image(String name,float x,float y,float w,float h,float alpha){d.image("textures/ui/"+name+".png",x,y,w,h,alpha*opacity);}
    private String fit(String s,float width,float size,boolean bold) {
        if(d.textWidth(s,size,bold?600:400)<=width)return s;
        int length=s.length();while(length>0&&d.textWidth(s.substring(0,length)+"…",size,bold?600:400)>width)length--;
        return s.substring(0,length)+"…";
    }
    private int wrap(String s,float x,float baseline,float width,float size,float lineHeight,int color,boolean bold) {
        StringBuilder line=new StringBuilder();List<String> lines=new ArrayList<>();
        for(int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            if(c=='\n'||d.textWidth(line.toString()+c,size,bold?600:400)>width) {
                // Keep Chinese closing punctuation with its preceding text.
                if(c!='\n'&&"，。！？；：、）》】」』…,.!?;:".indexOf(c)>=0&&!line.isEmpty()) {
                    line.append(c);continue;
                }
                lines.add(line.toString());line.setLength(0);
            }
            if(c!='\n')line.append(c);
        }
        if(!line.isEmpty())lines.add(line.toString());
        if(lines.size()>1&&lines.get(lines.size()-1).length()<3) {
            int previous=lines.size()-2;String prev=lines.get(previous),last=lines.get(previous+1);
            int move=Math.min(3-last.length(),Math.max(0,prev.length()-1));
            if(move>0){lines.set(previous,prev.substring(0,prev.length()-move));lines.set(previous+1,prev.substring(prev.length()-move)+last);}
        }
        for(int i=0;i<lines.size();i++)text(lines.get(i),x,baseline+i*lineHeight,size,color,bold);
        return lines.size();
    }
    private void pill(String label,float x,float y,int background,int color,boolean dot) {
        float w=d.textWidth(label,10,false)+(dot?24:17);
        d.roundRect(x,y,w,22,11,a(background));d.strokeRoundRect(x,y,w,22,11,1,a(0xb3ffffff));
        if(dot)d.circle(x+9,y+11,2,a(color));text(label,x+(dot?17:8),y+15,10,color,false);
    }
    private void button(String id,String label,float x,float y,float w,float h,boolean primary,boolean enabled,Runnable click) {
        boolean hover=enabled&&inside(x,y,w,h),press=enabled&&Objects.equals(pressedId,id);
        float hval=transition(id+"-hover",hover?1:0,140),move=transition(id+"-move",primary?(press?1:hover?-1:0):0,140);
        boolean displayDisabled=!enabled&&id.equals("display-selected");
        beginAlpha(enabled||displayDisabled?1:.42f,x,y,w,h);push(Matrix33.makeTranslate(0,move));
        // CSS gradient-image values are discrete; only the color/shadow/transform
        // longhands interpolate. Do not invent a gradient crossfade here.
        int from=primary?(hover?0xff987af5:0xff8b6cf1):mix(0xb8ffffff,0xffffffff,hval);
        int to=primary?(hover?0xff694acb:0xff6d53d6):mix(0xb8ffffff,0xffffffff,hval);
        if(displayDisabled){from=0x59ffffff;to=0x59ffffff;}
        d.boxShadow(x,y,w,h,12,0,primary?4+2*hval:3+2*hval,primary?10+6*hval:10+6*hval,0,
            primary?mix(0x337558d1,0x4d7558d1,hval):mix(0x186c6588,0x2b6c6588,hval));
        d.linearGradientRoundRect(x,y,w,h,12,primary?130:135,new int[]{from,to},new float[]{0,1});
        d.insetBoxShadow(x,y,w,h,12,0,1,0,0,primary?mix(0x61ffffff,0x70ffffff,hval):0xffffffff);
        d.strokeRoundRect(x,y,w,h,12,1,a(primary?0xff8c71e6:0xe6ffffff));
        d.text(label,x+(w-d.textWidth(label,12,500))/2,y+(h+10)/2,12,displayDisabled?0xff9a90b3:primary?0xffffffff:0xff64539e,500);
        if(enabled)hit(id,label,x,y,w,h,click);
        pop();endAlpha();
    }
    private void goldButton(String id,String label,float x,float y,float w,float h,Runnable click) {
        boolean hover=inside(x,y,w,h);float value=transition(id+"-hover",hover?1:0,140);
        float move=transition(id+"-move",Objects.equals(pressedId,id)?1:hover?-1:0,140);push(Matrix33.makeTranslate(0,move));
        d.boxShadow(x,y,w,h,12,0,4+2*value,10+6*value,0,mix(0x269c7e44,0x4d7558d1,value));
        d.linearGradientRoundRect(x,y,w,h,12,hover?130:140,new int[]{hover?0xff987af5:0xffbe9455,hover?0xff694acb:0xffa57e42},new float[]{0,1});
        d.insetBoxShadow(x,y,w,h,12,0,1,0,0,mix(0x66ffffff,0x70ffffff,value));
        d.strokeRoundRect(x,y,w,h,12,1,a(0xffc5a477));d.text(label,x+(w-d.textWidth(label,11,500))/2,y+24,11,0xffffffff,500);hit(id,label,x,y,w,h,click);pop();
    }
    private void textButton(String id,String label,float x,float y,float w,float h,Runnable click,boolean enabled) {
        float value=transition(id+"-hover",enabled&&inside(x,y,w,h)?1:0,140);
        if(value>0)d.roundRect(x,y,w,h,8,withAlpha(0x88ffffff,value));
        textCenter(label,x+w/2,y+(h+9)/2,11,enabled?0xff7a6a99:0xffb2aabc,false);
        if(enabled)hit(id,label,x,y,w,h,click);
    }
    private void iconButton(String id,String label,String name,float x,float y,float size,Runnable click,boolean enabled) {
        float value=transition(id+"-hover",enabled&&inside(x,y,size,size)?1:0,140);
        if(value>0)d.roundRect(x,y,size,size,10,withAlpha(0xffe6e8f7,value));
        icon(name,x+(size-18)/2,y+(size-18)/2,18,enabled?mix(0xff6f7a92,0xff5844b4,value):0xffb9bdcd);
        if(enabled)hit(id,label,x,y,size,size,click);
    }
    private void toggle(String id,boolean checked,float x,float y,Runnable click) {
        toggle(id,checked,x,y,click,true);
    }
    private void toggle(String id,boolean checked,float x,float y,Runnable click,boolean enabled) {
        HubMotion.Transition channel=transitions.computeIfAbsent(id+"-toggle",ignored->{HubMotion.Transition c=new HubMotion.Transition();c.value=c.target=c.from=checked?1:0;c.initialized=true;c.started=now;return c;});
        float value=channel.sample(checked?1:0,now,180_000_000L,reducedMotion);
        d.roundRect(x,y,33,18,20,mix(0xffd5daea,0xff8e76d9,value));
        d.strokeRoundRect(x,y,33,18,20,1,checked?0xff8770ce:0xffc5cbdd);
        d.circle(x+9+15*value,y+9,7,0xffffffff);
        if(enabled)hit(id,checked?"关闭":"开启",x-3,y-5,39,28,click);
    }
    private void hit(String id,String label,float x,float y,float w,float h,Runnable click) {
        if(interactiveAlpha<.2f)return;
        Hit region=new Hit(id,label,x,y,w,h,logical,id.startsWith("title-")?titleClipTransform:null,click);nextHits.add(region);
        if(keyboardFocus&&Objects.equals(focusId,id))d.strokeRoundRect(x-4,y-4,w+8,h+8,12,3,a(0xff7758e8));
    }
    private boolean inside(float x,float y,float w,float h){float[] p=HubDraw.inversePoint(logical,mx,my);return p[0]>=x&&p[1]>=y&&p[0]<x+w&&p[1]<y+h;}
    private int a(int color){return withAlpha(color,opacity);}
    private static int withAlpha(int color,float alpha){return (Math.round((color>>>24)*clamp(alpha,0,1))<<24)|(color&0xffffff);}
    private static int lighten(int color,float amount){return amount>=0?mix(color,0xffffffff,amount):mix(color,0xff26344f,-amount);}
    private static int desaturate(int color,float saturation){int r=(color>>>16)&255,g=(color>>>8)&255,b=color&255;int grey=Math.round(.2126f*r+.7152f*g+.0722f*b);return mix((color&0xff000000)|(grey<<16)|(grey<<8)|grey,color,saturation);}
    private static int mix(int from,int to,float fraction) {
        float f=clamp(fraction,0,1);int result=0;
        for(int shift=0;shift<=24;shift+=8){int start=(from>>>shift)&255,end=(to>>>shift)&255;result|=Math.round(start+(end-start)*f)<<shift;}
        return result;
    }
    private static float clamp(float value,float minimum,float maximum){return Math.max(minimum,Math.min(maximum,value));}
    private static float progress(long elapsed,long duration){return clamp((float)elapsed/duration,0,1);}
    private static float ease(float p){return 1-(1-p)*(1-p)*(1-p);}
    private static String shortDate(LocalDate date){return String.format(Locale.ROOT,"%02d/%02d",date.getMonthValue(),date.getDayOfMonth());}
    private String stateLabel(){return List.of("尚未抽签","等待解签","已经解签","已收进藏签匣").get(fortuneState);}
    private void setReducedMotion(boolean value){
        if(reducedMotion==value)return;reducedMotion=value;if(value)playWelcome=false;
        navAt=System.nanoTime();tilts.clear();
        for(HubMotion.Navigation state:navigation){state.enter.snap(value?1:0);state.hover.snap(0);state.press.snap(0);state.x.snap(0);state.y.snap(0);}
    }
    private int welcomeIndex(){return Math.max(0,List.of("once","login","always").indexOf(welcomeMode));}
    private void notify(String value){toast.show(value,HubToast.Kind.INFO,System.nanoTime());}
    private void navigate(int destination){if(destination==0||destination==1)request("titles","refresh",new JsonObject());if(destination==0)request("fortunes","refresh",new JsonObject());if(destination==2)calendarRequest();page=destination;pageAt=System.nanoTime();focusId=null;titleScroll=0;if(destination==2)monthAt=pageAt;if(destination==0)tilts.remove("medallion");}
    private void openOverlay(String value){restoreFocus=focusId;overlay=value;reading=null;focusId="modal-close";sliderDragging=false;modalAt=System.nanoTime();hits.clear();}
    private void openReading(HubData.Entry value){if(value==null)return;if(live()){JsonObject p=new JsonObject();p.addProperty("date",value.date().toString());request("fortunes","read",p);return;}showReading(value);}
    private void showReading(HubData.Entry value){restoreFocus=focusId;reading=value;overlay=null;focusId="modal-close";modalAt=System.nanoTime();hits.clear();}
    public boolean dismissOverlay() {
        if(overlay==null&&reading==null)return false;
        overlay=null;reading=null;sliderDragging=false;focusId=restoreFocus;restoreFocus=null;hits.clear();return true;
    }
    private void setWelcomeFromX(float x) {
        float[] p=HubDraw.inversePoint(sliderTransform,x,my);float start=modalX+92;int index=Math.round(clamp((p[0]-start)/128,0,2));
        welcomeMode=List.of("once","login","always").get(index);
    }

    public boolean mouseClicked(float x,float y,int button) {
        if(button!=0)return false;
        if(toast.press(x,y,System.nanoTime()))return true;
        if(isWelcomePlaying())return true;
        mx=x;my=y;keyboardFocus=false;keyboardPress=false;pressMoved=false;pressX=x;pressY=y;
        for(int i=hits.size()-1;i>=0;i--) {
            Hit hit=hits.get(i);if(!hit.contains(x,y))continue;
            focusId=hit.id();pressedId=hit.id();pressedHit=hit;sliderDragging="frequency".equals(hit.id());sliderTransform=hit.transform();
            if(sliderDragging||"search".equals(hit.id()))hit.action().run();return true;
        }
        if(overlay!=null||reading!=null) {
            if(x<modalX||x>modalX+modalW||y<modalY||y>modalY+modalH)dismissOverlay();
            return true;
        }
        focusId=null;return true;
    }
    public boolean mouseReleased(float x,float y,int button){
        if(button!=0)return false;
        if(toast.release(x,y,System.nanoTime()))return true;
        boolean active=sliderDragging||pressedHit!=null;
        Hit released=pressedHit==null?null:hits.stream().filter(hit->hit.id().equals(pressedHit.id())).findFirst().orElse(null);
        if(released!=null&&!sliderDragging&&!"search".equals(released.id())&&released.contains(x,y)&&(!released.id().startsWith("title-")||!pressMoved))released.action().run();
        sliderDragging=false;pressedId=null;pressedHit=null;return active;
    }
    public boolean mouseDragged(float x,float y,int button,float dx,float dy){
        mx=x;my=y;
        if(button==0&&toast.drag(y,System.nanoTime()))return true;
        if(button==0&&pressedId!=null&&Math.hypot(x-pressX,y-pressY)>6)pressMoved=true;
        if(sliderDragging&&button==0){setWelcomeFromX(x);return true;}return pressedId!=null;
    }
    public boolean mouseScrolled(float x,float y,double amount) {
        if(isWelcomePlaying()||overlay!=null||reading!=null)return true;
        float[] p=HubDraw.inversePoint(pageTransform,x,y);
        if(page==1&&p[0]>=230&&p[0]<=908&&p[1]>=272&&p[1]<=796){titleScroll-=amount*52;return true;}
        if(page==2&&history&&p[0]>=254&&p[0]<=898&&p[1]>=252&&p[1]<=746){historyScroll-=amount*48;return true;}
        return false;
    }
    public boolean keyPressed(int keyCode,int scanCode,int modifiers) {
        if(keyCode==256)return dismissOverlay();
        if(isWelcomePlaying())return true;
        if(keyCode==297&&!live()){if(overlay==null&&reading==null)openOverlay("preview");else if("preview".equals(overlay))dismissOverlay();return true;}
        if(keyCode==258) {
            keyboardFocus=true;
            if(hits.isEmpty())return true;int index=-1;
            for(int i=0;i<hits.size();i++)if(hits.get(i).id().equals(focusId)){index=i;break;}
            boolean shift=(modifiers&1)!=0;index=Math.floorMod(index+(shift?-1:1),hits.size());focusId=hits.get(index).id();return true;
        }
        if("search".equals(focusId)&&page==1&&overlay==null&&reading==null) {
            if((keyCode==259||keyCode==261)&&searchSelectAll){query="";searchCaret=0;searchSelectAll=false;titleScroll=0;return true;}
            if(keyCode==259&&searchCaret>0){int at=query.offsetByCodePoints(searchCaret,-1);query=query.substring(0,at)+query.substring(searchCaret);searchCaret=at;titleScroll=0;return true;}
            if(keyCode==261&&searchCaret<query.length()){int end=query.offsetByCodePoints(searchCaret,1);query=query.substring(0,searchCaret)+query.substring(end);titleScroll=0;return true;}
            if(keyCode==263){searchCaret=searchSelectAll?0:searchCaret>0?query.offsetByCodePoints(searchCaret,-1):0;searchSelectAll=false;return true;}
            if(keyCode==262){searchCaret=searchSelectAll?query.length():searchCaret<query.length()?query.offsetByCodePoints(searchCaret,1):query.length();searchSelectAll=false;return true;}
            if(keyCode==268){searchCaret=0;searchSelectAll=false;return true;}if(keyCode==269){searchCaret=query.length();searchSelectAll=false;return true;}
            if(keyCode==65&&(modifiers&2)!=0){searchSelectAll=true;searchCaret=query.length();return true;}
            // Text editing consumes navigation keys even when the caret cannot move.
            if(keyCode==259||keyCode==261||keyCode==263||keyCode==262||keyCode==268||keyCode==269)return true;
        }
        if("frequency".equals(focusId)&&"settings".equals(overlay)) {
            int index=welcomeIndex();
            if(keyCode==263||keyCode==264)index--;else if(keyCode==262||keyCode==265)index++;
            else if(keyCode==268)index=0;else if(keyCode==269)index=2;else index=-1;
            if(index>=0){welcomeMode=List.of("once","login","always").get(Math.min(2,index));return true;}
            if(keyCode==263||keyCode==264)return true;
        }
        if(keyCode==257||keyCode==335||(keyCode==32&&!"search".equals(focusId))) {
            for(Hit hit:hits)if(hit.id().equals(focusId)){
                pressedId=hit.id();pressedHit=hit;keyboardPress=true;keyboardFocus=true;
                if(keyCode!=32)hit.action().run();return true;
            }
        }
        return textInputFocused()||overlay!=null||reading!=null;
    }
    public boolean keyReleased(int keyCode,int scanCode,int modifiers) {
        if(!keyboardPress||(keyCode!=257&&keyCode!=335&&keyCode!=32))return false;
        if(keyCode==32&&pressedHit!=null){Hit current=hits.stream().filter(hit->hit.id().equals(pressedHit.id())).findFirst().orElse(null);if(current!=null)current.action().run();}
        pressedId=null;pressedHit=null;keyboardPress=false;return true;
    }
    public boolean charTyped(char codePoint,int modifiers) {
        if(textInputFocused()&&!Character.isISOControl(codePoint)&&(modifiers&6)==0) {
            pasteText(Character.toString(codePoint));return true;
        }
        return overlay!=null||reading!=null;
    }
    public boolean textInputFocused() {
        return page==1&&overlay==null&&reading==null&&"search".equals(focusId)&&!isWelcomePlaying();
    }
    public String pointerCursor() {
        if(toast.pointer(mx,my))return "pointer";
        if(isWelcomePlaying())return "default";
        for(int i=hits.size()-1;i>=0;i--)if(hits.get(i).contains(mx,my))return hits.get(i).id().equals("search")?"text":"pointer";
        return "default";
    }
    /** Clipboard access belongs to the Minecraft screen; only sanitized text enters this UI. */
    public void pasteText(String value) {
        if(!textInputFocused()||value==null)return;
        int available=60-(searchSelectAll?0:query.length());StringBuilder sanitized=new StringBuilder();
        value.codePoints().filter(codePoint->!Character.isISOControl(codePoint)&&codePoint!=0x7f)
            .forEach(codePoint->{if(sanitized.length()+Character.charCount(codePoint)<=available)sanitized.appendCodePoint(codePoint);});
        if(sanitized.isEmpty())return;
        if(searchSelectAll){query="";searchCaret=0;searchSelectAll=false;}
        query=query.substring(0,searchCaret)+sanitized+query.substring(searchCaret);
        searchCaret+=sanitized.length();titleScroll=0;
    }
}
