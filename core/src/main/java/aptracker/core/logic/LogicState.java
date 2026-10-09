package aptracker.core.logic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Answers "is this reachable?" for one inventory, the way an Archipelago {@code CollectionState}
 * does once it has swept the events. Regions form a tree and no rule depends on itself, so each
 * answer is computed once, on demand.
 */
public final class LogicState {

    private final LogicWorld world;
    private final Map<String, Integer> items;
    private final Map<String, Boolean> regions = new HashMap<>();
    private final Map<String, Boolean> locations = new HashMap<>();
    private final Set<String> resolving = new HashSet<>();

    /**
     * @param items how many copies of each item the player has received, by item name
     */
    public LogicState(LogicWorld world, Map<String, Integer> items) {
        this.world = world;
        this.items = Map.copyOf(items);
    }

    public boolean test(Rule rule) {
        if (rule instanceof Rule.Has has) {
            return has(has.item(), has.count());
        } else if (rule instanceof Rule.RegionReach reach) {
            return canReachRegion(reach.region());
        } else if (rule instanceof Rule.LocationReach reach) {
            return canReachLocation(reach.location());
        } else if (rule instanceof Rule.All all) {
            for (Rule child : all.rules()) {
                if (!test(child)) {
                    return false;
                }
            }
            return true;
        } else if (rule instanceof Rule.Any any) {
            for (Rule child : any.rules()) {
                if (test(child)) {
                    return true;
                }
            }
            return false;
        }
        return ((Rule.Const) rule).value();
    }

    public boolean has(String item, int count) {
        if (world.isEvent(item)) {
            // An event item exists once, and is held as soon as its location can be reached.
            return count <= 1 && canReachLocation(item);
        }
        return items.getOrDefault(item, 0) >= count;
    }

    public boolean canReachRegion(String region) {
        Boolean known = regions.get(region);
        if (known != null) {
            return known;
        }
        String exit = world.entranceOf(region);
        boolean result = exit == null || canUseEntrance(exit);
        regions.put(region, result);
        return result;
    }

    /** Whether an exit can be taken: its parent region is reachable and its own rule holds. */
    public boolean canUseEntrance(String exit) {
        String key = "entrance:" + exit;
        if (!resolving.add(key)) {
            throw new IllegalStateException("The rules of '" + exit + "' depend on themselves");
        }
        try {
            return canReachRegion(world.parentRegion(exit)) && test(world.entranceRule(exit));
        } finally {
            resolving.remove(key);
        }
    }

    /** Whether a location or event can be checked: its region is reachable and its own rule holds. */
    public boolean canReachLocation(String location) {
        Boolean known = locations.get(location);
        if (known != null) {
            return known;
        }
        String key = "location:" + location;
        if (!resolving.add(key)) {
            throw new IllegalStateException("The rules of '" + location + "' depend on themselves");
        }
        boolean result;
        try {
            result = canReachRegion(world.regionOf(location)) && test(world.locationRule(location));
        } finally {
            resolving.remove(key);
        }
        locations.put(location, result);
        return result;
    }
}
