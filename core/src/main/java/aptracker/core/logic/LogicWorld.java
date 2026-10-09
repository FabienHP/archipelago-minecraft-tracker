package aptracker.core.logic;

import aptracker.core.data.GameData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The region graph and access rules of one slot: what the apworld builds in {@code create_regions},
 * {@code shuffle_structures} and {@code set_rules}, for a given set of options.
 *
 * <p>Regions form a tree rooted at "Menu": every region is entered through exactly one exit.
 */
public final class LogicWorld {

    public static final String EVENT_BLAZE_RODS = "Blaze Rods";
    public static final String EVENT_ENDER_DRAGON = "Ender Dragon";
    public static final String EVENT_WITHER = "Wither";

    // Events are locations without an Archipelago id whose "item" is granted as soon as they are reachable.
    private static final Map<String, String> EVENT_REGIONS = Map.of(
            EVENT_BLAZE_RODS, "Nether Fortress",
            EVENT_ENDER_DRAGON, "The End",
            EVENT_WITHER, "Nether Fortress");

    private final GameData data;
    private final SlotOptions options;
    private final Map<String, String> exitParents = new LinkedHashMap<>();
    private final Map<String, String> exitTargets = new LinkedHashMap<>();
    private final Map<String, String> regionEntrances = new HashMap<>();
    private final Map<String, Rule> entranceRules;
    private final Map<String, Rule> locationRules;
    private final Map<String, Rule> expandedLocations = new HashMap<>();
    private final Map<String, Rule> expandedRegions = new HashMap<>();

    public LogicWorld(GameData data, SlotOptions options) {
        this.data = data;
        this.options = options;

        Set<String> regionNames = new HashSet<>();
        for (GameData.Region region : data.regions()) {
            regionNames.add(region.name());
            for (String exit : region.exits()) {
                exitParents.put(exit, region.name());
            }
        }
        for (String exit : exitParents.keySet()) {
            String target = data.mandatoryConnections().get(exit);
            if (target == null) {
                target = options.structures().get(exit);
            }
            if (target == null || !regionNames.contains(target)) {
                throw new IllegalArgumentException("Exit '" + exit + "' is not connected to a known region: " + target);
            }
            List<String> illegalExits = data.illegalConnections().getOrDefault(target, List.of());
            if (illegalExits.contains(exit)) {
                throw new IllegalArgumentException("'" + target + "' may not be attached to '" + exit + "'");
            }
            if (regionEntrances.put(target, exit) != null) {
                throw new IllegalArgumentException("Region '" + target + "' is attached to more than one exit");
            }
            exitTargets.put(exit, target);
        }

        Rules rules = new Rules(this);
        entranceRules = rules.entrances();
        locationRules = rules.locations();
        for (String exit : entranceRules.keySet()) {
            if (!exitParents.containsKey(exit)) {
                throw new IllegalStateException("Rule for unknown exit '" + exit + "'");
            }
        }
        for (String location : locationRules.keySet()) {
            if (!isEvent(location) && !data.locationRegions().containsKey(location)) {
                throw new IllegalStateException("Rule for unknown location '" + location + "'");
            }
        }
    }

    public GameData data() {
        return data;
    }

    public SlotOptions options() {
        return options;
    }

    /** The region an exit leads to. */
    public String connectedRegion(String exit) {
        String region = exitTargets.get(exit);
        if (region == null) {
            throw new IllegalArgumentException("Unknown exit '" + exit + "'");
        }
        return region;
    }

    /** The region an exit leaves from. */
    public String parentRegion(String exit) {
        String region = exitParents.get(exit);
        if (region == null) {
            throw new IllegalArgumentException("Unknown exit '" + exit + "'");
        }
        return region;
    }

    /** The exit that leads into a region, or {@code null} for the root region. */
    public String entranceOf(String region) {
        return regionEntrances.get(region);
    }

    public Rule entranceRule(String exit) {
        return entranceRules.getOrDefault(exit, Rule.TRUE);
    }

    /** The access rule of a location or event, not counting the reachability of its region. */
    public Rule locationRule(String location) {
        return locationRules.getOrDefault(location, Rule.TRUE);
    }

    public boolean isEvent(String name) {
        return EVENT_REGIONS.containsKey(name);
    }

    /** The region a location or event belongs to. */
    public String regionOf(String location) {
        String region = EVENT_REGIONS.get(location);
        if (region == null) {
            region = data.locationRegions().get(location);
        }
        if (region == null) {
            throw new IllegalArgumentException("Unknown location '" + location + "'");
        }
        return region;
    }

    /**
     * Everything needed to check a location, rewritten down to items the player can receive: region
     * reachability, other locations and events are replaced by their own requirements.
     */
    public Rule expandedLocationRule(String location) {
        Rule cached = expandedLocations.get(location);
        if (cached == null) {
            cached = Rule.all(expandedRegionRule(regionOf(location)), expand(locationRule(location)));
            expandedLocations.put(location, cached);
        }
        return cached;
    }

    /** Everything needed to enter a region, rewritten down to items the player can receive. */
    public Rule expandedRegionRule(String region) {
        Rule cached = expandedRegions.get(region);
        if (cached == null) {
            String exit = entranceOf(region);
            cached = exit == null
                    ? Rule.TRUE
                    : Rule.all(expandedRegionRule(parentRegion(exit)), expand(entranceRule(exit)));
            expandedRegions.put(region, cached);
        }
        return cached;
    }

    private Rule expand(Rule rule) {
        if (rule instanceof Rule.Has has) {
            return isEvent(has.item()) ? expandedLocationRule(has.item()) : rule;
        } else if (rule instanceof Rule.RegionReach reach) {
            return expandedRegionRule(reach.region());
        } else if (rule instanceof Rule.LocationReach reach) {
            return expandedLocationRule(reach.location());
        } else if (rule instanceof Rule.All all) {
            return new Rule.All(all.rules().stream().map(this::expand).toList());
        } else if (rule instanceof Rule.Any any) {
            return new Rule.Any(any.rules().stream().map(this::expand).toList());
        }
        return rule;
    }
}
