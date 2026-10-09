package aptracker.core.apworld;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Read access to the apworld file the port is checked against (see {@code reference/}). */
final class Apworld {

    private Apworld() {
    }

    static Path path() {
        Path path = Path.of(System.getProperty("aptracker.apworld", "../reference/minecraft.apworld"));
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("apworld not found at " + path.toAbsolutePath());
        }
        return path;
    }

    /** The text of every entry whose path matches, by entry path. */
    static Map<String, String> entries(Predicate<String> pathFilter) {
        Map<String, String> result = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(path().toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !pathFilter.test(entry.getName())) {
                    continue;
                }
                try (InputStream stream = zip.getInputStream(entry)) {
                    result.put(entry.getName(), new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return result;
    }
}
