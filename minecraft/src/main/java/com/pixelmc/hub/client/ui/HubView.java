/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.pixelmc.hub.client.render.HubDraw;
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
import java.util.function.Consumer;

/** Native, client-only rendering of the approved local handbook prototype. */
public final class HubView {
    private static final int INK=0xff26344f, MUTED=0xff73809a, PURPLE=0xff765bd4;
    private static final float LEFT=236, WIDTH=996, RIGHT=922;
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("yyyy / MM / dd");
    private final HubData data=new HubData();
    private final List<Hit> hits=new ArrayList<>();
    private final List<Hit> nextHits=new ArrayList<>();
    private HubDraw d;
    private float mx,my,opacity=1,offsetY;
    private long now=System.nanoTime(),openedAt=now,pageAt=now,monthAt=now,toastAt;
    private int page,filter,sort,fortuneState=3,searchCaret;
    private String playerName="zhan_cpu",equipped="dew",selectedTitle="dew",query="",welcomeMode="always";
    private boolean visible=true,reducedMotion,closeRequest,playWelcome,compact,history,sliderDragging,searchSelectAll;
    private float titleScroll,historyScroll;
    private YearMonth month=YearMonth.from(HubData.TODAY);
    private int monthDirection=1;
    private LocalDate selectedDate=HubData.TODAY,displayDate=HubData.TODAY;
    private String overlay,focusId,restoreFocus,toast;
    private HubData.Entry reading;
    private float modalX,modalY,modalW,modalH;

    private record Hit(String id,String label,float x,float y,float w,float h,Runnable action) {
        boolean contains(float px,float py) { return px>=x&&py>=y&&px<x+w&&py<y+h; }
    }
    public HubView() {}
    public void setPlayerName(String name) { if(name!=null&&!name.isBlank()) playerName=name; }
    public void setSettings(String mode,boolean reduced) {
        welcomeMode=List.of("once","login","always").contains(mode)?mode:"always";
        reducedMotion=reduced;
    }
    public String welcomeMode() { return welcomeMode; }
    public boolean reducedMotion() { return reducedMotion; }
    public boolean consumeCloseRequest() { boolean value=closeRequest;closeRequest=false;return value; }
    public boolean isWelcomePlaying() { return playWelcome&&!reducedMotion&&System.nanoTime()-openedAt<3_060_000_000L; }
    public void open(boolean welcome) {
        page=0;overlay=null;reading=null;focusId=null;closeRequest=false;
        openedAt=System.nanoTime();playWelcome=welcome&&!reducedMotion;
        pageAt=openedAt+(playWelcome?3_060_000_000L:0);hits.clear();
    }

    public void render(HubDraw draw,float mouseX,float mouseY,long nowNanos) {
        d=draw;mx=mouseX;my=mouseY;now=nowNanos;opacity=1;offsetY=0;nextHits.clear();
        d.save();d.clipRoundRect(0,0,1260,850,28);
        // The color belongs to the surface, never to its text or controls.
        d.gradientRoundRect(0,0,1260,850,28,0xc9edf6ff,0xc6eee8ff);
        d.rect(208,0,1052,850,0xaefbfcff);
        sidebar();topbar();
        boolean welcomeActive=playWelcome&&!reducedMotion&&now-openedAt<3_060_000_000L;
        if(!welcomeActive) {
            if(page==0)home();else if(page==1)titles();else fortunes();
        }
        if(welcomeActive) { nextHits.clear();welcome(); }
        d.restore();
        d.strokeRoundRect(.5f,.5f,1259,849,28,1,0xecffffff);
        if(!welcomeActive&&(overlay!=null||reading!=null))modal();
        if(toast!=null)toast();
        hits.clear();hits.addAll(nextHits);
        d=null;
    }

    private void sidebar() {
        d.gradientRoundRect(0,0,208,850,0,0x62ffffff,0x78dce5ff);
        d.rect(5,5,1,840,0x88ffffff);d.rect(207,0,1,850,0x88b3bfdf);
        image("server-logo",22,27,44,44,1);
        text("旅人手册",77,46,18,INK,true);text("PixelMC-Hub",77,65,10,0xff687491,false);
        hit("brand","旅人手册首页",18,20,174,61,()->navigate(0));
        text("我的空间",25,118,11,0xff677591,false);
        String[] labels={"个人主页","我的称号","我的签笺"};
        String[] icons={"home","tag","book-open"};
        int[] colors={0xff1a4be6,0xff7334d5,0xfff86a1b};
        int[] bright={0xff56a1fb,0xffbf72f3,0xffffb93d};
        for(int i=0;i<3;i++) {
            float y=145+i*74;final int destination=i;
            boolean hover=inside(21,y-8,172,64),current=page==i;
            if(current||hover)glass(20,y-8,173,64,18,current?0xbdffffff:0x64ffffff,0x52ffffff);
            float lift=!reducedMotion&&hover?-3:0;
            d.save();d.translate(81,y+lift+46);d.canvas().rotate(-16);
            d.gradientRoundRect(-46,-46,46,46,14,bright[i],colors[i]);
            d.strokeRoundRect(-46,-46,46,46,14,1,0xa4ffffff);d.restore();
            shadow(35,y+4+lift,46,46,14,0x17565790);
            d.gradientRoundRect(35,y+lift,46,46,14,0x4dffffff,0x29ffffff);
            d.strokeRoundRect(35,y+lift,46,46,14,1,0xe6ffffff);
            icon(icons[i],48,y+13+lift,20,0xffffffff);
            text(labels[i],99,y+28,14,current?0xff3a3590:0xff53627e,current);
            hit("nav-"+i,labels[i],20,y-8,173,64,()->navigate(destination));
        }
        glass(24,718,36,40,12,0xc9e7fff2,0x7ab4e9d7);icon("flower-2",32,728,21,0xff458c80);
        text("香草纪元",70,731,12,0xff52627b,true);
        d.circle(72,748,2,0xff6a9c8b);text("当前存档 · 演示",80,752,10,0xff708098,false);
        d.line(24,777,184,777,1,0x55b7c2dc);
        if(inside(20,791,172,40))d.roundRect(20,791,172,40,10,0x88ffffff);
        icon("settings",34,801,19,0xff65738c);text("界面设置",65,816,13,0xff65738c,false);
        hit("settings","界面设置",20,791,172,40,()->openOverlay("settings"));
    }
    private void topbar() {
        d.rect(208,0,1052,54,0x36ffffff);d.line(208,54,1260,54,1,0xb8ffffff);
        text("我的空间",236,33,12,0xff8a94aa,false);icon("chevron-right",301,22,13,0xffa1aac0);
        text(pageName(),326,33,12,0xff63718c,false);
        d.circle(1095,28,2.5f,0xff529c8c);text("本地原型",1105,32,11,0xff64748b,false);
        d.line(1183,21,1183,35,1,0x7ac7cfe0);
        iconButton("close","收起手册","x",1200,12,32,()->closeRequest=true,true);
    }
    private void heading(String title,String subtitle,String module,int count) {
        group(0,340);
        text(title,LEFT,108,25,INK,true);
        if(count>=0){float x=LEFT+d.textWidth(title,25,true)+12;pill(Integer.toString(count),x,87,0xffe9e1ff,0xff7361c3,false);}
        text(subtitle,LEFT,137,13,0xff6c7891,false);
        if(module!=null)textRight(module,1232,105,10,0xff7c72a6,false);
        ungroup();
    }
    private String pageName(){return page==0?"个人主页":page==1?"我的称号":"我的签笺";}

