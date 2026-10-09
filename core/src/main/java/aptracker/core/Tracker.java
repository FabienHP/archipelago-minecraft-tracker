package aptracker.core;

import aptracker.core.data.GameData;
import aptracker.core.logic.LogicState;
import aptracker.core.logic.LogicWorld;
import aptracker.core.logic.Requirements;
import aptracker.core.logic.SlotOptions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns what the Archipelago server knows about a slot (received items, checked locations) into the
 * state of every advancement: done, doable now, or out of logic and why.
 */
public final class Tracker {

    public enum Status {
        /** Already sent to the Archipelago server. */
        CHECKED,
        /** Not checked yet and reachable with the items received so far. */
        IN_LOGIC,
        /** Not checked yet and not reachable with the items received so far. */
        OUT_OF_LOGIC
    }

    /**
     * @param id       the Archipelago location id
     * @param excluded the slot's options keep progression items off this location (a hard, unreasonable
     *                 or postgame advancement that was not included); it still counts toward the goal
     * @param missing  for an out-of-logic location, the items to obtain as item name to total copies needed
     */
    public record LocationState(int id, String name, String region, Status status, boolean excluded,
                                Map<String, Integer> missing) {
    }

    /**
     * @param locations          every location, in Archipelago id order
     * @param items              the inventory the snapshot was computed from
     * @param enderDragonInLogic whether respawning and defeating the Ender Dragon is in logic
     * @param witherInLogic      whether defeating the Wither is in logic
     */
    public record Snapshot(List<LocationState> locations, Map<String, Integer> items, boolean enderDragonInLogic,
                           boolean witherInLogic) {

        public List<LocationState> withStatus(Status status) {
            return locations.stream().filter(location -> location.status() == status).toList();
        }

        public int count(Status status) {
            return (int) locations.stream().filter(location -> location.status() == status).count();
        }
    }

    private final GameData data;
    private final LogicWorld world;
    private final Set<String> excludedLocations;

    public Tracker(GameData data, SlotOptions options) {
        this.data = data;
        this.world = new LogicWorld(data, options);

        // Same selection as the end of set_rules in the apworld's Rules.py.
        Set<String> excluded = new HashSet<>();
        if (!options.includeHardAdvancements()) {
            excluded.addAll(data.hardLocations());
        }
        if (!options.includeUnreasonableAdvancements()) {
            excluded.addAll(data.unreasonableLocations());
        }
        if (!options.includePostgameAdvancements()) {
            if (options.requiredBosses().dragon()) {
                excluded.addAll(data.enderDragonLocations());
            }
            if (options.requiredBosses().wither()) {
                excluded.addAll(data.witherLocations());
            }
        }
        this.excludedLocations = Set.copyOf(excluded);
    }

    public GameData data() {
        return data;
    }

    public LogicWorld world() {
        return world;
    }

    /** Counts received items by name. Ids the apworld does not know are ignored. */
    public Map<String, Integer> countItems(Collection<Long> receivedItemIds) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (long id : receivedItemIds) {
            String name = data.itemName(id);
            if (name != null) {
                counts.merge(name, 1, Integer::sum);
            }
        }
        return counts;
    }

    /**
     * @param items              how many copies of each item the player has received, by item name
     * @param checkedLocationIds the Archipelago ids of the locations already checked
     */
    public Snapshot evaluate(Map<String, Integer> items, Set<Long> checkedLocationIds) {
        LogicState state = new LogicState(world, items);
        List<LocationState> locations = new ArrayList<>(data.locationNames().size());
        for (String name : data.locationNames()) {
            int id = data.locationIds().get(name);
            Status status;
            Map<String, Integer> missing = Map.of();
            if (checkedLocationIds.contains((long) id)) {
                status = Status.CHECKED;
            } else if (state.canReachLocation(name)) {
                status = Status.IN_LOGIC;
            } else {
                status = Status.OUT_OF_LOGIC;
                Map<String, Integer> needed = Requirements.missing(world.expandedLocationRule(name), items);
                if (needed != null) {
                    missing = Collections.unmodifiableMap(needed);
                }
            }
            locations.add(new LocationState(id, name, world.regionOf(name), status, excludedLocations.contains(name),
                    missing));
        }
        return new Snapshot(List.copyOf(locations), Map.copyOf(items),
                state.canReachLocation(LogicWorld.EVENT_ENDER_DRAGON), state.canReachLocation(LogicWorld.EVENT_WITHER));
    }
}
