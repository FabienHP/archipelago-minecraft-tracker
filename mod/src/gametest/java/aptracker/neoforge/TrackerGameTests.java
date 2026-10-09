package aptracker.neoforge;

import aptracker.core.Tracker.LocationState;
import aptracker.core.Tracker.Snapshot;
import aptracker.core.Tracker.Status;
import aptracker.neoforge.TrackerTab.Node;
import gg.archipelago.aprandomizer.APRandomizer;
import gg.archipelago.aprandomizer.ap.APClient;
import io.github.archipelagomw.parts.NetworkItem;
import io.netty.buffer.Unpooled;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Runs the tracker inside a real server, next to the randomizer mod, with a mock player. It checks
 * what a client would receive: the tab is decoded and loaded the way the vanilla client does it.
 */
@EventBusSubscriber(modid = ApTracker.MODID)
public final class TrackerGameTests {

    private static final Identifier TRACKER_TAB = Identifier.fromNamespaceAndPath(ApTracker.MODID, "tracker_tab");
    private static final ResourceKey<Consumer<GameTestHelper>> TRACKER_TAB_FUNCTION =
            ResourceKey.create(Registries.TEST_FUNCTION, TRACKER_TAB);

    // The mod polls the randomizer and refreshes the tab once a second.
    private static final int SETTLE_TICKS = 45;

    private TrackerGameTests() {
    }

