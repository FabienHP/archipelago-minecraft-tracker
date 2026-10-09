package aptracker.core.apworld;

import aptracker.core.data.GameData;
import aptracker.core.logic.CombatDifficulty;
import aptracker.core.logic.LogicState;
import aptracker.core.logic.LogicWorld;
import aptracker.core.logic.RequiredBosses;
import aptracker.core.logic.SlotOptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Replays the logic tests that ship inside the apworld ({@code test/test_*.py}) against the Java
 * port. The cases are read from the apworld file itself, so dropping a newer apworld in
 * {@code reference/} shows which rules changed.
 */
class ApworldLogicTest {

    private static final Pattern CLASS = Pattern.compile("(?m)^class\\s+(\\w+)\\s*\\(");
    private static final Pattern OPTIONS = Pattern.compile("(?m)^[ \\t]+options\\s*=\\s*");
    private static final Pattern RUN = Pattern.compile("self\\.run_(location|entrance)_tests\\(");

    private static final GameData DATA = GameData.load();

    /** One row of a {@code run_location_tests} or {@code run_entrance_tests} call. */
    private record Case(String name, boolean entrance, String target, boolean expected, List<String> items,
                        List<String> allExcept, SlotOptions options) {
    }

    @TestFactory
    Stream<DynamicTest> apworldTestCases() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Case testCase : loadCases()) {
            tests.add(DynamicTest.dynamicTest(testCase.name(), () -> check(testCase)));
        }
        return tests.stream();
    }

    @Test
    void theApworldTestsWereFound() {
        List<Case> cases = loadCases();
        assertTrue(cases.size() > 500, "only " + cases.size() + " test cases were read from the apworld");
        long locations = cases.stream().filter(testCase -> !testCase.entrance()).map(Case::target).distinct().count();
        assertEquals(DATA.locationNames().size(), locations, "locations covered by the apworld's tests");
    }

    // Mirrors run_location_tests / run_entrance_tests in the apworld's test/bases.py.
    private static void check(Case testCase) {
        LogicWorld world = new LogicWorld(DATA, testCase.options());
        boolean allExcept = !testCase.allExcept().isEmpty();

        Map<String, Integer> items = new HashMap<>();
        if (allExcept) {
            itemPool(testCase.options()).forEach((item, count) -> {
                if (!testCase.allExcept().contains(item)) {
                    items.put(item, count);
                }
            });
        }
        testCase.items().forEach(item -> items.merge(item, 1, Integer::sum));
        assertEquals(testCase.expected(), reach(world, items, testCase), "with " + items);

        // A location reached with exactly the listed items must not be reachable with one of them removed.
        if (!allExcept && testCase.expected()) {
            for (String removed : testCase.items()) {
                Map<String, Integer> partial = new HashMap<>(items);
                partial.merge(removed, -1, Integer::sum);
                assertFalse(reach(world, partial, testCase), "still reachable without " + removed);
            }
        }
    }

    private static boolean reach(LogicWorld world, Map<String, Integer> items, Case testCase) {
        LogicState state = new LogicState(world, items);
        return testCase.entrance() ? state.canUseEntrance(testCase.target()) : state.canReachLocation(testCase.target());
    }

    // The part of build_item_pool (ItemPool.py) that matters to the logic: junk items are left out.
    private static Map<String, Integer> itemPool(SlotOptions options) {
        Map<String, Integer> pool = new HashMap<>(DATA.requiredPool());
        if (options.structureCompasses()) {
            for (String item : DATA.itemNames()) {
                if (item.contains("Structure Compass")) {
                    pool.merge(item, 1, Integer::sum);
                }
            }
        }
        if (options.eggShardsRequired() > 0) {
            pool.merge("Dragon Egg Shard", options.eggShardsAvailable(), Integer::sum);
        }
        return pool;
    }

    private static List<Case> loadCases() {
        List<Case> cases = new ArrayList<>();
        Map<String, String> files = Apworld.entries(path -> path.matches("[^/]+/test/test_[^/]+\\.py"));
        for (Map.Entry<String, String> file : files.entrySet()) {
            String fileName = file.getKey().substring(file.getKey().lastIndexOf('/') + 1);
            String text = file.getValue();

            List<Integer> classStarts = new ArrayList<>();
            Matcher classMatcher = CLASS.matcher(text);
            while (classMatcher.find()) {
                classStarts.add(classMatcher.start());
            }

            Matcher run = RUN.matcher(text);
            while (run.find()) {
                int classStart = -1;
                int classEnd = text.length();
                for (int start : classStarts) {
                    if (start < run.start()) {
                        classStart = start;
                    } else {
                        classEnd = start;
                        break;
                    }
                }
                SlotOptions options = classOptions(fileName, text, classStart, classEnd);
                boolean entrance = run.group(1).equals("entrance");

                List<?> rows = (List<?>) PythonLiterals.parse(text, run.end()).value();
                for (int i = 0; i < rows.size(); i++) {
                    List<?> row = (List<?>) rows.get(i);
                    String target = (String) row.get(0);
                    boolean expected = (Boolean) row.get(1);
                    List<String> items = strings(row.get(2));
                    List<String> allExcept = row.size() > 3 ? strings(row.get(3)) : List.of();
                    String name = fileName + " " + target + " #" + cases.size() + " expects " + expected;
                    cases.add(new Case(name, entrance, target, expected, items, allExcept, options));
                }
            }
        }
        return cases;
    }

    private static List<String> strings(Object list) {
        List<String> result = new ArrayList<>();
        for (Object value : (List<?>) list) {
            result.add((String) value);
        }
        return result;
    }

    // WorldTestBase generates the world with the class's "options" dict on top of the apworld's defaults.
    private static SlotOptions classOptions(String fileName, String text, int classStart, int classEnd) {
        Map<?, ?> declared = Map.of();
        if (classStart >= 0) {
            Matcher options = OPTIONS.matcher(text).region(classStart, classEnd);
            if (options.find()) {
                declared = (Map<?, ?>) PythonLiterals.parse(text, options.end()).value();
            }
        }

        SlotOptions.Builder builder = SlotOptions.builder(DATA);
        boolean shuffled = true;
        for (Map.Entry<?, ?> option : declared.entrySet()) {
            String key = (String) option.getKey();
            Object value = option.getValue();
            switch (key) {
                case "shuffle_structures" -> shuffled = (Boolean) value;
                case "structure_compasses" -> builder.structureCompasses((Boolean) value);
                case "death_link" -> builder.deathLink((Boolean) value);
                case "include_hard_advancements" -> builder.includeHardAdvancements((Boolean) value);
                case "include_unreasonable_advancements" -> builder.includeUnreasonableAdvancements((Boolean) value);
                case "include_postgame_advancements" -> builder.includePostgameAdvancements((Boolean) value);
                case "combat_difficulty" -> builder.combatDifficulty(value instanceof Long number
                        ? CombatDifficulty.fromValue(number.intValue())
                        : CombatDifficulty.valueOf(((String) value).toUpperCase()));
                case "required_bosses" -> builder.requiredBosses(value instanceof Long number
                        ? RequiredBosses.fromValue(number.intValue())
                        : RequiredBosses.fromKey((String) value));
                case "advancement_goal" -> builder.advancementGoal(((Long) value).intValue());
                case "egg_shards_required" -> builder.eggShardsRequired(((Long) value).intValue());
                case "egg_shards_available" -> builder.eggShardsAvailable(((Long) value).intValue());
                default -> throw new IllegalStateException(fileName + ": option '" + key + "' is not handled by this test");
            }
        }
        if (shuffled) {
            // Structures would be placed at random, which cannot be reproduced without the generator's seed.
            throw new IllegalStateException(fileName + ": a test class runs logic tests with shuffled structures");
        }
        return builder.build();
    }
}
