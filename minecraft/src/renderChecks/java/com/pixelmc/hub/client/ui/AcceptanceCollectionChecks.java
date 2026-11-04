/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import java.time.LocalDate;
import java.util.List;

public final class AcceptanceCollectionChecks {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static HubData.Title title(String id,long at){return new HubData.Title(id,id,"leaf","旅途纪念","普通",true,"2026/11/04","","",0xff669988,0xffddeee4,0xff889988,true,List.of(),at);}
    private static HubData.Entry fortune(int day,long at){return new HubData.Entry(LocalDate.of(2026,11,day),"20:26:14",new HubData.Fortune(day,"签笺"+day,"中平签",List.of("山水相逢"),"测试"),at);}
    public static void main(String[] args) {
        HubData data=new HubData();data.clearLive();
        data.titles.addAll(List.of(title("a",10),title("b",30),title("c",20),title("d",40)));
        var rows=data.recentKeepsakes(data.archive);check(rows.size()==3&&rows.stream().allMatch(row->row.title()!=null),"Title-only collection reserved an empty fortune row");
        check(rows.stream().map(row->row.title().id()).toList().equals(List.of("d","b","c")),"Titles did not use exact acquired timestamps");
        data.titles.clear();for(int i=1;i<=4;i++){var entry=fortune(i,new long[]{0,300,100,400,200}[i]);data.archive.put(entry.date(),entry);}
        rows=data.recentKeepsakes(data.archive);check(rows.size()==3&&rows.stream().allMatch(row->row.entry()!=null),"Fortune-only collection reserved title rows");
        check(rows.stream().map(row->row.entry().date().getDayOfMonth()).toList().equals(List.of(3,1,4)),"Fortune ranking used date/text rather than exact draw time");
        data.titles.addAll(List.of(title("first",350),title("second",250),title("old",50)));
        rows=data.recentKeepsakes(data.archive);check(rows.size()==3&&rows.get(0).at()==400&&rows.get(1).title().id().equals("first")&&rows.get(2).at()==300,"Mixed collection imposed type quotas");
        data.titles.add(title("new",500));check(data.recentKeepsakes(data.archive).get(0).title().id().equals("new"),"New collection did not enter first position");
        data.titles.clear();data.archive.clear();check(data.recentKeepsakes(data.archive).isEmpty(),"Empty collection fabricated rows");
        data.manager(PlayerDataChecks.manager());long baseline=data.onlineBaselineAt;
        check(data.onlineMillis(baseline+61_000_000_000L)==data.totalOnlineMillis+61_000,"Online display did not advance from confirmed baseline");
        check(data.onlineMillis(baseline-1)==data.totalOnlineMillis,"Online baseline ran backwards");
        System.out.println("PASS: titles-only, fortunes-only, true-time mixed top-three, empty/new collection and confirmed online baseline elapsed");
    }
}
