/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

/** Explicit player-community fields. Administrator operations never enter this channel. */
public final class HubCommunityProtocol {
    public static final Set<String> ACTIONS = Set.of("snapshot", "profile", "profileOptions", "memories", "memory",
        "memoryOptions", "saveProfile", "saveNote", "appendSupplement", "deleteSupplement", "saveCollections");
    private static final Set<String> WRITES = Set.of("saveProfile", "saveNote", "appendSupplement", "deleteSupplement", "saveCollections");
    private static final Set<String> COLORS = Set.of("mist", "lilac", "sage", "sand", "blush", "apricot", "tide", "twilight", "frost", "stone");
    private static final Set<String> PATTERNS = Set.of("classic", "doodle", "tech", "architecture", "nature", "cosmos");

    private HubCommunityProtocol() {}

    public static boolean write(String action) { return WRITES.contains(action); }

    public static void validate(String action, JsonObject value) {
        Set<String> fields = switch (action) {
            case "snapshot" -> Set.of();
            case "profile" -> Set.of("playerId");
            case "profileOptions" -> Set.of("kind", "page");
            case "memories" -> Set.of("playerId", "page");
            case "memory" -> Set.of("playerId", "archiveId", "page");
            case "memoryOptions" -> Set.of("archiveId", "page");
            case "saveProfile" -> Set.of("patch", "expectedRevision");
            case "saveNote" -> Set.of("archiveId", "text", "expectedRevision");
            case "appendSupplement" -> Set.of("archiveId", "text", "operationId");
            case "deleteSupplement" -> Set.of("archiveId", "supplementId", "expectedRevision");
            case "saveCollections" -> Set.of("archiveId", "selection", "expectedRevision");
            default -> throw invalid();
        };
        if (!fields.containsAll(value.keySet())) throw invalid();
        if (value.has("playerId")) uuid(text(value, "playerId", 36, false));
        if (Set.of("memory", "memoryOptions", "saveNote", "appendSupplement", "deleteSupplement", "saveCollections").contains(action))
            text(value, "archiveId", 160, false);
        if (value.has("page")) integer(value.get("page"), 0, 100000);
        if (action.equals("profileOptions") && !Set.of("titles", "fortunes", "memories").contains(text(value, "kind", 16, false))) throw invalid();
        if (WRITES.contains(action) && !action.equals("appendSupplement")) text(value, "expectedRevision", 256, false);
        if (action.equals("saveNote")) text(value, "text", 1200, false);
        if (action.equals("appendSupplement")) {
            text(value, "text", 600, false);
            uuid(text(value, "operationId", 36, false));
        }
        if (action.equals("deleteSupplement")) text(value, "supplementId", 80, false);
        if (action.equals("saveProfile")) {
            JsonObject patch = object(value, "patch");
            if (!Set.of("bio", "background", "profilePattern", "featuredTitleId", "featuredFortuneDate", "featuredMemoryId").containsAll(patch.keySet())) throw invalid();
            if (patch.has("bio")) text(patch, "bio", 120, true);
            if (patch.has("background") && !COLORS.contains(text(patch, "background", 24, false))) throw invalid();
            if (patch.has("profilePattern") && !PATTERNS.contains(text(patch, "profilePattern", 24, false))) throw invalid();
            for (String key : Set.of("featuredTitleId", "featuredMemoryId")) nullableText(patch, key, 160);
            nullableText(patch, "featuredFortuneDate", 10);
            if (patch.has("featuredFortuneDate") && !patch.get("featuredFortuneDate").isJsonNull()) {
                String date = patch.get("featuredFortuneDate").getAsString();
                if (!LocalDate.parse(date).toString().equals(date)) throw invalid();
            }
        }
        if (action.equals("saveCollections")) {
            JsonObject selection = object(value, "selection");
            if (!selection.keySet().equals(Set.of("titleEventId", "fortuneEventId"))) throw invalid();
            nullableText(selection, "titleEventId", 160);
            nullableText(selection, "fortuneEventId", 160);
        }
    }

    /** A bounded stream identity reuses the existing session delta transport per query. */
    public static String stream(String action, JsonObject query) {
        JsonObject canonical = new JsonObject();
        query.keySet().stream().filter(key -> !key.equals("_hubViewRequestId")).sorted().forEach(key -> canonical.add(key, query.get(key)));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return "community/" + action + "/" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static JsonObject object(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonObject()) throw invalid();
        return parent.getAsJsonObject(key);
    }
    private static String text(JsonObject parent, String key, int limit, boolean empty) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw invalid();
        String text = value.getAsString();
        if (text.length() > limit || text.indexOf(0) >= 0 || !empty && text.isBlank()) throw invalid();
        return text;
    }
    private static void nullableText(JsonObject parent, String key, int limit) {
        if (parent.has(key) && !parent.get(key).isJsonNull()) text(parent, key, limit, false);
    }
    private static void uuid(String value) {
        if (!UUID.fromString(value).toString().equalsIgnoreCase(value)) throw invalid();
    }
    private static void integer(JsonElement value, int min, int max) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw invalid();
        int number = value.getAsBigDecimal().intValueExact();
        if (number < min || number > max) throw invalid();
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("社区请求参数无效"); }
}
