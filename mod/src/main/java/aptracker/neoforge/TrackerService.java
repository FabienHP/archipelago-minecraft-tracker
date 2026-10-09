package aptracker.neoforge;

import aptracker.core.Tracker;
import aptracker.core.Tracker.Snapshot;
import aptracker.core.data.GameData;
import aptracker.core.logic.SlotOptions;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ConcurrentModificationException;
import java.util.Optional;

/**
 * Keeps the tracker's view of the slot up to date: once a second it reads the randomizer's state
 * and, when something changed, recomputes which advancements are in logic.
 */
final class TrackerService {

    /**
     * @param previous     the snapshot before the change, or {@code null} for the first one
     * @param wasConnected whether the randomizer was connected to Archipelago before the change
     */
    record Update(@Nullable Snapshot previous, Snapshot current, boolean wasConnected) {
    }

    private static final int POLL_INTERVAL_TICKS = 20;

    private final GameData data = GameData.load();
    private @Nullable Tracker tracker;
    private @Nullable RandomizerState state;
    private @Nullable Snapshot snapshot;
    private int ticksUntilPoll;

    /** Reads the slot options. Without a readable {@code .apmc} file the tracker stays off. */
    void start(Path apDataDir) {
        tracker = null;
        state = null;
        snapshot = null;
        ticksUntilPoll = 0;

        Optional<JsonObject> apmc = ApmcReader.read(apDataDir);
        if (apmc.isEmpty()) {
            ApTracker.LOGGER.warn("No readable .apmc file in {}: the tracker is disabled.", apDataDir.toAbsolutePath());
            return;
        }
        if (!apmc.get().has("combat_difficulty")) {
            ApTracker.LOGGER.warn("The .apmc file does not list the logic options (older apworld?): the apworld's defaults are assumed.");
        }
        try {
            SlotOptions options = SlotOptions.fromSlotData(apmc.get(), data);
            tracker = new Tracker(data, options);
            ApTracker.LOGGER.info("Tracker ready: {}", options);
        } catch (RuntimeException e) {
            ApTracker.LOGGER.error("The slot options of the .apmc file are not usable: the tracker is disabled.", e);
        }
    }

    void stop() {
        tracker = null;
        state = null;
        snapshot = null;
    }

    /** Called every server tick. Returns what changed, or {@code null} when nothing did. */
    @Nullable Update tick() {
        if (tracker == null || --ticksUntilPoll > 0) {
            return null;
        }
        ticksUntilPoll = POLL_INTERVAL_TICKS;

        RandomizerState current;
        try {
            current = RandomizerState.read(data);
        } catch (ConcurrentModificationException e) {
            // The Archipelago connection thread was adding items to its list: read again next time.
            return null;
        }
        if (current.equals(state)) {
            return null;
        }
        boolean wasConnected = state != null && state.connected();
        Snapshot previous = snapshot;
        state = current;
        snapshot = tracker.evaluate(tracker.countItems(current.receivedItemIds()), current.checkedLocationIds());
        return new Update(previous, snapshot, wasConnected);
    }

    /** The tracker, or {@code null} while it is disabled. */
    @Nullable Tracker tracker() {
        return tracker;
    }

    /** The latest snapshot, or {@code null} before the first reading. */
    @Nullable Snapshot snapshot() {
        return snapshot;
    }

    /** The latest reading of the randomizer, or {@code null} before the first one. */
    @Nullable RandomizerState state() {
        return state;
    }
}
