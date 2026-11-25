package com.mrgregles.bsp_core.tracking;

import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.registry.ModMenus;
import com.mrgregles.bsp_core.team.Team;
import com.mrgregles.bsp_core.team.Teams;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Remote Control Transmitter's screen on the server: its three range card slots and the player's inventory, the range, cards, charge
 * and draw as synced values, and, sent once when it opens, who placed it and their team (name, id, online here), for the heads.
 */
public class TransmitterMenu extends AbstractContainerMenu {
    public static final int CARD_X = 166, CARD_Y = 61, INV_X = 8, INV_Y = 160;
    /** Synced values; a menu's values travel as 16 bits, so the charge goes in two halves. */
    public static final int RANGE = 0, CARDS = 1, FE_LO = 2, FE_HI = 3, MAX_LO = 4, MAX_HI = 5, IN_USE = 6, FE_TICK = 7, LINK = 8, LINK_PRICE = 9, DATA = 10;
    /** The screen's buttons ({@code clickMenuButton}): buy the Inventory Link upgrade. */
    public static final int BUY_LINK = 0;

    /** One person who may use the transmitter, as the screen shows them. */
    public record Person(UUID id, String name, boolean owner, boolean online) {
    }

    public final RemoteTransmitterBlockEntity transmitter;
    private final ContainerData data;
    public final List<Person> people;

    /** Client: from the opening packet. */
    public TransmitterMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, tx(inv, buf.readBlockPos()), new SimpleContainerData(DATA), readPeople(buf));
    }

    private static RemoteTransmitterBlockEntity tx(Inventory inv, BlockPos pos) {
        return inv.player.level().getBlockEntity(pos) instanceof RemoteTransmitterBlockEntity t ? t
                : new RemoteTransmitterBlockEntity(pos, ModBlocks.REMOTE_TRANSMITTER.get().defaultBlockState());
    }

    private static List<Person> readPeople(FriendlyByteBuf buf) {
        int n = Math.min(64, buf.readVarInt());
        List<Person> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new Person(buf.readUUID(), buf.readUtf(32), buf.readBoolean(), buf.readBoolean()));
        }
        return out;
    }

    private TransmitterMenu(int id, Inventory inv, RemoteTransmitterBlockEntity tx, ContainerData data, List<Person> people) {
        super(ModMenus.REMOTE_TRANSMITTER.get(), id);
        this.transmitter = tx;
        this.data = data;
        this.people = people;
        for (int s = 0; s < RemoteTransmitterBlockEntity.SLOTS; s++) {
            addSlot(new SlotItemHandler(tx.getCards(), s, CARD_X + s * 22, CARD_Y) {
                @Override
                public void setChanged() {
                    super.setChanged();
                    tx.setChanged();
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, INV_X + col * 18, INV_Y + 58));
        }
        addDataSlots(data);
    }

    /** Opens the screen for a player beside the transmitter. */
    public static void open(ServerPlayer player, RemoteTransmitterBlockEntity tx) {
        List<Person> people = people(player, tx);
        MenuProvider provider = new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("block.bsp_core.remote_transmitter");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new TransmitterMenu(id, inv, tx, serverData(tx), people);
            }
        };
        NetworkHooks.openScreen(player, provider, buf -> {
            buf.writeBlockPos(tx.getBlockPos());
            buf.writeVarInt(people.size());
            for (Person p : people) {
                buf.writeUUID(p.id());
                buf.writeUtf(p.name().length() > 32 ? p.name().substring(0, 32) : p.name(), 32);
                buf.writeBoolean(p.owner());
                buf.writeBoolean(p.online());
            }
        });
    }

    /** The placer, then their team's other members, at most 16. */
    static List<Person> people(ServerPlayer viewer, RemoteTransmitterBlockEntity tx) {
        List<Person> out = new ArrayList<>();
        UUID owner = tx.getOwner();
        if (owner == null) {
            return out;
        }
        var server = viewer.server;
        out.add(new Person(owner, tx.getOwnerName(), true, server.getPlayerList().getPlayer(owner) != null));
        Team team = Teams.teamOf(server, owner);
        if (team != null) {
            team.members().forEach((id, m) -> {
                if (!id.equals(owner) && out.size() < 16) {
                    out.add(new Person(id, m.name, false, server.getPlayerList().getPlayer(id) != null));
                }
            });
        }
        return out;
    }

    private static ContainerData serverData(RemoteTransmitterBlockEntity tx) {
        return new ContainerData() {
            @Override
            public int get(int i) {
                return switch (i) {
                    case RANGE -> tx.range();
                    case CARDS -> tx.cardCount();
                    case FE_LO -> tx.energyStored() & 0xFFFF;
                    case FE_HI -> tx.energyStored() >>> 16;
                    case MAX_LO -> tx.energyCapacity() & 0xFFFF;
                    case MAX_HI -> tx.energyCapacity() >>> 16;
                    case IN_USE -> tx.inUse() ? 1 : 0;
                    case FE_TICK -> tx.fePerTick();
                    case LINK -> tx.inventoryLink() ? 1 : 0;
                    case LINK_PRICE -> Math.min(0xFFFF, linkPrice());
                    default -> 0;
                };
            }

            @Override
            public void set(int i, int v) {
            }

            @Override
            public int getCount() {
                return DATA;
            }
        };
    }

    public int data(int i) {
        return data.get(i) & 0xFFFF;
    }

    public int fe() {
        return data(FE_LO) | (data(FE_HI) << 16);
    }

    public int feMax() {
        return data(MAX_LO) | (data(MAX_HI) << 16);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem(), original = stack.copy();
        int n = RemoteTransmitterBlockEntity.SLOTS;
        if (index < n) {
            if (!moveItemStackTo(stack, n, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!stack.is(ModItems.RANGE_CARD.get()) || !moveItemStackTo(stack, 0, n, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /** The owner buys the Inventory Link upgrade from the screen (owner decision RC1). */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BUY_LINK || !(player instanceof ServerPlayer sp)) {
            return false;
        }
        String why = buyLink(sp, transmitter);
        sp.displayClientMessage(Component.translatable("message.bsp_core.transmitter." + (why == null ? "link_bought" : why))
                .withStyle(why == null ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED), true);
        return why == null;
    }

    /** Buys the Inventory Link upgrade for {@code tx} with Illyrium Shatter Coins from its owner's inventory. A refusal key, or null when bought. */
    @org.jetbrains.annotations.Nullable
    public static String buyLink(ServerPlayer player, RemoteTransmitterBlockEntity tx) {
        if (tx.inventoryLink()) {
            return "link_owned";
        }
        if (tx.getOwner() == null || !tx.getOwner().equals(player.getUUID())) {
            return "link_owner_only";
        }
        int price = linkPrice();
        if (price > 0 && !com.mrgregles.bsp_core.coin.CoinWallet.take(player, com.mrgregles.bsp_core.coin.CoinTier.ILLYRIUM, price)) {
            return "no_illyrium";
        }
        tx.setInventoryLink(true);
        com.mrgregles.bsp_core.BSPCore.LOGGER.info("{} bought the Inventory Link upgrade for the transmitter at {} for {} Illyrium Shatter Coins",
                player.getGameProfile().getName(), tx.getBlockPos().toShortString(), price);
        return null;
    }

    public static int linkPrice() {
        return com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.TRANSMITTER_LINK_ILLYRIUM, 1);
    }

    @Override
    public boolean stillValid(Player player) {
        return !transmitter.isRemoved() && player.distanceToSqr(transmitter.getBlockPos().getCenter()) <= 64.0;
    }
}
