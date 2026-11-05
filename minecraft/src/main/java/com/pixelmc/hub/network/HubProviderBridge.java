/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.network;

import com.google.gson.JsonObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.*;

/** Optional providers, deliberately independent of client, Minecraft and Skija classes. */
public final class HubProviderBridge {
    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(32), task -> { Thread t = new Thread(task, "PixelMC-Hub provider"); t.setDaemon(true); return t; },
        new ThreadPoolExecutor.AbortPolicy());
    private HubProviderBridge() {}
    public static JsonObject discover() {
        JsonObject modules = new JsonObject();
        HubProtocol.PROVIDERS.forEach((module, name) -> {
            JsonObject state = new JsonObject();
            try { int version=version(name);state.addProperty("version", version); state.addProperty("available", version == 1); }
            catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) { state.addProperty("version", 0); state.addProperty("available", false); }
            modules.add(module, state);
        });
        JsonObject result = new JsonObject(); result.add("modules", modules); result.addProperty("sessionSync",1); return result;
    }
    private static int version(String name) throws ReflectiveOperationException {
        return ((Number) Class.forName(name).getMethod("protocolVersion").invoke(null)).intValue();
    }
    public static CompletableFuture<String> request(UUID player, String module, String action, String payload) {
        if (!HubProtocol.allowed(module, action)) return CompletableFuture.failedFuture(new IllegalArgumentException("不支持的玩家操作"));
        try { HubProtocol.payload(module, action, payload); }
        catch (RuntimeException error) { return CompletableFuture.failedFuture(error); }
        if(module.equals("fortunes")&&action.equals("calendarSync")) {
            JsonObject query=HubProtocol.payload(module,action,payload),plain=new JsonObject();plain.add("month",query.get("month"));
            JsonObject known=HubCalendarDelta.parseKnown(query.get("month").getAsString(),query.get("known").getAsString());
            return request(player,module,"calendar",plain.toString()).thenApply(value->HubCalendarDelta.encode(HubProtocol.object(value,HubProtocol.RESPONSE_BYTES),known).toString());
        }
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    if (module.equals("hub")) return CompletableFuture.completedFuture(discover().toString());
                    String name = HubProtocol.PROVIDERS.get(module);
                    if (version(name) != 1) throw new IllegalStateException("模块协议不兼容");
                    Method method = Class.forName(name).getMethod("request", UUID.class, String.class, String.class);
                    Object result = method.invoke(null, player, action, payload);
                    if (!(result instanceof CompletableFuture<?> future)) throw new IllegalStateException("模块响应格式不正确");
                    return future.thenApply(value -> HubProtocol.object((String) value, HubProtocol.RESPONSE_BYTES).toString());
                } catch (ClassNotFoundException absent) { throw new CompletionException(new IllegalStateException("服务器未安装此模块")); }
                catch (InvocationTargetException error) { throw new CompletionException(error.getCause()); }
                catch (ReflectiveOperationException error) { throw new CompletionException(new IllegalStateException("模块协议不兼容")); }
            }, WORKERS).thenCompose(future -> future).orTimeout(8, TimeUnit.SECONDS);
        } catch (RejectedExecutionException busy) { return CompletableFuture.failedFuture(new IllegalStateException("服务器繁忙，请稍后重试")); }
    }
    public static String safeError(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException || error instanceof InvocationTargetException) && error.getCause() != null) error = error.getCause();
        if (error instanceof TimeoutException) return "请求超时，请稍后重试";
        // Provider contract only uses validation/state errors for safe user facing messages.
        if (error instanceof IllegalArgumentException || error instanceof IllegalStateException) {
            String message = error.getMessage();
            if (message != null && message.length() <= 160 && !message.contains("\n") && !message.contains("jdbc:") && !message.contains("Exception")) return message;
        }
        return "读取玩家资料失败，请稍后重试";
    }
}
