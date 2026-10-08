package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.CloakRefreshPacket;
import com.mrgregles.bsp_core.network.CloakStatusPacket;
import com.mrgregles.bsp_core.totem.CloakBlind;
import com.mrgregles.bsp_core.totem.CloakSnapshot;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cloaking on the client. While this player is outside a cloaked cube and not allowed through, every block in the cube, the totem
 * included, is swapped in this client's copy of the world for the cube's snapshot (the land as generated), and the players and
 * mobs inside are not drawn. The client keeps its own record of each cloak, so the totem's block entity vanishing under the swap
 * changes nothing; while hidden it asks the server every two seconds whether the cloak still stands and whether it may see, and
 * when the blocks go back the server sends the chunks again, which restores the block entities the swap threw away. Walking in
 * (or being let in) does not put the blocks back at once: the real ones are drawn over the fake land as shells that turn solid
 * over {@code effects.cloakFadeSeconds}, then the real blocks take over; walking out puts the fake land back at once and the
 * real blocks fade out over it for {@code effects.cloakFadeOutSeconds}.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID, value = Dist.CLIENT)
public final class CloakClient {
    private static final int EVERY = 40;

    /** One cloak this client knows about, kept after its block entity is swapped away. */
    private static final class Entry {
        final BlockPos origin;
        CloakSnapshot snap;
        boolean hide;
        final Map<BlockPos, BlockState> real = new HashMap<>();
        /** Game time the fade-in began, or -1: the fake land stays while the real blocks are drawn over it, more solid each tick. */
        long revealStart = -1;
        /** Game time the fade-out began, or -1: the fake land is back while the real blocks are drawn over it, fainter each tick. */
        long fadeOutStart = -1;
        @Nullable
        com.mojang.blaze3d.vertex.VertexBuffer mesh;
        /** Whether this player stood inside the cube at the last look, so a step over the edge is acted on at once. */
        boolean lastInside;

        Entry(BlockPos origin, CloakSnapshot snap) {
            this.origin = origin;
            this.snap = snap;
        }

        boolean revealing() {
            return revealStart >= 0;
        }

        boolean fadingOut() {
            return fadeOutStart >= 0;
        }

        /** How solid the real blocks are drawn right now, 0..1, from whichever fade is running. */
        float alpha(float now) {
            if (revealing()) {
                return Math.max(0f, Math.min(1f, (now - revealStart) / (float) Math.max(1, fadeTicks())));
            }
            if (fadingOut()) {
                return Math.max(0f, Math.min(1f, 1f - (now - fadeOutStart) / (float) Math.max(1, fadeOutTicks())));
            }
            return 0f;
        }

        void endFades() {
            revealStart = -1;
            fadeOutStart = -1;
            if (mesh != null) {
                mesh.close();
                mesh = null;
            }
        }
    }

