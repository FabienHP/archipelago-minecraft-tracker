package aptracker.core.logic;

import java.util.List;

/**
 * An access rule, kept as a tree instead of a lambda so that it can be both evaluated and explained
 * ("what is missing?"). Each node mirrors one construct of the apworld's {@code Rules.py}.
 */
public sealed interface Rule {

    Rule TRUE = new Const(true);
    Rule FALSE = new Const(false);

    /** {@code state.has(item, player, count)}. The item may be an event such as "Blaze Rods". */
    record Has(String item, int count) implements Rule {
    }

    /** {@code state.can_reach_region(region, player)}. */
    record RegionReach(String region) implements Rule {
    }

    /** {@code state.can_reach_location(location, player)}. */
    record LocationReach(String location) implements Rule {
    }

    record All(List<Rule> rules) implements Rule {
    }

    record Any(List<Rule> rules) implements Rule {
    }

    record Const(boolean value) implements Rule {
    }

    static Rule has(String item) {
        return new Has(item, 1);
    }

    static Rule has(String item, int count) {
        return new Has(item, count);
    }

    static Rule region(String region) {
        return new RegionReach(region);
    }

    static Rule location(String location) {
        return new LocationReach(location);
    }

    static Rule all(Rule... rules) {
        return new All(List.of(rules));
    }

    static Rule any(Rule... rules) {
        return new Any(List.of(rules));
    }

    static Rule of(boolean value) {
        return value ? TRUE : FALSE;
    }
}
