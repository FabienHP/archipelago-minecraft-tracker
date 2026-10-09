package aptracker.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

@Mod(ApTracker.MODID)
public final class ApTracker {

    public static final String MODID = "aptracker";
    public static final Logger LOGGER = LogManager.getLogger();

    // The randomizer mod reads its .apmc file from this folder of the server directory.
    private static final Path AP_DATA_DIR = Path.of("APData");

    private final TrackerService service = new TrackerService();
    private final AdvancementLookup advancements = new AdvancementLookup();
    private final TrackerTab tab = new TrackerTab(service, advancements);
    private final Announcer announcer = new Announcer(advancements);
    private final TrackerCommand command = new TrackerCommand(service, tab, announcer);

    private static ApTracker instance;

    public ApTracker(IEventBus modEventBus) {
        instance = this;
        NeoForge.EVENT_BUS.register(this);
    }

    // For the game tests, which run inside the server next to the mod.
    static ApTracker instance() {
        return instance;
    }

    TrackerService service() {
        return service;
    }

    TrackerTab tab() {
        return tab;
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        advancements.load(event.getServer());
        service.start(AP_DATA_DIR);
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        service.stop();
        advancements.clear();
        tab.clear();
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        TrackerService.Update update = service.tick();
        if (update != null) {
            announcer.announce(event.getServer(), update);
        }
        tab.tick(event.getServer());
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tab.scheduleResend(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tab.forget(player);
        }
    }

    @SubscribeEvent
    public void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(tab::scheduleResend);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        command.register(event.getDispatcher());
    }
}
