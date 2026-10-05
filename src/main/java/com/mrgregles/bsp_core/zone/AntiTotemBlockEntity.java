package com.mrgregles.bsp_core.zone;

import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** The settings of one Anti Totem block: its zone (six distances), outline colour and blocked rules. Mirrored into {@link ZoneLedger}. */
public class AntiTotemBlockEntity extends BlockEntity {
    /** Outline colours an admin can pick between, to tell neighbouring zones apart. */
    public static final int[] COLOURS = {0xFF4B3C, 0xFF9A3A, 0xFFD23A, 0x8FE04A, 0x19D3B0, 0x3A8BFF, 0xB58CFF, 0xFF6BD0, 0xE8EAF0};

    private final int[] reach = {8, 8, 8, 8, 8, 8};
    private int colour;
    private final Set<String> blocked = new HashSet<>();
    private boolean fresh = true;

    public AntiTotemBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ANTI_TOTEM.get(), pos, state);
    }

    public int[] reach() {
        return reach;
    }

    public int colourIndex() {
        return colour;
    }

    public int colour() {
        return COLOURS[Mth.clamp(colour, 0, COLOURS.length - 1)];
    }

    public Set<String> blocked() {
        return blocked;
    }

    /** The zone as a box in world coordinates (whole blocks). */
    public AABB box() {
        BlockPos p = worldPosition;
        return new AABB(p.getX() - reach[ZoneLedger.W], p.getY() - reach[ZoneLedger.D], p.getZ() - reach[ZoneLedger.N],
                p.getX() + reach[ZoneLedger.E] + 1, p.getY() + reach[ZoneLedger.U] + 1, p.getZ() + reach[ZoneLedger.S] + 1);
    }

    public void configure(int[] newReach, int newColour, Collection<String> newBlocked) {
        for (int i = 0; i < 6; i++) {
            reach[i] = Mth.clamp(newReach[i], 0, ZoneLedger.MAX);
        }
        colour = Mth.clamp(newColour, 0, COLOURS.length - 1);
        blocked.clear();
        for (String key : newBlocked) {
            if (ZoneRules.known(key)) {
                blocked.add(key);
            }
        }
        fresh = false;
        publish();
    }

    /** Saves, tells clients, and updates the server-wide zone list. */
    private void publish() {
        setChanged();
        if (level instanceof ServerLevel sl) {
            sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            ZoneLedger.get(sl.getServer()).put(GlobalPos.of(sl.dimension(), worldPosition), reach, blocked);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) {
            if (fresh) {
                blocked.addAll(ZoneRules.defaults()); // just placed: totems and machines are kept out until an admin says otherwise
                fresh = false;
            }
            publish();
        }
    }

    /** The zone outline is drawn from this block and can reach far beyond it, so it is never culled with the block. */
    @Override
    public AABB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putIntArray("Reach", reach);
        tag.putInt("Colour", colour);
        ListTag rules = new ListTag();
        blocked.forEach(k -> rules.add(StringTag.valueOf(k)));
        tag.put("Blocked", rules);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        int[] r = tag.getIntArray("Reach");
        if (r.length == 6) {
            System.arraycopy(r, 0, reach, 0, 6);
            fresh = false;
            colour = tag.getInt("Colour");
            blocked.clear();
            for (Tag k : tag.getList("Blocked", Tag.TAG_STRING)) {
                blocked.add(k.getAsString());
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
