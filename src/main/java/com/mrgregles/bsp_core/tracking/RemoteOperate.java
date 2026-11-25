package com.mrgregles.bsp_core.tracking;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.team.Team;
import com.mrgregles.bsp_core.team.Teams;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Operating a tagged machine from the Holo Grid (owner decision MT3, MT29 to MT34): with the Remote Control unlock, while the player stands
 * inside the range of a powered Remote Control Transmitter of theirs or their team's that also covers the machine, a click on the machine's
 * box opens the machine's own screen as if they had right-clicked it (with an empty hand, through Forge's right-click event, so claim mods
 * still decide). Minecraft closes a screen when its player is too far from the block; for a screen opened this way {@link #stillValid}
 * answers instead (the mixins in {@code mixin/RemoteMenu*}), keeping it open while every condition holds, drawing the transmitter's FE each
 * tick, and closing it the moment one fails. Only on the machine's own server and world. Without the transmitter's Inventory Link
 * upgrade the screen is locked to the machine's own items ({@link InventoryLink}).
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class RemoteOperate {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    /** A screen opened from the Holo Grid: its menu, the machine, and the transmitter that carries it. */
    private record Session(AbstractContainerMenu menu, TrackedTag tag, GlobalPos transmitter, net.minecraft.world.level.block.entity.BlockEntity machine) {
    }

    private RemoteOperate() {}

    /** Opens the tag's machine's own screen for the player. Returns a refusal key, or null when the screen opened. */
    @Nullable
    public static String open(ServerPlayer player, String tagId) {
        MinecraftServer server = player.server;
        TrackerRecord r = TrackerOwners.get(server).record(server, player.getUUID());
        TrackedTag t = r.tags.stream().filter(x -> x.id().equals(tagId)).findFirst().orElse(null);
        if (t == null) {
            return "no_tag";
        }
        if (!TrackerOwners.ready(player.getUUID())) {
            return "loading";
        }
        if (!r.remoteIn(TrackerOwners.season(server))) {
            return "op_locked";
        }
        if (!t.server().equals(TrackerRules.serverId())) {
            return "op_other_server";
        }
        if (!player.level().dimension().location().equals(t.dim())) {
            return "op_other_world";
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = t.pos();
        if (!level.isLoaded(pos)) {
            return "op_asleep";
        }
        if (!TrackerRules.stillThere(level, t)) {
            return "op_gone";
        }
        if (!TrackerAccess.mayUse(level, pos, player)) {
            return "no_access";
        }
        RemoteTransmitterBlockEntity tx = transmitterFor(player, level, pos);
        if (tx == null) {
            return "op_no_transmitter";
        }
        if (!tx.powered()) {
            return "op_no_power";
        }
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer(); // whatever screen is open closes first (a locked one is settled as it closes)
        }
        AbstractContainerMenu before = player.containerMenu;
        boolean locked = !tx.inventoryLink();
        var machine = level.getBlockEntity(pos);
        var carried = machine == null ? null : InventoryLink.before(player, machine);
        boolean used = rightClick(player, level, pos, locked ? machine : null);
        if (locked && carried != null) {
            // whatever the use itself handed over goes back: the screen it opens is locked to the machine's own items
            InventoryLink.settleSince(player, machine, carried, player.containerMenu);
        }
        if (!used) {
            return "no_access";
        }
        if (player.containerMenu == before || player.containerMenu == player.inventoryMenu) {
            return "op_no_screen";
        }
        tx.operate();
        SESSIONS.put(player.getUUID(), new Session(player.containerMenu, t, GlobalPos.of(level.dimension(), tx.getBlockPos()), level.getBlockEntity(pos)));
        if (locked && machine != null) {
            InventoryLink.lock(player.containerMenu, machine, player); // no Inventory Link: items move only inside the machine
        }
        // after the screen's own opening packet, so the client knows which screen to cover
        com.mrgregles.bsp_core.network.BSPNetwork.sendTo(player, new com.mrgregles.bsp_core.network.RemoteLinkPacket(player.containerMenu.containerId, locked));
        return null;
    }

    /**
     * Right-clicks the block for the player with an empty main hand; false when a claim mod or the block refused. What the block puts in
     * the hand goes to the player, or, for a screen that opens locked ({@code lockedTo} its machine), back to the machine.
     */
    private static boolean rightClick(ServerPlayer player, ServerLevel level, BlockPos pos, @Nullable net.minecraft.world.level.block.entity.BlockEntity lockedTo) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        ItemStack held = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        try {
            PlayerInteractEvent.RightClickBlock event = ForgeHooks.onRightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
            if (event.isCanceled() || event.getUseBlock() == Event.Result.DENY) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            InteractionResult result = state.use(level, player, InteractionHand.MAIN_HAND, hit);
            return result.consumesAction();
        } finally {
            ItemStack now = player.getMainHandItem();
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            if (!now.isEmpty() && lockedTo != null) {
                InventoryLink.putBack(player, lockedTo, now);
            } else if (!now.isEmpty() && !player.getInventory().add(now)) {
                player.drop(now, false); // the block handed something over: never lost
            }
        }
    }

    /**
     * A powered-or-not transmitter here of the player's or their team's whose range covers both the player and the machine, or null.
     * Only loaded transmitters count.
     */
    @Nullable
    public static RemoteTransmitterBlockEntity transmitterFor(ServerPlayer player, ServerLevel level, BlockPos machine) {
        MinecraftServer server = player.server;
        RemoteTransmitterBlockEntity best = null;
        for (Map.Entry<GlobalPos, UUID> e : TransmitterLedger.get(server).all(server).entrySet()) {
            GlobalPos g = e.getKey();
            if (!g.dimension().equals(level.dimension()) || !level.isLoaded(g.pos()) || !mayUse(server, e.getValue(), player.getUUID())) {
                continue;
            }
            if (level.getBlockEntity(g.pos()) instanceof RemoteTransmitterBlockEntity tx && tx.covers(machine) && tx.covers(player.blockPosition())) {
                if (tx.powered() && tx.inventoryLink()) {
                    return tx; // a powered one with the Inventory Link is the best there is
                }
                if (best == null || (tx.powered() && !best.powered())) {
                    best = tx; // powered first; one in range without power is reported as "no power" rather than "no transmitter"
                }
            }
        }
        return best;
    }

    /** Whether a transmitter placed by {@code owner} serves {@code player}: theirs, or their team's (allies do not count). */
    public static boolean mayUse(MinecraftServer server, UUID owner, UUID player) {
        if (owner.equals(player)) {
            return true;
        }
        Team a = Teams.teamOf(server, owner), b = Teams.teamOf(server, player);
        return a != null && b != null && Objects.equals(a.id, b.id);
    }

    /**
     * For the mixins: whether the player's open menu is still valid, when it is one opened from the Holo Grid; null for any other menu
     * (vanilla's own check then runs). Checked a few times a tick; the transmitter is charged once a tick.
     */
    @Nullable
    public static Boolean stillValid(ServerPlayer player, AbstractContainerMenu menu) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || s.menu != menu) {
            return null;
        }
        String why = check(player, s);
        if (why != null) {
            SESSIONS.remove(player.getUUID());
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.bsp_core.tracking." + why)
                    .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
            return false;
        }
        return true;
    }

    /** What ends a session now, or null while it holds. */
    @Nullable
    private static String check(ServerPlayer player, Session s) {
        MinecraftServer server = player.server;
        if (!TrackerOwners.get(server).peek(server, player.getUUID()).remoteIn(TrackerOwners.season(server))) {
            return "op_locked";
        }
        if (!player.level().dimension().equals(s.transmitter.dimension())) {
            return "op_out_of_range";
        }
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(s.transmitter.pos()) || !(level.getBlockEntity(s.transmitter.pos()) instanceof RemoteTransmitterBlockEntity tx)
                || tx.getOwner() == null || !mayUse(server, tx.getOwner(), player.getUUID())) {
            return "op_no_transmitter";
        }
        if (!tx.covers(player.blockPosition()) || !tx.covers(s.tag.pos())) {
            return "op_out_of_range";
        }
        if (!level.isLoaded(s.tag.pos()) || !TrackerRules.stillThere(level, s.tag) || level.getBlockEntity(s.tag.pos()) != s.machine || s.machine == null
                || s.machine.isRemoved()) {
            return "op_gone"; // the very machine whose screen was opened, not one broken and placed again in its place
        }
        if (!TrackerAccess.mayUse(level, s.tag.pos(), player)) {
            return "no_access";
        }
        if (!tx.operate()) {
            return "op_no_power";
        }
        return null;
    }

    /** Whether the player has a screen open from the Holo Grid (for tests). */
    public static boolean operating(UUID player) {
        return SESSIONS.containsKey(player);
    }

    @SubscribeEvent
    public static void onClose(PlayerContainerEvent.Close event) {
        Session s = SESSIONS.get(event.getEntity().getUUID());
        if (s != null && s.menu == event.getContainer()) {
            SESSIONS.remove(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    /** The world key of a tag (for the client's checks). */
    static ResourceKey<net.minecraft.world.level.Level> world(TrackedTag t) {
        return ResourceKey.create(Registries.DIMENSION, t.dim());
    }
}
