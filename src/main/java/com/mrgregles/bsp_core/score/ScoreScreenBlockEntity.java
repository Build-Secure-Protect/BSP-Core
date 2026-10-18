package com.mrgregles.bsp_core.score;

import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
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
    public static final int MODE_BOARD = 0, MODE_RULES = 1, MODE_PRIZES = 2, MODE_PAGE = 3, MODE_ROTATE = 4, MODES = 5;
    public static final int LAYOUT_LIST = 0, LAYOUT_PODIUM = 1, LAYOUT_SPOTLIGHT = 2, FONTS = 3, TITLE_MAX = 28;
    /** How another mod's board is drawn: rows with icon, text and value; tiles with the value large; or a column beside the leaderboard. */
    public static final int LOOK_LEDGER = 0, LOOK_TILES = 1, LOOK_SIDE = 2, LOOKS = 3;
    /** Page ids in a rotation, beside a registered board's {@code namespace:path}. */
    public static final String PAGE_SCOREBOARD = "scoreboard", PAGE_RULES = "rules", PAGE_PRIZES = "prizes";
    public static final int ROTATE_MIN = 5, ROTATE_MAX = 120, BOARD_ID_MAX = 128;

    private int mode = MODE_BOARD, layout = LAYOUT_LIST, font, size = 100, speed = 40, look = LOOK_LEDGER, rotateSeconds = 10;
    private String title = "TOTEM LEADERBOARD", boardId = "";
    private List<String> pages = new ArrayList<>();

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

    /** The registered board shown in the Board mode, as {@code namespace:path}; "" for none. */
    public String boardId() {
        return boardId;
    }

    public int look() {
        return look;
    }

    /** Seconds a page stays in the rotation. */
    public int rotateSeconds() {
        return rotateSeconds;
    }

    /** The pages of the rotation in order; empty means the default: the scoreboard, then every registered board. */
    public List<String> pages() {
        return pages;
    }

    public void configure(int mode, int layout, int font, int size, int speed, String title) {
        configure(mode, layout, font, size, speed, title, boardId, look, rotateSeconds, pages);
    }

    public void configure(int mode, int layout, int font, int size, int speed, String title, String boardId, int look, int rotateSeconds, List<String> pages) {
        this.mode = Mth.clamp(mode, 0, MODES - 1);
        this.layout = Mth.clamp(layout, 0, 2);
        this.font = Mth.clamp(font, 0, FONTS - 1);
        this.size = Mth.clamp(size, 60, 160);
        this.speed = Mth.clamp(speed, 0, 100);
        this.title = title.length() > TITLE_MAX ? title.substring(0, TITLE_MAX) : title;
        this.boardId = boardId == null ? "" : boardId.length() > BOARD_ID_MAX ? boardId.substring(0, BOARD_ID_MAX) : boardId;
        this.look = Mth.clamp(look, 0, LOOKS - 1);
        this.rotateSeconds = Mth.clamp(rotateSeconds, ROTATE_MIN, ROTATE_MAX);
        List<String> clean = new ArrayList<>();
        for (String p : pages) {
            if (p != null && !p.isEmpty() && p.length() <= BOARD_ID_MAX && !clean.contains(p) && clean.size() < 16) {
                clean.add(p);
            }
        }
        this.pages = clean;
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
        tag.putString("Board", boardId);
        tag.putInt("Look", look);
        tag.putInt("Rotate", rotateSeconds);
        ListTag list = new ListTag();
        for (String p : pages) {
            list.add(StringTag.valueOf(p));
        }
        tag.put("Pages", list);
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
            boardId = tag.getString("Board");
            look = Mth.clamp(tag.getInt("Look"), 0, LOOKS - 1);
            rotateSeconds = tag.contains("Rotate") ? Mth.clamp(tag.getInt("Rotate"), ROTATE_MIN, ROTATE_MAX) : 10;
            pages = new ArrayList<>();
            for (Tag t : tag.getList("Pages", Tag.TAG_STRING)) {
                pages.add(t.getAsString());
            }
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
