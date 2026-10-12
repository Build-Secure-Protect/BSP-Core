package com.mrgregles.bsp_core.shop;

import com.mrgregles.bsp_core.BSPConfig;
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

import javax.annotation.Nullable;
import java.util.List;

/**
 * A Stall Display: shows one shop's wares floating above it, one after another, or one item an
 * admin pinned. An admin picks the shop (and the style) from the display's own screen. The server
 * picks the item every few seconds from the shop's visible lots (skipping sold-out ones) and sends
 * it to clients; while the shop is closed by its own hours, missing or empty nothing floats and the
 * block shows its neglected look. Admins set the interval, the pin, the size and the hover height
 * from the screen; the style's own item point and scale are the defaults.
 */
public class StallDisplayBlockEntity extends BlockEntity {
    public String shopId = "";
    /** What floats now (client and server), and the shop's name. */
    public ItemStack shown = ItemStack.EMPTY;
    public String shopName = "";
    /** Admin settings: seconds between items (0 = config), the pinned item's index in the shop (-1 = all in turn), size and height in percent (0 = the style's own), the style. */
    public int intervalSec, pinned = -1, scalePct, heightPct, skinIndex = StallSkin.VANILLA.ordinal();
    private int tick, cursor;

    public StallDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STALL_DISPLAY.get(), pos, state);
    }

    public static int defaultIntervalSec() {
        return Math.max(1, BSPConfig.getOr(BSPConfig.DISPLAY_SECONDS, 4));
    }

    public StallSkin skin() {
        return StallSkin.values()[Math.floorMod(skinIndex, StallSkin.values().length)];
    }

    /** The style's own size and height for the floating item, in percent; a setting of 0 means these. */
    public int defaultScalePct() {
        return Math.round(skin().displayScale * 100);
    }

    public int defaultHeightPct() {
        return Math.round(skin().displayItem[1] / 16f * 100);
    }

    public float scale() {
        return (scalePct == 0 ? defaultScalePct() : scalePct) / 100f;
    }

    public float height() {
        return (heightPct == 0 ? defaultHeightPct() : heightPct) / 100f;
    }

    public boolean linked() {
        return !shopId.isEmpty();
    }

    /** Links the display to a shop by id; false if no such shop exists. */
    public boolean linkShop(String id) {
        if (!(level instanceof ServerLevel sl) || Shops.ledger(sl.getServer()).shop(id) == null) {
            return false;
        }
        shopId = id;
        pinned = -1;
        cursor = 0;
        setChanged();
        refreshNow();
        return true;
    }

    public void unlink() {
        shopId = "";
        pinned = -1;
        refreshNow();
    }

    public void setSkin(StallSkin skin) {
        skinIndex = skin.ordinal();
        refreshNow();
    }

    /** The shop it shows; null when none is set or it no longer exists. */
    @Nullable
    public Shop shop(ServerLevel level) {
        return shopId.isEmpty() ? null : Shops.ledger(level.getServer()).shop(shopId);
    }

    /** The shop if it is open now by its own hours; null otherwise. */
    @Nullable
    public Shop shopIfOpen(ServerLevel level) {
        Shop shop = shop(level);
        return shop != null && shop.hours.isOpen(level) ? shop : null;
    }

    /**
     * What should float now: the pinned item if it still exists in the shop, otherwise the next
     * visible item card with stock left, in turn; empty when there is nothing.
     */
    public ItemStack choose(ServerLevel level, Shop shop) {
        if (pinned >= 0) {
            ShopItem it = shop.item(pinned);
            if (it != null && it.section.isEmpty() && !it.stack.isEmpty()) {
                return it.stack.copy();
            }
        }
        List<Integer> visible = Shops.visible(level.getServer(), shop, System.currentTimeMillis());
        for (int tries = 0; tries < visible.size(); tries++) {
            int i = visible.get((cursor++) % visible.size());
            ShopItem it = shop.item(i);
            if (it != null && it.section.isEmpty() && !it.stack.isEmpty()) {
                var st = Shops.ledger(level.getServer()).stock(shop.id, i);
                if (it.stockGlobal < 0 || st.left != 0) {
                    return it.stack.copy();
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, StallDisplayBlockEntity be) {
        int every = 20 * (be.intervalSec > 0 ? be.intervalSec : defaultIntervalSec());
        if (++be.tick % every != 0) {
            return;
        }
        be.refresh(level);
    }

    /** Re-picks the floating item now (after a settings change). */
    public void refreshNow() {
        if (level instanceof ServerLevel sl) {
            tick = 0;
            refresh(sl);
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void refresh(ServerLevel level) {
        Shop shop = shopIfOpen(level);
        if (shop == null) {
            show(ItemStack.EMPTY, "");
        } else {
            show(choose(level, shop), shop.name);
        }
        // the block state carries the style and the neglected look (while nothing floats) for the model
        boolean broken = shown.isEmpty();
        BlockState st = level.getBlockState(worldPosition);
        if (st.hasProperty(StallDisplayBlock.SKIN) && (st.getValue(StallDisplayBlock.SKIN) != skin() || st.getValue(StallDisplayBlock.BROKEN) != broken)) {
            level.setBlock(worldPosition, st.setValue(StallDisplayBlock.SKIN, skin()).setValue(StallDisplayBlock.BROKEN, broken), 3);
        }
    }

    private void show(ItemStack stack, String name) {
        if (ItemStack.matches(shown, stack) && shopName.equals(name)) {
            return;
        }
        shown = stack;
        shopName = name;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("ShopId", shopId);
        tag.put("Shown", shown.save(new CompoundTag()));
        tag.putString("ShopName", shopName);
        tag.putInt("Interval", intervalSec);
        tag.putInt("Pinned", pinned);
        tag.putInt("Scale", scalePct);
        tag.putInt("Height", heightPct);
        tag.putInt("Skin", skinIndex);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        shopId = tag.getString("ShopId");
        shown = ItemStack.of(tag.getCompound("Shown"));
        shopName = tag.getString("ShopName");
        intervalSec = tag.getInt("Interval");
        pinned = tag.contains("Pinned") ? tag.getInt("Pinned") : -1;
        scalePct = tag.getInt("Scale");
        heightPct = tag.getInt("Height");
        skinIndex = tag.contains("Skin") ? tag.getInt("Skin") : StallSkin.VANILLA.ordinal();
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
        return new AABB(worldPosition).expandTowards(0, height() + scale(), 0).inflate(scale());
    }
}
