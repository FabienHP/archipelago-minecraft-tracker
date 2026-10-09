package aptracker.core.logic;

import java.util.LinkedHashMap;
import java.util.Map;

/** Works out which items are still missing to satisfy a rule. */
public final class Requirements {

    private Requirements() {
    }

    /**
     * The items to obtain so that the rule holds, as item name to the total number of copies needed.
     * When a rule offers alternatives, the one that needs the fewest additional items is chosen.
     *
     * @param expandedRule a rule that only refers to receivable items, see {@link LogicWorld#expandedLocationRule}
     * @param items        how many copies of each item the player has
     * @return an empty map when the rule already holds, or {@code null} when no set of items can satisfy it
     */
    public static Map<String, Integer> missing(Rule expandedRule, Map<String, Integer> items) {
        if (expandedRule instanceof Rule.Has has) {
            Map<String, Integer> result = new LinkedHashMap<>();
            if (items.getOrDefault(has.item(), 0) < has.count()) {
                result.put(has.item(), has.count());
            }
            return result;
        } else if (expandedRule instanceof Rule.All all) {
            Map<String, Integer> result = new LinkedHashMap<>();
            for (Rule child : all.rules()) {
                Map<String, Integer> childMissing = missing(child, items);
                if (childMissing == null) {
                    return null;
                }
                childMissing.forEach((item, count) -> result.merge(item, count, Math::max));
            }
            return result;
        } else if (expandedRule instanceof Rule.Any any) {
            Map<String, Integer> best = null;
            int bestCost = Integer.MAX_VALUE;
            for (Rule child : any.rules()) {
                Map<String, Integer> childMissing = missing(child, items);
                if (childMissing == null) {
                    continue;
                }
                int cost = cost(childMissing, items);
                if (cost < bestCost) {
                    best = childMissing;
                    bestCost = cost;
                }
            }
            return best;
        } else if (expandedRule instanceof Rule.Const constant) {
            return constant.value() ? new LinkedHashMap<>() : null;
        }
        throw new IllegalArgumentException("Rule is not expanded: " + expandedRule);
    }

    private static int cost(Map<String, Integer> missing, Map<String, Integer> items) {
        int cost = 0;
        for (Map.Entry<String, Integer> entry : missing.entrySet()) {
            cost += entry.getValue() - items.getOrDefault(entry.getKey(), 0);
        }
        return cost;
    }
}