    private void home() {
        heading("个人主页","很高兴又见到你，今天是旅程的第 18 天。",null,-1);
        icon("sun",1062,88,18,0xffbf8a37);text("11 月 3 日，星期二",1089,103,12,0xff6e7990,false);
        group(50,440);
        shadow(LEFT,174,WIDTH,276,22,0x188980c5);
        d.gradientRoundRect(LEFT,174,WIDTH,276,22,0xffd9f0ff,0xffdbc9f9);
        d.save();d.clipRoundRect(LEFT,174,WIDTH,276,22);
        for(int i=0;i<20;i++)d.circle(1041,256,205-i*9,a(0x02aaa3f7));
        d.rect(LEFT,366,WIDTH,84,a(0x9effffff));
        d.line(LEFT,366,1232,366,1,a(0xc7ffffff));d.restore();
        d.strokeRoundRect(LEFT,174,WIDTH,276,22,1,a(0xf2ffffff));
        text("你好",263,211,12,0xff586987,false);
        text(fit(playerName,390,35,true),263,255,35,0xff253657,true);
        float nameWidth=Math.min(390,d.textWidth(playerName,35,true));
        pill("本地角色",277+nameWidth,232,0xbceffff2,0xff25816a,true);
        HubData.Title worn=HubData.title(equipped);
        text(worn==null?"暂未佩戴称号":worn.name(),263,287,15,worn==null?MUTED:HubData.titleText(worn),true);
        if(!visible&&worn!=null){icon("eye-off",371,276,13,MUTED);text("展示已隐藏",390,287,10,MUTED,false);}
        button("profile","个人资料  ↗",263,315,106,31,false,true,()->openOverlay("profile"));
        d.save();d.translate(1080,268);d.canvas().rotate(-29);
        d.strokeRoundRect(-120,-57,240,114,57,2,a(0x70ffffff));d.restore();
        float tilt=!reducedMotion&&inside(987,187,190,160)?(mx-1082)/32:-5;
        d.save();d.translate(1082,267);d.canvas().rotate(tilt);
        glass(-95,-80,190,160,26,0x66ffffff,0x24e7e5ff);
        if(opacity>.04f)HubHead.draw(d,-27,-53,54,-23);
        pill("✧ 旅程第 18 天",-62,42,0x6bffffff,0xff5d5491,false);
        d.restore();text("✦",1190,222,27,0xffffffff,false);text("✧",967,321,21,0xffffffff,false);
        stat(263,391,"clock","累计在线 · 演示","128", "小时  42 分钟",0xff6b7dd6,0xffe8eeff);
        d.line(566,388,566,430,1,a(0x4ab7c1e0));
        stat(590,391,"log-in","登录次数 · 演示","36","次",0xffaa75c6,0xfff1e9ff);
        d.line(887,388,887,430,1,a(0x4ab7c1e0));
        glass(910,392,33,33,11,0xfff8f0df,0xfff8f0df);icon("calendar-days",918,400,17,0xffb29047);
        text("上次登录 · 演示",955,400,11,0xff72809c,false);
        text("2026/11/02",955,424,16,0xff344566,true);text("20:26:14",1060,424,12,0xff71809b,false);
        ungroup();
        group(110,440);
        homeToday();homeKeepsakes();ungroup();
        group(170,340);
        icon("leaf",LEFT,811,13,0xff8a93a8);text("每一份收藏，都记得来时的路。",256,823,11,0xff8a93a8,false);
        textRight("样例统计与收藏 · F8 预览选项",1232,823,10,0xff8a93a8,false);ungroup();
    }
    private void stat(float x,float y,String name,String label,String main,String unit,int color,int background) {
        d.roundRect(x,y,33,33,11,a(background));icon(name,x+8,y+8,17,color);
        text(label,x+45,y+9,11,0xff72809c,false);text(main,x+45,y+34,21,0xff344566,true);
        text(unit,x+49+d.textWidth(main,21,true),y+33,10,0xff78869d,false);
    }
    private void homeToday() {
        glass(LEFT,470,515,310,22,0xfffffefa,0xfffff5e4);
        icon("leaf",259,493,19,0xffbc9345);text("今日签笺",287,510,16,INK,true);
        textRight("每日一签",728,508,10,MUTED,false);
        HubData.Fortune f=data.fortune(1);
        if(fortuneState>=2) {
            text(f.grade()+"  ·  第"+f.id()+"签",259,551,11,HubData.gradeColor(f.grade()),false);
            serif(f.title(),259,597,29,0xff4e4f42,true);
            serif(f.poem().get(0),259,630,13,0xff828069,false);serif(f.poem().get(1),259,655,13,0xff828069,false);
        } else {
            text("今日机缘",259,551,11,0xffae8132,false);
            text(fortuneState==0?"有一签，等你开启":"签已入手，静候解读",259,593,24,0xff4e4f42,true);
            wrap(fortuneState==0?"拿起签筒，为今天留下一份期许。":"带上未解的签，前往解签龛。",259,629,260,13,23,0xff928675,false);
        }
        d.circle(662,606,61,a(0x30ebcf87));image("fortune-barrel",598,541,126,140,1);
        text("✦",716,556,12,0xffd5ae61,false);text("✧",586,675,16,0xffd5ae61,false);
        d.line(259,706,728,706,1,a(0x33cabc9b));
        icon(fortuneState==3?"check-check":"info",259,731,15,0xff7f8e81);
        text(stateLabel(),281,743,10,0xff7f8e81,false);
        goldButton("today-read",fortuneState>=2?"查看签文  →":fortuneState==0?"如何抽签  →":"如何解签  →",608,721,120,37,
            ()->{if(fortuneState>=2)openReading(todayEntry());else openOverlay("fortune-help");});
    }
    private void homeKeepsakes() {
        glass(771,470,461,310,22,0xecffffff,0xabffffff);
        icon("bookmark",793,493,18,0xff8c74c4);text("最近收藏",820,510,16,INK,true);
        textRight("旅途的印记",1210,508,10,MUTED,false);
        HubData.Entry latest=archive().values().stream().reduce((a,b)->b).orElse(null);
        if(latest!=null)keepsake(537,latest.fortune().title(),"收藏签笺 · "+shortDate(latest.date())+" "+latest.time(),"fortune-slip",0xfff8edda,HubData.gradeColor(latest.fortune().grade()),latest.fortune().grade(),()->openReading(latest));
        keepsake(601,"朝露行者","获得新称号 · 11/02 18:42","leaf",0xffd8f3e6,0xff53a484,null,()->navigate(1));
        keepsake(665,"拾星之人","获得新称号 · 10/30 21:16","star",0xffe3dbff,0xff9875d5,null,()->{selectedTitle="star";navigate(1);});
        d.line(793,731,1210,731,1,a(0xffe4e5f1));
        text("6",793,760,17,0xff5c5790,true);text("枚称号",810,760,10,0xff80899f,false);
        d.line(866,746,866,762,1,a(0xffd9dcec));
        text(Integer.toString(archive().size()),884,760,17,0xff5c5790,true);text("张签笺",911,760,10,0xff80899f,false);
        iconButton("archive-link","打开签笺月历","arrow-up-right",1177,740,30,()->navigate(2),true);
    }
    private void keepsake(float y,String title,String subtitle,String art,int bg,int color,String grade,Runnable click) {
        if(inside(793,y-1,417,60))d.roundRect(787,y-1,429,60,10,a(0x80f5f2ff));
        glass(793,y+5,36,39,12,bg,lighten(bg,.1f));
        if(art.equals("fortune-slip"))image(art,797,y+7,28,34,1);else icon(art,800,y+13,22,color);
        text(title,841,y+19,13,0xff4c5570,false);
        if(grade!=null){float x=848+d.textWidth(title,13,false);d.strokeRoundRect(x,y+6,44,17,4,.7f,a(0xffeadfc7));text(grade,x+5,y+18,9,color,false);}
        text(subtitle,841,y+40,10,0xff8490a5,false);icon("chevron-right",1195,y+19,16,0xffa5a9c0);
        if(y<665)d.line(793,y+59,1210,y+59,1,a(0x57e5e7f3));
        hit("keepsake-"+y,title,793,y-1,417,60,click);
    }

