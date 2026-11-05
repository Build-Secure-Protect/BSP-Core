package com.mrgregles.bsp_core.totem;

import com.mrgregles.bsp_core.admin.SeasonData;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.storage.RemoteTotemLevels;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * How many live Shatter Totems a player owns, for the kits' "owns no totem" condition. Counted:
 * <ul>
 *   <li>totems in their name in their inventory (main, armour, offhand) and ender chest; an unowned totem there counts too,
 *       since it becomes theirs the next tick;</li>
 *   <li>totems placed in their name on this server ({@link TotemLedger#placedFor}), including one being stolen right now: it is
 *       theirs until the steal finishes;</li>
 *   <li>totems this server's ledger last saw with them, held (also in a chest or a machine, which comes back to them) or dropped,
 *       when the copy kept names them or names nobody;</li>
 *   <li>totems the ledger marked in transit for them within the last {@link #TRANSIT_TICKS} ticks (a server transfer, a death in
 *       a zone). An older mark is not counted: the origin server keeps the mark after the totem has arrived elsewhere, and a totem
 *       placed or stolen there must not lock the kit here for good;</li>
 *   <li>totems placed in their name on the network's other servers, as last read ({@link RemoteTotemLevels#placedElsewhere}).</li>
 * </ul>
 * Husks, copies of a reissued totem and totems from an earlier season do not count. Each totem counts once, by its id.
 * Server thread only. On a network a kit's REDEEM replaces the last read of the other servers with a fresh one
 * ({@link #local} plus {@code NetworkStorage.totemsElsewhere}).
 */
public final class TotemCount {
    private TotemCount() {}

    /** Ten minutes: far longer than any server transfer or respawn. */
    static final long TRANSIT_TICKS = 20L * 60 * 10;

    /** Every totem the player owns, with the other servers' placed totems as last read. */
    public static int of(ServerPlayer player) {
        return local(player) + RemoteTotemLevels.placedElsewhere(player.getUUID());
    }

    /** The totems the player owns that this server knows of by itself: everything in {@link #of} but the other servers' placed totems. */
    public static int local(ServerPlayer player) {
        UUID me = player.getUUID();
        TotemLedger ledger = TotemLedger.get(player.server);
        int season = SeasonData.get(player.server).number;
        Set<UUID> ids = new HashSet<>();
        int unnamed = 0;
        for (Container c : new Container[] {player.getInventory(), player.getEnderChestInventory()}) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!live(s.is(ModItems.SHATTER_TOTEM.get()) ? s.getTag() : null, s.is(ModItems.SHATTER_TOTEM.get()), me, season)) {
                    continue;
                }
                UUID id = TotemIdentity.id(s);
                if (id == null) {
                    unnamed++; // brand new, not given an id yet: still a totem in their hands
                } else if (!ledger.stale(id, TotemIdentity.instance(s))) {
                    ids.add(id);
                }
            }
        }
        long now = player.server.overworld().getGameTime();
        for (var e : ledger.sightings().entrySet()) {
            TotemLedger.Sighting s = e.getValue();
            boolean away = s.where() == TotemLedger.HELD || s.where() == TotemLedger.DROPPED
                    || s.where() == TotemLedger.IN_TRANSIT && now - s.lastSeen() <= TRANSIT_TICKS;
            if (away && me.equals(s.holder()) && live(s.copy(), true, me, season)) {
                ids.add(e.getKey());
            }
        }
        return ids.size() + unnamed + ledger.placedFor(me).size();
    }

    /** Whether a totem with this item tag is live and the player's: not spent, this season, and in their name or nobody's. */
    private static boolean live(@Nullable CompoundTag tag, boolean isTotem, UUID me, int season) {
        if (!isTotem) {
            return false;
        }
        if (tag == null) {
            return true;
        }
        if (tag.getBoolean(TotemIdentity.TAG_SPENT)) {
            return false;
        }
        // a totem with an id and no season stamp was made in season 1; one with neither is brand new (stamped when it gets its id)
        int made = tag.contains(TotemIdentity.TAG_SEASON) ? tag.getInt(TotemIdentity.TAG_SEASON) : tag.hasUUID(TotemIdentity.TAG_ID) ? 1 : season;
        if (made < season) {
            return false;
        }
        return TotemOwner.load(tag).map(o -> o.uuid().equals(me)).orElse(true);
    }
}
