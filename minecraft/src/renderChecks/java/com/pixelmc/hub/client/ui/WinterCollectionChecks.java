/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.pixelmc.hub.client.render.HubRenderer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import static com.pixelmc.hub.client.ui.WinterStationChecks.*;

final class WinterCollectionChecks {
    private static List<String> controls(HubView view) throws Exception {
        List<String> ids=new ArrayList<>();
        for(Object hit:(List<?>)field(view,"hits"))
            ids.add((String)hit.getClass().getDeclaredMethod("id").invoke(hit));
        return ids;
    }

    static void clickId(HubView v,String id) throws Exception {
        for(Object hit:(List<?>)field(v,"hits")) {
            Class<?> type=hit.getClass();
            if(!id.equals(type.getDeclaredMethod("id").invoke(hit))) continue;
            float x=(float)type.getDeclaredMethod("x").invoke(hit);
            float y=(float)type.getDeclaredMethod("y").invoke(hit);
            float w=(float)type.getDeclaredMethod("w").invoke(hit);
            float h=(float)type.getDeclaredMethod("h").invoke(hit);
            click(v,Math.round(x+w/2),Math.round(y+h/2));
            return;
        }
        throw new AssertionError("Missing control "+id);
    }

    private static void previewState(HubRenderer r,HubView v,int state) throws Exception {
        v.keyPressed(297,0,0);
        frame(r,v,1000,null);
        clickId(v,"preview-state-"+state);
        v.dismissOverlay();
        frame(r,v,1000,null);
    }

    private static void checkUnsupported(HubRenderer renderer, boolean dark) throws Exception {
        HubView view = new HubView();
        List<String> calls = new ArrayList<>();
        view.setWinter(true);
        view.setDark(dark);
        view.setSettings("once", true);
        view.live((module, action, query) -> calls.add(module + "." + action));
        view.setSessionPlayerId(java.util.UUID.fromString("00000000-0000-4000-8000-000000000001"));
        String reason = "服务器未安装此模块或协议不兼容";
        for (String module : List.of("manager", "titles", "fortunes", "updates", "rankings", "community"))
            view.moduleStatus(module, reason);
        view.open(false);
        epoch = System.nanoTime();
        String mode = dark ? "dark" : "light";
        frame(renderer, view, 1200, "unsupported-home-" + mode);
        check(!controls(view).contains("today-read"), "unavailable fortune link is actionable");
        view.moduleStatus("updates", "更新服务超时，请稍后重试。".repeat(16));
        frame(renderer, view, 1200, "unsupported-home-long-" + mode);
        view.moduleStatus("updates", reason);
        String[] modules = {"titles", "fortunes", "updates", "rankings"};
        for (int i = 0; i < modules.length; i++) {
            clickId(view, "nav-" + (i + 1));
            frame(renderer, view, 1200, "unsupported-" + modules[i] + "-" + mode);
            String retry = i == 2 ? "updates-retry" : "retry-" + modules[i];
            if (i == 3) continue;
            Object hit = ((List<?>) field(view, "hits")).stream().filter(item -> {
                try { return retry.equals(item.getClass().getDeclaredMethod("id").invoke(item)); }
                catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            }).findFirst().orElseThrow();
            Class<?> type = hit.getClass();
            float x = (float) type.getDeclaredMethod("x").invoke(hit);
            float w = (float) type.getDeclaredMethod("w").invoke(hit);
            float y = (float) type.getDeclaredMethod("y").invoke(hit);
            check(Math.abs(x + w / 2 - 723) < 1, "retry is off the winter page axis: " + retry);
            if (i == 2) check(y >= 123.25 + 671.75 / 2 + 29 + 12, "retry overlaps the reason");
            int before = calls.size();
            clickId(view, retry);
            check(calls.size() > before, "retry did not dispatch: " + retry);
        }
        view.clearSession();
        check(((String)field(view,"sessionPlayerId")).isEmpty(), "player id survived disconnect");
    }

