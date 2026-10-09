package aptracker.neoforge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads the slot options out of the {@code .apmc} file. The randomizer mod only keeps the fields it
 * needs itself, so the options the logic depends on (combat difficulty, compasses...) are read again
 * here, from the same file the randomizer picks.
 */
final class ApmcReader {

    private static final byte[] ZIP_HEADER = {0x50, 0x4B, 0x03, 0x04};

    private ApmcReader() {
    }

    /** The JSON of the most recent {@code .apmc} file of the folder, if there is one that can be read. */
    static Optional<JsonObject> read(Path apDataDir) {
        if (!Files.isDirectory(apDataDir)) {
            return Optional.empty();
        }
        try {
            Optional<Path> file;
            try (Stream<Path> files = Files.list(apDataDir)) {
                file = files.filter(path -> path.getFileName().toString().endsWith(".apmc"))
                        .max(Comparator.comparing(ApmcReader::lastModified));
            }
            if (file.isEmpty()) {
                return Optional.empty();
            }
            String text = isZip(file.get()) ? readZipped(file.get()) : Files.readString(file.get());
            return text == null ? Optional.empty() : Optional.of(parse(text));
        } catch (IOException | RuntimeException e) {
            ApTracker.LOGGER.error("Could not read the .apmc file in {}", apDataDir, e);
            return Optional.empty();
        }
    }

    private static FileTime lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }

    private static boolean isZip(Path file) throws IOException {
        try (InputStream stream = Files.newInputStream(file)) {
            return Arrays.equals(stream.readNBytes(ZIP_HEADER.length), ZIP_HEADER);
        }
    }

    private static String readZipped(Path file) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && (entry.getName().endsWith(".apmc") || entry.getName().endsWith(".apmcmeta"))) {
                    try (InputStream stream = zip.getInputStream(entry)) {
                        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
            }
        }
        return null;
    }

    // Older apworld versions wrote the JSON base64-encoded.
    private static JsonObject parse(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("{")) {
            trimmed = new String(Base64.getDecoder().decode(trimmed), StandardCharsets.UTF_8);
        }
        return JsonParser.parseString(trimmed).getAsJsonObject();
    }
}
