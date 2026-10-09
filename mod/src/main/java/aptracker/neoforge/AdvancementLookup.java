package aptracker.neoforge;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.Optional;

/** Finds the Minecraft advancement behind an Archipelago location, to reuse its icon and texts. */
final class AdvancementLookup {

    private Map<Long, Identifier> advancementIds = Map.of();

    void load(MinecraftServer server) {
        advancementIds = RandomizerState.advancementsByLocationId(server);
    }

    void clear() {
        advancementIds = Map.of();
    }

    Optional<AdvancementHolder> find(MinecraftServer server, long locationId) {
        Identifier id = advancementIds.get(locationId);
        return id == null ? Optional.empty() : Optional.ofNullable(server.getAdvancements().get(id));
    }
}
