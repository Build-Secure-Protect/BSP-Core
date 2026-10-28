package com.mrgregles.bsp_core.lottery;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One draw's result: who won, how many pieces, when, out of how many players, and the draw's id (also in the log). */
public record LotteryWin(UUID id, String name, long pieces, long ms, int entrants, String draw) {
    /** How many past winners are kept. */
    public static final int KEEP = 10;
    public static final String STATE_KEY = "lottery_wins";

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name);
        tag.putLong("Pieces", pieces);
        tag.putLong("Ms", ms);
        tag.putInt("Entrants", entrants);
        tag.putString("Draw", draw);
        return tag;
    }

    public static LotteryWin load(CompoundTag tag) {
        return new LotteryWin(tag.getUUID("Id"), tag.getString("Name"), tag.getLong("Pieces"), tag.getLong("Ms"), tag.getInt("Entrants"), tag.getString("Draw"));
    }

    /** {@code win} put in front of {@code wins}, the list cut to {@link #KEEP}. */
    public static List<LotteryWin> prepend(LotteryWin win, List<LotteryWin> wins) {
        List<LotteryWin> out = new ArrayList<>();
        out.add(win);
        for (LotteryWin w : wins) {
            if (out.size() < KEEP) {
                out.add(w);
            }
        }
        return out;
    }

    /** The shared state's text for a list of wins, newest first. */
    public static String text(List<LotteryWin> wins) {
        ListTag list = new ListTag();
        wins.forEach(w -> list.add(w.save()));
        CompoundTag tag = new CompoundTag();
        tag.put("Wins", list);
        return tag.toString();
    }

    /** Wins from {@link #text}; missing or unreadable text gives none. */
    public static List<LotteryWin> fromText(String text) {
        List<LotteryWin> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        try {
            for (Tag t : TagParser.parseTag(text).getList("Wins", Tag.TAG_COMPOUND)) {
                try {
                    out.add(load((CompoundTag) t));
                } catch (RuntimeException e) {
                    // one unreadable win (no id, say) is left out, never the list
                }
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            // unreadable: no wins
        }
        return out;
    }
}
