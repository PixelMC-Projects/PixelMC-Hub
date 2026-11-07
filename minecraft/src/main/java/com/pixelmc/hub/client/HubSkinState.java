/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client;

import java.util.Objects;
import java.util.UUID;

/** Bounded session-only retry schedule. No images, network, files, GL or Minecraft classes. */
public final class HubSkinState {
    private static final long[] DELAYS={0,1_000_000_000L,5_000_000_000L};
    public record Key(UUID player,String location,int texture,long resourceGeneration){}
    public record Capture(long session,long revision,Key key,int attempt){}
    private UUID player;
    private Key wanted;
    private long session,revision,windowAt,firstAttemptAt=Long.MIN_VALUE;
    private int attempts;
    private String hash="";
    private boolean confirmed;
    public void session(UUID value,long now){if(!Objects.equals(player,value)){clear();player=value;windowAt=now;}}
    public void clear(){player=null;wanted=null;hash="";confirmed=false;attempts=0;firstAttemptAt=Long.MIN_VALUE;session++;revision++;}
    public void observe(Key key,long now){
        if(player==null||!player.equals(key.player))return;
        if(!key.equals(wanted)){wanted=key;attempts=0;firstAttemptAt=Long.MIN_VALUE;windowAt=Math.max(now,windowAt);revision++;}
    }
    /** Opening/reload gives an already-known texture a fresh finite opportunity, retaining the current image. */
    public void retryWindow(long now){attempts=0;firstAttemptAt=Long.MIN_VALUE;windowAt=now;revision++;}
    public Capture claim(long now){
        if(wanted==null||attempts>=DELAYS.length)return null;
        if(attempts==0){if(now<windowAt)return null;firstAttemptAt=now;}else if(now-firstAttemptAt<DELAYS[attempts])return null;
        return new Capture(session,revision,wanted,attempts++);
    }
    public boolean current(Capture capture){return capture!=null&&capture.session==session&&capture.revision==revision&&Objects.equals(wanted,capture.key)&&Objects.equals(player,capture.key.player);}
    public boolean changed(Capture capture,String next){return current(capture)&&(!confirmed||!hash.equals(next));}
    public void confirm(Capture capture,String next){if(current(capture)){hash=next;confirmed=true;}}
    public int attempts(){return attempts;}
    public boolean confirmed(){return confirmed;}
    public UUID player(){return player;}
    public Key wanted(){return wanted;}
}
