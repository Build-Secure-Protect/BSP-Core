package com.mrgregles.bsp_core.team;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One alliance: a name, its teams in the order they joined (each with the perks it gives its allies and whether it shares its
 * players' coordinates), and the last lines on the alliance's board. Every member of every team in it is an ally of every
 * member of the other teams; the rules that change an alliance live in {@link Alliances}.
 */
public final class Alliance {
    /** A team's place in the alliance: what it gives its allies ({@code TotemAccess} perk flags) and whether it shares coordinates. */
    public static final class Seat {
        public final long joinedMs;
        public int perks;
        public boolean shareCoords;

        public Seat(long joinedMs, int perks, boolean shareCoords) {
            this.joinedMs = joinedMs;
            this.perks = perks;
            this.shareCoords = shareCoords;
        }

        public Seat copy() {
            return new Seat(joinedMs, perks, shareCoords);
        }
    }

    /** A line on the alliance's board, with the speaker's team as it was when they spoke. */
    public record Message(int seq, long ms, UUID author, String authorName, String teamName, int colour, String text) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("Seq", seq);
            t.putLong("Ms", ms);
            t.putUUID("By", author);
            t.putString("ByName", authorName);
            t.putString("Team", teamName);
            t.putInt("Colour", colour);
            t.putString("Text", text);
            return t;
        }

        static Message load(CompoundTag t) {
            return new Message(t.getInt("Seq"), t.getLong("Ms"), t.getUUID("By"), t.getString("ByName"), t.getString("Team"), t.getInt("Colour"), t.getString("Text"));
        }
    }

    public final UUID id;
    public String name;
    public final long createdMs;
    private final Map<UUID, Seat> teams = new LinkedHashMap<>();
    private final List<Message> messages = new ArrayList<>();
    private int messageSeq;
    /** The newest line each player has read. */
    private final Map<UUID, Integer> reads = new HashMap<>();

    public Alliance(UUID id, String name, long createdMs) {
        this.id = id;
        this.name = name;
        this.createdMs = createdMs;
    }

    public Map<UUID, Seat> teams() {
        return teams;
    }

    public boolean has(UUID teamId) {
        return teams.containsKey(teamId);
    }

    public int size() {
        return teams.size();
    }

    void join(UUID teamId, long nowMs) {
        teams.putIfAbsent(teamId, new Seat(nowMs, 0, false));
    }

    Seat remove(UUID teamId) {
        return teams.remove(teamId);
    }

    public int messageSeq() {
        return messageSeq;
    }

    public void setMessageSeq(int seq) {
        messageSeq = Math.max(messageSeq, seq);
    }

    /** The board, oldest first. */
    public List<Message> messages() {
        return messages;
    }

    public Map<UUID, Integer> reads() {
        return reads;
    }

    /** Adds a line, drops the oldest beyond {@code keep} and marks it read for its author. */
    Message post(UUID author, String authorName, String teamName, int colour, String text, long nowMs, int keep) {
        Message m = new Message(++messageSeq, nowMs, author, authorName, teamName, colour, text);
        messages.add(m);
        while (messages.size() > Math.max(0, keep)) {
            messages.remove(0);
        }
        reads.put(author, messageSeq);
        return m;
    }

    public int readSeq(UUID player) {
        return reads.getOrDefault(player, 0);
    }

    /** How many lines on the board the player has not read. */
    public int unread(UUID player) {
        int read = readSeq(player);
        int n = 0;
        for (Message x : messages) {
            if (x.seq() > read) {
                n++;
            }
        }
        return n;
    }

    void markRead(UUID player) {
        reads.put(player, messageSeq);
    }

    /** A copy for the storage thread, which must never read an alliance the server thread is changing. */
    public Alliance copy() {
        Alliance a = new Alliance(id, name, createdMs);
        teams.forEach((k, v) -> a.teams.put(k, v.copy()));
        a.messages.addAll(messages);
        a.messageSeq = messageSeq;
        a.reads.putAll(reads);
        return a;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("Id", id);
        t.putString("Name", name);
        t.putLong("Created", createdMs);
        ListTag list = new ListTag();
        for (var e : teams.entrySet()) {
            CompoundTag s = new CompoundTag();
            s.putUUID("Team", e.getKey());
            s.putLong("Joined", e.getValue().joinedMs);
            s.putInt("Perks", e.getValue().perks);
            s.putBoolean("Share", e.getValue().shareCoords);
            list.add(s);
        }
        t.put("Teams", list);
        ListTag msgs = new ListTag();
        for (Message m : messages) {
            msgs.add(m.save());
        }
        t.put("Messages", msgs);
        t.putInt("Seq", messageSeq);
        ListTag read = new ListTag();
        for (var e : reads.entrySet()) {
            CompoundTag r = new CompoundTag();
            r.putUUID("Id", e.getKey());
            r.putInt("Seq", e.getValue());
            read.add(r);
        }
        t.put("Reads", read);
        return t;
    }

    public static Alliance load(CompoundTag t) {
        Alliance a = new Alliance(t.getUUID("Id"), t.getString("Name"), t.getLong("Created"));
        for (Tag x : t.getList("Teams", Tag.TAG_COMPOUND)) {
            CompoundTag s = (CompoundTag) x;
            a.teams.put(s.getUUID("Team"), new Seat(s.getLong("Joined"), s.getInt("Perks"), s.getBoolean("Share")));
        }
        for (Tag x : t.getList("Messages", Tag.TAG_COMPOUND)) {
            a.messages.add(Message.load((CompoundTag) x));
        }
        a.messageSeq = t.getInt("Seq");
        for (Tag x : t.getList("Reads", Tag.TAG_COMPOUND)) {
            CompoundTag r = (CompoundTag) x;
            a.reads.put(r.getUUID("Id"), r.getInt("Seq"));
        }
        return a;
    }
}
