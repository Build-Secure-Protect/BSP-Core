package com.mrgregles.bsp_core.shield;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The radiation shielding set: the Radiation Shielding Panel (lets nothing through), Window (a tenth) and Door (3% shut). What each
 * lets through is Voltaic's data, not code: {@code data/bsp_core/radiation/bsp_core_radiation_shielding.json} (tools/gen_lead_assets.py).
 * Voltaic weakens any open door by itself: it applies its door rule and its trapdoor rule, which read the same {@code OPEN} property,
 * so an open door keeps only 4% of what it blocks shut (about 96% gets through). The door shields only while it is shut.
 *
 * <p>All three are as hard as an iron block and resist explosions like one (5, 6), mined with a stone pickaxe or better, so a
 * shielded room is no stronger against raiders than an iron wall. The window is a thick pane ({@link ShieldingWindowBlock}) that lets
 * light and sight through, and windows side by side in one plane show no face between them.
 */
public final class ShieldingBlocks {
    /**
     * Lead, with the iron door's sounds. Not "open by hand" as a block set: mobs take any door whose set opens by hand for a wooden door
     * (zombies break it, villagers walk through it). {@link ShieldingDoorBlock} opens by hand itself.
     */
    public static final BlockSetType LEAD = new BlockSetType("bsp_core_lead", false, SoundType.METAL,
            SoundEvents.IRON_DOOR_CLOSE, SoundEvents.IRON_DOOR_OPEN, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundEvents.IRON_TRAPDOOR_OPEN,
            SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundEvents.STONE_BUTTON_CLICK_ON);

    /** An iron block's hardness and blast resistance. */
    public static final float HARDNESS = 5.0F, BLAST = 6.0F;

    private ShieldingBlocks() {}

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(HARDNESS, BLAST).sound(SoundType.METAL);
    }

    public static Block panel() {
        return new Block(metal());
    }

    public static Block window() {
        return new ShieldingWindowBlock(metal().noOcclusion().isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false)
                .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));
    }

    public static Block door() {
        return new ShieldingDoorBlock(metal().noOcclusion().pushReaction(PushReaction.DESTROY), LEAD);
    }
}
