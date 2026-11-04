/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client;

public final class LoginNoticeChecks {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        LoginNotice notice=new LoginNotice();Object a=new Object(),b=new Object();
        notice.start(a,0);check(!notice.claim(a,10_999_999_999L),"Login message fired early");
        check(notice.claim(a,11_000_000_000L),"Login message missed deadline");check(!notice.claim(a,99_000_000_000L),"Login message repeated");
        notice.start(a,0);notice.cancel();check(!notice.claim(a,20_000_000_000L),"Logout left a pending notice");
        notice.start(a,0);check(!notice.claim(b,20_000_000_000L),"Old connection leaked into new session");
        notice.start(b,20_000_000_000L);check(!notice.claim(b,30_000_000_000L)&&notice.claim(b,31_000_000_000L),"Reconnect deadline incorrect");
        System.out.println("PASS: 11-second login notice, exactly once, logout cancellation and connection isolation");
    }
}
