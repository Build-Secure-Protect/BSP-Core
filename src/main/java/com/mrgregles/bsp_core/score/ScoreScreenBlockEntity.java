package com.mrgregles.bsp_core.score;

import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

/**
 * One Score Screen panel. Every panel can hold settings, but only those of the screen's bottom-left
 * panel are used: it draws the whole joined screen.
 */
public class ScoreScreenBlockEntity extends BlockEntity {
    public static final int MODE_BOARD = 0, MODE_RULES = 1, MODE_PRIZES = 2, LAYOUT_LIST = 0, LAYOUT_PODIUM = 1, LAYOUT_SPOTLIGHT = 2, FONTS = 3, TITLE_MAX = 28;

    private int mode = MODE_BOARD, layout = LAYOUT_LIST, font, size = 100, speed = 40;
    private String title = "TOTEM LEADERBOARD";

    public ScoreScreenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCORE_SCREEN.get(), pos, state);
    }

    public int mode() {
        return mode;
    }

    public int layout() {
        return layout;
    }

    public int font() {
        return font;
    }

    /** Text size in percent, 60 to 160. */
    public int size() {
        return size;
    }

    /** Scroll speed, 0 (still) to 100. */
    public int speed() {
        return speed;
    }

    public String title() {
        return title;
    }

    public void configure(int mode, int layout, int font, int size, int speed, String title) {
        this.mode = Mth.clamp(mode, 0, 2);
        this.layout = Mth.clamp(layout, 0, 2);
        this.font = Mth.clamp(font, 0, FONTS - 1);
        this.size = Mth.clamp(size, 60, 160);
        this.speed = Mth.clamp(speed, 0, 100);
        this.title = title.length() > TITLE_MAX ? title.substring(0, TITLE_MAX) : title;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** The joined screen can reach well past this block, so it must not be culled with it. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(ScoreScreenBlock.MAX_W);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Mode", mode);
        tag.putInt("Layout", layout);
        tag.putInt("Font", font);
        tag.putInt("Size", size);
        tag.putInt("Speed", speed);
        tag.putString("Title", title);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Title")) {
            mode = tag.getInt("Mode");
            layout = tag.getInt("Layout");
            font = tag.getInt("Font");
            size = Mth.clamp(tag.getInt("Size"), 60, 160);
            speed = tag.getInt("Speed");
            title = tag.getString("Title");
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
