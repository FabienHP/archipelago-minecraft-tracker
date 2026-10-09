package aptracker.neoforge;

import aptracker.core.Tracker.LocationState;
import aptracker.core.Tracker.Snapshot;
import aptracker.core.Tracker.Status;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** {@code /tracker}: lists what is doable right now and brings the tracker's tab forward. */
final class TrackerCommand {

    private final TrackerService service;
    private final TrackerTab tab;
    private final Announcer announcer;

    TrackerCommand(TrackerService service, TrackerTab tab, Announcer announcer) {
        this.service = service;
        this.tab = tab;
        this.announcer = announcer;
    }

    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tracker").executes(this::show));
    }

    private int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Texts texts = Texts.of(player);
        Snapshot snapshot = service.snapshot();
        if (snapshot == null) {
            context.getSource().sendFailure(Component.literal(texts.disabled()));
            return 0;
        }

        List<LocationState> doable = snapshot.withStatus(Status.IN_LOGIC);
        MutableComponent message = Component.literal(texts.doableCount(doable.size())).withStyle(ChatFormatting.GOLD);
        for (LocationState location : doable) {
            message.append(" ").append(announcer.name(player.level().getServer(), location));
        }
        message.append("\n").append(Component.literal(texts.openTheTab()).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(message);
        tab.select(player);
        return doable.size();
    }
}
