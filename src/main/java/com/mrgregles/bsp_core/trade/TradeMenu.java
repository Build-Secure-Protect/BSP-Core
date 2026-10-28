package com.mrgregles.bsp_core.trade;

import com.mrgregles.bsp_core.market.Market;
import com.mrgregles.bsp_core.registry.ModMenus;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The trade screen's menu (look C, low and wide): your 12 offer slots at the left, the other side's 12 at the right (shown,
 * never touched), your inventory at the bottom left. While the countdown runs your offer slots are locked; refused items
 * (totems, Totem Cores, the config list) never go in. Closing it, or walking more than {@link Trades#REACH} blocks from the
 * Trade Post, cancels the trade and gives the items back.
 */
public class TradeMenu extends AbstractContainerMenu {
    public static final int W = 380, H = 212, MINE_X = 10, MINE_Y = 18, THEIRS_X = 298, INV_X = 10, INV_Y = 122;
    public static final int MINE = 0, THEIRS = TradeSession.SLOTS, INV = 2 * TradeSession.SLOTS, END = INV + 36;

    /** What the trade screen shows: the other side, both pieces and READY lights, the stage ("OPEN", "COUNTDOWN", "SETTLING", ...), the countdown, the chat. */
    public record State(String theirName, String theirServer, long myPieces, long theirPieces, boolean myReady, boolean theirReady, String stage,
                        long countdownMs, boolean locked, long balance, java.util.List<TradeSession.Line> chat) {
        public static final State EMPTY = new State("", "", 0, 0, false, false, "OPEN", 0, false, 0, java.util.List.of());
    }

    @Nullable
    private final Trade trade;
    /** Server: what this screen may send. Chat lines: four at once, then two a second (each is logged, sent to both and maybe published); everything else: eight, then eight a second. */
    public final RateLimit chatLimit = new RateLimit(4, 2), actionLimit = new RateLimit(8, 8);
    /** Client side: the last state from the server, and the message that came with it (a lang key, "" none). */
    public State shown = State.EMPTY;
    public String message = "";
    public java.util.List<String> messageArgs = java.util.List.of();
    public int serial;
    @Nullable
    private final UUID player;

    /** Client side: empty containers the server's slot updates fill. */
    public TradeMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, null, null, new SimpleContainer(TradeSession.SLOTS), new SimpleContainer(TradeSession.SLOTS));
    }

    /** Server side: {@code player}'s view of the trade (run here or on another server). */
    public TradeMenu(int id, Inventory inv, Trade trade, UUID player) {
        this(id, inv, trade, player, trade.mine(player), trade.theirs(player));
    }

    /** Server side, a trade run here: {@code side}'s view. */
    public TradeMenu(int id, Inventory inv, TradeSession session, TradeSession.Side side) {
        this(id, inv, session, side.id);
    }

    private TradeMenu(int id, Inventory inv, @Nullable Trade trade, @Nullable UUID player, Container mine, Container theirs) {
        super(ModMenus.TRADE.get(), id);
        this.trade = trade;
        this.player = player;
        for (int i = 0; i < TradeSession.SLOTS; i++) {
            addSlot(new Slot(mine, i, MINE_X + 1 + (i % 4) * 18, MINE_Y + 1 + (i / 4) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return !locked() && !Market.refused(stack);
                }

                @Override
                public boolean mayPickup(Player player) {
                    return !locked();
                }
            });
        }
        for (int i = 0; i < TradeSession.SLOTS; i++) {
            addSlot(new Slot(theirs, i, THEIRS_X + 1 + (i % 4) * 18, MINE_Y + 1 + (i / 4) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, INV_X + 1 + col * 18, INV_Y + 1 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, INV_X + 1 + col * 18, INV_Y + 59));
        }
    }

    @Nullable
    public Trade trade() {
        return trade;
    }

    public boolean locked() {
        return trade != null ? trade.locked() : shown.locked();
    }

    /** Opens the trade screen for a player here (a player on another server gets it from their own server). */
    public static void open(Player p, Trade t) {
        if (!(p instanceof net.minecraft.server.level.ServerPlayer sp) || sp.hasDisconnected()) {
            return;
        }
        net.minecraftforge.network.NetworkHooks.openScreen(sp, new net.minecraft.world.MenuProvider() {
            @Override
            public net.minecraft.network.chat.Component getDisplayName() {
                return net.minecraft.network.chat.Component.translatable("gui.bsp_core.trade.title");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player who) {
                return new TradeMenu(id, inv, t, who.getUUID());
            }
        }, buf -> {});
        sync(sp, "", java.util.List.of());
    }

    /** Sends the player's trade screen its state, with a message. */
    public static void sync(net.minecraft.server.level.ServerPlayer sp, String message, java.util.List<String> args) {
        if (sp.containerMenu instanceof TradeMenu m && m.trade != null && !m.trade.finished()) {
            com.mrgregles.bsp_core.network.BSPNetwork.sendTo(sp, new com.mrgregles.bsp_core.network.TradeStatePacket(m.trade.state(sp.getUUID()), message, args));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || index >= THEIRS && index < INV || locked()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < THEIRS) {
            if (!moveItemStackTo(stack, INV, END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (Market.refused(stack) || !moveItemStackTo(stack, MINE, THEIRS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.index >= INV && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean stillValid(Player player) {
        if (trade == null) {
            return true;
        }
        if (trade.finished()) {
            return false;
        }
        GlobalPos post = Trades.present(player.getUUID()).map(TradeHub.Present::post).orElse(null);
        return post == null || post.dimension() == player.level().dimension()
                && player.distanceToSqr(post.pos().getX() + 0.5, post.pos().getY() + 0.5, post.pos().getZ() + 0.5) <= Trades.REACH * Trades.REACH;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (trade != null && !trade.finished()) {
            boolean far = !stillValid(player);
            Trades.leave(player, far ? "far" : "closed");
        }
    }
}
