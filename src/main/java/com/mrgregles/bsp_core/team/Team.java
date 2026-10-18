package com.mrgregles.bsp_core.team;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One team: a name and a colour, a leader, its members in the order they joined, one pinned note and
 * the last messages left for the team. Members count as friends with every access flag on each
 * other's totems (see {@code ShatterTotemBlockEntity.hasAccess}); the rules that change a team live
 * in {@link Teams}.
 */
public final class Team {
    /** A member: the name last seen and the newest message they have read. */
    public static final class Member {
        public String name;
        public final long joinedMs;
        public int readSeq;
        /** When this server last saw them online (login, logout, or joining); 0 when never. */
        public long seenMs;
        /** The network server they are online on right now, "" when none is known; from the database, never saved locally. */
        public String onlineServer = "";

        public Member(String name, long joinedMs, int readSeq) {
            this.name = name;
            this.joinedMs = joinedMs;
            this.readSeq = readSeq;
            this.seenMs = joinedMs;
        }
    }

    /** A line on the team's board. {@code seq} counts up for the team, so unread lines are those above a member's {@code readSeq}. */
    public record Message(int seq, long ms, UUID author, String authorName, String text) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("Seq", seq);
            t.putLong("Ms", ms);
            t.putUUID("By", author);
            t.putString("ByName", authorName);
            t.putString("Text", text);
            return t;
        }

        static Message load(CompoundTag t) {
            return new Message(t.getInt("Seq"), t.getLong("Ms"), t.getUUID("By"), t.getString("ByName"), t.getString("Text"));
        }
    }

    /** The note the leader pinned above the board. */
    public record Note(String text, UUID by, String byName, long ms) {}

    public final UUID id;
    public String name;
    public ChatFormatting colour;
    public UUID leader;
    public final long createdMs;
    private final Map<UUID, Member> members = new LinkedHashMap<>();
    @Nullable
    private Note pinned;
    private final List<Message> messages = new ArrayList<>();
    private int messageSeq;

    public Team(UUID id, String name, ChatFormatting colour, UUID leader, long createdMs) {
        this.id = id;
        this.name = name;
        this.colour = colour;
        this.leader = leader;
        this.createdMs = createdMs;
    }

    public Map<UUID, Member> members() {
        return members;
    }

    public boolean has(UUID player) {
        return members.containsKey(player);
    }

    public int size() {
        return members.size();
    }

    public boolean isLeader(UUID player) {
        return leader.equals(player);
    }

    void join(UUID player, String name, long nowMs) {
        members.put(player, new Member(name, nowMs, messageSeq));
    }

    @Nullable
    Member remove(UUID player) {
        return members.remove(player);
    }

    @Nullable
    public Note pinned() {
        return pinned;
    }

    public void pin(@Nullable Note note) {
        pinned = note;
    }

    /** The team's message counter: the seq of the newest line ever posted. */
    public int messageSeq() {
        return messageSeq;
    }

    /** Sets the counter when the team is read from the database. */
    public void setMessageSeq(int seq) {
        messageSeq = Math.max(messageSeq, seq);
    }

    /** The board, oldest first. */
    public List<Message> messages() {
        return messages;
    }

    /** Adds a line and drops the oldest beyond {@code keep}. */
    Message post(UUID author, String authorName, String text, long nowMs, int keep) {
        Message m = new Message(++messageSeq, nowMs, author, authorName, text);
        messages.add(m);
        while (messages.size() > Math.max(0, keep)) {
            messages.remove(0);
        }
        Member self = members.get(author);
        if (self != null) {
            self.readSeq = messageSeq;
        }
        return m;
    }

    /** How many lines on the board the member has not read. */
    public int unread(UUID player) {
        Member m = members.get(player);
        if (m == null) {
            return 0;
        }
        int n = 0;
        for (Message x : messages) {
            if (x.seq() > m.readSeq) {
                n++;
            }
        }
        return n;
    }

    void markRead(UUID player) {
        Member m = members.get(player);
        if (m != null) {
            m.readSeq = messageSeq;
        }
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("Id", id);
        t.putString("Name", name);
        t.putString("Colour", colour.getName());
        t.putUUID("Leader", leader);
        t.putLong("Created", createdMs);
        ListTag list = new ListTag();
        for (var e : members.entrySet()) {
            CompoundTag m = new CompoundTag();
            m.putUUID("Id", e.getKey());
            m.putString("Name", e.getValue().name);
            m.putLong("Joined", e.getValue().joinedMs);
            m.putInt("Read", e.getValue().readSeq);
            m.putLong("Seen", e.getValue().seenMs);
            list.add(m);
        }
        t.put("Members", list);
        if (pinned != null) {
            CompoundTag p = new CompoundTag();
            p.putString("Text", pinned.text());
            p.putUUID("By", pinned.by());
            p.putString("ByName", pinned.byName());
            p.putLong("Ms", pinned.ms());
            t.put("Pinned", p);
        }
        ListTag msgs = new ListTag();
        for (Message m : messages) {
            msgs.add(m.save());
        }
        t.put("Messages", msgs);
        t.putInt("Seq", messageSeq);
        return t;
    }

    public static Team load(CompoundTag t) {
        ChatFormatting colour = ChatFormatting.getByName(t.getString("Colour"));
        Team team = new Team(t.getUUID("Id"), t.getString("Name"), colour == null || !colour.isColor() ? ChatFormatting.AQUA : colour, t.getUUID("Leader"), t.getLong("Created"));
        for (Tag x : t.getList("Members", Tag.TAG_COMPOUND)) {
            CompoundTag m = (CompoundTag) x;
            Member member = new Member(m.getString("Name"), m.getLong("Joined"), m.getInt("Read"));
            member.seenMs = m.contains("Seen") ? m.getLong("Seen") : member.joinedMs;
            team.members.put(m.getUUID("Id"), member);
        }
        if (t.contains("Pinned")) {
            CompoundTag p = t.getCompound("Pinned");
            team.pinned = new Note(p.getString("Text"), p.getUUID("By"), p.getString("ByName"), p.getLong("Ms"));
        }
        for (Tag x : t.getList("Messages", Tag.TAG_COMPOUND)) {
            team.messages.add(Message.load((CompoundTag) x));
        }
        team.messageSeq = t.getInt("Seq");
        return team;
    }
}
