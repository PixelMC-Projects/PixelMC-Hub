/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.network;

import java.util.HashMap;
import java.util.Map;

/** Coalesces change notifications/navigation, waiting for the current read/write cycle to finish. */
public final class HubRefreshGate {
    private final Map<String,Long> dirty=new HashMap<>(),lastRequest=new HashMap<>();
    public void invalidate(String module,long now){dirty.putIfAbsent(module,now);}
    public boolean pending(String module){return dirty.containsKey(module);}
    public boolean due(String module,long now,boolean busy){
        Long changed=dirty.get(module);return !busy&&changed!=null&&now-changed>=150_000_000L
            &&now-lastRequest.getOrDefault(module,Long.MIN_VALUE/2)>=750_000_000L;
    }
    public void requested(String module,long now){dirty.remove(module);lastRequest.put(module,now);}
}
