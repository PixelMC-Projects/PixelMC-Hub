/* SPDX-License-Identifier: Apache-2.0 */
package com.pixelmc.hub.network;

import com.google.gson.JsonObject;
import java.util.*;

/** Bounded client scheduling and freshness, independent of Minecraft for protocol checks. */
public final class HubRequestQueue {
    public record Ticket(
            long id,
            String module,
            String action,
            JsonObject payload,
            long epoch,
            long at,
            boolean write,
            boolean stationList) {}

    private final Map<Long, Ticket> active = new LinkedHashMap<>();
    private final Deque<Ticket> queued = new ArrayDeque<>();
    private final Map<String, Long> epochs = new HashMap<>(),
            latest = new HashMap<>(),
            writes = new HashMap<>();
    private long nextId;

    public Set<Long> pendingIds() {
        Set<Long> ids = new HashSet<>(active.keySet());
        queued.forEach(t -> ids.add(t.id));
        return ids;
    }

    public boolean awaiting(long id, String module, String action) {
        Ticket t = active.get(id);
        return t != null && t.module.equals(module) && t.action.equals(action) && current(t);
    }

    public void clear(String module) {
        active.values().removeIf(t -> t.module.equals(module));
        queued.removeIf(t -> t.module.equals(module));
        writes.remove(module);
        epochs.merge(module, 1L, Long::sum);
        latest.keySet().removeIf(k -> k.startsWith(module + ":") || k.startsWith(module + "/"));
    }

    public boolean writing(String module) {
        return writes.containsKey(module);
    }

    public boolean reading(String module) {
        return java.util.stream.Stream.concat(active.values().stream(), queued.stream())
                .anyMatch(t -> !t.write && t.module.equals(module) && current(t));
    }

    public void invalidateReads(String module) {
        if (writing(module)) throw new IllegalStateException("正在等待服务器确认");
        epochs.merge(module, 1L, Long::sum);
        queued.removeIf(t -> !t.write && t.module.equals(module) && !receipt(t));
        latest.keySet().removeIf(k -> k.startsWith(module + ":"));
    }

    public void offer(String module, String action, JsonObject payload, long now) {
        offer(module, action, payload, now, false);
    }

    public boolean readingStationNotices() {
        return latest.containsKey("updates:stationNotices");
    }

    public boolean stationNoticeResponse(long id, String module, String action) {
        Ticket ticket = active.get(id);
        return ticket != null
                && ticket.stationList
                && ticket.module.equals(module)
                && ticket.action.equals(action);
    }

    public void cancelStationNotices() {
        queued.removeIf(Ticket::stationList);
        latest.remove("updates:stationNotices");
    }

    public void offerStationNotices(JsonObject payload, long now) {
        offer("updates", "list", payload, now, true);
    }

    private void offer(
            String module, String action, JsonObject payload, long now, boolean stationList) {
        boolean write =
                module.equals("community")
                        ? HubCommunityProtocol.write(action)
                        : Set.of(
                                        "equip",
                                        "visibility",
                                        "displayPreferences",
                                        "feature",
                                        "like",
                                        "comment",
                                        "deleteComment")
                                .contains(
                                        module.equals("updates")
                                                ? HubProtocol.updatesPlain(action)
                                                : action);
        if (write && writing(module)) throw new IllegalStateException("正在等待服务器确认");
        String key = key(module, action, payload, stationList);
        if (!write
                && java.util.stream.Stream.concat(active.values().stream(), queued.stream())
                        .anyMatch(
                                t ->
                                        current(t)
                                                && t.module.equals(module)
                                                && t.action.equals(action)
                                                && t.stationList == stationList
                                                && t.payload.equals(payload))) return;
        queued.removeIf(t -> !t.write && key(t).equals(key));
        if (queued.size() >= 16
                && (!write || queued.stream().noneMatch(t -> t.module.equals(module) && !t.write)))
            throw new IllegalStateException("请求较多，请稍后重试");
        if (write) {
            epochs.merge(module, 1L, Long::sum);
            queued.removeIf(t -> t.module.equals(module) && !t.write && !receipt(t));
            latest.keySet().removeIf(k -> k.startsWith(module + ":"));
        }
        Ticket ticket =
                new Ticket(
                        ++nextId,
                        module,
                        action,
                        payload.deepCopy(),
                        epochs.getOrDefault(module, 0L),
                        now,
                        write,
                        stationList);
        latest.put(key, ticket.id);
        if (write) {
            writes.put(module, ticket.id);
            queued.addFirst(ticket);
        } else queued.addLast(ticket);
    }

    public List<Ticket> ready(long now) {
        return ready(now, 4);
    }

    public List<Ticket> ready(long now, int maximum) {
        List<Ticket> result = new ArrayList<>();
        while (active.size() < 4 && result.size() < maximum && !queued.isEmpty()) {
            Ticket ticket = queued.removeFirst();
            if (!current(ticket)) continue;
            Ticket sent =
                    new Ticket(
                            ticket.id,
                            ticket.module,
                            ticket.action,
                            ticket.payload,
                            ticket.epoch,
                            now,
                            ticket.write,
                            ticket.stationList);
            active.put(sent.id, sent);
            result.add(sent);
        }
        return result;
    }

    private static boolean receipt(Ticket t) {
        return HubProtocol.isUpdateReceipt(t.module, t.action);
    }

    private static String key(Ticket ticket) {
        return key(ticket.module, ticket.action, ticket.payload, ticket.stationList);
    }

    private static String key(
            String module, String action, JsonObject payload, boolean stationList) {
        if (stationList) return "updates:stationNotices";
        if (HubProtocol.isUpdateReceipt(module, action))
            return module + "/" + action + "/" + payload.get("id");
        return module.equals("community") && !HubCommunityProtocol.write(action)
                ? module + ":" + HubCommunityProtocol.stream(action, payload)
                : module + ":" + action;
    }

    private boolean current(Ticket t) {
        return (receipt(t) || t.epoch == epochs.getOrDefault(t.module, 0L))
                && latest.getOrDefault(key(t), 0L) == t.id;
    }

    private void forget(Ticket ticket) {
        latest.remove(key(ticket), ticket.id);
    }

    public Ticket complete(long id, String module, String action) {
        Ticket ticket = active.get(id);
        if (ticket == null) return null;
        if (!ticket.module.equals(module) || !ticket.action.equals(action)) return null;
        active.remove(id);
        if (!current(ticket)) return null;
        if (ticket.write) writes.remove(module, ticket.id);
        forget(ticket);
        return ticket;
    }

    public List<Ticket> expire(long now) {
        List<Ticket> result = new ArrayList<>();
        active.values()
                .removeIf(
                        t -> {
                            if (now - t.at < 10_000_000_000L) return false;
                            if (current(t)) {
                                if (t.write) writes.remove(t.module, t.id);
                                result.add(t);
                            }
                            forget(t);
                            return true;
                        });
        return result;
    }
}