    static void run(HubRenderer r,boolean dark) throws Exception {
        HubView v=new HubView();
        v.setWinter(true);
        v.setDark(dark);
        v.setSettings("always",true);
        v.open(false);
        epoch=System.nanoTime();
        String mode=dark?"dark":"light";
        frame(r,v,1000,null);
        clickId(v,"nav-1");
        frame(r,v,1000,null);
        check(controls(v).stream().filter(id->id.startsWith("album-pin-")).count()==6,"six pins");
        check(!controls(v).contains("density"),"classic card toggle leaked into winter album");
        clickId(v,"filter-1");
        frame(r,v,1000,null);
        clickId(v,"album-next");
        frame(r,v,1000,"album-last-"+mode);
        check(controls(v).stream().filter(id->id.startsWith("album-pin-")).count()==2,"last tray");
        check(!controls(v).contains("album-next")&&!controls(v).contains("equip"),"last tray bounds and locked equip");
        clickId(v,"album-previous");
        frame(r,v,1000,null);
        clickId(v,"sort");
        frame(r,v,1000,"album-sort-"+mode);
        v.keyPressed(264,0,0);
        v.keyPressed(257,0,0);
        frame(r,v,1000,null);
        check((int)field(v,"sort")==1&&!controls(v).contains("sort-option-0"),"keyboard sort");
        clickId(v,"search");
        check(v.charTyped('龘',0),"type in album search");
        frame(r,v,1000,"album-empty-"+mode);
        check(controls(v).contains("reset-filter")&&!controls(v).contains("equip"),"empty album actions");
        clickId(v,"reset-filter");
        frame(r,v,1000,null);
        clickId(v,"nav-2");
        frame(r,v,1000,null);
        LocalDate today=(LocalDate)field(v,"selectedDate");
        check(!controls(v).contains("day-"+today.plusDays(1)),"future day enabled");
        check(!controls(v).contains("next-month"),"future month enabled");
        clickId(v,"read-feature");
        frame(r,v,1000,"fortune-reading-"+mode);
        check(controls(v).contains("reading-display"),"featured reading confirmed as archived");
        clickId(v,"reading-display");
        check(field(v,"reading")!=null,"repeat feature closed reading");
        v.dismissOverlay(); frame(r,v,1000,null);
        clickId(v,"counter-list");
        frame(r,v,1000,"counter-list-"+mode);
        check(controls(v).stream().anyMatch(id->id.startsWith("history-")),"list missing stored entries");
        clickId(v,"previous-month");
        frame(r,v,1000,null);
        check(controls(v).contains("next-month"),"previous month cannot go forward");
        clickId(v,"current-month");
        frame(r,v,1000,null);
        check(today.equals(field(v,"selectedDate")),"today selection did not restore");
        clickId(v,"nav-0");
        frame(r,v,1000,null);
        for(int state=0;state<=2;state++) {
            previewState(r,v,state);
            clickId(v,"today-read");
            frame(r,v,1000,(state==0?"help-draw-":state==1?"help-read-":"read-unarchived-")+mode);
            if(state<2) {
                check(field(v,"reading")==null,"unread fortune disclosed");
                clickId(v,"fortune-help-ok");
                check(field(v,"overlay")==null,"help confirmation");
            } else {
                check(field(v,"reading")!=null&&!controls(v).contains("reading-display"),"unarchived slip cannot be displayed");
                v.dismissOverlay();
            }
            frame(r,v,1000,null);
        }
        // Running through the actual renderer exercises the rich-name and badge fallback path.
        PlayerDataChecks.populate(v);
        clickId(v,"nav-1");
        frame(r,v,1000,"album-provider-"+mode);
        check(controls(v).contains("album-pin-rich") && controls(v).contains("display-settings"),"provider catalogue or permissions missing");
        v.pending("titles",true);
        frame(r,v,1000,null);
        check(!controls(v).contains("equip")&&!controls(v).contains("visibility"),"pending mutation still exposed");
        WinterAlbumInkChecks.run(r,dark);
        checkUnsupported(r,dark);
    }
}
