/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Resolves the latest public notices from the pin-first pagination of an older Manager. */
final class LegacyStationNotices {
    private static final int PAGE_SIZE = 12, MAX_PAGE = 100_000;
    private static final List<String> SUMMARY_FIELDS =
            List.of(
                    "id",
                    "packId",
                    "packName",
                    "packVersion",
                    "updateVersion",
                    "title",
                    "summary",
                    "category",
                    "publishedAt");
    private static final Comparator<JsonObject> LATEST =
            Comparator.comparingLong((JsonObject row) -> row.get("publishedAt").getAsLong())
                    .reversed()
                    .thenComparing(row -> row.get("id").getAsString());

    private final String packId, revision;
    private final int total, last;
    private final List<JsonObject> pinned = new ArrayList<>(2), unpinned = new ArrayList<>(2);
    private int low, high, next = -1;
    private boolean tail, unavailable;

    private LegacyStationNotices(String packId, String revision, int total) {
        this.packId = packId;
        this.revision = revision;
        this.total = total;
        last = total == 0 ? 0 : (total - 1) / PAGE_SIZE;
        high = last + 1;
    }

    static LegacyStationNotices begin(JsonObject snapshot) {
        if (snapshot.has("stationNotices")) return null;
        try {
            String packId = text(snapshot, "selectedPackId");
            if (packId.isEmpty()
                    || !snapshot.getAsJsonObject("settings").get("updatesEnabled").getAsBoolean())
                return null;
            if (!UUID.fromString(packId).toString().equals(packId)) return null;
            boolean current = false;
            for (var value : snapshot.getAsJsonArray("packs")) {
                JsonObject pack = value.getAsJsonObject();
                if (packId.equals(text(pack, "id"))
                        && pack.get("current").getAsBoolean()
                        && (!pack.has("visible") || pack.get("visible").getAsBoolean())) {
                    current = true;
                    break;
                }
            }
            if (!current) return null;
            JsonObject page = snapshot.getAsJsonObject("list");
            int total = page.get("total").getAsBigDecimal().intValueExact();
            if (total < 0) return null;
            LegacyStationNotices scan =
                    new LegacyStationNotices(packId, text(snapshot, "revision"), total);
            try {
                scan.first(page);
            } catch (RuntimeException invalid) {
                scan.fail();
            }
            return scan;
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static String text(JsonObject value, String key) {
        var field = value.get(key);
        return field != null && field.isJsonPrimitive() && field.getAsJsonPrimitive().isString()
                ? field.getAsString()
                : "";
    }

    private record Page(List<JsonObject> pinned, List<JsonObject> unpinned) {}

    private Page page(JsonObject value, int index) {
        if (value.get("page").getAsBigDecimal().intValueExact() != index
                || value.get("size").getAsBigDecimal().intValueExact() != PAGE_SIZE
                || value.get("total").getAsBigDecimal().intValueExact() != total)
            throw new IllegalArgumentException("Inconsistent station notice page");
        int expected = Math.min(PAGE_SIZE, total - index * PAGE_SIZE);
        JsonArray rows = value.getAsJsonArray("rows");
        if (rows.size() != expected)
            throw new IllegalArgumentException("Incomplete station notice page");
        List<JsonObject> pins = new ArrayList<>(2), others = new ArrayList<>(2);
        Set<String> ids = new HashSet<>();
        JsonObject previous = null;
        boolean sawUnpinned = false;
        for (var element : rows) {
            JsonObject row = element.getAsJsonObject();
            String id = text(row, "id");
            if (!packId.equals(text(row, "packId"))
                    || !UUID.fromString(id).toString().equals(id)
                    || !ids.add(id)
                    || row.get("publishedAt").getAsBigDecimal().longValueExact() <= 0
                    || row.has("status") && !text(row, "status").equals("published")
                    || !row.getAsJsonPrimitive("pinned").isBoolean())
                throw new IllegalArgumentException("Unexpected station notice row");
            boolean pin = row.get("pinned").getAsBoolean();
            if (pin && sawUnpinned
                    || previous != null
                            && (pin || sawUnpinned)
                            && LATEST.compare(previous, row) > 0)
                throw new IllegalArgumentException("Unexpected station notice ordering");
            sawUnpinned |= !pin;
            previous = row;
            List<JsonObject> group = pin ? pins : others;
            if (group.size() < 2) {
                JsonObject summary = new JsonObject();
                for (String key : SUMMARY_FIELDS)
                    if (row.has(key) && row.get(key).isJsonPrimitive())
                        summary.add(key, row.get(key).deepCopy());
                group.add(summary);
            }
        }
        return new Page(pins, others);
    }

    private void first(JsonObject value) {
        Page page = page(value, 0);
        pinned.addAll(page.pinned);
        unpinned.addAll(page.unpinned);
        if (last == 0 || unpinned.size() >= 2) return;
        if (revision.isEmpty()) {
            fail();
            return;
        }
        if (!unpinned.isEmpty()) {
            high = 0;
            boundaryFound(0);
        } else {
            // A single adjacent read resolves the common case of roughly one page of pins.
            next = 1;
        }
    }

    private void boundaryFound(int boundary) {
        if (unpinned.size() >= 2
                || boundary == last
                || pinned.size() >= 2 && LATEST.compare(pinned.get(1), unpinned.get(0)) <= 0) {
            next = -1;
            return;
        }
        tail = true;
        schedule(boundary + 1);
    }

    private void schedule(int page) {
        if (page > MAX_PAGE) fail();
        else next = page;
    }

    JsonObject query() {
        JsonObject query = new JsonObject();
        query.addProperty("packId", packId);
        query.addProperty("page", next);
        query.addProperty("search", "");
        return query;
    }

    boolean pending() {
        return next >= 0;
    }

    boolean complete() {
        return !pending() && !unavailable;
    }

    boolean sameRevision(LegacyStationNotices other) {
        return !revision.isEmpty()
                && revision.equals(other.revision)
                && packId.equals(other.packId)
                && total == other.total;
    }

    void accept(JsonObject result) {
        if (!pending()) return;
        if (!revision.equals(text(result, "revision"))) {
            fail();
            return;
        }
        try {
            Page page = page(result, next);
            if (tail) {
                if (!page.pinned.isEmpty()
                        || page.unpinned.isEmpty()
                        || LATEST.compare(unpinned.get(0), page.unpinned.get(0)) >= 0)
                    throw new IllegalArgumentException("Unexpected station notice boundary");
                unpinned.add(page.unpinned.get(0));
                next = -1;
                return;
            }
            if (page.unpinned.isEmpty()) {
                low = next;
            } else {
                high = next;
                unpinned.clear();
                unpinned.addAll(page.unpinned);
            }
            if (low + 1 < high) schedule(Math.min(MAX_PAGE, (low + high) / 2));
            else if (high > last) next = -1;
            else boundaryFound(high);
            if (pending() && next == low) fail();
        } catch (RuntimeException invalid) {
            fail();
        }
    }

    void fail() {
        next = -1;
        unavailable = true;
    }

    JsonObject viewResult() {
        JsonObject out = new JsonObject();
        out.addProperty("packId", packId);
        out.addProperty("revision", revision);
        out.addProperty("state", unavailable ? "unavailable" : pending() ? "loading" : "ready");
        JsonArray rows = new JsonArray();
        if (complete()) {
            List<JsonObject> candidates = new ArrayList<>(pinned);
            candidates.addAll(unpinned);
            candidates.stream()
                    .sorted(LATEST)
                    .limit(2)
                    .map(JsonObject::deepCopy)
                    .forEach(rows::add);
        }
        out.add("rows", rows);
        return out;
    }
}
