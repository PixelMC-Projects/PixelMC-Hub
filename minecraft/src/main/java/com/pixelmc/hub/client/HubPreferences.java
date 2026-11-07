package com.pixelmc.hub.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;

/** Local presentation preferences only; no gameplay or remote credentials. */
final class HubPreferences {
    private final Path path;
    private final JsonObject scopes = new JsonObject();
    private JsonObject legacyDemo;
    private String scope="";
    private String fallbackScope="";
    private boolean seen,compact;
    private String welcomeMode = "login";
    private boolean reducedMotion,systemReducedMotion,dark;

    HubPreferences(Path path) {
        this.path = path;
        if (!Files.isRegularFile(path)) return;
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject data = JsonParser.parseReader(reader).getAsJsonObject();
            if(data.has("legacyDemo")&&data.get("legacyDemo").isJsonObject())legacyDemo=data.getAsJsonObject("legacyDemo");
            else if(data.has("welcomeMode"))legacyDemo=data.deepCopy();
            if(data.has("scopes")&&data.get("scopes").isJsonObject())
                for(var entry:data.getAsJsonObject("scopes").entrySet())if(entry.getKey().matches("[a-f0-9]{64}")&&entry.getValue().isJsonObject())scopes.add(entry.getKey(),entry.getValue());
        } catch (Exception exception) {
            LogUtils.getLogger().warn("PixelMC-Hub preferences could not be read; using safe local defaults", exception);
        }
    }

    void select(String value) {
        scope=value==null?"":value;fallbackScope="";welcomeMode="login";reducedMotion=false;dark=false;seen=false;compact=false;
        if(scopes.has(scope)) {
            JsonObject entry=scopes.getAsJsonObject(scope);
            if(entry.has("welcomeMode"))setWelcomeMode(entry.get("welcomeMode").getAsString());
            reducedMotion=entry.has("reducedMotion")&&entry.get("reducedMotion").getAsBoolean();
            dark=entry.has("theme")&&entry.get("theme").isJsonPrimitive()&&"dark".equals(entry.get("theme").getAsString());
            seen=entry.has("seen")&&entry.get("seen").getAsBoolean();compact=entry.has("compact")&&entry.get("compact").getAsBoolean();
        }
    }
    void migrateLegacyDemo(String scope) {
        if(legacyDemo==null||scopes.has(scope))return;
        JsonObject entry=new JsonObject();entry.addProperty("welcomeMode",legacyDemo.has("welcomeMode")?legacyDemo.get("welcomeMode").getAsString():"login");
        entry.addProperty("reducedMotion",legacyDemo.has("reducedMotion")&&legacyDemo.get("reducedMotion").getAsBoolean());
        boolean seen=false;if(legacyDemo.has("seenScopes"))for(var value:legacyDemo.getAsJsonArray("seenScopes"))if(value.getAsString().equals(scope))seen=true;
        entry.addProperty("seen",seen);scopes.add(scope,entry);
    }
    void copyScope(String from,String to) {
        if(!scopes.has(to)&&scopes.has(from)) {
            JsonObject source=scopes.getAsJsonObject(from);
            if(source.has("canonicalOrigin")&&!source.get("canonicalOrigin").getAsString().equals(to))return;
            JsonObject copy=source.deepCopy();copy.remove("canonicalOrigin");scopes.add(to,copy);
        }
    }
    void fallbackScope(String value){fallbackScope=value==null?"":value;}
    boolean dark(){return dark;}
    void setDark(boolean value){dark=value;}
    boolean compact(){return compact;}
    void setCompact(boolean value){compact=value;}
    String welcomeMode() { return welcomeMode; }
    boolean reducedMotion() { return reducedMotion; }
    void setWelcomeMode(String mode) { welcomeMode = Set.of("once", "login", "always").contains(mode) ? mode : "login"; }
    void setReducedMotion(boolean value) { reducedMotion = value; }
    void setSystemReducedMotion(boolean value) { systemReducedMotion = value; }
    boolean shouldPlay(String scope, boolean shownThisLogin) {
        if (reducedMotion||systemReducedMotion) return false;
        return switch (welcomeMode) {
            case "once" -> !seen;
            case "login" -> !shownThisLogin;
            default -> true;
        };
    }
    void markSeen(String scope) { if(this.scope.equals(scope)&&!scope.isEmpty()&&!seen){seen=true;save();} }

    void save() {
        if(scope.isEmpty())return;
        JsonObject entry=new JsonObject();entry.addProperty("welcomeMode",welcomeMode);entry.addProperty("reducedMotion",reducedMotion);
        entry.addProperty("seen",seen);entry.addProperty("compact",compact);entry.addProperty("theme",dark?"dark":"light");
        if(scopes.has(scope)&&scopes.getAsJsonObject(scope).has("canonicalOrigin"))entry.add("canonicalOrigin",scopes.getAsJsonObject(scope).get("canonicalOrigin"));
        scopes.add(scope,entry);
        // The next login can honor once/login before asynchronous metadata arrives.
        // Origin prevents this connection hint from seeding a different canonical archive.
        if(!fallbackScope.isEmpty()&&!fallbackScope.equals(scope)){JsonObject hint=entry.deepCopy();hint.addProperty("canonicalOrigin",scope);scopes.add(fallbackScope,hint);}
        JsonObject data = new JsonObject();data.addProperty("version",2);data.add("scopes",scopes);
        if(legacyDemo!=null)data.add("legacyDemo",legacyDemo);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(data), StandardCharsets.UTF_8);
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception exception) {
            LogUtils.getLogger().warn("PixelMC-Hub could not save local display preferences", exception);
        }
    }
}
