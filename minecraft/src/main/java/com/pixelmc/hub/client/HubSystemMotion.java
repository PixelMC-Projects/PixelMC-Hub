package com.pixelmc.hub.client;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

/** Read-only Windows animation preference, cached off the render and client tick threads. */
final class HubSystemMotion {
    private interface User32 extends StdCallLibrary {
        boolean SystemParametersInfoW(int action,int parameter,IntByReference result,int flags);
    }
    private static final class Windows {
        private static final User32 API=Native.load("user32",User32.class,W32APIOptions.DEFAULT_OPTIONS);
    }
    private final BooleanSupplier reader;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(task->{
        Thread thread=new Thread(task,"PixelMC-Hub system motion");thread.setDaemon(true);return thread;
    });
    // An unresolved initial read skips welcome without marking it seen. Failure restores local-only behavior.
    private volatile boolean reduced=true;
    private volatile boolean openingGuard;
    private boolean pending;
    private long nextRead;
    private Runnable openingReady;

    HubSystemMotion(){this(HubSystemMotion::readSystem);}
    HubSystemMotion(BooleanSupplier reader){this.reader=reader;}
    boolean reducedMotion(){return reduced||openingGuard;}

    synchronized boolean refreshForOpening(Runnable ready) {
        if(!pending&&System.nanoTime()<nextRead)return true;
        // Defer opening until this read completes. Repeated open requests share the latest callback.
        openingGuard=true;openingReady=ready;
        refresh();
        return false;
    }

    synchronized void refresh() {
        long now=System.nanoTime();
        if(pending||now<nextRead)return;
        pending=true;nextRead=now+1_000_000_000L;
        worker.execute(()->{
            try{reduced=reader.getAsBoolean();}
            catch(RuntimeException|LinkageError unavailable){reduced=false;}
            finally {
                Runnable ready;
                synchronized(this) {
                    pending=false;openingGuard=false;nextRead=System.nanoTime()+1_000_000_000L;
                    ready=openingReady;openingReady=null;
                }
                if(ready!=null)ready.run();
            }
        });
    }

    private static boolean readSystem() {
        if(!Platform.isWindows())return false;
        IntByReference enabled=new IntByReference(1);
        // SPI_GETCLIENTAREAANIMATION. No setting or broadcast is written to the OS.
        return Windows.API.SystemParametersInfoW(0x1042,0,enabled,0)&&enabled.getValue()==0;
    }
}
