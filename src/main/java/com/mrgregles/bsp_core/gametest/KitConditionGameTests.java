package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.admin.SeasonData;
import com.mrgregles.bsp_core.api.BspKits;
import com.mrgregles.bsp_core.api.event.KitEvent;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.kit.Kit;
import com.mrgregles.bsp_core.kit.KitCondition;
import com.mrgregles.bsp_core.kit.Kits;
import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.storage.RemoteTotemLevels;
import com.mrgregles.bsp_core.totem.TotemCount;
import com.mrgregles.bsp_core.totem.TotemIdentity;
import com.mrgregles.bsp_core.totem.TotemOwner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * GameTests for kit conditions: the "owns no totem" condition, what counts as owning a totem ({@link TotemCount}), where the
 * condition sits among the kit rules, and that gifts and owed kits skip it. Mock players are new each run, so the test world's
 * leftover totems never belong to them; every kit, ledger entry and cache a test makes is removed in a {@code finally}.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class KitConditionGameTests {
    private static final String EMPTY = "empty", BATCH = "kit_conditions";

    private static MinecraftServer server(GameTestHelper h) {
        return h.getLevel().getServer();
    }

    private static ServerPlayer survivor(GameTestHelper h) {
        return MockPlayers.serverPlayer(h, GameType.SURVIVAL);
    }

    /** A saved kit of 4 bread with the given conditions, once only. */
    private static Kit kit(GameTestHelper h, String prefix, String... conditions) {
        String id = (prefix + "_" + Long.toString(System.nanoTime() % 1_000_000_000L, 36)).toLowerCase(Locale.ROOT);
        Kit k = new Kit(id.substring(0, Math.min(24, id.length())), "Kit " + prefix, System.currentTimeMillis());
        k.items.add(new ItemStack(Items.BREAD, 4));
        k.once = true;
        k.conditions.addAll(List.of(conditions));
        Kits.Outcome out = Kits.put(server(h), k);
        h.assertTrue(out.ok(), "the kit saves: " + out.message());
        return k;
    }

    /** A live totem in {@code owner}'s name with an id, made this season; {@code owner} null leaves it unowned and without an id. */
    private static ItemStack totem(GameTestHelper h, ServerPlayer owner) {
        ItemStack t = new ItemStack(ModItems.SHATTER_TOTEM.get());
        if (owner != null) {
            new TotemOwner(owner.getUUID(), owner.getGameProfile().getName()).applyTo(t);
            TotemIdentity.ensure(t);
        }
        return t;
    }

    private static Kits.Status status(ServerPlayer p, Kit k) {
        return Kits.status(p, k);
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_no_totem_kit_is_only_for_a_player_with_no_totem(GameTestHelper h) {
        MinecraftServer server = server(h);
        Kit k = kit(h, "second", "no_totem");
        ServerPlayer a = survivor(h), b = survivor(h);
        try {
            h.assertTrue(status(a, k).ready(), "a player with no totem may take it: " + status(a, k));
            a.getInventory().add(totem(h, a));
            Kits.Status s = status(a, k);
            h.assertTrue(s.state() == Kits.State.CONDITION && s.text().equals("no_totem") && s.number() == 1, "one carried totem locks it: " + s);
            Kits.Outcome[] got = new Kits.Outcome[1];
            Kits.redeem(a, k.id, o -> got[0] = o);
            h.assertTrue(got[0] != null && !got[0].ok() && got[0].message() != null
                    && got[0].message().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                    && tc.getKey().equals("message.bsp_core.kit.condition.no_totem"), "REDEEM refuses with the reason: " + (got[0] == null ? "nothing" : got[0].message()));
            h.assertTrue(BspKits.status(a, k.id).map(x -> x.state().equals("CONDITION") && x.text().equals("no_totem")).orElse(false), "the API says why");

            a.getInventory().clearContent();
            a.getInventory().add(totem(h, null));
            h.assertTrue(TotemCount.of(a) == 1, "an unowned totem in their hands is theirs the next tick, so it counts");
            a.getInventory().clearContent();
            a.getInventory().add(totem(h, b));
            h.assertTrue(TotemCount.of(a) == 0, "someone else's totem in their hands is not theirs");
            a.getInventory().clearContent();
            ItemStack husk = totem(h, a);
            TotemIdentity.spend(husk);
            a.getInventory().add(husk);
            h.assertTrue(TotemCount.of(a) == 0, "a husk does not count");
            a.getInventory().clearContent();
            ItemStack old = totem(h, a);
            TotemIdentity.stampSeason(old, SeasonData.get(server).number - 1);
            a.getInventory().add(old);
            h.assertTrue(TotemCount.of(a) == 0, "a totem from an earlier season does not count");
            a.getInventory().clearContent();
            a.getInventory().offhand.set(0, totem(h, a));
            h.assertTrue(TotemCount.of(a) == 1, "the offhand counts");
            a.getInventory().clearContent();

            h.assertTrue(status(a, k).ready(), "with nothing left it is ready again");
            Kits.redeem(a, k.id, o -> got[0] = o);
            h.assertTrue(got[0].ok(), "and redeems: " + got[0].message());
            a.getInventory().add(totem(h, a));
            h.assertTrue(status(a, k).state() == Kits.State.REDEEMED, "once taken, the kit says so before the condition: " + status(a, k));
        } finally {
            Kits.delete(server, k.id);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void placed_stored_dropped_travelling_and_remote_totems_count(GameTestHelper h) {
        MinecraftServer server = server(h);
        TotemLedger ledger = TotemLedger.get(server);
        ServerPlayer a = survivor(h);
        UUID me = a.getUUID();
        GlobalPos spot = GlobalPos.of(h.getLevel().dimension(), h.absolutePos(new BlockPos(1, 2, 1)));
        ItemStack away = totem(h, a);
        UUID held = TotemIdentity.id(away), dropped = UUID.randomUUID(), travelling = UUID.randomUUID();
        long now = server.overworld().getGameTime();
        try {
            h.assertTrue(TotemCount.of(a) == 0, "a new player owns nothing");
            a.getEnderChestInventory().setItem(0, totem(h, a));
            h.assertTrue(TotemCount.of(a) == 1, "the ender chest counts");
            a.getEnderChestInventory().clearContent();

            ledger.recordPlaced(me, spot, 0);
            h.assertTrue(TotemCount.of(a) == 1, "a placed totem counts");
            ledger.recordRemoved(spot);
            h.assertTrue(TotemCount.of(a) == 0, "and stops counting when it is gone");

            ledger.seen(held, TotemIdentity.instance(away), me, TotemLedger.HELD, away.getTag(), now);
            h.assertTrue(TotemCount.of(a) == 1, "a totem last held by them (in a chest now, it comes back to them) counts");
            a.getInventory().add(away.copy());
            h.assertTrue(TotemCount.of(a) == 1, "a totem in hand and in the ledger counts once");
            a.getInventory().clearContent();
            ledger.retire(held);
            h.assertTrue(TotemCount.of(a) == 0, "a retired totem does not count");

            ledger.seen(dropped, UUID.randomUUID(), me, TotemLedger.DROPPED, totem(h, a).getTag(), now);
            h.assertTrue(TotemCount.of(a) == 1, "a dropped totem counts");
            ledger.forget(dropped);
            h.assertTrue(TotemCount.of(a) == 0, "forgotten");

            ItemStack moving = totem(h, a);
            ledger.inTransit(travelling, UUID.randomUUID(), me, moving.getTag(), now);
            h.assertTrue(TotemCount.of(a) == 1, "a totem travelling to another server right now counts");
            ledger.inTransit(travelling, ledger.currentInstance(travelling), me, moving.getTag(), now - 20L * 60 * 11);
            h.assertTrue(TotemCount.of(a) == 0, "an in-transit mark older than ten minutes does not: the totem arrived elsewhere long ago");

            RemoteTotemLevels.applyCounts(java.util.Map.of(me, 2));
            h.assertTrue(TotemCount.of(a) == 2 && RemoteTotemLevels.placedElsewhere(me) == 2, "totems placed on the other servers count");
            RemoteTotemLevels.apply(List.of(new RemoteTotemLevels.Row(me, "alarm=1")));
            h.assertTrue(RemoteTotemLevels.placedElsewhere(me) == 2, "the levels read does not change the counts (they come from their own, fresh read)");
        } finally {
            RemoteTotemLevels.apply(List.of());
            RemoteTotemLevels.applyCounts(java.util.Map.of());
            ledger.recordRemoved(spot);
            ledger.drop(held);
            ledger.drop(dropped);
            ledger.drop(travelling);
            a.getEnderChestInventory().clearContent();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void gifts_and_owed_kits_skip_the_conditions(GameTestHelper h) {
        MinecraftServer server = server(h);
        Kit k = kit(h, "gift", "no_totem");
        ServerPlayer a = survivor(h);
        try {
            a.getInventory().add(totem(h, a));
            h.assertTrue(status(a, k).state() == Kits.State.CONDITION, "locked for a player with a totem");
            h.assertTrue(Kits.give(server, a.getUUID(), k.id, KitEvent.How.ADMIN).ok(), "an admin's GIVE is not refused");
            h.assertTrue(status(a, k).state() == Kits.State.OWED, "the kit waits for them, owed: " + status(a, k));
            Kits.Outcome took = Kits.take(a, k.id);
            h.assertTrue(took.ok(), "TAKE IT delivers it although they own a totem: " + took.message());
            int bread = 0;
            for (ItemStack s : a.getInventory().items) {
                if (s.is(Items.BREAD)) {
                    bread += s.getCount();
                }
            }
            h.assertTrue(bread == 4, "the items landed: " + bread);
        } finally {
            Kits.delete(server, k.id);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void conditions_save_by_key_and_an_unknown_one_locks_the_kit(GameTestHelper h) {
        MinecraftServer server = server(h);
        Kit k = kit(h, "keys", "NO_TOTEM", "no_totem", " ", "from_the_future");
        ServerPlayer a = survivor(h);
        try {
            h.assertTrue(k.conditions.equals(List.of("no_totem", "from_the_future")), "saved by key, each once, known ones first: " + k.conditions);
            h.assertTrue(Kit.load(k.save()).conditions.equals(k.conditions) && k.copy().conditions.equals(k.conditions), "they save, load and copy");
            h.assertTrue(KitCondition.of("no_totem") == KitCondition.NO_TOTEM && KitCondition.of("NO_TOTEM") == KitCondition.NO_TOTEM
                    && KitCondition.of("from_the_future") == null, "conditions are found by key or name");
            Kits.Status s = status(a, k);
            h.assertTrue(s.state() == Kits.State.CONDITION && s.text().equals("unknown:from_the_future"), "a condition this version does not know is never met: " + s);
            h.assertTrue(BspKits.kit(server, k.id).map(i -> i.conditions().equals(k.conditions)).orElse(false), "the API lists them");
            k.conditions.clear();
            Kits.put(server, k);
            h.assertTrue(status(a, k).ready(), "a kit with no conditions is for anyone");
        } finally {
            Kits.delete(server, k.id);
        }
        h.succeed();
    }

    /** The refusal's lang key and arguments, or "ok". */
    private static String said(Kits.Outcome o) {
        if (o == null) {
            return "nothing";
        }
        if (o.ok()) {
            return "ok";
        }
        if (o.message() != null && o.message().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
            StringBuilder b = new StringBuilder(tc.getKey().replace("message.bsp_core.kit.", ""));
            for (Object arg : tc.getArgs()) {
                b.append(' ').append(arg instanceof net.minecraft.network.chat.Component c ? c.getString() : String.valueOf(arg));
            }
            return b.toString();
        }
        return "refused";
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void redeem_asks_the_network_afresh(GameTestHelper h) {
        MinecraftServer server = server(h);
        Kit k = kit(h, "fresh", "no_totem");
        ServerPlayer a = survivor(h);
        Kits.TotemsElsewhere real = Kits.totemsElsewhere;
        Integer[] answer = {0};
        java.util.List<java.util.function.Consumer<Integer>> waiting = new java.util.ArrayList<>();
        boolean[] defer = {false};
        Kits.totemsElsewhere = (p, then) -> {
            if (defer[0]) {
                waiting.add(then);
            } else {
                then.accept(answer[0]);
            }
        };
        Kits.Outcome[] got = new Kits.Outcome[1];
        try {
            h.assertTrue(Kit.split(Kit.joined(List.of("no_totem", "x"))).equals(List.of("no_totem", "x")), "the database column keeps the keys");

            answer[0] = 2;
            Kits.redeem(a, k.id, o -> got[0] = o);
            h.assertTrue(said(got[0]).equals("condition.no_totem Kit fresh 2"), "two totems on other servers refuse it: " + said(got[0]));

            answer[0] = null;
            Kits.redeem(a, k.id, o -> got[0] = o);
            h.assertTrue(said(got[0]).equals("condition.try_again Kit fresh"), "storage down: try again shortly: " + said(got[0]));

            answer[0] = 0;
            RemoteTotemLevels.applyCounts(java.util.Map.of(a.getUUID(), 1));
            h.assertTrue(status(a, k).state() == Kits.State.CONDITION, "the card goes by the last read, which still lists a totem elsewhere");
            a.getInventory().add(totem(h, a));
            Kits.redeem(a, k.id, o -> got[0] = o);
            h.assertTrue(said(got[0]).equals("condition.no_totem Kit fresh 1"), "a totem in hand still counts with the fresh read: " + said(got[0]));
            a.getInventory().clearContent();

            defer[0] = true;
            got[0] = null;
            Kits.redeem(a, k.id, o -> got[0] = o);
            h.assertTrue(got[0] == null && waiting.size() == 1, "on a network the answer waits for the database");
            Kits.Outcome[] second = new Kits.Outcome[1];
            Kits.redeem(a, k.id, o -> second[0] = o);
            h.assertTrue(said(second[0]).equals("pending Kit fresh") && waiting.size() == 1, "a second REDEEM meanwhile is refused at once: " + said(second[0]));
            waiting.get(0).accept(0);
            h.assertTrue(said(got[0]).equals("ok"), "the fresh read (none elsewhere) wins over the stale one and the kit is given: " + said(got[0]));
            h.assertTrue(status(a, k).state() == Kits.State.REDEEMED, "and it is recorded");

            waiting.clear();
            Kits.reset(server, a.getUUID(), k.id);
            got[0] = null;
            Kits.redeem(a, k.id, o -> got[0] = o);
            Kit edited = k.copy(); // the editor and the network both put a new object in place of the old one
            edited.enabled = false;
            Kits.put(server, edited);
            waiting.get(0).accept(0);
            h.assertTrue(said(got[0]).equals("hidden Kit fresh"), "the kit as it stands after the wait is the one checked: " + said(got[0]));
        } finally {
            Kits.totemsElsewhere = real;
            RemoteTotemLevels.applyCounts(java.util.Map.of());
            Kits.delete(server, k.id);
        }
        h.succeed();
    }

    /** The one Shatter Totem in the player's inventory, or empty when there is none or more than one. */
    private static ItemStack onlyTotem(ServerPlayer p) {
        ItemStack found = ItemStack.EMPTY;
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(ModItems.SHATTER_TOTEM.get())) {
                found = s;
                n += s.getCount();
            }
        }
        return n == 1 ? found : ItemStack.EMPTY;
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_kit_hands_every_taker_a_new_totem_of_their_own(GameTestHelper h) {
        MinecraftServer server = server(h);
        int season = SeasonData.get(server).number;
        ServerPlayer admin = survivor(h), a = survivor(h), b = survivor(h);
        ItemStack adminsTotem = totem(h, admin);
        com.mrgregles.bsp_core.totem.TotemUpgrades.setTier(adminsTotem, 2);
        String id = ("mint_" + Long.toString(System.nanoTime() % 1_000_000_000L, 36)).toLowerCase(Locale.ROOT);
        Kit k = new Kit(id.substring(0, Math.min(24, id.length())), "Kit mint", System.currentTimeMillis());
        k.items.add(adminsTotem.copy());
        k.items.add(new ItemStack(Items.BREAD, 2));
        k.icon = adminsTotem.copy();
        try {
            h.assertTrue(Kits.put(server, k).ok(), "the kit saves");
            h.assertTrue(!k.items.get(0).hasTag() && k.items.get(0).is(ModItems.SHATTER_TOTEM.get()) && !k.icon.hasTag(),
                    "the admin's totem is kept as a blank one (no id, owner, season or tier): " + k.items.get(0).getTag());

            ItemStack box = new ItemStack(Items.SHULKER_BOX);
            net.minecraft.nbt.ListTag inside = new net.minecraft.nbt.ListTag();
            net.minecraft.nbt.CompoundTag slot = totem(h, admin).save(new net.minecraft.nbt.CompoundTag());
            slot.putByte("Slot", (byte) 0);
            inside.add(slot);
            net.minecraft.nbt.CompoundTag bet = new net.minecraft.nbt.CompoundTag();
            bet.put("Items", inside);
            box.getOrCreateTag().put("BlockEntityTag", bet);
            Kit boxed = new Kit("boxed_kit", "Boxed", 0);
            boxed.items.add(box);
            h.assertTrue(Kits.check(boxed) != null, "a totem inside a shulker box is refused (it would go out as a copy of that one totem)");

            Kit older = new Kit("old_kit", "Old", 0);
            ItemStack lastSeason = totem(h, admin);
            TotemIdentity.stampSeason(lastSeason, season - 1);
            older.items.add(lastSeason);
            net.minecraft.nbt.CompoundTag saved = older.save();
            h.assertTrue(!Kit.load(saved).items.get(0).hasTag(), "a kit saved with a live totem before this rule is cleaned when it loads");

            Kits.Outcome[] got = new Kits.Outcome[1];
            Kits.redeem(a, k.id, o -> got[0] = o);
            h.assertTrue(got[0] != null && got[0].ok(), "A redeems: " + (got[0] == null ? "nothing" : got[0].message()));
            Kits.redeem(b, k.id, o -> got[0] = o);
            h.assertTrue(got[0].ok(), "B redeems: " + got[0].message());
            ItemStack ta = onlyTotem(a), tb = onlyTotem(b);
            h.assertTrue(!ta.isEmpty() && !tb.isEmpty(), "each has exactly one totem");
            UUID ia = TotemIdentity.id(ta), ib = TotemIdentity.id(tb);
            h.assertTrue(ia != null && ib != null && !ia.equals(ib) && !ia.equals(TotemIdentity.id(adminsTotem)), "two different totems, neither the admin's");
            h.assertTrue(TotemOwner.fromStack(ta).map(o -> o.uuid().equals(a.getUUID())).orElse(false)
                    && TotemOwner.fromStack(tb).map(o -> o.uuid().equals(b.getUUID())).orElse(false), "each in the taker's name");
            h.assertTrue(TotemIdentity.season(ta) == season && !TotemIdentity.isSpent(ta), "made this season, live");
            h.assertTrue(com.mrgregles.bsp_core.totem.TotemUpgrades.getTier(ta) == 0, "Tier I, not the admin's tier");
            h.assertTrue(TotemCount.of(a) == 1, "and it is counted as theirs");
            h.assertTrue(Kits.test(admin, k.id).ok() && TotemOwner.fromStack(onlyTotem(admin)).map(o -> o.uuid().equals(admin.getUUID())).orElse(false),
                    "TEST ON ME gives the admin a new one in their own name too");
        } finally {
            Kits.delete(server, k.id);
        }
        h.succeed();
    }

    /** A placed totem made for {@code owner} (its own id), at {@code pos}. */
    private static com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity placed(GameTestHelper h, BlockPos pos, ServerPlayer owner) {
        h.setBlock(pos, com.mrgregles.bsp_core.registry.ModBlocks.SHATTER_TOTEM.get());
        var be = (com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity) h.getBlockEntity(pos);
        be.loadFromStack(com.mrgregles.bsp_core.totem.TotemMint.fresh(owner));
        be.setOwner(new TotemOwner(owner.getUUID(), owner.getGameProfile().getName()));
        return be;
    }

    /** Stands {@code p} two blocks east of the totem at {@code pos}, inside the steal radius. */
    private static void near(GameTestHelper h, ServerPlayer p, BlockPos pos) {
        BlockPos at = h.absolutePos(pos);
        p.setPos(at.getX() + 2.5, at.getY(), at.getZ() + 0.5);
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_steal_back_soon_after_a_kit_totem_is_refused(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer owner = survivor(h), friend = survivor(h), mate = survivor(h), other = survivor(h);
        Kit k = kit(h, "steal_back");
        k.items.add(new ItemStack(ModItems.SHATTER_TOTEM.get()));
        Kits.put(server, k);
        BlockPos pos = new BlockPos(4, 2, 4), pos2 = new BlockPos(8, 2, 4);
        var realClock = com.mrgregles.bsp_core.totem.StealBacks.clock;
        long[] now = {System.currentTimeMillis()};
        com.mrgregles.bsp_core.totem.StealBacks.clock = () -> now[0];
        java.util.List<String> started = new java.util.ArrayList<>();
        java.util.function.Consumer<com.mrgregles.bsp_core.api.event.TotemEvent.StealStarted> listener = e -> started.add(String.valueOf(e.getThief()));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener);
        int hoursBefore = com.mrgregles.bsp_core.BSPConfig.KIT_STEAL_BACK_HOURS.get();
        var realNaughty = com.mrgregles.bsp_core.totem.StealBacks.naughty;
        java.util.List<UUID> shown = new java.util.ArrayList<>();
        com.mrgregles.bsp_core.totem.StealBacks.naughty = p -> shown.add(p.getUUID());
        try {
            var be = placed(h, pos, owner);
            UUID id = be.totemId();
            h.assertTrue(id != null, "the placed totem has an id");
            near(h, friend, pos);
            be.tryStartSteal(friend);
            h.assertTrue(be.getSteal().isPresent() && be.finishStealBy(friend), "the friend steals it");
            h.assertTrue(be.getOwner().map(o -> o.uuid().equals(friend.getUUID())).orElse(false), "it is the friend's now");

            near(h, owner, pos);
            be.tryStartSteal(owner);
            h.assertTrue(be.getSteal().isPresent(), "without a kit totem the owner may steal it back as usual");
            be.adminCancelSteal();

            h.assertTrue(Kits.test(owner, k.id).ok(), "TEST ON ME hands a totem");
            be.tryStartSteal(owner);
            h.assertTrue(be.getSteal().isPresent(), "but starts no watch");
            be.adminCancelSteal();
            owner.getInventory().clearContent();

            be.tryStartSteal(owner);
            h.assertTrue(be.getSteal().isPresent(), "the owner starts stealing it back before taking any kit");
            h.assertTrue(Kits.give(server, owner.getUUID(), k.id, KitEvent.How.ADMIN).ok() && Kits.take(owner, k.id).ok(), "a kit hands the owner a new totem meanwhile");
            h.assertTrue(be.finishStealBy(owner) && be.getSteal().isEmpty(), "the steal reaches its end");
            h.assertTrue(be.getOwner().map(o -> o.uuid().equals(friend.getUUID())).orElse(false), "but fails: a steal started before the kit cannot finish after it");
            h.assertTrue(shown.equals(List.of(owner.getUUID())), "and the thief is shown NAUGHTY NAUGHTY: " + shown);

            started.clear();
            be.tryStartSteal(owner);
            h.assertTrue(be.getSteal().isEmpty() && started.isEmpty(), "a new steal-back is refused: no steal, no StealStarted event: " + started);
            h.assertTrue(shown.equals(List.of(owner.getUUID(), owner.getUUID())), "and shown NAUGHTY NAUGHTY again: " + shown);

            var fresh = placed(h, pos2, friend);
            near(h, owner, pos2);
            fresh.tryStartSteal(owner);
            h.assertTrue(fresh.getSteal().isPresent(), "a totem that was never theirs can be stolen as usual");
            fresh.adminCancelSteal();

            if (com.mrgregles.bsp_core.team.Teams.enabled()) {
                String team = ("Sb " + Long.toString(System.nanoTime() % 1_000_000_000L, 36));
                h.assertTrue(com.mrgregles.bsp_core.team.Teams.create(owner, team.substring(0, Math.min(16, team.length())), net.minecraft.ChatFormatting.GOLD).ok()
                        && com.mrgregles.bsp_core.team.Teams.invite(owner, mate).ok() && com.mrgregles.bsp_core.team.Teams.accept(mate).ok(), "owner and mate share a team");
                near(h, mate, pos);
                be.tryStartSteal(mate);
                h.assertTrue(be.getSteal().isEmpty() && shown.size() == 3 && shown.get(2).equals(mate.getUUID()), "a teammate cannot steal it back for them either, and sees the same");
            }

            int before = shown.size();
            near(h, other, pos);
            be.tryStartSteal(other);
            h.assertTrue(be.getSteal().isPresent() && shown.size() == before, "anyone else may steal it, and sees nothing of the rule");
            be.adminCancelSteal();

            now[0] += 3L * 3_600_000L + 1000L;
            near(h, owner, pos);
            be.tryStartSteal(owner);
            h.assertTrue(be.getSteal().isPresent(), "after 3 hours the owner may steal it back");
            be.adminCancelSteal();

            now[0] -= 3L * 3_600_000L;
            com.mrgregles.bsp_core.BSPConfig.KIT_STEAL_BACK_HOURS.set(0);
            be.tryStartSteal(owner);
            h.assertTrue(be.getSteal().isPresent(), "stealBackHours = 0 turns the rule off");
            be.adminCancelSteal();
            com.mrgregles.bsp_core.BSPConfig.KIT_STEAL_BACK_HOURS.set(hoursBefore);

            UUID elsewhere = UUID.randomUUID(), totem = UUID.randomUUID();
            com.mrgregles.bsp_core.totem.StealBacks.applyNetwork(java.util.Map.of(elsewhere,
                    new com.mrgregles.bsp_core.totem.StealBacks.Watch(now[0] + 60_000L, java.util.Set.of(totem))));
            h.assertTrue(elsewhere.equals(com.mrgregles.bsp_core.totem.StealBacks.refuses(server, elsewhere, totem))
                    && com.mrgregles.bsp_core.totem.StealBacks.refuses(server, elsewhere, UUID.randomUUID()) == null,
                    "a watch started on another server counts once read, for the totems it names");
        } finally {
            com.mrgregles.bsp_core.BSPConfig.KIT_STEAL_BACK_HOURS.set(hoursBefore);
            com.mrgregles.bsp_core.totem.StealBacks.naughty = realNaughty;
            com.mrgregles.bsp_core.totem.StealBacks.clock = realClock;
            com.mrgregles.bsp_core.totem.StealBacks.clearNetwork();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);
            for (ServerPlayer p : List.of(owner, friend, mate, other)) {
                com.mrgregles.bsp_core.totem.StealBacks.forget(server, p.getUUID());
            }
            if (com.mrgregles.bsp_core.team.Teams.teamOf(server, owner.getUUID()) != null) {
                com.mrgregles.bsp_core.team.Teams.leave(mate);
                com.mrgregles.bsp_core.team.Teams.leave(owner);
            }
            Kits.delete(server, k.id);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void the_kit_card_carries_its_conditions(GameTestHelper h) {
        MinecraftServer server = server(h);
        Kit k = kit(h, "card", "no_totem");
        ServerPlayer a = survivor(h);
        try {
            a.getInventory().add(totem(h, a));
            var sent = com.mrgregles.bsp_core.network.KitViewPacket.of(a, false);
            var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            com.mrgregles.bsp_core.network.KitViewPacket.encode(sent, buf);
            var got = com.mrgregles.bsp_core.network.KitViewPacket.decode(buf);
            var card = got.cards().stream().filter(c -> c.id().equals(k.id)).findFirst().orElse(null);
            h.assertTrue(card != null && card.state().equals("CONDITION") && card.text().equals("no_totem"), "the card is locked by the condition: " + card);
            h.assertTrue(card.conds().equals(List.of(new com.mrgregles.bsp_core.network.KitViewPacket.Cond("no_totem", false, 1))),
                    "and lists it, unmet, with the totem count, after the trip over the network: " + card.conds());
            a.getInventory().clearContent();
            var free = com.mrgregles.bsp_core.network.KitViewPacket.of(a, false).cards().stream().filter(c -> c.id().equals(k.id)).findFirst().orElseThrow();
            h.assertTrue(free.state().equals("READY") && free.conds().equals(List.of(new com.mrgregles.bsp_core.network.KitViewPacket.Cond("no_totem", true, 0))),
                    "met once the totem is gone: " + free.conds());
        } finally {
            Kits.delete(server, k.id);
        }
        h.succeed();
    }
}
