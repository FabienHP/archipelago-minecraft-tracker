package aptracker.neoforge;

import aptracker.core.Tracker;
import aptracker.core.Tracker.LocationState;
import aptracker.core.Tracker.Snapshot;
import aptracker.core.Tracker.Status;
import aptracker.core.data.GameData;
import aptracker.core.logic.LogicState;
import aptracker.core.logic.LogicWorld;
import aptracker.core.logic.Requirements;
import aptracker.core.logic.SlotOptions;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSelectAdvancementsTabPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Shows the tracker as an extra tab of the advancements screen. The tab does not exist on the
 * server: its advancements are made up and sent to each client, which is enough for an unmodded
 * client to display them. The advancements doable now come first, lit; then, region by region,
 * the ones still out of reach with the items they are waiting for.
 */
final class TrackerTab {

    private static final Identifier ROOT = id("tab/root");
    private static final Identifier SUMMARY = id("tab/summary");
    private static final Identifier DOABLE = id("tab/doable");
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("block/polished_deepslate");

    // A made-up advancement is "obtained" on the client once its single criterion is granted.
    private static final String CRITERION = "lit";
    private static final AdvancementRequirements REQUIREMENTS = AdvancementRequirements.allOf(List.of(CRITERION));

    // With the two header columns this fills the width of the advancements window without scrolling sideways.
    private static final int COLUMNS = 6;
    private static final int REFRESH_INTERVAL_TICKS = 20;
    // The client drops every advancement when the server resets them (on join and on /reload);
    // the reset packet goes out on the player's next tick, so the tab is sent again shortly after.
    private static final int RESEND_DELAY_TICKS = 5;

    private static final Map<String, Item> REGION_ICONS = Map.ofEntries(
            Map.entry("Overworld", Items.GRASS_BLOCK),
            Map.entry("The Nether", Items.NETHERRACK),
            Map.entry("The End", Items.END_STONE),
            Map.entry("Village", Items.EMERALD),
            Map.entry("Pillager Outpost", Items.CROSSBOW),
            Map.entry("Nether Fortress", Items.NETHER_BRICKS),
            Map.entry("Bastion Remnant", Items.GILDED_BLACKSTONE),
            Map.entry("End City", Items.PURPUR_BLOCK),
            Map.entry("Ocean Monument", Items.PRISMARINE_BRICKS),
            Map.entry("Woodland Mansion", Items.DARK_OAK_LOG),
            Map.entry("Ancient City", Items.SCULK_CATALYST),
            Map.entry("Trail Ruins", Items.SUSPICIOUS_GRAVEL),
            Map.entry("Trial Chambers", Items.TRIAL_KEY),
            Map.entry("Sulfur Caves", Items.SULFUR),
            Map.entry("Unvisited Biomes", Items.FILLED_MAP));

    /** One made-up advancement, as the client should show it. */
    record Node(Identifier id, Optional<Identifier> parent, ItemStackTemplate icon, Component title,
                Component description, AdvancementType type, float x, float y, boolean lit) {

        AdvancementHolder toHolder() {
            Optional<ClientAsset.ResourceTexture> background = parent.isEmpty()
                    ? Optional.of(new ClientAsset.ResourceTexture(BACKGROUND))
                    : Optional.empty();
            DisplayInfo display = new DisplayInfo(icon, title, description, background, type, false, false, false);
            display.setLocation(x, y);
            return new AdvancementHolder(id, new Advancement(parent, Optional.of(display), AdvancementRewards.EMPTY,
                    Map.of(), REQUIREMENTS, false));
        }

        AdvancementProgress progress() {
            AdvancementProgress progress = new AdvancementProgress();
            progress.update(REQUIREMENTS);
            if (lit) {
                progress.grantProgress(CRITERION);
            }
            return progress;
        }
    }

    private static final class View {
        /** What the client currently shows, parents before their children. */
        final Map<Identifier, Node> sent = new LinkedHashMap<>();
        int resendIn = RESEND_DELAY_TICKS;
    }

