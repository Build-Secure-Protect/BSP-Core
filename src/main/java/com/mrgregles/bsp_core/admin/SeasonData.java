package com.mrgregles.bsp_core.admin;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The season this server is in, its prize and holder reward rows, and the items still owed to
 * players who were offline when a prize or reward was paid, handed over at their next login.
 */
public class SeasonData extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_season";

    public int number = 1;
    public long startMs = System.currentTimeMillis();
    public String lastWinner = "";
    /** Goes up with every full reset. A player whose own number is lower has their Shatter Coins removed at login. */
    public int coinEpoch;
    public int lastWinnerPoints;
    /** When an admin last changed the prizes or the holder reward interval here; on a network the newest change wins. */
    public long settingsRev;
    public static final int PLACES = 3, PER_PLACE = 10, HOLDER_ROW = PLACES, ROWS = PLACES + 1, REWARD_SLOTS = ROWS * PER_PLACE;
    /** Hours between automatic holder rewards; 0 = only when an admin presses the button. */
    public int holderHours;
    public long lastHolderMs = System.currentTimeMillis();
    /**
     * Ten slots each for first, second and third place (this season's prizes, handed over and
     * emptied when the season ends), then ten for the holder reward (copied to every totem
     * holder at each payout, and never used up).
     */
    public final net.minecraftforge.items.ItemStackHandler rewards = new net.minecraftforge.items.ItemStackHandler(REWARD_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setDirty();
            AdminService.prizesChanged();
        }
    };
    /** Prize items owed to players who were offline when the season ended. */
    public final Map<UUID, java.util.List<net.minecraft.world.item.ItemStack>> pendingItems = new HashMap<>();

    public static SeasonData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SeasonData::load, SeasonData::new, DATA_NAME);
    }

    public int day() {
        return (int) ((System.currentTimeMillis() - startMs) / 86_400_000L) + 1;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Number", number);
        tag.putLong("StartMs", startMs);
        tag.putString("LastWinner", lastWinner);
        tag.putInt("CoinEpoch", coinEpoch);
        tag.putInt("LastWinnerPoints", lastWinnerPoints);
        tag.putLong("SettingsRev", settingsRev);
        tag.putInt("HolderHours", holderHours);
        tag.putLong("LastHolderMs", lastHolderMs);
        tag.put("Rewards", this.rewards.serializeNBT());
        ListTag items = new ListTag();
        pendingItems.forEach((id, stacks) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Id", id);
            ListTag list = new ListTag();
            stacks.forEach(s -> list.add(s.save(new CompoundTag())));
            e.put("Items", list);
            items.add(e);
        });
        tag.put("PendingItems", items);
        return tag;
    }

    private static SeasonData load(CompoundTag tag) {
        SeasonData data = new SeasonData();
        data.number = Math.max(1, tag.getInt("Number"));
        data.startMs = tag.getLong("StartMs");
        data.lastWinner = tag.getString("LastWinner");
        data.coinEpoch = tag.getInt("CoinEpoch");
        data.lastWinnerPoints = tag.getInt("LastWinnerPoints");
        data.settingsRev = tag.getLong("SettingsRev");
        data.holderHours = tag.getInt("HolderHours");
        data.lastHolderMs = tag.contains("LastHolderMs") ? tag.getLong("LastHolderMs") : System.currentTimeMillis();
        if (tag.contains("Rewards")) {
            net.minecraftforge.items.ItemStackHandler saved = new net.minecraftforge.items.ItemStackHandler();
            saved.deserializeNBT(tag.getCompound("Rewards")); // an older save may have fewer rows
            for (int i = 0; i < Math.min(saved.getSlots(), REWARD_SLOTS); i++) {
                data.rewards.setStackInSlot(i, saved.getStackInSlot(i));
            }
        }
        for (Tag raw : tag.getList("PendingItems", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) raw;
            if (e.hasUUID("Id")) {
                java.util.List<net.minecraft.world.item.ItemStack> stacks = new java.util.ArrayList<>();
                for (Tag s : e.getList("Items", Tag.TAG_COMPOUND)) {
                    stacks.add(net.minecraft.world.item.ItemStack.of((CompoundTag) s));
                }
                data.pendingItems.put(e.getUUID("Id"), stacks);
            }
        }
        return data;
    }
}
