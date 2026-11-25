package com.pixelmc.hub.wenzhou;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/** The model and the local test console share this read-only command vocabulary. */
final class WenzhouCommands {
    private static final Pattern MOD = Pattern.compile("[a-z][a-z0-9_]{1,63}");
    private static final String JAVA_NAME =
            "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*";
    private static final Pattern METHOD = Pattern.compile(JAVA_NAME);
    private static final Pattern CLASS = Pattern.compile("(?:" + JAVA_NAME + "\\.)*" + JAVA_NAME);
    private static final Pattern CLASS_SEARCH = Pattern.compile("[\\p{javaJavaIdentifierPart}.]+");
    private static final int PAGE_LINES = 36;
    private static final int MAX_OUTPUT = 1024 * 1024;
    private static final ThreadPoolExecutor WORKER =
            new ThreadPoolExecutor(
                    0,
                    1,
                    30,
                    TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(12),
                    task -> {
                        Thread thread = new Thread(task, "Wenzhou mod code reader");
                        thread.setDaemon(true);
                        return thread;
                    });

    static CompletableFuture<JsonObject> run(MinecraftServer server, String line) {
        if (line == null
                || line.isBlank()
                || line.length() > 300
                || line.chars().anyMatch(Character::isISOControl)) return invalid();
        String[] args = line.trim().split(" +");
        if (args.length == 2 && args[0].equals("recipe-json")) {
            ResourceLocation id = WenzhouReadTools.resourceId(args[1]);
            return id == null
                    ? invalid()
                    : server.submit(() -> WenzhouRecipes.definition(server, id))
                            .thenCompose(future -> future);
        }
        boolean names = args[0].equals("classes");
        if ((!names && !args[0].equals("javap"))
                || args.length < 3
                || args.length > (names ? 4 : 5)
                || !MOD.matcher(args[1]).matches()) return invalid();
        if (names && !CLASS_SEARCH.matcher(args[2]).matches()) return invalid();
        if (!names
                && (!CLASS.matcher(args[2]).matches()
                        || args.length > 3
                                && !args[3].equals("-")
                                && !METHOD.matcher(args[3]).matches())) return invalid();
        String pageArg = args.length == (names ? 4 : 5) ? args[args.length - 1] : "1";
        if (!pageArg.matches("[1-9][0-9]{0,2}")) return invalid();
        int page = Integer.parseInt(pageArg);
        var mod = ModList.get().getModFileById(args[1]);
        if (mod == null) return done("not_found", "本实例未安装这个模组。");
        Path jar = mod.getFile().getFilePath().toAbsolutePath();
        try {
            return CompletableFuture.supplyAsync(
                    () -> {
                        try (ZipFile archive = new ZipFile(jar.toFile())) {
                            if (names) return classes(archive, args[2], page);
                            String entry = args[2].replace('.', '/') + ".class";
                            var info = archive.getEntry(entry);
                            if (info == null || info.getSize() < 0 || info.getSize() > MAX_OUTPUT)
                                return message("not_found", "所选模组 JAR 中未找到可读取的该类。");
                            String method = args.length > 3 ? args[3] : "-";
                            return javap(jar, args[2], method, page);
                        } catch (IOException | RuntimeException ex) {
                            return message("unavailable", "已安装模组文件或本机 javap 无法读取。");
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            return message("interrupted", "此次读取已中断。");
                        }
                    },
                    WORKER);
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            return done("busy", "代码查阅繁忙，请稍后再试。");
        }
    }

    private static JsonObject classes(ZipFile archive, String keyword, int page) {
        JsonArray items = new JsonArray();
        int seen = 0;
        String needle = keyword.toLowerCase(Locale.ROOT);
        var entries = archive.entries();
        while (entries.hasMoreElements()) {
            String entry = entries.nextElement().getName();
            if (!entry.endsWith(".class")) continue;
            String name = entry.substring(0, entry.length() - 6).replace('/', '.');
            // Validate only the Java binary name. Package conventions vary between installed mods.
            if (!CLASS.matcher(name).matches() || !name.toLowerCase(Locale.ROOT).contains(needle))
                continue;
            if (seen++ < (page - 1) * PAGE_LINES) continue;
            if (items.size() == PAGE_LINES) break;
            items.add(name);
        }
        JsonObject result = message("installed_code", "所选模组 JAR 中匹配的类。");
        result.add("classes", items);
        result.addProperty("more", seen > page * PAGE_LINES);
        return result;
    }

    private static JsonObject javap(Path jar, String type, String method, int page)
            throws IOException, InterruptedException {
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        Path executable =
                Path.of(System.getProperty("java.home"), "bin", windows ? "javap.exe" : "javap");
        if (!Files.isRegularFile(executable))
            return message("unavailable", "本次 Java 运行环境未提供 javap。");
        var argv =
                new java.util.ArrayList<>(
                        java.util.List.of(executable.toString(), "-J-Dfile.encoding=UTF-8", "-p"));
        if (!method.equals("-")) argv.add("-c");
        // A class-file URL confines javap to the exact archive entry, independent of classpath
        // lookup.
        argv.add("jar:" + jar.toUri().toASCIIString() + "!/" + type.replace('.', '/') + ".class");
        ProcessBuilder builder = new ProcessBuilder(argv);
        String systemRoot = builder.environment().get("SystemRoot");
        builder.environment().clear();
        if (windows && systemRoot != null) builder.environment().put("SystemRoot", systemRoot);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process = builder.start();
        try {
            process.getOutputStream().close();
            CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS)
                    .execute(
                            () -> {
                                if (process.isAlive()) process.destroyForcibly();
                            });
            byte[] bytes = process.getInputStream().readNBytes(MAX_OUTPUT + 1);
            if (bytes.length > MAX_OUTPUT
                    || !process.waitFor(5, TimeUnit.SECONDS)
                    || process.exitValue() != 0)
                return message("unavailable", "javap 未能在限制内完成此次读取。");
            JsonArray lines = new JsonArray();
            int seen = 0;
            Pattern signature = Pattern.compile("(?:^|\\s)" + Pattern.quote(method) + "\\(");
            boolean selected = method.equals("-");
            for (String line : new String(bytes, StandardCharsets.UTF_8).split("\\R")) {
                if (!method.equals("-") && line.matches("^  \\S.*")) {
                    selected = signature.matcher(line).find();
                }
                if (!selected || line.isBlank()) continue;
                if (seen++ < (page - 1) * PAGE_LINES) continue;
                if (lines.size() == PAGE_LINES) break;
                lines.add(line.length() > 240 ? line.substring(0, 240) + "…" : line);
            }
            if (lines.isEmpty()) {
                return message(
                        "not_found",
                        seen == 0 ? "这个类没有声明所查方法。可查看它的方法列表和父类，再按声明位置读取。" : "本方法没有这一页输出。");
            }
            JsonObject result = message("installed_code", "当前安装包的 javap 输出。包内实现需结合本服运行状态判断。");
            result.addProperty("class", type);
            result.addProperty("method", method);
            result.add("lines", lines);
            result.addProperty("more", seen > page * PAGE_LINES);
            return result;
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static CompletableFuture<JsonObject> invalid() {
        return done(
                "invalid_request",
                "用法：recipe-json <配方ID>；classes <modid> <关键词> [页]；javap <modid> <类全名> [方法名或 -] [页]。");
    }

    private static CompletableFuture<JsonObject> done(String status, String text) {
        return CompletableFuture.completedFuture(message(status, text));
    }

    private static JsonObject message(String status, String text) {
        return WenzhouReadTools.message(status, text);
    }

    private WenzhouCommands() {}
}