    private final TrackerService service;
    private final AdvancementLookup advancements;
    private final Map<UUID, View> views = new HashMap<>();
    private int ticksUntilRefresh;

    TrackerTab(TrackerService service, AdvancementLookup advancements) {
        this.service = service;
        this.advancements = advancements;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ApTracker.MODID, path);
    }

    /** The client is about to forget every advancement: send the whole tab again once it has. */
    void scheduleResend(ServerPlayer player) {
        views.computeIfAbsent(player.getUUID(), uuid -> new View()).resendIn = RESEND_DELAY_TICKS;
    }

    void forget(ServerPlayer player) {
        views.remove(player.getUUID());
    }

    void clear() {
        views.clear();
    }

    /** What the player's client currently shows, parents before their children. */
    List<Node> shown(ServerPlayer player) {
        View view = views.get(player.getUUID());
        return view == null ? List.of() : List.copyOf(view.sent.values());
    }

    /** Makes the tracker's tab the one shown when the player opens the advancements screen. */
    void select(ServerPlayer player) {
        player.connection.send(new ClientboundSelectAdvancementsTabPacket(ROOT));
    }

    void tick(MinecraftServer server) {
        boolean refresh = --ticksUntilRefresh <= 0;
        if (refresh) {
            ticksUntilRefresh = REFRESH_INTERVAL_TICKS;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            View view = views.computeIfAbsent(player.getUUID(), uuid -> new View());
            if (view.resendIn > 0) {
                if (--view.resendIn == 0) {
                    view.sent.clear();
                    send(player, view, build(server, player));
                }
            } else if (refresh) {
                send(player, view, build(server, player));
            }
        }
    }

    private Map<Identifier, Node> build(MinecraftServer server, ServerPlayer player) {
        Map<Identifier, Node> nodes = new LinkedHashMap<>();
        Tracker tracker = service.tracker();
        Snapshot snapshot = service.snapshot();
        RandomizerState state = service.state();
        if (tracker == null || snapshot == null || state == null) {
            return nodes;
        }
        Texts texts = Texts.of(player);
        boolean englishTitles = Texts.usesEnglishTitles(player);
        LogicWorld world = tracker.world();
        LogicState logic = new LogicState(world, snapshot.items());

        nodes.put(ROOT, new Node(ROOT, Optional.empty(), new ItemStackTemplate(Items.COMPASS),
                Component.literal(texts.tabTitle()), Component.literal(texts.tabDescription()), AdvancementType.TASK,
                0, 0, true));
        nodes.put(SUMMARY, new Node(SUMMARY, Optional.of(ROOT), new ItemStackTemplate(Items.KNOWLEDGE_BOOK),
                Component.literal(texts.progressTitle()), summary(texts, tracker, snapshot, state), AdvancementType.GOAL,
                1, 0, true));

        // Regions in the apworld's order; within a region, locations keep the Archipelago order.
        List<String> regionOrder = tracker.data().regions().stream().map(GameData.Region::name).toList();
        List<LocationState> ordered = new ArrayList<>(snapshot.locations());
        ordered.sort(Comparator.comparingInt(location -> regionOrder.indexOf(location.region())));

        // First what the player can go and do, so that it is on screen when the tab opens.
        List<LocationState> doable = ordered.stream().filter(location -> location.status() == Status.IN_LOGIC).toList();
        int row = 1;
        if (!doable.isEmpty()) {
            nodes.put(DOABLE, new Node(DOABLE, Optional.of(ROOT), new ItemStackTemplate(Items.GLOWSTONE),
                    Component.literal(texts.doable()), Component.literal(texts.doableCount(doable.size())),
                    AdvancementType.GOAL, 1, row, true));
            row = addLocations(nodes, DOABLE, doable, row, texts, englishTitles, server, snapshot);
        }

        // Then, region by region, what is still out of reach and which items it is waiting for.
        for (String region : regionOrder) {
            List<LocationState> remaining = ordered.stream()
                    .filter(location -> location.region().equals(region) && location.status() != Status.CHECKED)
                    .toList();
            if (remaining.isEmpty()) {
                continue;
            }
            Identifier header = id("tab/region/" + region.toLowerCase(Locale.ROOT).replace(' ', '_'));
            nodes.put(header, regionNode(header, region, row, remaining, texts, world, logic, snapshot));
            List<LocationState> blocked = remaining.stream().filter(location -> location.status() != Status.IN_LOGIC).toList();
            row = addLocations(nodes, header, blocked, row, texts, englishTitles, server, snapshot);
        }
        return nodes;
    }

    /** Lays the locations out in rows to the right of their header. Returns the first free row. */
    private int addLocations(Map<Identifier, Node> nodes, Identifier header, List<LocationState> locations, int row,
                             Texts texts, boolean englishTitles, MinecraftServer server, Snapshot snapshot) {
        for (int i = 0; i < locations.size(); i++) {
            LocationState location = locations.get(i);
            Identifier id = id("tab/location/" + location.id());
            nodes.put(id, locationNode(id, header, location, 2 + i % COLUMNS, row + i / COLUMNS, texts, englishTitles,
                    server, snapshot));
        }
        return row + Math.max(1, (locations.size() + COLUMNS - 1) / COLUMNS);
    }

    private Component summary(Texts texts, Tracker tracker, Snapshot snapshot, RandomizerState state) {
        SlotOptions options = tracker.world().options();
        int checked = snapshot.count(Status.CHECKED);
        MutableComponent text = Component.empty();
        if (!state.connected() && state.receivedItemIds().isEmpty()) {
            text.append(Component.literal(texts.notConnected()).withStyle(ChatFormatting.RED)).append("\n");
        }
        text.append(Component.literal(texts.doableCount(snapshot.count(Status.IN_LOGIC))).withStyle(ChatFormatting.GREEN));
        text.append("\n").append(texts.checkedCount(checked, snapshot.locations().size()));
        if (options.advancementGoal() > 0) {
            text.append("\n").append(texts.goal(checked, options.advancementGoal()));
        }
        if (options.eggShardsRequired() > 0) {
            int shards = snapshot.items().getOrDefault("Dragon Egg Shard", 0);
            text.append("\n").append(texts.eggShards(shards, options.eggShardsRequired()));
        }
        if (options.requiredBosses().dragon()) {
            text.append("\n").append(boss(texts, LogicWorld.EVENT_ENDER_DRAGON, snapshot.enderDragonInLogic(), tracker, snapshot));
        }
        if (options.requiredBosses().wither()) {
            text.append("\n").append(boss(texts, LogicWorld.EVENT_WITHER, snapshot.witherInLogic(), tracker, snapshot));
        }
        return text;
    }

    /** Whether the items received are enough to defeat a required boss, and which ones are missing if not. */
    private Component boss(Texts texts, String boss, boolean inLogic, Tracker tracker, Snapshot snapshot) {
        if (inLogic) {
            return Component.literal(texts.bossReady(boss)).withStyle(ChatFormatting.GREEN);
        }
        Map<String, Integer> missing = Requirements.missing(tracker.world().expandedLocationRule(boss), snapshot.items());
        return Component.literal(texts.bossMissing(boss, itemList(missing))).withStyle(ChatFormatting.RED);
    }

    private Node regionNode(Identifier id, String region, int row, List<LocationState> remaining, Texts texts,
                            LogicWorld world, LogicState logic, Snapshot snapshot) {
        boolean reachable = logic.canReachRegion(region);
        MutableComponent text = Component.empty();
        if (reachable) {
            text.append(Component.literal("✔ " + texts.reachable()).withStyle(ChatFormatting.GREEN));
        } else {
            Map<String, Integer> missing = Requirements.missing(world.expandedRegionRule(region), snapshot.items());
            text.append(Component.literal("✘ " + missingText(texts, missing)).withStyle(ChatFormatting.RED));
        }
        // A shuffled structure is not where vanilla puts it: say which dimension holds it.
        if (world.options().structures().containsValue(region)) {
            text.append("\n").append(texts.locatedIn(world.parentRegion(world.entranceOf(region))));
        }
        int doable = (int) remaining.stream().filter(location -> location.status() == Status.IN_LOGIC).count();
        text.append("\n").append(Component.literal(texts.regionCounts(doable, remaining.size())).withStyle(ChatFormatting.GRAY));

        Item icon = REGION_ICONS.getOrDefault(region, Items.MAP);
        return new Node(id, Optional.of(ROOT), new ItemStackTemplate(icon), Component.literal(region), text,
                AdvancementType.GOAL, 1, row, reachable);
    }

    private Node locationNode(Identifier id, Identifier parent, LocationState location, int x, int y, Texts texts,
                              boolean englishTitles, MinecraftServer server, Snapshot snapshot) {
        Optional<DisplayInfo> display = advancements.find(server, location.id()).flatMap(holder -> holder.value().display());
        boolean doable = location.status() == Status.IN_LOGIC;

        MutableComponent text = Component.empty();
        if (doable) {
            text.append(Component.literal("✔ " + texts.doable()).withStyle(ChatFormatting.GREEN));
        } else {
            text.append(Component.literal("✘ " + missingText(texts, location.missing())).withStyle(ChatFormatting.RED));
        }
        text.append("\n").append(Component.literal(texts.area(location.region())).withStyle(ChatFormatting.GRAY));
        if (location.excluded()) {
            text.append("\n").append(Component.literal(texts.fillerOnly()).withStyle(ChatFormatting.GRAY));
        }
        // Archipelago's chat and hints use the English names, whatever the client's language.
        if (!englishTitles) {
            text.append("\n").append(Component.literal(texts.archipelagoName(location.name())).withStyle(ChatFormatting.GRAY));
        }
        display.ifPresent(info -> text.append("\n").append(info.getDescription()));

        return new Node(id, Optional.of(parent),
                display.map(DisplayInfo::getIcon).orElseGet(() -> new ItemStackTemplate(Items.PAPER)),
                display.map(DisplayInfo::getTitle).orElseGet(() -> Component.literal(location.name())), text,
                display.map(DisplayInfo::getType).orElse(AdvancementType.TASK), x, y, doable);
    }

    static String missingText(Texts texts, Map<String, Integer> missing) {
        if (missing == null || missing.isEmpty()) {
            return texts.outOfLogic();
        }
        return texts.missing(itemList(missing));
    }

    /**
     * "Bucket, Progressive Tools 2, 3 Ender Pearls ×4". A progressive item is named by the level
     * needed, as in the randomizer's "Received Items" tab; any other item by the copies needed.
     */
    static String itemList(Map<String, Integer> missing) {
        if (missing == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        missing.forEach((item, needed) -> {
            if (needed <= 1) {
                parts.add(item);
            } else if (item.startsWith("Progressive ")) {
                parts.add(item + " " + needed);
            } else {
                parts.add(item + " ×" + needed);
            }
        });
        return String.join(", ", parts);
    }

    /**
     * Brings the client's tab in line with {@code desired}, sending only what changed. Removing an
     * advancement makes the client drop its children too, so those are sent again.
     */
    private void send(ServerPlayer player, View view, Map<Identifier, Node> desired) {
        Set<Identifier> gone = new HashSet<>();
        Set<Identifier> removed = new LinkedHashSet<>();
        for (Node old : view.sent.values()) {
            if (old.parent().isPresent() && gone.contains(old.parent().get())) {
                gone.add(old.id());
            } else if (!old.equals(desired.get(old.id()))) {
                gone.add(old.id());
                removed.add(old.id());
            }
        }

        List<AdvancementHolder> added = new ArrayList<>();
        Map<Identifier, AdvancementProgress> progress = new HashMap<>();
        for (Node node : desired.values()) {
            if (!view.sent.containsKey(node.id()) || gone.contains(node.id())) {
                added.add(node.toHolder());
                progress.put(node.id(), node.progress());
            }
        }
        if (removed.isEmpty() && added.isEmpty()) {
            return;
        }
        player.connection.send(new ClientboundUpdateAdvancementsPacket(false, added, removed, progress, false));
        view.sent.clear();
        view.sent.putAll(desired);
    }
}
