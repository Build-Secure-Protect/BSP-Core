package com.mrgregles.bsp_core.mixin.gametest;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds a GameTest back until its area's chunks tick, and its clock with it. In 1.20.1 a test's clock starts when its structure
 * is placed, whether or not the chunks under it tick yet (later versions wait for them). In a big batch the chunks of the last
 * tests are still loading while their ticks run out, so blocks there never tick and a test can time out having seen nothing.
 * Here, until every chunk the structure touches ticks blocks and entities, the test does not tick and its start moves on by a
 * tick, so its timeout and every delay inside it count from the moment its area is live.
 * <p>Only the GameTest server loads this mixin (bsp_core.gametest.mixins.json, handed to the {@code gameTestServer} run in
 * build.gradle); the mod's jar does not name it, so players and servers never apply it.
 */
@Mixin(GameTestInfo.class)
public abstract class GameTestInfoMixin {
    /** The longest a test waits for its chunks before running anyway (and failing in its own words). */
    @Unique
    private static final long bsp_core$WAIT_LIMIT_NS = 120_000_000_000L;

    @Shadow
    private long startTick;
    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    public abstract AABB getStructureBounds();

    @Shadow
    public abstract String getTestName();

    @Unique
    private boolean bsp_core$live;
    @Unique
    private int bsp_core$waited;
    @Unique
    private long bsp_core$since;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void bsp_core$waitForTickingChunks(CallbackInfo ci) {
        if (bsp_core$live) {
            return;
        }
        if (bsp_core$waited == 0) {
            bsp_core$since = System.nanoTime();
        }
        boolean ticking;
        try {
            ticking = bsp_core$areaTicks(getStructureBounds());
        } catch (RuntimeException e) {
            ticking = true; // no structure to measure: leave the test to the runner as it is
        }
        if (!ticking && System.nanoTime() - bsp_core$since > bsp_core$WAIT_LIMIT_NS) {
            BSPCore.LOGGER.warn("GameTest {}: its chunks did not start ticking in {} ticks; running it anyway", getTestName(), bsp_core$waited);
            ticking = true;
        }
        if (ticking) {
            bsp_core$live = true;
            if (bsp_core$waited >= 20) {
                BSPCore.LOGGER.info("GameTest {} waited {} ticks for its chunks to tick", getTestName(), bsp_core$waited);
            }
            return;
        }
        bsp_core$waited++;
        startTick++;
        ci.cancel();
    }

    @Unique
    private boolean bsp_core$areaTicks(AABB box) {
        int x0 = (int) Math.floor(box.minX) >> 4, x1 = (int) Math.floor(box.maxX) >> 4;
        int z0 = (int) Math.floor(box.minZ) >> 4, z1 = (int) Math.floor(box.maxZ) >> 4;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!level.shouldTickBlocksAt(ChunkPos.asLong(x, z)) || !level.isPositionEntityTicking(new ChunkPos(x, z).getWorldPosition())) {
                    return false;
                }
            }
        }
        return true;
    }
}
