package aptracker.neoforge;

import aptracker.core.Tracker.LocationState;
import aptracker.core.Tracker.Snapshot;
import aptracker.core.Tracker.Status;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Tells the players, in the chat, which advancements an item they just received made doable. */
final class Announcer {

    private static final int MAX_LISTED = 5;

    private final AdvancementLookup advancements;

    Announcer(AdvancementLookup advancements) {
        this.advancements = advancements;
    }

    void announce(MinecraftServer server, TrackerService.Update update) {
        Snapshot previous = update.previous();
        // The first reading after connecting brings the whole inventory at once: nothing is news yet.
        if (previous == null || !update.wasConnected()) {
            return;
        }
        Set<Integer> blocked = new HashSet<>();
        for (LocationState location : previous.withStatus(Status.OUT_OF_LOGIC)) {
            blocked.add(location.id());
        }
        List<LocationState> unlocked = new ArrayList<>();
        for (LocationState location : update.current().withStatus(Status.IN_LOGIC)) {
            if (blocked.contains(location.id())) {
                unlocked.add(location);
            }
        }
        if (unlocked.isEmpty()) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Texts texts = Texts.of(player);
            MutableComponent message = Component.literal(texts.nowDoable()).withStyle(ChatFormatting.GOLD);
            for (int i = 0; i < Math.min(unlocked.size(), MAX_LISTED); i++) {
                if (i > 0) {
                    message.append(Component.literal(", ").withStyle(ChatFormatting.GOLD));
                }
                message.append(name(server, unlocked.get(i)));
            }
            if (unlocked.size() > MAX_LISTED) {
                message.append(Component.literal(texts.andMore(unlocked.size() - MAX_LISTED)).withStyle(ChatFormatting.GOLD));
            }
            player.sendSystemMessage(message);
        }
    }

    /** The advancement's own name, which the client translates and explains on hover. */
    Component name(MinecraftServer server, LocationState location) {
        return advancements.find(server, location.id())
                .map(Advancement::name)
                .orElseGet(() -> Component.literal("[" + location.name() + "]").withStyle(ChatFormatting.GREEN));
    }
}
