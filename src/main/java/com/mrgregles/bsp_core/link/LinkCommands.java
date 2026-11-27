package com.mrgregles.bsp_core.link;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.chunk.ChunkLedger;
import com.mrgregles.bsp_core.chunk.ChunkLoading;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * {@code /bsp links} (operators): every loaded Plasma Accelerator with its owner and each link's far side, saying whether that far
 * chunk is held by a totem's Anchor, ticking for another reason, woken by the WAKE key, or asleep. {@code /bsp links held} lists every
 * chunk BSP-Core's Anchor holds. Neither loads anything.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class LinkCommands {
    private LinkCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("links").requires(src -> src.hasPermission(2))
                .executes(ctx -> send(ctx.getSource(), lines(ctx.getSource().getServer())))
                .then(Commands.literal("held").executes(ctx -> send(ctx.getSource(), heldLines(ctx.getSource().getServer()))));
    }

    private static int send(CommandSourceStack src, List<String> lines) {
        for (String l : lines) {
            src.sendSuccess(() -> Component.literal(l), false);
        }
        return lines.size();
    }

    /** The listing, one line per accelerator and one per link under it. */
    public static List<String> lines(MinecraftServer server) {
        List<AcceleratorBlockEntity> all = AcceleratorBlockEntity.loaded();
        int links = 0;
        for (AcceleratorBlockEntity a : all) {
            links += a.links().size();
        }
        List<String> out = new ArrayList<>();
        out.add("Plasma Accelerators loaded: " + all.size() + ", with " + links + " link(s). A link never loads its far side; the WAKE key is "
                + (AcceleratorBlockEntity.wakeAllowed() ? "on" : "off") + " (plasma.linkWake).");
        for (AcceleratorBlockEntity a : all) {
            GlobalPos here = a.here();
            out.add(dim(here) + " " + xyz(here.pos()) + " (chunk " + chunk(here.pos()) + "), owner " + owners(server, a) + ", "
                    + (a.isFormed() ? "formed" : "not formed") + ", " + a.links().size() + "/" + a.maxLinks() + " link(s), sending " + a.outRate() + " mB/t");
            int i = 1;
            for (GlobalPos at : a.links()) {
                Links.Report r = a.report(at);
                out.add("  " + i++ + ". " + dim(at) + " chunk " + chunk(at.pos()) + ", core " + xyz(at.pos()) + ": " + farState(server, a, at)
                        + "; last hop " + r.state().name().toLowerCase(Locale.ROOT) + (r.rate() > 0 ? " " + r.rate() + " mB/t" : ""));
            }
        }
        return out;
    }

    /** Where a link's far side stands right now, read without loading anything. */
    public static String farState(MinecraftServer server, AcceleratorBlockEntity a, GlobalPos at) {
        ServerLevel far = server.getLevel(at.dimension());
        if (far == null) {
            return "no such world";
        }
        long c = ChunkPos.asLong(at.pos());
        boolean loaded = far.getChunkSource().getChunkNow(ChunkPos.getX(c), ChunkPos.getZ(c)) != null;
        boolean ticking = far.getChunkSource().isPositionTicking(c);
        GlobalPos own = ChunkLoading.holder(at.dimension(), c, false), beside = own != null ? null : ChunkLoading.holder(at.dimension(), c, true);
        String s;
        if (own != null) {
            s = "held by the Anchor of the " + kind(server, own) + " at " + xyz(own.pos());
        } else if (beside != null) {
            s = "held (beside a chunk the Anchor of the " + kind(server, beside) + " at " + xyz(beside.pos()) + " holds)";
        } else if (a.woken(at, far)) {
            s = "woken by the WAKE key" + (ticking ? ", ticking" : ", loading");
        } else if (ticking) {
            s = "ticking, not held by BSP-Core (a player near it, or another mod)";
        } else {
            s = loaded ? "asleep (in memory, not ticking)" : "asleep (not loaded)";
        }
        if (ticking && !AcceleratorBlockEntity.runsMachines(far)) {
            s += ", but its world runs no machines (no players, no force-loaded chunks)";
        }
        return s;
    }

    /** Every chunk BSP-Core's Anchor holds, by the totem or projector it is held for. */
    public static List<String> heldLines(MinecraftServer server) {
        Map<GlobalPos, Set<Long>> held = ChunkLoading.held();
        int n = 0;
        for (Set<Long> s : held.values()) {
            n += s.size();
        }
        List<String> out = new ArrayList<>();
        out.add("Chunks BSP-Core's Anchor holds: " + n + ", for " + held.size() + " totem(s) and projector(s). Nothing else in BSP-Core keeps chunks loaded.");
        held.entrySet().stream()
                .sorted(java.util.Comparator.comparing((Map.Entry<GlobalPos, Set<Long>> e) -> e.getKey().dimension().location().toString())
                        .thenComparingLong(e -> e.getKey().pos().asLong()))
                .forEach(e -> {
                    StringBuilder b = new StringBuilder(dim(e.getKey()) + " " + kind(server, e.getKey()) + " at " + xyz(e.getKey().pos()) + ":");
                    e.getValue().stream().sorted().forEach(c -> b.append(" [").append(ChunkPos.getX(c)).append(", ").append(ChunkPos.getZ(c)).append("]"));
                    out.add(b.toString());
                });
        return out;
    }

    private static String kind(MinecraftServer server, GlobalPos source) {
        return ChunkLedger.get(server).totems.containsKey(source) ? "totem" : "projector";
    }

    private static String owners(MinecraftServer server, AcceleratorBlockEntity a) {
        Set<UUID> ids = a.access().owners;
        if (ids.isEmpty()) {
            return "none yet (never fed)";
        }
        List<String> names = new ArrayList<>();
        for (UUID id : ids) {
            names.add(name(server, id));
        }
        return String.join(", ", names);
    }

    private static String name(MinecraftServer server, UUID id) {
        @Nullable var cache = server.getProfileCache();
        return cache == null ? id.toString() : cache.get(id).map(com.mojang.authlib.GameProfile::getName).orElse(id.toString());
    }

    private static String dim(GlobalPos p) {
        return p.dimension().location().toString();
    }

    private static String xyz(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static String chunk(BlockPos p) {
        return "[" + (p.getX() >> 4) + ", " + (p.getZ() >> 4) + "]";
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        BSPCore.LOGGER.info("BSP-Core links: an accelerator never loads a far chunk by itself; the WAKE key is {} (plasma.linkWake)",
                AcceleratorBlockEntity.wakeAllowed()
                        ? "on: a background load of five seconds, at most once a minute per accelerator, only into worlds whose machines run"
                        : "off");
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        AcceleratorBlockEntity.forgetLoaded();
    }
}
