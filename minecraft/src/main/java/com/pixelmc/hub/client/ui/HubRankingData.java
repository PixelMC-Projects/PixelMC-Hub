/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client.ui;

import com.google.gson.*;
import java.util.*;

/** Validated immutable public DTOs, shared by the native list, podium and self position. */
final class HubRankingData {
    static final List<String> BOARDS = List.of("food", "fortunes", "online");
    private static final long MAX_SAFE = 9_007_199_254_740_991L;
    private static final Set<String> FIELDS = Set.of("schemaVersion", "board", "status", "supported", "period", "ranking",
            "scope", "unit", "rows", "total", "page", "size", "updatedAt", "revision", "comparisonRevision", "viewer", "self");
    record Row(String playerId, String name, Long rank, Long value, Long delta) {}
    record Snapshot(String board, String status, String scope, String revision, String comparison,
                    String viewerId, String viewerName, List<Row> rows, Row self, long total, int page, int size) {
        boolean confirmed() { return status.equals("ready") || status.equals("empty"); }
    }
    private HubRankingData() {}
    private static void require(boolean condition) { if (!condition) throw new IllegalArgumentException("排行数据格式无效"); }
    private static void fields(JsonObject object, Set<String> allowed) { require(allowed.containsAll(object.keySet())); }
    private static String string(JsonObject object, String key, int maximum, boolean empty) {
        JsonElement value = object.get(key);
        require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString());
        String result = value.getAsString();
        require(result.length() <= maximum && (empty || !result.isBlank()) && result.indexOf(0) < 0);
        return result;
    }
    private static long number(JsonObject object, String key, long minimum, long maximum) {
        JsonElement value = object.get(key);
        require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber());
        long result = value.getAsBigDecimal().longValueExact();
        require(result >= minimum && result <= maximum);
        return result;
    }
    private static Long optionalNumber(JsonObject object, String key, long minimum) {
        return !object.has(key) || object.get(key).isJsonNull() ? null : number(object, key, minimum, MAX_SAFE);
    }
    private static String optionalString(JsonObject object, String key) {
        return !object.has(key) || object.get(key).isJsonNull() ? "" : string(object, key, 160, false);
    }
    private static String uuid(JsonObject object, String key) {
        String value = string(object, key, 36, false);
        require(UUID.fromString(value).toString().equals(value));
        return value;
    }
    private static Row row(JsonObject object, boolean nullable, boolean comparison) {
        fields(object, Set.of("playerId", "name", "rank", "value", "rankDelta", "avatar"));
        String id = uuid(object, "playerId"), name = string(object, "name", 128, false);
        Long rank = optionalNumber(object, "rank", 1), value = optionalNumber(object, "value", 0);
        require(nullable || rank != null && value != null);
        Long delta = optionalNumber(object, "rankDelta", -MAX_SAFE);
        if (delta != null) require(comparison && rank != null && Math.addExact(rank, delta) >= 1 && rank + delta <= MAX_SAFE);
        if (object.has("avatar")) {
            JsonObject avatar = object.getAsJsonObject("avatar"); fields(avatar, Set.of("url", "version"));
            String url = string(avatar, "url", 2048, false); string(avatar, "version", 128, true);
            require(url.startsWith("/") && !url.startsWith("//") && !url.contains("\\") && !url.matches(".*[\\x00-\\x20\\x7f].*"));
            String lower = url.toLowerCase(Locale.ROOT);
            require(!lower.matches(".*%(5c|0[0-9a-f]|1[0-9a-f]|7f).*"));
        }
        return new Row(id, name, rank, value, delta);
    }
    static Snapshot parse(JsonObject object) {
        fields(object, FIELDS); require(number(object, "schemaVersion", 1, 1) == 1);
        String board = string(object, "board", 16, false), status = string(object, "status", 16, false);
        require(BOARDS.contains(board));
        require(Set.of("ready", "loading", "empty", "unsupported", "unavailable", "error").contains(status));
        require(object.getAsJsonPrimitive("supported").isBoolean() && object.get("supported").getAsBoolean() == !status.equals("unsupported"));
        require(string(object, "period", 16, false).equals("all") && string(object, "ranking", 20, false).equals("competition"));
        String unit = board.equals("food") ? "foods" : board.equals("fortunes") ? "fortunes" : "ms";
        require(string(object, "unit", 16, false).equals(unit));
        JsonObject scope = object.getAsJsonObject("scope"); fields(scope, Set.of("kind", "id", "label"));
        require(string(scope, "kind", 16, false).equals(board.equals("fortunes") ? "global" : "archive"));
        String scopeId = string(scope, "id", 256, false); string(scope, "label", 256, true);
        String revision = optionalString(object, "revision"), comparison = optionalString(object, "comparisonRevision");
        require(comparison.isEmpty() || !revision.isEmpty() && !comparison.equals(revision));
        JsonObject viewer = object.getAsJsonObject("viewer"); fields(viewer, Set.of("playerId", "name"));
        String viewerId = uuid(viewer, "playerId"), viewerName = string(viewer, "name", 128, true);
        int page = (int)number(object, "page", 0, 100000), size = (int)number(object, "size", 1, 100);
        Long total = optionalNumber(object, "total", 0), at = optionalNumber(object, "updatedAt", 0);
        JsonArray entries = object.getAsJsonArray("rows"); require(entries.size() <= size);
        List<Row> rows = new ArrayList<>(); Set<String> ids = new HashSet<>();
        for (JsonElement entry : entries) {
            Row item = row(entry.getAsJsonObject(), false, !comparison.isEmpty()); require(ids.add(item.playerId));
            if (rows.isEmpty()) require(item.rank <= (long)page * size + 1);
            else {
                Row previous = rows.get(rows.size() - 1);
                if (previous.value.equals(item.value)) require(previous.rank.equals(item.rank) && previous.playerId.compareTo(item.playerId) < 0);
                else require(previous.value > item.value && item.rank == (long)page * size + rows.size() + 1);
            }
            rows.add(item);
        }
        Row self = object.get("self").isJsonNull() ? null : row(object.getAsJsonObject("self"), true, !comparison.isEmpty());
        require(self == null || self.playerId.equals(viewerId));
        for (Row item : rows) if (item.playerId.equals(viewerId)) require(item.equals(self));
        if (status.equals("ready")) require(total != null && total > 0 && at != null && rows.size() <= Math.max(0, total - (long)page * size));
        else require(rows.isEmpty());
        if (status.equals("empty")) require(total != null && total == 0 && at != null);
        else if (!status.equals("ready")) require(total == null && at == null && self == null && comparison.isEmpty());
        return new Snapshot(board, status, scopeId, revision, comparison, viewerId, viewerName, List.copyOf(rows), self,
                total == null ? -1 : total, page, size);
    }
}
