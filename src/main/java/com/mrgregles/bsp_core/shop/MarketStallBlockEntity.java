package com.mrgregles.bsp_core.shop;

import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A Market Stall's lower half: which shop it runs and its opening hours (the skin lives in the
 * block state). Once a second the server checks the shop: BROKEN follows "no shop assigned" on
 * both halves; {@code open}, {@code stocked} and the counter item ({@code shown}, the shop's first
 * item card) are sent to clients for the renderer's lamps and display item, and the block beneath
 * is powered while open.
 */
public class MarketStallBlockEntity extends BlockEntity implements Assignable {
    public String shopId = "";
    public OpenHours hours = OpenHours.ALWAYS;
    /** Synced to clients: the open lamp, the stock lamp and the item on the counter. */
    public boolean open, stocked;
    public ItemStack shown = ItemStack.EMPTY;
    public String shopName = "";
    private int tick;

    public MarketStallBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MARKET_STALL.get(), pos, state);
    }

    public boolean assigned() {
        return !shopId.isEmpty();
    }

    @Override
    public void assign(String shopId, OpenHours hours, String mob, String mobNbt) {
        this.shopId = shopId;
        this.hours = hours;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            tick = 19; // re-check on the next tick
        }
    }

    @Override
    public String shopId() {
        return shopId;
    }

    @Override
    public OpenHours hours() {
        return hours;
    }

    public StallSkin skin() {
        return getBlockState().hasProperty(MarketStallBlock.SKIN) ? getBlockState().getValue(MarketStallBlock.SKIN) : StallSkin.VANILLA;
    }

    /** Changes the skin on both halves. */
    public void setSkin(StallSkin skin) {
        if (level != null) {
            MarketStallBlock.setBoth(level, worldPosition, s -> s.setValue(MarketStallBlock.SKIN, skin));
        }
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, MarketStallBlockEntity be) {
        if (++be.tick % 20 != 0) {
            return;
        }
        Shop shop = be.assigned() ? Shops.ledger(level.getServer()).shop(be.shopId) : null;
        boolean broken = shop == null;
        if (state.getValue(MarketStallBlock.BROKEN) != broken) {
            MarketStallBlock.setBoth(level, pos, s -> s.setValue(MarketStallBlock.BROKEN, broken));
        }
        boolean open = shop != null && shop.effectiveHours(be.hours).isOpen(level);
        boolean stocked = shop != null && Shops.anyStock(level.getServer(), shop);
        ItemStack first = shop == null ? ItemStack.EMPTY : Shops.firstItem(level.getServer(), shop);
        String name = shop == null ? "" : shop.name;
        boolean openChanged = be.open != open;
        if (openChanged || be.stocked != stocked || !ItemStack.matches(be.shown, first) || !be.shopName.equals(name)) {
            be.open = open;
            be.stocked = stocked;
            be.shown = first;
            be.shopName = name;
            be.setChanged();
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            if (openChanged) {
                level.updateNeighborsAt(pos, state.getBlock()); // the block beneath re-reads the signal
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Shop", shopId);
        tag.put("Hours", hours.save());
        tag.putBoolean("Open", open);
        tag.putBoolean("Stocked", stocked);
        tag.put("Shown", shown.save(new CompoundTag()));
        tag.putString("ShopName", shopName);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        shopId = tag.getString("Shop");
        hours = OpenHours.load(tag.getCompound("Hours"));
        open = tag.getBoolean("Open");
        stocked = tag.getBoolean("Stocked");
        shown = ItemStack.of(tag.getCompound("Shown"));
        shopName = tag.getString("ShopName");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0, 2, 0).inflate(0.5);
    }
}
