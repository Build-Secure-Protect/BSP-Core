package com.mrgregles.bsp_core.demo;

import com.mrgregles.bsp_core.looks.TotemHats;
import com.mrgregles.bsp_core.looks.TotemLook;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

/**
 * A Demo Totem's data: only its look. The look is shown as it is, to everyone: it does not hang on anyone's rank, record or staff
 * clear, since a demo has no owner. The look's own "made for" is the admin who dressed it (whose skin MY HEAD shows).
 */
public class DemoTotemBlockEntity extends BlockEntity implements com.mrgregles.bsp_core.looks.Dressable {
    @Nullable
    private TotemLook look;
    /** The name of the admin who dressed it: a skin is looked up by name, and MY HEAD shows theirs. */
    private String dresser = "";
    /** The hat on the plain baked body while it wears no look (as a totem without a look wears a bought hat), or "". */
    private String plainHat = "";

    public DemoTotemBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DEMO_TOTEM.get(), pos, state);
    }

    /** The look it wears, or null for a plain gold totem. */
    @Nullable
    public TotemLook getLook() {
        return look;
    }

    @Nullable
    @Override
    public TotemLook shownBody() {
        return look;
    }

    @Override
    public String shownHat() {
        return look == null ? plainHat : look.hat();
    }

    /** The hat on the plain body ("" for none, or while it wears a look). */
    public String getPlainHat() {
        return look == null ? plainHat : "";
    }

    /** Takes any look off and puts {@code hat} ("" for none) on the plain baked gold body. */
    public void setPlain(String hat) {
        setLook(null, "");
        plainHat = TotemHats.known(hat) ? hat : "";
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * MY HEAD on a demo is the head of the admin who dressed it. A skin is looked up by name: with none known the totem's own face is
     * drawn (a lookup without a name would settle on the default skin for that player's id for the rest of the session).
     */
    @Nullable
    @Override
    public com.mrgregles.bsp_core.totem.TotemOwner skinOwner() {
        return look == null || dresser.isEmpty() ? null : new com.mrgregles.bsp_core.totem.TotemOwner(look.owner(), dresser);
    }

    /** Whether the block state was checked against the look since this block entity loaded. */
    private boolean stateChecked;

    /**
     * Once after loading: a demo loaded with a look but a plain block state (or the other way round), as a {@code /setblock} with NBT
     * or a pasted structure leaves it, gets the state that matches. Not in {@code onLoad}: a block state may not change while its chunk loads.
     */
    public void serverTick() {
        if (!stateChecked) {
            stateChecked = true;
            if (level != null && getBlockState().getValue(DemoTotemBlock.LOOKS) != (look != null)) {
                level.setBlock(worldPosition, getBlockState().setValue(DemoTotemBlock.LOOKS, look != null), 3);
            }
        }
    }

    /** The name of the admin who dressed it ("" when not known). */
    public String getDresser() {
        return dresser;
    }

    /** Puts on (or, with null, takes off) a look, keeping the dresser's name as it was. */
    public void setLook(@Nullable TotemLook look) {
        setLook(look, dresser);
    }

    /** Puts on (or, with null, takes off) a look made by {@code dresser}, and keeps the block state's {@code looks} and the clients in step. */
    public void setLook(@Nullable TotemLook look, String dresser) {
        this.look = look;
        this.dresser = look == null ? "" : dresser;
        this.plainHat = ""; // a look brings its own hat; taking one off leaves the plain body bare
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState was = getBlockState(), now = was.setValue(DemoTotemBlock.LOOKS, look != null);
            if (now != was) {
                level.setBlock(worldPosition, now, 3);
            }
            level.sendBlockUpdated(worldPosition, was, now, 3);
        }
    }

    // --- the look on the item ---

    /** The look a Demo Totem item carries, or null (none, or one that is not sound, such as an unknown hat). */
    @Nullable
    public static TotemLook lookOn(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || !tag.contains(TotemLook.TAG) ? null : TotemLook.load(tag.getCompound(TotemLook.TAG));
    }

    /** The dresser's name a Demo Totem item carries beside its look ("" when none). */
    public static String dresserOn(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : name(tag.getString(DRESSER_TAG));
    }

    /** A player name as a tag may carry it: at most 16 characters, as Minecraft's names are. */
    private static String name(String s) {
        return s.length() > 16 ? "" : s;
    }

    private static final String DRESSER_TAG = "LookBy", PLAIN_HAT_TAG = "PlainHat";

    /** The hat a Demo Totem item carries for the plain body ("" when none, or when it carries a look). */
    public static String plainHatOn(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null || lookOn(stack) != null || !TotemHats.known(tag.getString(PLAIN_HAT_TAG)) ? "" : tag.getString(PLAIN_HAT_TAG);
    }

    /** Writes the hat for the plain body onto a Demo Totem item that carries no look. */
    public static void putPlainHat(ItemStack stack, String hat) {
        if (!hat.isEmpty()) {
            stack.getOrCreateTag().putString(PLAIN_HAT_TAG, hat);
        }
    }

    /** Writes the look onto a Demo Totem item (none for a plain one, so plain demos stack together). */
    public static void putLook(ItemStack stack, @Nullable TotemLook look) {
        putLook(stack, look, "");
    }

    /** Writes the look and its dresser's name onto a Demo Totem item. */
    public static void putLook(ItemStack stack, @Nullable TotemLook look, String dresser) {
        if (look != null) {
            stack.getOrCreateTag().put(TotemLook.TAG, look.save());
            if (!dresser.isEmpty()) {
                stack.getTag().putString(DRESSER_TAG, dresser);
            }
        } else if (stack.getTag() != null) {
            stack.getTag().remove(TotemLook.TAG);
            stack.getTag().remove(DRESSER_TAG);
            if (stack.getTag().isEmpty()) {
                stack.setTag(null);
            }
        }
    }

    // --- saving and sending ---

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (look != null) {
            tag.put(TotemLook.TAG, look.save());
            tag.putString(DRESSER_TAG, dresser);
        } else if (!plainHat.isEmpty()) {
            tag.putString(PLAIN_HAT_TAG, plainHat);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        look = tag.contains(TotemLook.TAG) ? TotemLook.load(tag.getCompound(TotemLook.TAG)) : null;
        dresser = look == null ? "" : name(tag.getString(DRESSER_TAG));
        stateChecked = false; // a later load (/data merge) may change the look: check the state again
        plainHat = look == null && TotemHats.known(tag.getString(PLAIN_HAT_TAG)) ? tag.getString(PLAIN_HAT_TAG) : "";
    }

    /** Clients draw the look, so it rides with the block. */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = saveWithoutMetadata();
        // a plain demo writes nothing, and Minecraft drops update packets whose tag is empty: without this marker a look taken off
        // would stay on the clients' copy (the read-only screen, a creative middle-click)
        tag.putBoolean("Synced", true);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Tall enough for the tallest hat. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0, 2, 0).inflate(0.5);
    }
}
