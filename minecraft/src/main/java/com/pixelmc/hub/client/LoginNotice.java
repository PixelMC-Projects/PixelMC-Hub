/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client;

/** One deadline per authenticated game connection. No worker, data query, or recurring task. */
final class LoginNotice {
    private Object connection;
    private long due;
    void start(Object value,long now){connection=value;due=now+11_000_000_000L;}
    void cancel(){connection=null;}
    boolean claim(Object value,long now){
        if(connection==null)return false;
        if(connection!=value){cancel();return false;}
        if(now<due)return false;
        cancel();return true;
    }
}
