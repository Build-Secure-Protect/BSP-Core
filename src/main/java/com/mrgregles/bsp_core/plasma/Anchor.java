package com.mrgregles.bsp_core.plasma;

import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * The totem whose Anchor levels travel with some plasma, in its own world, with who owned it and who could edit it (owner, Upgrades
 * friends, teammates) when that plasma left it. A projector holds chunks under it only while the totem is still that owner's and
 * everyone who may edit the projector is among those editors ({@link #holdsFor}).
 */
public record Anchor(GlobalPos totem, @Nullable UUID owner, Set<UUID> editors) {
    public Anchor {
        editors = Set.copyOf(editors);
    }

    /** The anchor of a totem at {@code totem} owned by {@code owner}, editable by {@code access}'s editors. */
    public static Anchor of(GlobalPos totem, UUID owner, PlasmaAccess access) {
        return new Anchor(totem, owner, access == null ? Set.of() : access.editors);
    }

    /** Whether it knows who it belongs to: an anchor saved before owners and editors travelled with it knows nobody. */
    public boolean known() {
        return owner != null && !editors.isEmpty();
    }

    /** Whether every one of {@code ids} (at least one) is among this anchor's editors. */
    public boolean covers(Collection<UUID> ids) {
        return !ids.isEmpty() && editors.containsAll(ids);
    }

    /** Who the chunk ledger says owns the totem now, or null when it does not know it (or no server runs). */
    @Nullable
    public UUID ownerNow(@Nullable MinecraftServer server) {
        if (server == null) {
            return null;
        }
        var entry = com.mrgregles.bsp_core.chunk.ChunkLedger.get(server).totems.get(totem);
        return entry == null ? null : entry.owner;
    }

    /** Whether the totem has changed hands since this plasma left it (a totem the chunk ledger does not know has not). */
    public boolean changedHands(@Nullable MinecraftServer server) {
        UUID now = ownerNow(server);
        return now != null && !now.equals(owner);
    }

    /** Why a projector may or may not hold chunks under an anchor. */
    public enum Hold { HOLDS, STALE, OUTSIDERS }

    /**
     * Whether {@code ids} (a projector's editors, and who chose Anchor there) may hold chunks under this anchor: it knows its owner and
     * editors, the totem has not changed hands, and every one of {@code ids} is among the editors it carries. While the totem is loaded
     * its owner and editors are also read from the totem itself, so a friend or teammate removed since the plasma left it drops off.
     */
    public Hold hold(@Nullable MinecraftServer server, Collection<UUID> ids) {
        if (!known() || changedHands(server)) {
            return Hold.STALE;
        }
        if (!covers(ids)) {
            return Hold.OUTSIDERS;
        }
        Live live = live(server);
        if (live == null) {
            return Hold.HOLDS;
        }
        if (!live.owner.equals(owner)) {
            return Hold.STALE;
        }
        return live.editors.containsAll(ids) ? Hold.HOLDS : Hold.OUTSIDERS;
    }

    /** {@link #hold} is {@link Hold#HOLDS}. */
    public boolean holdsFor(@Nullable MinecraftServer server, Collection<UUID> ids) {
        return hold(server, ids) == Hold.HOLDS;
    }

    private record Live(UUID owner, Set<UUID> editors) {}

    /** The totem's owner and editors read from the totem now, or null when its chunk is not loaded or no owned totem stands there (never loads it). */
    @Nullable
    private Live live(@Nullable MinecraftServer server) {
        if (server == null) {
            return null;
        }
        net.minecraft.server.level.ServerLevel level = server.getLevel(totem.dimension());
        if (level == null || !level.isLoaded(totem.pos())
                || !(level.getBlockEntity(totem.pos()) instanceof com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity be) || be.getOwner().isEmpty()) {
            return null;
        }
        return new Live(be.getOwner().get().uuid(), PlasmaAccess.of(be).editors);
    }

    /**
     * Of several anchors arriving together, the one whose editors cover all of {@code joined}, then the one at the highest level, then
     * the one from {@code current}'s totem (so a tie does not swap totems and clear the projector's picked chunks), then the first.
     */
    @Nullable
    public static Anchor pick(java.util.List<Anchor> anchors, java.util.List<Integer> levels, Collection<UUID> joined, @Nullable Anchor current) {
        Anchor best = null;
        int bestLevel = -1;
        boolean bestCovers = false, bestCurrent = false;
        for (int i = 0; i < anchors.size(); i++) {
            Anchor a = anchors.get(i);
            if (a == null) {
                continue;
            }
            boolean c = a.covers(joined), cur = current != null && current.totem.equals(a.totem);
            int l = i < levels.size() ? levels.get(i) : 0;
            if (best == null || c && !bestCovers || c == bestCovers && (l > bestLevel || l == bestLevel && cur && !bestCurrent)) {
                best = a;
                bestLevel = l;
                bestCovers = c;
                bestCurrent = cur;
            }
        }
        return best;
    }

    /** Saves as {@code <key>Dim}, {@code <key>Pos}, {@code <key>Owner} and {@code <key>Editors}; a null anchor removes them. */
    public static void save(CompoundTag tag, String key, @Nullable Anchor anchor) {
        com.mrgregles.bsp_core.link.Links.save(tag, key, anchor == null ? null : anchor.totem);
        tag.remove(key + "Owner");
        tag.remove(key + "Editors");
        if (anchor == null) {
            return;
        }
        if (anchor.owner != null) {
            tag.putUUID(key + "Owner", anchor.owner);
        }
        long[] ids = new long[anchor.editors.size() * 2];
        int i = 0;
        for (UUID id : anchor.editors) {
            ids[i++] = id.getMostSignificantBits();
            ids[i++] = id.getLeastSignificantBits();
        }
        tag.putLongArray(key + "Editors", ids);
    }

    /** An anchor saved by {@link #save}; one saved before owners and editors were kept loads knowing nobody ({@link #known()} false). */
    @Nullable
    public static Anchor load(CompoundTag tag, String key) {
        GlobalPos totem = com.mrgregles.bsp_core.link.Links.load(tag, key);
        if (totem == null) {
            return null;
        }
        UUID owner = tag.hasUUID(key + "Owner") ? tag.getUUID(key + "Owner") : null;
        Set<UUID> editors = new java.util.HashSet<>();
        long[] ids = tag.getLongArray(key + "Editors");
        for (int i = 0; i + 1 < ids.length; i += 2) {
            editors.add(new UUID(ids[i], ids[i + 1]));
        }
        return new Anchor(totem, owner, editors);
    }

    /** Removes what {@link #save} wrote, for update tags the client never needs. */
    public static void strip(CompoundTag tag, String key) {
        tag.remove(key + "Dim");
        tag.remove(key + "Pos");
        tag.remove(key + "Owner");
        tag.remove(key + "Editors");
    }
}
