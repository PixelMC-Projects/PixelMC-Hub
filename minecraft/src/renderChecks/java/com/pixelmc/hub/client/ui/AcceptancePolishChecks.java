/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.google.gson.*;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;

public final class AcceptancePolishChecks {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static Object field(HubView v,String name)throws Exception {Field f=HubView.class.getDeclaredField(name);f.setAccessible(true);return f.get(v);}
    private static Object invoke(HubView v,String name)throws Exception {Method m=HubView.class.getDeclaredMethod(name);m.setAccessible(true);return m.invoke(v);}
    private static void set(HubView v,String name,Object value)throws Exception {Field f=HubView.class.getDeclaredField(name);f.setAccessible(true);f.set(v,value);}
    private static JsonObject calendar(String month,JsonObject... records){JsonObject value=new JsonObject();value.addProperty("month",month);JsonArray rows=new JsonArray();for(var r:records)rows.add(r);value.add("records",rows);return value;}
    public static void main(String[] args)throws Exception {
        HubView v=new HubView();v.live((module,action,payload)->{});v.moduleStatus("titles","");v.moduleStatus("fortunes","");
        v.acceptLive("titles","snapshot",PlayerDataChecks.titles());v.catalogueComplete();HubData data=(HubData)field(v,"data");
        var before=List.copyOf(data.titles);String selected=(String)field(v,"selectedTitle"),equipped=(String)field(v,"equipped");
        set(v,"titleScroll",91f);v.catalogueInvalidated();check(v.isCatalogueComplete(),"Refresh hid confirmed count/button state");
        JsonObject first=PlayerDataChecks.titles();first.add("titles",new JsonArray());first.addProperty("nextCursor","next");
        v.acceptLive("titles","snapshot",first);
        check(data.titles.equals(before)&&field(v,"equipped").equals(equipped)&&v.isCatalogueComplete(),"Partial catalogue replaced confirmed UI");
        check(field(v,"titleScroll").equals(91f)&&field(v,"selectedTitle").equals(selected),"Refresh reset user navigation state");
        v.moduleStatus("titles","请求超时，请稍后重试");check(data.titles.equals(before)&&v.isCatalogueComplete(),"Failed refresh discarded cache");
        v.moduleStatus("titles","");v.acceptLive("titles","snapshot",first);
        JsonObject last=PlayerDataChecks.titles();last.add("titles",new JsonArray());v.acceptLive("titles","catalogue",last);v.catalogueComplete();
        check(data.titles.isEmpty()&&v.isCatalogueComplete(),"Confirmed empty catalogue was not published");
        // Keep both a complete viewed month and the selected date through feature/clear replies.
        v.acceptLive("fortunes","snapshot",PlayerDataChecks.fortunes());
        JsonObject old=PlayerDataChecks.record();old.addProperty("date","2026-10-17");
        set(v,"month",YearMonth.of(2026,10));set(v,"selectedDate",LocalDate.of(2026,10,17));
        v.acceptLive("fortunes","calendar",calendar("2026-10",old));
        check(!(boolean)invoke(v,"calendarInitialLoading"),"Completed month still loading");
        v.pending("fortunes",true);v.acceptLive("fortunes","feature",PlayerDataChecks.fortunes());v.pending("fortunes",false);
        check(data.archive.containsKey(LocalDate.of(2026,10,17))&&!(boolean)invoke(v,"calendarInitialLoading"),"Feature result blanked calendar");
        check(field(v,"month").equals(YearMonth.of(2026,10))&&field(v,"selectedDate").equals(LocalDate.of(2026,10,17)),"Feature result changed month/selection");
        v.moduleStatus("fortunes","请求超时，请稍后重试");check(data.archive.containsKey(LocalDate.of(2026,10,17)),"Calendar failure erased last confirmed month");
        v.moduleStatus("fortunes","");v.acceptLive("fortunes","calendar",calendar("2026-10"));
        check(!data.archive.containsKey(LocalDate.of(2026,10,17)),"Confirmed empty month kept removed entry");
        v.live((module,action,payload)->{});check(data.titles.isEmpty()&&data.archive.isEmpty()&&data.calendarMonths.isEmpty(),"New session retained old private snapshot");
        HubToast toast=new HubToast();toast.show("服务器已确认更新",HubToast.Kind.SUCCESS,0);
        toast.advance(2_000_000_000L,false,false);check(toast.remaining()==2,"Fuse timing drifted");
        toast.advance(20_000_000_000L,true,false);check(toast.remaining()==2,"Hover did not pause fuse");
        toast.advance(22_000_000_000L,false,false);check(toast.active(),"Toast removed before its exit animation");
        toast.advance(22_200_000_000L,false,false);check(toast.active(),"Toast exit cut short");
        toast.advance(22_300_000_000L,false,false);check(!toast.active(),"Toast never completed exit");
        System.out.println("PASS: atomic paged titles, stable cached refresh/error/selection, retained calendar during feature, empty replacement, session isolation, toast fuse/pause/full exit");
    }
}
