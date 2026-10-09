package aptracker.core.apworld;

import com.google.gson.JsonParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The data files bundled with the tracker must be the ones of the reference apworld. */
class ApworldDataTest {

    @ParameterizedTest
    @ValueSource(strings = {"items.json", "locations.json", "regions.json", "excluded_locations.json"})
    void bundledDataMatchesTheApworld(String fileName) throws IOException {
        Map<String, String> entries = Apworld.entries(path -> path.matches("[^/]+/data/" + fileName));
        assertEquals(1, entries.size(), fileName + " in the apworld");
        String expected = entries.values().iterator().next();

        String bundled;
        try (InputStream stream = getClass().getResourceAsStream("/aptracker/data/" + fileName)) {
            assertNotNull(stream, fileName + " is not bundled");
            bundled = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(JsonParser.parseString(expected), JsonParser.parseString(bundled),
                fileName + " differs from the apworld: copy the apworld's data/ files into core/src/main/resources/aptracker/data/");
    }
}
