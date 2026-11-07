/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.network;

import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** Connection-owned assembly. Validate the active request before calling; never allocates a declared total. */
public final class HubFragmentBuffer {
    public static final int PART_BYTES=262144,MAX_BYTES=2097152,MAX_PARTS=8,MAX_ACTIVE=4;
    private static final long TIMEOUT=10_000_000_000L;
    private static final class Assembly {
        final String action;final int bytes;final byte[][] parts;final long at;int received;
        Assembly(String action,int count,int bytes,long at){this.action=action;this.bytes=bytes;parts=new byte[count][];this.at=at;}
    }
    private final Map<Long,Assembly> active=new HashMap<>();
    public void clear(){active.clear();}
    public void remove(long id){active.remove(id);}
    public void expire(long now){active.values().removeIf(a->now-a.at>=TIMEOUT);}
    public int activeCount(){return active.size();}
    public String accept(long id,String action,int index,int count,int bytes,byte[] part,long now){
        expire(now);
        if(id<=0||!HubProtocol.allowed("updates",action)||bytes<=HubProtocol.RESPONSE_BYTES||bytes>MAX_BYTES||count<1||count>MAX_PARTS||count!=(bytes+PART_BYTES-1)/PART_BYTES||index<0||index>=count||part==null||part.length!=(index==count-1?bytes-index*PART_BYTES:PART_BYTES))throw invalid();
        Assembly a=active.get(id);
        if(a==null){if(active.size()>=MAX_ACTIVE)throw invalid();a=new Assembly(action,count,bytes,now);active.put(id,a);}
        if(a.bytes!=bytes||a.parts.length!=count||!a.action.equals(action)){active.remove(id);throw invalid();}
        if(a.parts[index]!=null){if(!Arrays.equals(a.parts[index],part)){active.remove(id);throw invalid();}return null;}
        a.parts[index]=part.clone();a.received++;
        if(a.received<count)return null;
        active.remove(id);byte[] joined=new byte[bytes];int at=0;for(byte[] p:a.parts){System.arraycopy(p,0,joined,at,p.length);at+=p.length;}
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(joined)).toString();}
        catch(CharacterCodingException bad){throw invalid();}
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("更新日志分片无效，请重新读取");}
}
