package com.cebims.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Equipment categories and drop-off locations, loaded after login and refreshed when Admin edits them. */
public final class Lookups {
    public record Option(String id, String name) {
        @Override public String toString() { return name; }
    }

    private static List<Option> categories = new ArrayList<>();
    private static List<Option> dropLocations = new ArrayList<>();
    private static final Map<String, String> categoryNames = new HashMap<>();
    private static final Map<String, String> dropNames = new HashMap<>();

    private Lookups() {}

    public static List<Option> categories()      { return categories; }
    public static List<Option> dropLocations()   { return dropLocations; }
    public static String categoryName(String id) { return categoryNames.getOrDefault(id, id); }
    public static String dropName(String id)     { return dropNames.getOrDefault(id, id); }

    public static CompletableFuture<Void> load() {
        CompletableFuture<Void> c = ApiClient.get("/v1/categories").thenAccept(r -> {
            if (r.ok()) categories = parse(r.json(), categoryNames);
        });
        CompletableFuture<Void> d = ApiClient.get("/v1/drop-locations").thenAccept(r -> {
            if (r.ok()) dropLocations = parse(r.json(), dropNames);
        });
        return CompletableFuture.allOf(c, d).exceptionally(e -> null);
    }

    private static List<Option> parse(JsonNode root, Map<String, String> names) {
        List<Option> out = new ArrayList<>();
        names.clear();
        if (root == null) return out;
        JsonNode array = root.isArray() ? root : root.path("content");
        for (JsonNode n : array) {
            String id = n.path("id").asText("");
            String name = n.path("name").asText("");
            if (!id.isEmpty() && !name.isEmpty()) {
                Option o = new Option(id, name);
                out.add(o);
                names.put(o.id(), o.name());
            }
        }
        return out;
    }
}