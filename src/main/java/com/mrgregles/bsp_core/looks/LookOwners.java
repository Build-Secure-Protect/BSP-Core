package com.mrgregles.bsp_core.looks;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import com.mrgregles.bsp_core.totem.TotemInventories;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Every player's {@link LookOwner} record on this server, kept in step with the network database when there is one. A player's
 * dressing and hats are checked against LuckPerms when they log in and every five minutes while online; when they lose
 * dressing (VIP lapsed) their totems' looks are hidden, not deleted, and come back with it. What a totem shows is
 * {@link #shown}.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class LookOwners extends SavedData {
    private static final String DATA_NAME = "bsp_core_look_owners";
    /** Ticks between checks of the online players' dressing and hats: five minutes. */
    public static final int CHECK_TICKS = 5 * 60 * 20;

    private final Map<UUID, LookOwner> owners = new HashMap<>();

    /** Called on the server thread when a player's record changes here or arrives from another server (task 3 refreshes their totems). */
    public static final List<BiConsumer<MinecraftServer, UUID>> CHANGED = new ArrayList<>();

    public static LookOwners get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(LookOwners::load, LookOwners::new, DATA_NAME);
    }

    /** The player's record, or null when they never had dressing or a hat. Read-only: change it through this class. */
    @Nullable
    public LookOwner record(UUID player) {
        return owners.get(player);
    }

    // ------------------------------------------------------------------ what a totem shows

    /** What a totem shows: its body look (null for the plain totem) and its hat ("" for none). */
    public record Shown(@Nullable TotemLook body, String hat) {
        public static final Shown PLAIN = new Shown(null, "");
    }

    /**
     * What a totem with this look shows for an owner with this record. The body, the head and the look's own hat need dressing:
     * when the owner has lost it (VIP lapsed) the look is hidden, not deleted. A totem with no look wears no hat while its owner
     * has dressing; an owner without dressing (a hat buyer, or a VIP lapsed) has every totem of theirs wear their hat of choice
     * among those they have, by default the newest bought.
     */
    public static Shown shown(@Nullable TotemLook look, @Nullable LookOwner owner) {
        if (owner == null) {
            return Shown.PLAIN;
        }
        if (owner.dressing && look != null) {
            String hat = look.hat().isEmpty() || owner.owned.contains(look.hat()) ? look.hat() : "";
            return new Shown(look, hat);
        }
        return new Shown(null, owner.plainHat());
    }

    /** What this totem shows now, from its look and its owner's record here. */
    public Shown shown(ShatterTotemBlockEntity totem) {
        TotemLook look = totem.getLook();
        return totem.getOwner().map(o -> shown(look, owners.get(o.uuid()))).orElse(Shown.PLAIN);
    }

    // ------------------------------------------------------------------ checks

    /**
     * Checks the player's dressing and hats against LuckPerms and records what changed: a hat seen for the first time is "bought" now.
     * Returns whether the record changed. A player who never had dressing or a hat gets no record.
     */
    public boolean check(ServerPlayer player) {
        UUID id = player.getUUID();
        Boolean dressing = TotemLooks.dressingKnown(player);
        if (dressing == null) {
            return false; // this server cannot tell: keep what the record says rather than share a guess
        }
        LookOwner was = owners.get(id);
        LookOwner now = was == null ? new LookOwner() : was.copy();
        now.dressing = dressing;
        now.owned.clear();
        now.owned.addAll(TotemLooks.hats(player));
        long ms = System.currentTimeMillis();
        List<String> bought = TotemLooks.boughtHats(player);
        now.bought.keySet().retainAll(bought);
        for (String h : bought) {
            now.bought.putIfAbsent(h, ms);
        }
        if (was == null ? !now.worthKeeping() : now.save().equals(was.save())) {
            return false;
        }
        if (was != null && was.dressing != now.dressing) {
            BSPCore.LOGGER.info("{} {} totem dressing", player.getGameProfile().getName(), now.dressing ? "has" : "no longer has");
        }
        put(player.server, id, now, true);
        return true;
    }

    /** Writes every record here up to the network (after the database was out of reach): the newer row and the later clear win there. */
    public void pushAll() {
        owners.forEach((id, r) -> {
            NetworkStorage.lookOwnerWrite(id, r.save(), r.updatedMs);
            if (r.clearedBefore > 0) {
                NetworkStorage.lookOwnerCleared(id, r.clearedBefore);
            }
        });
    }

    /** Stores a changed record, writes it to the network and tells listeners. */
    private void put(MinecraftServer server, UUID id, LookOwner record, boolean share) {
        record.updatedMs = Math.max(System.currentTimeMillis(), record.updatedMs + 1);
        owners.put(id, record);
        setDirty();
        if (share) {
            NetworkStorage.lookOwnerWrite(id, record.save(), record.updatedMs);
        }
        refreshTotems(server, id);
        CHANGED.forEach(l -> l.accept(server, id));
    }

    /** A record read from the network database: kept when it is newer than this server's; a staff clear is never lost. */
    public void arrived(MinecraftServer server, UUID id, CompoundTag data, long clearedMs) {
        LookOwner theirs = LookOwner.load(data);
        theirs.clearedBefore = Math.max(theirs.clearedBefore, clearedMs);
        LookOwner mine = owners.get(id);
        if (mine != null) {
            if (theirs.updatedMs <= mine.updatedMs && theirs.clearedBefore <= mine.clearedBefore) {
                return;
            }
            if (theirs.updatedMs <= mine.updatedMs) {
                mine.clearedBefore = theirs.clearedBefore; // only the clear is news
                theirs = mine;
            } else {
                theirs.clearedBefore = Math.max(theirs.clearedBefore, mine.clearedBefore);
            }
        }
        theirs.dropCleared();
        owners.put(id, theirs);
        setDirty();
        refreshTotems(server, id);
        CHANGED.forEach(l -> l.accept(server, id));
    }

    /** The owner's placed totems in loaded chunks here show what their record now allows; the rest catch up when they load. */
    public static void refreshTotems(MinecraftServer server, UUID owner) {
        for (GlobalPos pos : TotemLedger.get(server).placedFor(owner)) {
            ServerLevel level = server.getLevel(pos.dimension());
            if (level != null && level.isLoaded(pos.pos()) && level.getBlockEntity(pos.pos()) instanceof ShatterTotemBlockEntity totem) {
                totem.refreshLook();
            }
        }
    }

    // ------------------------------------------------------------------ the owner's choices

    /** Saves a look in the owner's list (slot 0 to {@link LookOwner#MAX_SAVED} - 1; the next free one with -1). Returns a refusal, or null. */
    @Nullable
    public String saveLook(ServerPlayer player, int slot, TotemLook look) {
        return saveLook(player, slot, look, false);
    }

    /** As {@link #saveLook(ServerPlayer, int, TotemLook)}; {@code demoAdmin}: an admin saving from a Demo Totem's tab, who needs no dressing. */
    @Nullable
    public String saveLook(ServerPlayer player, int slot, TotemLook look, boolean demoAdmin) {
        if (!look.owner().equals(player.getUUID())) {
            return "not_yours";
        }
        String problem = look.problem();
        if (problem != null) {
            return problem;
        }
        if (!demoAdmin && !TotemLooks.mayDress(player)) {
            return "no_dressing";
        }
        check(player);
        LookOwner r = owners.get(player.getUUID());
        r = r == null ? new LookOwner() : r.copy(); // an admin without dressing has no record until their first saved look
        if (slot < 0) {
            slot = r.saved.size();
        }
        if (slot > r.saved.size() || slot >= LookOwner.MAX_SAVED) {
            return "saved_full";
        }
        TotemLook kept = look.madeAt(System.currentTimeMillis());
        if (slot == r.saved.size()) {
            r.saved.add(kept);
        } else {
            r.saved.set(slot, kept);
        }
        put(player.server, player.getUUID(), r, true);
        return null;
    }

    /** Removes a saved look. */
    public void deleteLook(ServerPlayer player, int slot) {
        LookOwner r = owners.get(player.getUUID());
        if (r == null || slot < 0 || slot >= r.saved.size()) {
            return;
        }
        r = r.copy();
        r.saved.remove(slot);
        put(player.server, player.getUUID(), r, true);
    }

    /** The hat a player picks for totems no look of theirs dresses: a hat they have, {@link LookOwner#NO_HAT}, or "" for the newest. */
    @Nullable
    public String chooseHat(ServerPlayer player, String hat) {
        if (!hat.isEmpty() && !LookOwner.NO_HAT.equals(hat) && !TotemLooks.ownsHat(player, hat)) {
            return "hat_not_owned";
        }
        check(player);
        LookOwner r = owners.get(player.getUUID());
        if (r != null && r.hatChoice.equals(hat)) {
            return null; // nothing changed: nothing written, nothing sent round the network
        }
        r = r == null ? new LookOwner() : r.copy();
        r.hatChoice = hat;
        put(player.server, player.getUUID(), r, true);
        return null;
    }

    // ------------------------------------------------------------------ staff

    /** Whether staff took this look off after it was made (it is dropped wherever it turns up). */
    public boolean cleared(TotemLook look) {
        LookOwner r = owners.get(look.owner());
        return r != null && look.made() < r.clearedBefore;
    }

    /**
     * Takes every look off the player's totems and empties their saved looks: at once on their totems in loaded chunks here and the
     * totems they carry, and on every other totem (unloaded, in a chest, on another server) as soon as it loads or is placed.
     * Returns how many looks came off at once.
     */
    public int clearPlayer(MinecraftServer server, UUID player) {
        LookOwner r = owners.get(player);
        r = r == null ? new LookOwner() : r.copy();
        long now = System.currentTimeMillis();
        r.clearedBefore = now;
        r.saved.clear();
        put(server, player, r, true);
        NetworkStorage.lookOwnerCleared(player, now);
        int n = 0;
        for (GlobalPos pos : TotemLedger.get(server).placedFor(player)) {
            ServerLevel level = server.getLevel(pos.dimension());
            if (level != null && level.isLoaded(pos.pos()) && level.getBlockEntity(pos.pos()) instanceof ShatterTotemBlockEntity totem && totem.getLook() != null) {
                totem.setLook(null);
                n++;
            }
        }
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            n += clearCarried(online);
        }
        return n;
    }

    /** Takes the looks off the totems a player carries. */
    public static int clearCarried(ServerPlayer player) {
        int n = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (TotemInventories.isTotem(s) && TotemLook.readFor(s.getTag(), player.getUUID()) != null) {
                s.getTag().remove(TotemLook.TAG);
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LookActions.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // read the network's record first (it may have changed on another server), then check here; without a database, check now
            NetworkStorage.lookOwnerRead(player.server, player.getUUID(), () -> {
                if (!player.hasDisconnected()) {
                    get(player.server).check(player);
                }
            });
        }
    }

    private static int ticks;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks < CHECK_TICKS) {
            return;
        }
        ticks = 0;
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        LookOwners all = get(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            all.check(p);
        }
    }

    // ------------------------------------------------------------------ persistence

    private static LookOwners load(CompoundTag tag) {
        LookOwners l = new LookOwners();
        CompoundTag o = tag.getCompound("Owners");
        for (String k : o.getAllKeys()) {
            try {
                l.owners.put(UUID.fromString(k), LookOwner.load(o.getCompound(k)));
            } catch (IllegalArgumentException ignored) {
                // not a player id
            }
        }
        return l;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag o = new CompoundTag();
        owners.forEach((id, r) -> o.put(id.toString(), r.save()));
        tag.put("Owners", o);
        return tag;
    }
}