    private static int fadeTicks() {
        return 20 * com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.CLOAK_FADE_SECONDS, 7);
    }

    private static int fadeOutTicks() {
        return 20 * com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.CLOAK_FADE_OUT_SECONDS, 3);
    }

    /** A hidden cube that may now be seen: start the fade-in rather than putting the blocks back at once (or at once, with no fade set). */
    private static void reveal(Entry e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.real.isEmpty() || fadeTicks() <= 0 || mc.level == null) {
            drop(e);
            return;
        }
        if (e.revealing()) {
            return;
        }
        long now = mc.level.getGameTime();
        float from = e.fadingOut() ? e.alpha(now) : 0f; // turned back during a fade-out: carry on from where it is
        if (e.mesh == null) {
            e.mesh = BlockShells.build(mc.level, e.real, e.origin);
        }
        e.fadeOutStart = -1;
        e.revealStart = now - (long) (from * fadeTicks());
    }

    /** A cube this player was seeing is hidden again: the fake land goes in now and the real blocks fade out over it (or vanish, with no fade set). */
    private static void hide(Entry e, boolean fade) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        float from = e.revealing() ? e.alpha(now) : 1f; // stepped out during a fade-in: fade out from where it is
        boolean wasFading = e.revealing() || e.fadingOut();
        swap(e, false);
        if (!fade || fadeOutTicks() <= 0 || e.real.isEmpty()) {
            e.endFades();
            return;
        }
        if (e.fadingOut()) {
            return;
        }
        if (!wasFading || e.mesh == null) {
            if (e.mesh != null) {
                e.mesh.close();
            }
            e.mesh = BlockShells.build(mc.level, e.real, e.origin);
        }
        e.revealStart = -1;
        e.fadeOutStart = now - (long) ((1f - from) * fadeOutTicks());
    }

    private static final Map<BlockPos, Entry> CLOAKS = new LinkedHashMap<>();
    /** Totem block entities present in this client's world right now. */
    private static final Map<BlockPos, ShatterTotemBlockEntity> TOTEMS = new HashMap<>();
    /** Origins whose block entity just appeared: looked at on the next tick, once its data has arrived. */
    private static final Set<BlockPos> RECHECK = new HashSet<>();

    private CloakClient() {}

    public static void register(ShatterTotemBlockEntity totem) {
        TOTEMS.put(totem.getBlockPos(), totem);
        RECHECK.add(totem.getBlockPos());
    }

    /** The block entity went away: under our own swap, or really gone. The record stays until the server says the cloak is over. */
    public static void unregister(ShatterTotemBlockEntity totem) {
        TOTEMS.remove(totem.getBlockPos(), totem);
    }

    /** The server's word on {@code /bsp cloak see}: mirrored onto the client player, then every cloak is looked at afresh. */
    public static void setBlind(boolean blind) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            CloakBlind.mirror(mc.player, blind);
        }
        for (Entry e : new ArrayList<>(CLOAKS.values())) {
            drop(e); // at once, no fade: the chunks come back from the server if we may see them; if not, the totem reappears and is hidden again
        }
    }

    /** The server answered a query: a cloak that is over, or one we may see, is put back. */
    public static void status(CloakStatusPacket msg) {
        Entry e = CLOAKS.get(msg.origin());
        if (e != null && (!msg.cloaked() || msg.maySee())) {
            e.hide = false;
            reveal(e);
        }
    }

    /** Whether {@code pos} is inside a cloaked cube this player is being kept out of: X-ray leaves those blocks alone. */
    public static boolean hiddenFromMe(BlockPos pos) {
        for (Entry e : CLOAKS.values()) {
            if ((e.hide || e.revealing()) && e.snap.contains(pos)) { // fading out counts as hidden through e.hide
                return true;
            }
        }
        return false;
    }

    private static boolean shouldHide(CloakSnapshot snap, @Nullable ShatterTotemBlockEntity totem) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || snap.contains(mc.player.blockPosition())) {
            return false;
        }
        return totem == null || !totem.seesThroughCloak(mc.player); // without the block entity (swapped away) the last answer stands
    }

    /** Puts the real blocks back, forgets the cloak and asks for the chunks, so the block entities come back whole. */
    private static void drop(Entry e) {
        Minecraft mc = Minecraft.getInstance();
        CLOAKS.remove(e.origin);
        e.endFades();
        if (mc.level == null) {
            return;
        }
        boolean any = false;
        for (Map.Entry<BlockPos, BlockState> r : e.real.entrySet()) {
            if (mc.level.isLoaded(r.getKey())) {
                mc.level.setBlock(r.getKey(), r.getValue(), 3);
                any = true;
            }
        }
        if (any && mc.level.isLoaded(e.origin)) {
            BSPNetwork.CHANNEL.sendToServer(new CloakRefreshPacket(e.origin, e.snap.radius));
        }
    }

    /** Swaps every loaded block of the cube that is not already the snapshot's, remembering what stood there. */
    private static void swap(Entry e, boolean onlyOrigin) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        CloakSnapshot snap = e.snap;
        int r = onlyOrigin ? 0 : snap.radius;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    p.set(e.origin.getX() + dx, e.origin.getY() + dy, e.origin.getZ() + dz);
                    if (!mc.level.isLoaded(p)) {
                        continue;
                    }
                    BlockState want = snap.at(p), now = mc.level.getBlockState(p);
                    if (now != want) {
                        e.real.put(p.immutable(), now); // the server told us something new: remember it under the swap
                        mc.level.setBlock(p, want, 3);
                    }
                }
            }
        }
    }

    /** Brings the record up to date from a present block entity and acts on it; returns false when the cloak is over. */
    private static boolean refresh(BlockPos origin, ShatterTotemBlockEntity totem, boolean onlyOrigin) {
        CloakSnapshot snap = totem.isRemoved() ? null : totem.cloak();
        Entry e = CLOAKS.get(origin);
        if (snap == null) {
            if (e != null) {
                drop(e);
            }
            return false;
        }
        boolean wasSeen = e != null && !e.hide; // the real blocks were in the world (inside, or let through): a fade-out is due if that ends
        if (e == null) {
            e = new Entry(origin, snap);
            e.lastInside = mc().player != null && snap.contains(mc().player.blockPosition());
            CLOAKS.put(origin, e);
        } else if (e.snap != snap && (e.snap.radius != snap.radius || e.snap.version != snap.version)) {
            drop(e); // a different cube: start over from the real blocks
            e = new Entry(origin, snap);
            e.lastInside = mc().player != null && snap.contains(mc().player.blockPosition());
            CLOAKS.put(origin, e);
        } else {
            e.snap = snap;
        }
        e.hide = shouldHide(snap, totem);
        if (e.hide) {
            hide(e, wasSeen || e.revealing());
        } else if (!e.real.isEmpty()) {
            reveal(e);
            return e.revealing();
        }
        return true;
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    /** The look at a cube whose block entity is under our swap or gone: keep it as it is and ask the server which it is. */
    private static void refreshWithoutEntity(Entry e) {
        Minecraft mc = Minecraft.getInstance();
        boolean wasSeen = !e.hide;
        e.hide = shouldHide(e.snap, null);
        if (!e.hide) {
            reveal(e); // walked in
            return;
        }
        hide(e, wasSeen || e.revealing());
        if (mc.level != null && mc.level.isLoaded(e.origin)) {
            BSPNetwork.CHANNEL.sendToServer(new CloakRefreshPacket(e.origin, e.snap.radius));
        }
    }

    /** The cloak record covering {@code pos} that is hidden or mid-fade, if any. */
    @Nullable
    private static Entry coverRecord(BlockPos pos) {
        for (Entry e : CLOAKS.values()) {
            if ((e.hide || e.revealing()) && e.snap.contains(pos)) {
                return e;
            }
        }
        return null;
    }

    /**
     * Players and mobs inside a cube that hides from this player are not drawn; while the cube fades in or out they are drawn
     * see-through at the fade's strength, the owner and the players let in included, so they come and go with the blocks.
     */
    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getEntity() == mc.player || CLOAKS.isEmpty() || FadedEntities.isRendering() || mc.level == null) {
            return;
        }
        Entry e = coverRecord(event.getEntity().blockPosition());
        if (e == null) {
            return;
        }
        event.setCanceled(true);
        if (e.revealing() || e.fadingOut()) {
            FadedEntities.render(event, e.alpha(mc.level.getGameTime() + event.getPartialTick()));
        }
    }

    /** Nor are their name tags. */
    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (!CLOAKS.isEmpty() && hiddenFromMe(event.getEntity().blockPosition())) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (mc.level == null || mc.player == null) {
            CLOAKS.clear();
            TOTEMS.clear();
            RECHECK.clear();
            return;
        }
        // a totem block entity that just appeared (chunk arrived, or the server put the block back): its data is in by now
        if (!RECHECK.isEmpty()) {
            for (BlockPos origin : new ArrayList<>(RECHECK)) {
                ShatterTotemBlockEntity totem = TOTEMS.get(origin);
                if (totem != null) {
                    refresh(origin, totem, false); // the whole cube straight away, so a chunk that just arrived never shows the base
                }
            }
            RECHECK.clear();
        }
        long now = mc.level.getGameTime();
        for (Entry e : new ArrayList<>(CLOAKS.values())) {
            // fades that have run their course: the real blocks take over, or the fake land is all that is left
            if (e.revealing() && now - e.revealStart >= fadeTicks()) {
                drop(e);
                continue;
            }
            if (e.fadingOut() && now - e.fadeOutStart >= fadeOutTicks()) {
                e.endFades();
            }
            // a step over the cube's edge is acted on this tick, not at the next pass
            boolean inside = e.snap.contains(mc.player.blockPosition());
            if (inside != e.lastInside) {
                e.lastInside = inside;
                ShatterTotemBlockEntity totem = TOTEMS.get(e.origin);
                if (totem != null) {
                    refresh(e.origin, totem, false);
                } else {
                    refreshWithoutEntity(e);
                }
            }
        }
        if (now % EVERY != 0) {
            return;
        }
        for (Map.Entry<BlockPos, ShatterTotemBlockEntity> t : new ArrayList<>(TOTEMS.entrySet())) {
            refresh(t.getKey(), t.getValue(), false);
        }
        for (Entry e : new ArrayList<>(CLOAKS.values())) {
            if (!TOTEMS.containsKey(e.origin)) {
                refreshWithoutEntity(e);
            }
        }
    }

    /** The real blocks of a cube fading in or out, drawn over the fake land at the fade's strength. */
    @SubscribeEvent
    public static void onRenderLevel(net.minecraftforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() != net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || CLOAKS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float now = mc.level.getGameTime() + event.getPartialTick();
        for (Entry e : CLOAKS.values()) {
            if ((e.revealing() || e.fadingOut()) && e.mesh != null) {
                BlockShells.draw(e.mesh, e.origin, event, e.alpha(now));
            }
        }
    }
}
