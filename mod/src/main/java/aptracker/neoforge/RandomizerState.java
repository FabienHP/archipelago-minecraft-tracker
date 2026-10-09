package aptracker.neoforge;

import aptracker.core.data.GameData;
import gg.archipelago.aprandomizer.APRandomizer;
import gg.archipelago.aprandomizer.APRegistries;
import gg.archipelago.aprandomizer.ap.APClient;
import gg.archipelago.aprandomizer.locations.APLocation;
import gg.archipelago.aprandomizer.locations.AdvancementLocation;
import gg.archipelago.aprandomizer.managers.advancementmanager.AdvancementManager;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the randomizer mod knows at one instant. This class is the only place that touches the
 * randomizer's classes, so an upstream change breaks one file.
 *
 * @param connected          whether the randomizer is connected to the Archipelago server
 * @param receivedItemIds    the Archipelago ids of every item received, with repeats
 * @param checkedLocationIds the Archipelago ids of the advancements already earned
 */
record RandomizerState(boolean connected, List<Long> receivedItemIds, Set<Long> checkedLocationIds) {

    static RandomizerState read(GameData data) {
        Set<Long> checked = new HashSet<>();
        AdvancementManager advancements = APRandomizer.getAdvancementManager();
        if (advancements != null) {
            for (long id = 1; id <= data.locationNames().size(); id++) {
                if (advancements.hasAdvancement(id)) {
                    checked.add(id);
                }
            }
        }

        // The client library fills its list from the Archipelago connection thread without locking,
        // so this call can throw a ConcurrentModificationException while items are arriving.
        List<Long> items = List.of();
        APClient client = APRandomizer.getAP();
        if (client != null) {
            items = List.copyOf(client.getItemManager().getReceivedItemIDs());
        }
        return new RandomizerState(APRandomizer.isConnected(), items, Set.copyOf(checked));
    }

    /** The Minecraft advancement behind each Archipelago location id. */
    static Map<Long, Identifier> advancementsByLocationId(MinecraftServer server) {
        Map<Long, Identifier> result = new HashMap<>();
        AdvancementManager advancements = APRandomizer.getAdvancementManager();
        if (advancements == null) {
            return result;
        }
        Registry<APLocation> locations = server.registryAccess().lookupOrThrow(APRegistries.ARCHIPELAGO_LOCATION);
        for (Map.Entry<ResourceKey<APLocation>, APLocation> entry : locations.entrySet()) {
            long id = advancements.getAdvancementID(entry.getKey());
            if (id != 0 && entry.getValue() instanceof AdvancementLocation(Identifier advancement)) {
                result.put(id, advancement);
            }
        }
        return result;
    }
}
