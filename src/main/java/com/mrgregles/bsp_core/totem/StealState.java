package com.mrgregles.bsp_core.totem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A steal attempt in progress on a placed totem.
 *
 * @param thief        who is stealing
 * @param thiefName    their name, for display
 * @param totalTicks   steal length in ticks when the attempt started
 * @param ticksLeft    steal ticks remaining
 * @param graceLeft    ticks the thief may still spend outside the radius; equals the configured
 *                     grace while inside the radius
 * @param outside      whether the thief is currently outside the radius
 */
public record StealState(UUID thief, String thiefName, int totalTicks, int ticksLeft, int graceLeft, boolean outside) {

    public StealState withTick(int ticksLeft, int graceLeft, boolean outside) {
        return new StealState(thief, thiefName, totalTicks, ticksLeft, graceLeft, outside);
    }

    /** 1.0 at the start, 0.0 when complete. */
    public float fractionLeft() {
        return totalTicks <= 0 ? 0 : Math.max(0f, Math.min(1f, ticksLeft / (float) totalTicks));
    }

    public void save(CompoundTag tag) {
        CompoundTag t = new CompoundTag();
        t.putUUID("Thief", thief);
        t.putString("ThiefName", thiefName);
        t.putInt("TotalTicks", totalTicks);
        t.putInt("TicksLeft", ticksLeft);
        t.putInt("GraceLeft", graceLeft);
        t.putBoolean("Outside", outside);
        tag.put("Steal", t);
    }

    @Nullable
    public static StealState load(CompoundTag tag) {
        if (!tag.contains("Steal")) {
            return null;
        }
        CompoundTag t = tag.getCompound("Steal");
        if (!t.hasUUID("Thief")) {
            return null;
        }
        int total = t.contains("TotalTicks") ? t.getInt("TotalTicks") : Math.max(1, t.getInt("TicksLeft"));
        return new StealState(t.getUUID("Thief"), t.getString("ThiefName"), total, t.getInt("TicksLeft"), t.getInt("GraceLeft"), t.getBoolean("Outside"));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(thief);
        buf.writeUtf(thiefName);
        buf.writeVarInt(totalTicks);
        buf.writeVarInt(ticksLeft);
        buf.writeVarInt(graceLeft);
        buf.writeBoolean(outside);
    }

    public static StealState read(FriendlyByteBuf buf) {
        return new StealState(buf.readUUID(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }
}
