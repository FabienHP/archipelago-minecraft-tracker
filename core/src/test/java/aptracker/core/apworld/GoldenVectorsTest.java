package aptracker.core.apworld;

import aptracker.core.data.GameData;
import aptracker.core.logic.LogicState;
import aptracker.core.logic.LogicWorld;
import aptracker.core.logic.SlotOptions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compares the Java port with results recorded from the real apworld running inside Archipelago
 * ({@code tools/golden/generate_vectors.py}). Unlike the apworld's own tests, the recordings cover
 * every combat difficulty, structure compasses, death link and shuffled structures.
 */
class GoldenVectorsTest {

    private static final GameData DATA = GameData.load();

    @TestFactory
    Stream<DynamicTest> thePortAgreesWithTheApworld() {
        JsonObject document = load();
        List<String> locations = strings(document.getAsJsonArray("locations"));
        List<String> entrances = strings(document.getAsJsonArray("entrances"));
        List<String> events = strings(document.getAsJsonArray("events"));
        assertEquals(DATA.locationNames(), locations, "the recordings were made with another location list");

        List<DynamicTest> tests = new ArrayList<>();
        JsonArray configurations = document.getAsJsonArray("configurations");
        assertTrue(configurations.size() >= 100, "only " + configurations.size() + " configurations were recorded");
        for (int i = 0; i < configurations.size(); i++) {
            JsonObject configuration = configurations.get(i).getAsJsonObject();
            JsonObject slotData = configuration.getAsJsonObject("slot_data");
            tests.add(DynamicTest.dynamicTest("#" + i + " " + slotData,
                    () -> check(configuration, locations, entrances, events)));
        }
        return tests.stream();
    }

    private static void check(JsonObject configuration, List<String> locations, List<String> entrances,
                              List<String> events) {
        SlotOptions options = SlotOptions.fromSlotData(configuration.getAsJsonObject("slot_data"), DATA);
        LogicWorld world = new LogicWorld(DATA, options);

        for (JsonElement orderElement : configuration.getAsJsonArray("orders")) {
            JsonObject order = orderElement.getAsJsonObject();
            List<String> items = strings(order.getAsJsonArray("items"));
            JsonArray states = order.getAsJsonArray("states");
            assertEquals(items.size() + 1, states.size());

            // State n was recorded after receiving the first n items of the order.
            Map<String, Integer> inventory = new HashMap<>();
            for (int received = 0; received < states.size(); received++) {
                if (received > 0) {
                    inventory.merge(items.get(received - 1), 1, Integer::sum);
                }
                JsonObject expected = states.get(received).getAsJsonObject();
                LogicState state = new LogicState(world, inventory);
                String context = " with " + inventory;
                assertEquals(reachable(expected.get("locations").getAsString(), locations),
                        select(locations, state::canReachLocation), "locations" + context);
                assertEquals(reachable(expected.get("entrances").getAsString(), entrances),
                        select(entrances, state::canUseEntrance), "entrances" + context);
                assertEquals(reachable(expected.get("events").getAsString(), events),
                        select(events, event -> state.has(event, 1)), "events" + context);
            }
        }
    }

    // The recordings pack one flag per name into a hex number, first name in the lowest bit.
    private static List<String> reachable(String hex, List<String> names) {
        BigInteger flags = new BigInteger(hex, 16);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            if (flags.testBit(i)) {
                result.add(names.get(i));
            }
        }
        return result;
    }

    private static List<String> select(List<String> names, Predicate<String> predicate) {
        return names.stream().filter(predicate).toList();
    }

    private static List<String> strings(JsonArray array) {
        List<String> result = new ArrayList<>();
        for (JsonElement element : array) {
            result.add(element.getAsString());
        }
        return result;
    }

    private static JsonObject load() {
        try (InputStream stream = GoldenVectorsTest.class.getResourceAsStream("/golden/vectors.json.gz")) {
            assertNotNull(stream, "golden/vectors.json.gz is missing: run tools/golden/generate_vectors.py");
            try (Reader reader = new InputStreamReader(new GZIPInputStream(stream), StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
