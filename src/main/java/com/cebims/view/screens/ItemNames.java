package com.cebims.view.screens;

import com.cebims.api.ApiClient;
import javafx.application.Platform;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Claims only carry an item id, so look an item's name up once and remember it (quietly: it may no longer be listed). */
final class ItemNames {
    private static final Map<String, String> names = new HashMap<>();
    private static final Set<String> requested = new HashSet<>();

    private ItemNames() {}

    static String get(String itemId) {
        return names.getOrDefault(itemId, "...");
    }

    static void resolve(String itemId, Runnable onLoaded) {
        if (itemId == null || itemId.isEmpty() || !requested.add(itemId)) return;
        ApiClient.get("/v1/items/" + itemId).whenComplete((r, ex) -> Platform.runLater(() -> {
            names.put(itemId, ex == null && r.ok() ? r.json().path("name").asText("(item)") : "(no longer listed)");
            onLoaded.run();
        }));
    }
}