    private List<HubData.Title> titleList() {
        List<HubData.Title> list=new ArrayList<>(HubData.TITLES.stream().filter(t->
            (filter==1||(filter==0?t.owned():!t.owned()))&&(t.name()+t.category()).contains(query.strip())).toList());
        if(sort==0)list.sort(Comparator.comparing(HubData.Title::date).reversed());
        else {Collator collator=Collator.getInstance(Locale.CHINA);list.sort((a,b)->collator.compare(a.name(),b.name()));}
        return list;
    }
    private void titles() {
        heading("我的称号","选一个喜欢的称号，让名字多一点自己的颜色。","MemorialTitles · 演示",6);
        group(30,350);
        glass(LEFT,174,234,36,13,0x5cdce0f0,0x5cdce0f0);
        String[] labels={"已拥有","全部","未获得"};
        for(int i=0;i<3;i++) {
            final int value=i;float x=LEFT+3+i*76;
            if(filter==i)glass(x,177,74,30,10,0xffffffff,0xffffffff);
            textCenter(labels[i],x+37,197,11,filter==i?0xff6650a7:0xff78839b,filter==i);
            hit("filter-"+i,labels[i],x,177,74,30,()->{filter=value;titleScroll=0;});
        }
        iconButton("density",compact?"切换卡片显示":"切换紧凑显示",compact?"grid-2x2":"list",864,176,32,()->{compact=!compact;titleScroll=0;},true);
        glass(LEFT,223,518,36,11,0xb3ffffff,0xb3ffffff);
        icon("search",248,233,16,0xff929bb1);
        if(searchSelectAll&&!query.isEmpty()&&"search".equals(focusId))d.roundRect(273,230,Math.min(438,d.textWidth(query,12,false))+2,21,3,a(0xffd4c9ff));
        text(query.isEmpty()?"搜索称号":fit(query,439,12,false),274,247,12,query.isEmpty()?0xff949caf:INK,false);
        if("search".equals(focusId)) {
            d.strokeRoundRect(LEFT,223,518,36,11,1.5f,a(0xffa391dc));
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
        if(list.isEmpty()) {
            icon("search",548,374,28,0xff9d90b4);textCenter("没有找到这个称号",567,437,17,0xff8c8495,true);
            textCenter("换个关键词，或查看全部称号。",567,466,12,MUTED,false);
            button("reset-filter","查看全部",512,492,110,37,false,true,()->{filter=1;query="";titleScroll=0;});
        }
        text(list.size()+" 枚称号 · "+(filter==2?"还有新的旅程等你开启":"每一个名字，都有它的故事"),LEFT,816,11,0xff8993a9,false);
        titleDetail();ungroup();
    }
    private void titleTile(HubData.Title t,float x,float y,float w,float h) {
        boolean selected=selectedTitle.equals(t.id()),hover=inside(x,y,w,h);
        d.save();
        if(hover&&!reducedMotion) {
            d.translate(x+w/2,y+h/2);d.canvas().rotate((mx-(x+w/2))/w*2.1f);
            d.canvas().scale(1.012f,1.012f);d.translate(-x-w/2,-y-h/2);
        }
        shadow(x,y,w,h,18,selected?0x1f607097:0x0b607097);
        d.gradientRoundRect(x,y,w,h,18,a(0xf9ffffff),a(lighten(t.light(),.15f)));
        d.strokeRoundRect(x,y,w,h,18,selected?1.5f:1,a(selected?mix(t.color(),0xff6452ad,.2f):0xfaffffff));
        if(hover)d.gradientRoundRect(x+1,y+1,w-2,h-2,17,a(0x32ffffff),a(0x04ffffff));
        if(compact) {
            emblem(t,x+16,y+13,42);
            text(t.name(),x+75,y+30,17,t.owned()?HubData.titleText(t):0xff78859b,true);
            text(t.category(),x+75,y+50,10,0xff7c859a,false);
            if(Objects.equals(equipped,t.id()))pill("✓ 已佩戴",x+w-84,y+22,0xa3ffffff,0xff448772,false);
            else if(!t.owned())icon("lock",x+w-36,y+26,14,0xff8d97ad);
        } else {
            text(t.category(),x+20,y+32,10,0xff7c859a,false);
            text(t.name(),x+20,y+66,20,t.owned()?HubData.titleText(t):0xff78859b,true);
            d.circle(x+23,y+88,2,a(t.color()));text(t.rarity(),x+32,y+92,10,mix(t.color(),INK,.5f),false);
            emblem(t,x+w-81,y+47,57);
            if(Objects.equals(equipped,t.id()))pill("✓ 已佩戴",x+w-86,y+12,0xa3ffffff,0xff448772,false);
            else if(!t.owned())icon("lock",x+w-31,y+17,14,0xff8d97ad);
            d.line(x+20,y+124,x+w-20,y+124,1,a(mix(t.color(),0x00ffffff,.75f)));
            icon("calendar-days",x+20,y+136,12,0xff7b859c);
            text(t.owned()?t.date():"尚未解锁",x+38,y+146,10,0xff7b859c,false);
            if(selected){icon("check",x+w-77,y+135,12,mix(t.color(),INK,.4f));text("已选中",x+w-60,y+146,10,mix(t.color(),INK,.4f),false);}
            else icon("arrow-up-right",x+w-34,y+135,14,mix(t.color(),INK,.4f));
        }
        d.restore();
        // Clamp hit areas to the scroll viewport so clipped cards cannot steal input.
        float top=Math.max(y,274),bottom=Math.min(y+h,790);
        if(bottom>top)hit("title-"+t.id(),t.name()+(t.owned()?"":"，未获得"),x,top,w,bottom-top,()->selectedTitle=t.id());
    }
    private void emblem(HubData.Title t,float x,float y,float size) {
        float radius=size*.28f;
        d.save();d.translate(x+size/2+4,y+size/2-4);d.canvas().rotate(8);
        shadow(-size/2,-size/2,size,size,radius,0x195b5278);
        d.gradientRoundRect(-size/2,-size/2,size,size,radius,a(t.color()),a(mix(t.color(),0xff463169,.17f)));d.restore();
        d.save();d.translate(x+size/2,y+size/2);d.canvas().rotate(-8);
        d.gradientRoundRect(-size/2,-size/2,size,size,radius,a(0xbfffffff),a(0x5fffffff));
        d.strokeRoundRect(-size/2,-size/2,size,size,radius,1,a(0xe8ffffff));
        icon(t.icon(),-size*.27f,-size*.27f,size*.54f,mix(t.color(),0xff25364c,.32f));d.restore();
    }
    private void titleDetail() {
        HubData.Title t=HubData.title(selectedTitle);if(t==null)t=HubData.TITLES.get(0);
        final HubData.Title selected=t;
        glass(RIGHT,174,310,626,22,0xecffffff,0xabffffff);
        text("佩戴预览",940,207,13,0xff4f5a73,true);
        boolean worn=Objects.equals(equipped,t.id());
        pill(worn?"✓ 已佩戴":"◉ 仅预览",1144,188,worn?0xffeff9f4:0xfff6f1ff,worn?0xff4c8573:0xff85709d,false);
        d.gradientRoundRect(940,225,274,226,18,a(t.light()),a(0xffe8e3ff));
        d.strokeRoundRect(940,225,274,226,18,1,a(0xffffffff));d.strokeRoundRect(948,233,258,210,11,1,a(0x8cffffff));
        d.save();d.clipRoundRect(940,225,274,226,18);d.translate(1077,338);d.canvas().rotate(-30);
        d.strokeRoundRect(-134,-49,268,98,49,1,a(0xb8ffffff));d.restore();
        icon(t.icon(),931,292,106,mix(t.color(),t.light(),.65f));
        textCenter(t.name(),1077,267,21,HubData.titleText(t),true);
        textCenter(fit(playerName,230,13,true),1077,290,13,0xff62718f,true);
        d.roundRect(1011,389,132,28,14,a(0x9cf6f8ff));d.strokeRoundRect(1011,389,132,28,14,1,a(0xc8ffffff));
        if(opacity>.04f)HubHead.draw(d,1051,321,51,-23);
        textCenter("游戏内效果示意 · 本地样例",1077,435,10,0xff7d87a1,false);
        icon(t.icon(),940,473,18,mix(t.color(),INK,.55f));text(t.name(),966,488,14,mix(t.color(),INK,.55f),true);
        wrap(t.description(),940,516,274,12,22,0xff748098,false);
        glass(940,568,274,73,10,0xfff7f7fc,0xfff7f7fc);
        icon("gift",952,580,13,0xff8990a5);text(t.owned()?"获得方式":"解锁条件",973,591,10,0xff8990a5,false);
        wrap(t.condition(),952,615,250,11,18,0xff6d7890,false);
        if(t.owned()) {icon("calendar-days",942,655,13,MUTED);text(t.date()+"  获得",962,667,10,MUTED,false);}
        String equipText=!t.owned()?"尚未获得":worn?"✓ 已佩戴 · 点击摘下":"佩戴这个称号  →";
        button("equip",equipText,940,687,274,38,!worn,t.owned(),()->{
            equipped=Objects.equals(equipped,selected.id())?null:selected.id();
            notify(equipped==null?"已摘下称号 · 本地演示":"已佩戴「"+selected.name()+"」· 本地演示");
        });
        text("展示给其他玩家",940,754,11,0xff65718b,false);
        text(visible?"本地展示示意":"已隐藏展示，佩戴状态保留",940,774,10,0xff8a94aa,false);
        toggle("visibility",visible,1174,746,()->{visible=!visible;notify(visible?"已开启称号展示 · 本地演示":"展示已隐藏，佩戴状态保留");});
    }

    private Map<LocalDate,HubData.Entry> archive() {
        if(fortuneState==3)return data.archive;
        Map<LocalDate,HubData.Entry> copy=new java.util.LinkedHashMap<>(data.archive);copy.remove(HubData.TODAY);return copy;
    }
    private HubData.Entry todayEntry(){return new HubData.Entry(HubData.TODAY,"20:26",data.fortune(1));}
    private void fortunes() {
        Map<LocalDate,HubData.Entry> records=archive();
        heading("我的签笺","一日一签，把沿途的机缘慢慢收藏。","DailyFortunes · 演示",records.size());
        group(35,360);
        glass(LEFT,174,662,626,22,0xecffffff,0xabffffff);
        int stored=(int)records.keySet().stream().filter(date->YearMonth.from(date).equals(month)).count();
        float monthProgress=progress(now-monthAt,260_000_000L);
        float monthOffset=reducedMotion?0:monthDirection*10*(1-ease(monthProgress));
        float pageOpacity=opacity;
        if(!reducedMotion)opacity*=ease(monthProgress);
        text(String.format(Locale.ROOT,"%02d",month.getMonthValue()),258+monthOffset,231,39,0xff67549b,true);
        text(month.getYear()+" 年 "+month.getMonthValue()+" 月",320+monthOffset,211,13,0xff505b76,true);
        icon("bookmark",321+monthOffset,223,12,0xff8a8fa5);text("本月收藏 "+stored+" 张",339+monthOffset,234,10,0xff8a8fa5,false);
        opacity=pageOpacity;
        glass(704,195,171,37,11,0xfff0edf8,0xfff0edf8);
        textButton("current-month","本月",707,198,45,30,()->{monthDirection=1;month=YearMonth.from(HubData.TODAY);selectedDate=HubData.TODAY;monthAt=System.nanoTime();historyScroll=0;},true);
        iconButton("previous-month","上个月","chevron-left",752,198,29,()->shiftMonth(-1),true);
        iconButton("next-month","下个月","chevron-right",782,198,29,()->shiftMonth(1),month.isBefore(YearMonth.from(HubData.TODAY)));
        d.line(816,205,816,225,1,a(0xffdbd7e7));
        iconButton("fortune-mode",history?"切换月历显示":"切换签笺列表",history?"grid-2x2":"list",829,198,30,()->{history=!history;historyScroll=0;monthAt=System.nanoTime();},true);
        d.save();d.clipRoundRect(255,252,624,498,0);
        float slide=reducedMotion?0:monthDirection*28*(1-ease(monthProgress));d.translate(slide,0);
        if(!reducedMotion)opacity*=ease(monthProgress);
        if(history)fortuneHistory(records,slide);else calendar(records,slide);
        d.restore();opacity=pageOpacity;
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
            boolean future=date.isAfter(HubData.TODAY),selected=date.equals(selectedDate),today=date.equals(HubData.TODAY);
            int grade=entry==null?0xffa0a7bb:HubData.gradeColor(entry.fortune().grade());
            if(entry!=null)d.gradientRoundRect(x,y,cellW,cellH,11,a(0xffffffff),a(lighten(grade,.43f)));
            else d.roundRect(x,y,cellW,cellH,11,a(future?0xfff5f5fa:0xfff4f4fa));
            d.strokeRoundRect(x,y,cellW,cellH,11,selected?1.7f:1,a(selected?0xff8b6bb9:entry!=null?0xffe1d9d0:0xffe6e7f1));
            if(selected)d.strokeRoundRect(x-2,y-2,cellW+4,cellH+4,13,1.7f,a(0x279a7bc3));
            if(!future&&inside(x+slide,y,cellW,cellH))d.strokeRoundRect(x,y,cellW,cellH,11,1,a(0xffb396d8));
            text(Integer.toString(day),x+10,y+20,12,future?0xffacb2c2:0xff65728b,false);
            if(today)textRight("今天",x+cellW-7,y+18,9,0xff8f70ad,false);
            float slipH=Math.min(38,cellH-26),slipW=slipH*.83f;
            if(!future)image("fortune-slip",x+(cellW-slipW)/2,y+cellH/2-10,slipW,slipH,entry!=null?1:.14f);
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
        if(selectedDate.equals(HubData.TODAY))pill("今天",1160,187,0xbceffff2,0xff25816a,true);
        if(entry==null) {
            image("fortune-slip",1039,314,76,97,.3f);textCenter("这一天，没有留下签笺",1077,456,15,0xff8c8495,true);
            wrap("打开藏签匣时，背包中属于你的已解签会按对应日期归档。",957,491,240,12,22,0xffa39bab,false);
            if(selectedDate.equals(HubData.TODAY)&&fortuneState<3)textCenter(stateLabel()+" · 尚未归档",1077,562,11,0xffa39bab,false);
        } else {
            HubData.Fortune f=entry.fortune();
            paper(937,219,280,331);
            glass(1053,238,48,49,13,0x94f2deac,0xfffff7e6);image("fortune-slip",1062,244,29,37,1);
            textCenter(f.grade()+" · 第"+f.id()+"签",1077,310,11,HubData.gradeColor(f.grade()),false);
            serifCenter(f.title(),1077,348,25,0xff615b4f,true);
            d.line(961,365,1193,365,1,a(0x36c9b78d));
            for(int i=0;i<f.poem().size();i++)serifCenter(f.poem().get(i),1077,391+i*29,13,0xff817967,false);
            d.line(961,494,1193,494,1,a(0x36c9b78d));
            text(fit(playerName,190,9,false)+" 所得",960,526,9,0xffae9565,false);textRight(entry.time(),1195,526,9,0xffae9565,false);
            icon("check-check",940,582,14,0xff7f8f87);text("已收藏",962,594,10,0xff7f8f87,false);
            button("read-selected","阅读解签  →",1093,572,123,35,true,true,()->openReading(entry));
        }
        glass(RIGHT,640,310,160,19,0x99e6eaff,0xd1f6edff);
        glass(938,656,53,56,14,0xd4ffffff,0x45ffffff);image("collection-chest",936,653,57,61,1);
        HubData.Entry display=records.get(displayDate);
        text("手册展示",1004,668,10,0xff7b869b,false);
        text(display==null?"还没有选择签笺":display.fortune().title(),1004,691,13,0xff625c80,true);
        text(display==null?"选择一张已收藏的签":shortDate(display.date())+" · 已收藏",1004,709,10,0xff9290aa,false);
        boolean current=Objects.equals(displayDate,selectedDate)&&entry!=null;
        button("display-selected",current?"当前选择已展示  ✓":"展示选中的签  ↗",938,725,278,33,false,entry!=null&&!current,()->displaySelected(entry));
        textCenter("仅用于手册，方块展示在游戏内设置",1077,783,9,0xff948ba6,false);
    }
    private void paper(float x,float y,float w,float h) {
        d.gradientRoundRect(x,y,w,h,14,a(0xfffff9ec),a(0xfffffef9));
        d.strokeRoundRect(x,y,w,h,14,1,a(0xffe9dcbf));
        d.strokeRoundRect(x+6,y+6,w-12,h-12,9,1,a(0x2bcdb57e));
    }
    private void shiftMonth(int amount) {
        YearMonth target=month.plusMonths(amount);if(target.isAfter(YearMonth.from(HubData.TODAY)))return;
        month=target;monthDirection=Integer.signum(amount);monthAt=System.nanoTime();historyScroll=0;
        selectedDate=archive().keySet().stream().filter(date->YearMonth.from(date).equals(month)).max(Comparator.naturalOrder()).orElse(month.atDay(1));
    }
    private void displaySelected(HubData.Entry entry) {
        if(entry==null||!archive().containsKey(entry.date())||Objects.equals(displayDate,entry.date()))return;
        displayDate=entry.date();notify("已更新手册展示的签笺 · 本地演示");
    }

    private void modal() {
        opacity=1;offsetY=0;nextHits.clear();
        d.roundRect(0,0,1260,850,28,0x6634405c);
        boolean wide=reading!=null;
        modalW=wide?880:560;modalH=wide?586:"settings".equals(overlay)?520:"profile".equals(overlay)?590:"preview".equals(overlay)?430:355;
        modalX=(1260-modalW)/2;modalY=(850-modalH)/2;
        shadow(modalX,modalY,modalW,modalH,25,0x3435416c);
        d.gradientRoundRect(modalX,modalY,modalW,modalH,25,0xfffcfdff,0xfff5f3ff);
        d.strokeRoundRect(modalX,modalY,modalW,modalH,25,1,0xffffffff);
        String title=wide?"签笺详情":switch(overlay){case "settings"->"界面设置";case "profile"->"个人资料";case "preview"->"预览选项";default->fortuneState==0?"如何抽签":"如何解签";};
        String subtitle=wide?reading.date().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))+" · "+playerName+" 所得":switch(overlay){case "settings"->"让手册更符合你的习惯";case "profile"->"你在这段旅程中留下的足迹";case "preview"->"仅用于体验不同状态，不会修改游戏数据";default->"DailyFortunes · 玩法提示";};
        text(title,modalX+27,modalY+39,19,0xff35415e,true);
        text(subtitle,modalX+27,modalY+63,12,0xff8c8fa5,false);
        iconButton("modal-close","关闭弹窗","x",modalX+modalW-58,modalY+22,32,this::dismissOverlay,true);
        d.line(modalX+26,modalY+84,modalX+modalW-26,modalY+84,1,0xffe7e6f1);
        if(wide)readingModal();else switch(overlay) {
            case "settings"->settingsModal();case "profile"->profileModal();case "preview"->previewModal();default->fortuneHelp();
        }
    }
    private void settingsModal() {
        float x=modalX+28,y=modalY+118;
        text("开屏动画",x,y,14,INK,true);textRight("从低到高",modalX+modalW-28,y,10,0xff9694ac,false);
        glass(x,y+22,504,94,14,0x96eaf0ff,0x8cf1eafe);
        int selected=welcomeIndex();float start=x+83,end=x+421,trackY=y+48;
        d.roundRect(start,trackY,338,6,4,0xffd9dced);
        if(selected>0)d.gradientRoundRect(start,trackY,selected*169,6,4,0xff819de0,0xff9575da);
        for(int i=0;i<3;i++){d.circle(start+i*169,trackY+3,3,0xffffffff);d.strokeRoundRect(start+i*169-3,trackY,6,6,3,1,0xffbdc4dd);}
        d.circle(start+selected*169,trackY+5,12,0x226b61ae);d.circle(start+selected*169,trackY+3,11,0xffffffff);
        d.circle(start+selected*169,trackY+3,8,selected==0?0xff96b4f1:selected==1?0xff97a0e8:0xff9172d8);
        hit("frequency","开屏动画频率",start-15,trackY-12,368,30,()->setWelcomeFromX(mx));
        String[] labels={"仅初次","每次登录","每次打开"};
        for(int i=0;i<3;i++) {
            final int index=i;float bx=x+5+i*165;
            if(i==selected)d.roundRect(bx,y+74,164,28,8,0x8fffffff);
            textCenter(labels[i],bx+82,y+93,11,i==selected?0xff7056af:0xff81869e,i==selected);
            hit("frequency-label-"+i,labels[i],bx,y+74,164,28,()->welcomeMode=List.of("once","login","always").get(index));
        }
        String explanation=reducedMotion?"减少动态效果已开启，开屏动画会跳过。":switch(welcomeMode){case "once"->"仅首次打开时播放；已经看过的记录会保留。";case "login"->"每次进入游戏后，首次打开手册时播放。";default->"每次打开手册时播放约 3 秒的手写问候。";};
        text(explanation,x,y+143,12,0xff8c8fa5,false);
        d.line(x,y+169,x+504,y+169,1,0xffe7e6f1);
        text("减少动态效果",x,y+203,14,INK,true);text("关闭开屏动画、卡片倾斜与页面过渡",x,y+229,12,0xff8c8fa5,false);
        toggle("reduce-motion",reducedMotion,x+457,y+190,()->{reducedMotion=!reducedMotion;if(reducedMotion)playWelcome=false;});
        d.line(x,y+252,x+504,y+252,1,0xffe7e6f1);
        text("界面主题",x,y+284,14,INK,true);text("Liquid Glass · 浅色",x,y+309,12,0xff8c8fa5,false);
        glass(x+465,y+270,39,38,11,0xffe0e9ff,0xfff3e0ff);icon("check",x+477,y+282,14,0xff817393);
        icon("info",x,y+345,15,0xff8c8fa5);text("偏好保存在当前测试实例；统计与收藏为本地演示。",x+25,y+357,11,0xff8c8fa5,false);
    }
    private void profileModal() {
        float cx=630,top=modalY+115;
        glass(cx-46,top,92,92,24,0xffe5ecff,0xffeae1ff);HubHead.draw(d,cx-27,top+21,54,-23);
        textCenter(fit(playerName,480,24,true),cx,top+130,24,INK,true);
        HubData.Title worn=HubData.title(equipped);textCenter(worn==null?"暂未佩戴称号":worn.name(),cx,top+160,14,worn==null?MUTED:HubData.titleText(worn),true);
        String[][] rows={{"首次加入 · 演示","2026/10/17"},{"累计在线 · 演示","128 小时 42 分钟"},{"累计登录 · 演示","36 次"},{"玩家 UUID · 演示身份","7f1a0000-0000-4000-8000-000000000001"}};
        for(int i=0;i<rows.length;i++) {
            float y=top+207+i*48;d.line(modalX+28,y-21,modalX+modalW-28,y-21,1,0xffe7e6f1);
            text(rows[i][0],modalX+28,y,11,0xff8c8fa5,false);textRight(rows[i][1],modalX+modalW-28,y,i==3?10:12,0xff59657f,false);
        }
        textCenter("名字与头像取自本地角色，其他资料均为样例。",cx,modalY+modalH-28,11,MUTED,false);
    }
    private void previewModal() {
        float x=modalX+28,y=modalY+120;
        text("今日抽签状态",x,y,13,INK,true);
        String[] labels={"尚未抽签","等待解签","已经解签","已收进藏签匣"};
        for(int i=0;i<4;i++) {
            final int state=i;float bx=x+(i%2)*257,by=y+22+(i/2)*55;
            button("preview-state-"+i,(fortuneState==i?"✓ ":"")+labels[i],bx,by,247,42,fortuneState==i,true,()->{fortuneState=state;notify("今日状态："+stateLabel()+" · 本地演示");});
        }
        wrap("未解签不会显示结果；已解但未归档的签不会出现在收藏和月历中。",x,y+164,504,12,22,0xff8c8fa5,false);
        button("reset-demo","还原演示数据",x,y+236,148,37,false,true,()->{equipped="dew";visible=true;fortuneState=3;displayDate=HubData.TODAY;selectedDate=HubData.TODAY;notify("样例已还原，界面偏好保留");});
        text("F8 打开此面板 · 界面偏好不参与演示还原",x,y+295,11,MUTED,false);
    }
    private void fortuneHelp() {
        float x=modalX+30,y=modalY+125;
        image(fortuneState==0?"fortune-barrel":"fortune-slip",x,y,94,106,1);
        text(fortuneState==0?"拿起签筒，为今天留一份期许。":"带上未解的签，前往解签龛。",x+119,y+24,14,0xff807789,true);
        wrap(fortuneState==0?"在游戏中手持签筒使用，即可抽取今日的签。抽到之后仍需解签，才会看见结果。":"签文尚未解读，因此手册不会提前展示内容。完成解签后，打开藏签匣即可归档收藏。",x+119,y+61,355,12,23,0xff807789,false);
        text("这里是本地界面原型，不会触发游戏抽签。",x,modalY+modalH-40,11,0xff9992a4,false);
    }
    private void readingModal() {
        HubData.Entry entry=reading;HubData.Fortune f=entry.fortune();
        float x=modalX+28,y=modalY+110;
        paper(x,y,350,444);
        textCenter("第"+f.id()+"签 · "+f.grade(),x+175,y+48,12,HubData.gradeColor(f.grade()),false);
        serifCenter(f.title(),x+175,y+105,32,0xff68604c,true);
        for(int i=0;i<f.poem().size();i++)serifCenter(f.poem().get(i),x+175,y+170+i*39,18,0xff84806c,false);
        d.strokeRoundRect(x+272,y+345,42,44,5,1.5f,0x70ae8173);text("签",x+281,y+376,25,0xffb18a78,false);
        textCenter("DAILY FORTUNES",x+175,y+415,10,0xffb6a783,false);
        float rx=x+383;
        icon("leaf",rx,y+14,15,0xff8f7da7);text("解签",rx+24,y+27,13,0xff8f7da7,true);
        wrap(f.interpretation(),rx,y+70,413,15,29,0xff7b7883,false);
        boolean archived=archive().containsKey(entry.date());
        d.line(rx,y+320,modalX+modalW-28,y+320,1,0xffe6e2ef);
        icon("bookmark",rx,y+343,16,0xff8f91a2);
        text(archived?"已收藏于你的藏签匣":"已解签，尚未收藏",rx+26,y+355,12,0xff8f91a2,false);
        text(archived?"签笺一经收藏，便成为旅途的一部分。":"在游戏中打开藏签匣，即可收存这张签。",rx+26,y+380,11,0xff9997aa,false);
        boolean current=Objects.equals(displayDate,entry.date());
        button("reading-display",!archived?"收藏后可设为展示":current?"当前展示的签笺":"设为手册展示  ✧",rx,y+406,413,38,true,archived&&!current,()->displaySelected(entry));
    }

    private static final float[][] HELLO=sampleCurve(new float[]{24,111},new float[][]{
        {42,99,63,77,72,48},{83,14,67,11,58,29},{48,49,49,86,41,121},
        {48,94,60,77,73,81},{92,88,64,127,88,124},{101,122,111,111,120,101},
        {139,80,143,95,128,104},{99,122,112,139,139,123},{160,111,176,80,185,48},
        {194,13,177,12,170,33},{162,57,159,100,157,113},{154,140,178,126,194,105},
        {211,82,222,51,224,32},{228,11,210,14,206,38},{201,65,196,102,199,116},
        {201,136,220,125,235,108},{253,85,268,91,262,112},{256,136,231,136,229,116},
        {227,98,243,87,255,96},{270,107,284,109,303,93}
    });
    private static final float[][] FLOURISH=sampleCurve(new float[]{97,145},new float[][]{{153,133,229,134,283,142},{302,145,316,143,329,136}});
    private static float[][] sampleCurve(float[] start,float[][] curves) {
        List<float[]> points=new ArrayList<>();float px=start[0],py=start[1],length=0;points.add(new float[]{px,py,0});
        for(float[] c:curves) {
            float x0=px,y0=py;
            for(int i=1;i<=22;i++) {
                float t=i/22f,s=1-t,x=s*s*s*x0+3*s*s*t*c[0]+3*s*t*t*c[2]+t*t*t*c[4];
                float y=s*s*s*y0+3*s*s*t*c[1]+3*s*t*t*c[3]+t*t*t*c[5];
                length+=(float)Math.hypot(x-px,y-py);points.add(new float[]{x,y,length});px=x;py=y;
            }
        }
        for(float[] p:points)p[2]/=length;
        return points.toArray(float[][]::new);
    }
    private void welcome() {
        float seconds=(now-openedAt)/1_000_000_000f;
        float alpha=seconds<2.6f?1:1-progress((long)((seconds-2.6f)*1_000_000_000),420_000_000L);
        // Hide page content until the complete welcome has finished; its entrance starts afterwards.
        d.gradientRoundRect(0,0,1260,850,28,withAlpha(0xffe9f4ff,alpha),withAlpha(0xfff0e9fc,alpha));
        d.save();d.translate(421,302);d.canvas().scale(1.2727f,1.2727f);d.translate(165,80);d.canvas().rotate(-5);d.translate(-165,-80);
        float write=clamp((seconds-.12f)/1.9f,0,1);
        strokeCurve(HELLO,write,6.8f,alpha);
        strokeCurve(FLOURISH,clamp((seconds-1.81f)/.35f,0,1),2.1f,alpha*.58f);
        d.restore();
        float signature=clamp((seconds-1.55f)/.5f,0,1)*alpha;
        textCenter("P I X E L M C",630,549+5*(1-signature),10,withAlpha(0xff9292ad,signature),false);
    }
    private void strokeCurve(float[][] points,float fraction,float width,float alpha) {
        if(fraction<=0)return;
        for(int i=1;i<points.length;i++) {
            float[] a=points[i-1],b=points[i];if(a[2]>fraction)break;
            float partial=Math.min(1,(fraction-a[2])/Math.max(.00001f,b[2]-a[2]));
            float x=a[0]+(b[0]-a[0])*partial,y=a[1]+(b[1]-a[1])*partial;
            float tint=clamp((x-24)/279,0,1);
            int color=tint<.55f?mix(0xff5d84d6,0xff8970d8,tint/.55f):mix(0xff8970d8,0xffb184bf,(tint-.55f)/.45f);
            d.line(a[0],a[1],x,y,width,withAlpha(color,alpha));
        }
    }
    private void toast() {
        float elapsed=(now-toastAt)/1_000_000_000f;if(elapsed>2.8f){toast=null;return;}
        float entrance=reducedMotion?1:ease(clamp(elapsed/.2f,0,1));
        float exit=elapsed>2.5f?clamp((2.8f-elapsed)/.3f,0,1):1;
        float w=Math.min(720,Math.max(290,d.textWidth(toast,12,false)+73)),x=(1260-w)/2,y=18-14*(1-entrance);
        shadow(x,y,w,49,18,withAlpha(0x35465482,exit));
        d.roundRect(x,y,w,49,18,withAlpha(0xf7ffffff,exit));d.strokeRoundRect(x,y,w,49,18,1,withAlpha(0xffffffff,exit));
        d.roundRect(x+12,y+12,26,26,9,withAlpha(0xffeee7ff,exit));
        d.icon("check",x+18,y+18,14,withAlpha(0xff8f70ce,exit));
        d.text(toast,x+49,y+30,12,withAlpha(0xff625582,exit),false);
    }

    private void group(int delayMs,int durationMs) {
        float p=reducedMotion?1:progress(now-pageAt-delayMs*1_000_000L,durationMs*1_000_000L);
        opacity=ease(p);offsetY=12*(1-opacity);d.save();d.translate(0,offsetY);
    }
    private void ungroup(){d.restore();opacity=1;offsetY=0;}
    private void shadow(float x,float y,float w,float h,float r,int color) {
        d.roundRect(x-3,y+5,w+6,h+4,r+3,a(withAlpha(color,.25f)));
        d.roundRect(x-1,y+3,w+2,h+2,r+1,a(withAlpha(color,.4f)));
    }
    private void glass(float x,float y,float w,float h,float radius,int from,int to) {
        shadow(x,y,w,h,radius,0x104b5d85);
        d.gradientRoundRect(x,y,w,h,radius,a(from),a(to));
        d.strokeRoundRect(x+.5f,y+.5f,w-1,h-1,radius,1,a(0xe9ffffff));
        if(h>28)d.line(x+radius,y+1,x+w-radius,y+1,1,a(0xd9ffffff));
    }
    private void text(String s,float x,float baseline,float size,int color,boolean bold){d.text(s,x,baseline,size,a(color),bold);}
    private void serif(String s,float x,float baseline,float size,int color,boolean bold){d.textSerif(s,x,baseline,size,a(color),bold);}
    private void serifCenter(String s,float x,float baseline,float size,int color,boolean bold){serif(s,x-d.textWidthSerif(s,size,bold)/2,baseline,size,color,bold);}
    private void textCenter(String s,float x,float baseline,float size,int color,boolean bold){text(s,x-d.textWidth(s,size,bold)/2,baseline,size,color,bold);}
    private void textRight(String s,float right,float baseline,float size,int color,boolean bold){text(s,right-d.textWidth(s,size,bold),baseline,size,color,bold);}
    private void icon(String name,float x,float y,float size,int color){d.icon(name,x,y,size,a(color));}
    private void image(String name,float x,float y,float w,float h,float alpha){d.image("textures/ui/"+name+".png",x,y,w,h,alpha*opacity);}
    private String fit(String s,float width,float size,boolean bold) {
        if(d.textWidth(s,size,bold)<=width)return s;
        int length=s.length();while(length>0&&d.textWidth(s.substring(0,length)+"…",size,bold)>width)length--;
        return s.substring(0,length)+"…";
    }
    private int wrap(String s,float x,float baseline,float width,float size,float lineHeight,int color,boolean bold) {
        StringBuilder line=new StringBuilder();List<String> lines=new ArrayList<>();
        for(int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            if(c=='\n'||d.textWidth(line.toString()+c,size,bold)>width) {
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
        boolean hover=enabled&&inside(x,y,w,h);int from=primary?hover?0xff987af5:0xff8b6cf1:0xd4ffffff;
        int to=primary?hover?0xff694acb:0xff6d53d6:0xabffffff;
        if(!enabled){from=0x59ffffff;to=0x59ffffff;}
        shadow(x,y,w,h,12,primary&&enabled?0x257558d1:0x126c6588);
        d.gradientRoundRect(x,y,w,h,12,a(from),a(to));
        d.strokeRoundRect(x,y,w,h,12,1,a(primary&&enabled?0xff8c71e6:0xcfffffff));
        textCenter(label,x+w/2,y+(h+10)/2,12,!enabled?0xffa099b5:primary?0xffffffff:0xff64539e,enabled&&primary);
        if(enabled)hit(id,label,x,y,w,h,click);
    }
    private void goldButton(String id,String label,float x,float y,float w,float h,Runnable click) {
        boolean hover=inside(x,y,w,h);shadow(x,y,w,h,12,0x269c7e44);
        d.gradientRoundRect(x,y,w,h,12,a(hover?0xffcda267:0xffbe9455),a(0xffa57e42));
        d.strokeRoundRect(x,y,w,h,12,1,a(0xffc5a477));textCenter(label,x+w/2,y+24,11,0xffffffff,true);hit(id,label,x,y,w,h,click);
    }
    private void textButton(String id,String label,float x,float y,float w,float h,Runnable click,boolean enabled) {
        if(enabled&&inside(x,y,w,h))d.roundRect(x,y,w,h,8,a(0x88ffffff));
        textCenter(label,x+w/2,y+(h+9)/2,11,enabled?0xff7a6a99:0xffb2aabc,false);
        if(enabled)hit(id,label,x,y,w,h,click);
    }
    private void iconButton(String id,String label,String name,float x,float y,float size,Runnable click,boolean enabled) {
        if(enabled&&inside(x,y,size,size))d.roundRect(x,y,size,size,10,a(0xffe6e8f7));
        icon(name,x+(size-18)/2,y+(size-18)/2,18,enabled?0xff6f7a92:0xffb9bdcd);
        if(enabled)hit(id,label,x,y,size,size,click);
    }
    private void toggle(String id,boolean checked,float x,float y,Runnable click) {
        d.roundRect(x,y,40,23,12,a(checked?0xff8e76d9:0xffd5daea));
        d.strokeRoundRect(x,y,40,23,12,1,a(checked?0xff8770ce:0xffc5cbdd));
        d.circle(x+(checked?29:11),y+11.5f,8.5f,a(0xffffffff));
        hit(id,checked?"关闭":"开启",x-3,y-5,46,33,click);
    }
    private void hit(String id,String label,float x,float y,float w,float h,Runnable click) {
        if(opacity<.2f)return;
        Hit region=new Hit(id,label,x,y+offsetY,w,h,click);nextHits.add(region);
        if(Objects.equals(focusId,id))d.strokeRoundRect(x-3,y-3,w+6,h+6,12,2,a(0xff7758e8));
    }
    private boolean inside(float x,float y,float w,float h){return mx>=x&&my>=y+offsetY&&mx<x+w&&my<y+offsetY+h;}
    private int a(int color){return withAlpha(color,opacity);}
    private static int withAlpha(int color,float alpha){return (Math.round((color>>>24)*clamp(alpha,0,1))<<24)|(color&0xffffff);}
    private static int lighten(int color,float amount){return amount>=0?mix(color,0xffffffff,amount):mix(color,0xff26344f,-amount);}
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
    private int welcomeIndex(){return Math.max(0,List.of("once","login","always").indexOf(welcomeMode));}
    private void notify(String value){toast=value;toastAt=System.nanoTime();}
    private void navigate(int destination){page=destination;pageAt=System.nanoTime();focusId=null;titleScroll=0;}
    private void openOverlay(String value){restoreFocus=focusId;overlay=value;reading=null;focusId="modal-close";sliderDragging=false;hits.clear();}
    private void openReading(HubData.Entry value){if(value==null)return;restoreFocus=focusId;reading=value;overlay=null;focusId="modal-close";hits.clear();}
    public boolean dismissOverlay() {
        if(overlay==null&&reading==null)return false;
        overlay=null;reading=null;sliderDragging=false;focusId=restoreFocus;restoreFocus=null;hits.clear();return true;
    }
    private void setWelcomeFromX(float x) {
        float start=modalX+111;int index=Math.round(clamp((x-start)/169,0,2));
        welcomeMode=List.of("once","login","always").get(index);
    }

    public boolean mouseClicked(float x,float y,int button) {
        if(button!=0)return false;
        if(isWelcomePlaying())return true;
        mx=x;my=y;
        for(int i=hits.size()-1;i>=0;i--) {
            Hit hit=hits.get(i);if(!hit.contains(x,y))continue;
            focusId=hit.id();sliderDragging="frequency".equals(hit.id());hit.action().run();return true;
        }
        if(overlay!=null||reading!=null) {
            if(x<modalX||x>modalX+modalW||y<modalY||y>modalY+modalH)dismissOverlay();
            return true;
        }
        focusId=null;return true;
    }
    public boolean mouseReleased(float x,float y,int button){boolean active=sliderDragging;sliderDragging=false;return active;}
    public boolean mouseDragged(float x,float y,int button,float dx,float dy){if(sliderDragging&&button==0){setWelcomeFromX(x);return true;}return false;}
    public boolean mouseScrolled(float x,float y,double amount) {
        if(isWelcomePlaying()||overlay!=null||reading!=null)return true;
        if(page==1&&x>=230&&x<=908&&y>=272&&y<=796){titleScroll-=amount*52;return true;}
        if(page==2&&history&&x>=254&&x<=898&&y>=252&&y<=746){historyScroll-=amount*48;return true;}
        return false;
    }
    public boolean keyPressed(int keyCode,int scanCode,int modifiers) {
        if(keyCode==256)return dismissOverlay();
        if(isWelcomePlaying())return true;
        if(keyCode==297){if(overlay==null&&reading==null)openOverlay("preview");else if("preview".equals(overlay))dismissOverlay();return true;}
        if(keyCode==258) {
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
            for(Hit hit:hits)if(hit.id().equals(focusId)){hit.action().run();return true;}
        }
        return textInputFocused()||overlay!=null||reading!=null;
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