    @SubscribeEvent
    static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION,
                registry -> registry.register(TRACKER_TAB_FUNCTION, TrackerGameTests::trackerTab));
    }

    @SubscribeEvent
    static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment =
                event.registerEnvironment(Identifier.fromNamespaceAndPath(ApTracker.MODID, "default"));
        event.registerTest(TRACKER_TAB, new FunctionGameTestInstance(TRACKER_TAB_FUNCTION,
                new TestData<>(environment, Identifier.withDefaultNamespace("empty"), 200, 0, true)));
    }

    private static void trackerTab(GameTestHelper helper) {
        ApTracker mod = ApTracker.instance();
        APClient client = APRandomizer.getAP();
        helper.assertTrue(client != null, "the randomizer did not create its Archipelago client (no .apmc in APData?)");
        helper.assertTrue(mod.service().tracker() != null, "the tracker did not start");

        setReceivedItems(client);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        helper.runAfterDelay(SETTLE_TICKS, () -> {
            Snapshot empty = mod.service().snapshot();
            helper.assertTrue(empty != null, "no snapshot was computed");
            helper.assertTrue(status(empty, "Hot Stuff") == Status.OUT_OF_LOGIC, "Hot Stuff should need items");
            List<Node> shown = mod.tab().shown(player);
            checkTab(helper, shown, empty);
            helper.assertFalse(location(shown, empty, "Hot Stuff").lit(), "Hot Stuff should not be lit yet");

            // A progressive item is named by the level needed, another item by the copies needed.
            String diamonds = location(shown, empty, "Diamonds!").description().getString();
            helper.assertTrue(diamonds.contains("Progressive Tools 2"), "unexpected text for Diamonds!: " + diamonds);
            String theEnd = location(shown, empty, "The End").description().getString();
            helper.assertTrue(theEnd.contains("3 Ender Pearls ×4"), "unexpected text for The End: " + theEnd);
            String summary = shown.stream().filter(node -> node.id().getPath().equals("tab/summary")).findFirst()
                    .orElseThrow().description().getString();
            helper.assertTrue(summary.contains("Ender Dragon: missing ") && summary.contains("Progressive Weapons 2"),
                    "unexpected summary: " + summary);

            // What the randomizer's client holds after the Archipelago server sent these three items.
            setReceivedItems(client, "Progressive Tools", "Progressive Resource Crafting", "Bucket");

            helper.runAfterDelay(SETTLE_TICKS, () -> {
                Snapshot stocked = mod.service().snapshot();
                helper.assertTrue(status(stocked, "Hot Stuff") == Status.IN_LOGIC, "Hot Stuff should be doable with a bucket");
                List<Node> updated = mod.tab().shown(player);
                checkTab(helper, updated, stocked);
                helper.assertTrue(location(updated, stocked, "Hot Stuff").lit(), "Hot Stuff should be lit");

                setReceivedItems(client);
                helper.getLevel().getServer().getPlayerList().remove(player);
                helper.succeed();
            });
        });
    }

    private static void setReceivedItems(APClient client, String... itemNames) {
        Map<String, Integer> ids = ApTracker.instance().service().tracker().data().itemIds();
        List<NetworkItem> items = new ArrayList<>();
        for (String name : itemNames) {
            NetworkItem item = new NetworkItem();
            item.itemID = ids.get(name);
            item.itemName = name;
            items.add(item);
        }
        client.getItemManager().writeFromSave(items, items.size());
    }

    private static Status status(Snapshot snapshot, String location) {
        return snapshot.locations().stream().filter(state -> state.name().equals(location)).findFirst().orElseThrow().status();
    }

    private static Node location(List<Node> shown, Snapshot snapshot, String name) {
        LocationState state = snapshot.locations().stream().filter(location -> location.name().equals(name)).findFirst().orElseThrow();
        String path = "tab/location/" + state.id();
        return shown.stream().filter(node -> node.id().getPath().equals(path)).findFirst().orElseThrow();
    }

    private static void checkTab(GameTestHelper helper, List<Node> shown, Snapshot snapshot) {
        long locations = shown.stream().filter(node -> node.id().getPath().startsWith("tab/location/")).count();
        long litLocations = shown.stream().filter(node -> node.id().getPath().startsWith("tab/location/") && node.lit()).count();
        int remaining = snapshot.locations().size() - snapshot.count(Status.CHECKED);
        helper.assertTrue(locations == remaining, "the tab lists " + locations + " advancements, " + remaining + " are left");
        helper.assertTrue(litLocations == snapshot.count(Status.IN_LOGIC),
                litLocations + " advancements are lit, " + snapshot.count(Status.IN_LOGIC) + " are doable");

        // Send the tab through the network encoding, then load it the way ClientAdvancements does.
        List<AdvancementHolder> holders = shown.stream().map(Node::toHolder).toList();
        Map<Identifier, AdvancementProgress> progress = new HashMap<>();
        shown.forEach(node -> progress.put(node.id(), node.progress()));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        ClientboundUpdateAdvancementsPacket.STREAM_CODEC.encode(buffer,
                new ClientboundUpdateAdvancementsPacket(false, holders, Set.of(), progress, false));
        ClientboundUpdateAdvancementsPacket received = ClientboundUpdateAdvancementsPacket.STREAM_CODEC.decode(buffer);

        AdvancementTree tree = new AdvancementTree();
        tree.addAll(received.getAdded());
        helper.assertTrue(tree.nodes().size() == shown.size(),
                "the client would load " + tree.nodes().size() + " of the " + shown.size() + " advancements");
        long roots = 0;
        for (AdvancementNode root : tree.roots()) {
            roots++;
            helper.assertTrue(root.advancement().display().orElseThrow().getBackground().isPresent(), "the tab has no background");
        }
        helper.assertTrue(roots == 1, "the client would show " + roots + " tabs");

        long obtained = 0;
        for (Map.Entry<Identifier, AdvancementProgress> entry : received.getProgress().entrySet()) {
            AdvancementNode node = tree.get(entry.getKey());
            helper.assertTrue(node != null, "progress for an advancement the client does not know: " + entry.getKey());
            entry.getValue().update(node.advancement().requirements());
            if (entry.getValue().isDone()) {
                obtained++;
            }
        }
        long lit = shown.stream().filter(Node::lit).count();
        helper.assertTrue(obtained == lit, "the client would light " + obtained + " advancements instead of " + lit);
        ApTracker.LOGGER.info("Tracker tab as a client loads it: {} advancements left, {} doable, {} bytes on the wire",
                locations, litLocations, buffer.writerIndex());
    }
}
