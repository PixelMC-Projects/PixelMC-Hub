package com.pixelmc.hub.wenzhou;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Flash prices in nano-yuan per token, shared with the two Web previews. */
final class WenzhouBilling {
    private static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private static final JsonObject TARIFF = loadTariff();

    private static JsonObject loadTariff() {
        String path = "/assets/pixelmc_hub/wenzhou/flash-pricing.json";
        try (InputStream source = WenzhouBilling.class.getResourceAsStream(path)) {
            if (source == null) throw new IllegalStateException("Missing Flash tariff");
            return JsonParser.parseReader(new InputStreamReader(source, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read Flash tariff", e);
        }
    }

    static long cost(long time, long hits, long misses, long output) {
        ZonedDateTime local = Instant.ofEpochMilli(time).atZone(BEIJING);
        String period = "offPeak";
        if (local.getDayOfWeek() != DayOfWeek.SATURDAY
                && local.getDayOfWeek() != DayOfWeek.SUNDAY
                && isPeakHour(local.getHour())) {
            JsonArray holidays =
                    TARIFF.getAsJsonObject("holidays")
                            .getAsJsonArray(String.valueOf(local.getYear()));
            if (holidays == null) return -1;
            String monthDay = local.toLocalDate().toString().substring(5);
            boolean holiday = false;
            for (JsonElement entry : holidays) {
                JsonArray range = entry.getAsJsonArray();
                if (monthDay.compareTo(range.get(0).getAsString()) >= 0
                        && monthDay.compareTo(range.get(1).getAsString()) <= 0) {
                    holiday = true;
                    break;
                }
            }
            if (!holiday) period = "peak";
        }
        JsonObject rate = TARIFF.getAsJsonObject("ratesNanoCnyPerToken").getAsJsonObject(period);
        return hits * rate.get("hit").getAsLong()
                + misses * rate.get("miss").getAsLong()
                + output * rate.get("output").getAsLong();
    }

    private static boolean isPeakHour(int hour) {
        for (JsonElement entry : TARIFF.getAsJsonArray("peakHoursBeijing")) {
            JsonArray range = entry.getAsJsonArray();
            if (hour >= range.get(0).getAsInt() && hour < range.get(1).getAsInt()) return true;
        }
        return false;
    }

    private WenzhouBilling() {}
}
