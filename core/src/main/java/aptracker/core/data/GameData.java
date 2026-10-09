package aptracker.core.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The static tables of the Minecraft apworld: items, locations, regions and exclusion lists.
 * They are loaded from the apworld's own {@code data/*.json} files, copied unchanged into
 * {@code aptracker/data/}, so that a new apworld version is picked up by replacing those files.
 */
public final class GameData {

    /** A region and the names of the exits that leave it. */
    public record Region(String name, List<String> exits) {
    }

    private static final String RESOURCE_DIR = "/aptracker/data/";

    private final List<String> itemNames;
    private final Map<String, Integer> itemIds;
    private final Set<String> progressionItems;
    private final Map<String, Integer> requiredPool;

    private final List<String> locationNames;
    private final Map<String, Integer> locationIds;
    private final Map<String, String> locationRegions;

    private final List<Region> regions;
    private final Map<String, String> mandatoryConnections;
    private final Map<String, String> defaultConnections;
    private final Map<String, List<String>> illegalConnections;

    private final Set<String> hardLocations;
    private final Set<String> unreasonableLocations;
    private final Set<String> enderDragonLocations;
    private final Set<String> witherLocations;

    private GameData(JsonObject items, JsonObject locations, JsonObject regionInfo, JsonObject exclusions) {
        itemNames = strings(items.getAsJsonArray("all_items"));
        itemIds = idsOf(itemNames);
        progressionItems = Collections.unmodifiableSet(new LinkedHashSet<>(strings(items.getAsJsonArray("progression_items"))));
        Map<String, Integer> pool = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : items.getAsJsonObject("required_pool").entrySet()) {
            pool.put(entry.getKey(), entry.getValue().getAsInt());
        }
        requiredPool = Collections.unmodifiableMap(pool);

        locationNames = strings(locations.getAsJsonArray("all_locations"));
        locationIds = idsOf(locationNames);
        Map<String, String> byLocation = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : locations.getAsJsonObject("locations_by_region").entrySet()) {
            for (String location : strings(entry.getValue().getAsJsonArray())) {
                byLocation.put(location, entry.getKey());
            }
        }
        locationRegions = Collections.unmodifiableMap(byLocation);

        List<Region> regionList = new ArrayList<>();
        for (JsonElement element : regionInfo.getAsJsonArray("regions")) {
            JsonArray pair = element.getAsJsonArray();
            regionList.add(new Region(pair.get(0).getAsString(), strings(pair.get(1).getAsJsonArray())));
        }
        regions = Collections.unmodifiableList(regionList);
        mandatoryConnections = pairs(regionInfo.getAsJsonArray("mandatory_connections"));
        defaultConnections = pairs(regionInfo.getAsJsonArray("default_connections"));
        Map<String, List<String>> illegal = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : regionInfo.getAsJsonObject("illegal_connections").entrySet()) {
            illegal.put(entry.getKey(), strings(entry.getValue().getAsJsonArray()));
        }
        illegalConnections = Collections.unmodifiableMap(illegal);

        hardLocations = stringSet(exclusions.getAsJsonArray("hard"));
        unreasonableLocations = stringSet(exclusions.getAsJsonArray("unreasonable"));
        enderDragonLocations = stringSet(exclusions.getAsJsonArray("ender_dragon"));
        witherLocations = stringSet(exclusions.getAsJsonArray("wither"));
    }

    public static GameData load() {
        return new GameData(read("items.json"), read("locations.json"), read("regions.json"), read("excluded_locations.json"));
    }

    private static JsonObject read(String name) {
        try (InputStream stream = GameData.class.getResourceAsStream(RESOURCE_DIR + name)) {
            if (stream == null) {
                throw new IllegalStateException("Missing data file " + RESOURCE_DIR + name);
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> strings(JsonArray array) {
        List<String> result = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            result.add(element.getAsString());
        }
        return Collections.unmodifiableList(result);
    }

    private static Set<String> stringSet(JsonArray array) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(strings(array)));
    }

    private static Map<String, String> pairs(JsonArray array) {
        Map<String, String> result = new LinkedHashMap<>();
        for (JsonElement element : array) {
            JsonArray pair = element.getAsJsonArray();
            result.put(pair.get(0).getAsString(), pair.get(1).getAsString());
        }
        return Collections.unmodifiableMap(result);
    }

    // Archipelago ids are the 1-based position in the "all_*" lists (see the apworld's Constants.py).
    private static Map<String, Integer> idsOf(List<String> names) {
        Map<String, Integer> ids = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            ids.put(names.get(i), i + 1);
        }
        return Collections.unmodifiableMap(ids);
    }

    public List<String> itemNames() {
        return itemNames;
    }

    /** The item name for an Archipelago item id, or {@code null} when the id is unknown. */
    public String itemName(long id) {
        return id >= 1 && id <= itemNames.size() ? itemNames.get((int) id - 1) : null;
    }

    public Map<String, Integer> itemIds() {
        return itemIds;
    }

    public Set<String> progressionItems() {
        return progressionItems;
    }

    /** How many copies of each required item the generator puts in the pool. */
    public Map<String, Integer> requiredPool() {
        return requiredPool;
    }

    public List<String> locationNames() {
        return locationNames;
    }

    /** The location name for an Archipelago location id, or {@code null} when the id is unknown. */
    public String locationName(long id) {
        return id >= 1 && id <= locationNames.size() ? locationNames.get((int) id - 1) : null;
    }

    public Map<String, Integer> locationIds() {
        return locationIds;
    }

    /** The region each location belongs to. */
    public Map<String, String> locationRegions() {
        return locationRegions;
    }

    public List<Region> regions() {
        return regions;
    }

    /** Exit name to region name, for the connections that never change. */
    public Map<String, String> mandatoryConnections() {
        return mandatoryConnections;
    }

    /** Exit name to structure name when structures are not shuffled. */
    public Map<String, String> defaultConnections() {
        return defaultConnections;
    }

    /** Structure name to the exits it may not be attached to. */
    public Map<String, List<String>> illegalConnections() {
        return illegalConnections;
    }

    public Set<String> hardLocations() {
        return hardLocations;
    }

    public Set<String> unreasonableLocations() {
        return unreasonableLocations;
    }

    /** Locations that need the Ender Dragon to be defeated. */
    public Set<String> enderDragonLocations() {
        return enderDragonLocations;
    }

    /** Locations that need the Wither to be defeated. */
    public Set<String> witherLocations() {
        return witherLocations;
    }
}
