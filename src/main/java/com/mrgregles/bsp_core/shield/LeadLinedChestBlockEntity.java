package com.mrgregles.bsp_core.shield;

import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

import javax.annotation.Nullable;

/**
 * The Lead-Lined Chest's store: 27 slots in an {@link ItemStackHandler}, reached by hoppers, pipes and players through the
 * item-handler capability.
 *
 * <p><b>This class must never implement {@code net.minecraft.world.Container} (or {@code WorldlyContainer}).</b> Voltaic's
 * radiation scan ({@code RadiationEmitterHandler.containerScan}) only counts block entities that are a {@code Container}, so a
 * store kept behind the capability never radiates, whatever is in it. That is the chest's whole point. If a later Voltaic scans the
 * item-handler capability too, the fallback is to clear {@code EmitterId.container(pos)} on {@code LevelTickEvent} END at LOWEST
 * priority (see the BSP-Mod Fixes handoff for the lead-lined chest).
 *
 * <p>It is a {@link MenuProvider} so that claim mods that ask {@code BlockState.getMenuProvider} (BSP Region Guard's chest-access
 * flag) treat it as a chest.
 *
 * <p>Joined into a double chest, the capability on either half and the menu reach both halves' 54 slots, the RIGHT half's first
 * (see {@link #handler()}); each half still saves and spills only its own 27.
 */
public class LeadLinedChestBlockEntity extends BlockEntity implements MenuProvider, net.minecraft.world.Clearable {
    public static final int ROWS = 3, SLOTS = ROWS * 9;
    private static final String ITEMS_KEY = "Items";

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            // the other half of a double chest reads all 54 slots for comparators: tell its neighbours too, as vanilla's double chest does
            LeadLinedChestBlockEntity other = level == null ? null : LeadLinedChestBlock.partner(level, worldPosition, getBlockState());
            if (other != null) {
                other.setChanged();
            }
        }
    };
    /** What hoppers and pipes get: whatever {@link #handler()} is at the moment of each call, so it follows joins and splits. */
    private final IItemHandlerModifiable joined = new IItemHandlerModifiable() {
        private IItemHandlerModifiable now() {
            return handler();
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            now().setStackInSlot(slot, stack);
        }

        @Override
        public int getSlots() {
            return now().getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return now().getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return now().insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return now().extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return now().getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return now().isItemValid(slot, stack);
        }
    };
    private LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> joined);

    /** Server: players with this chest's screen open. Everyone: the count the last block event carried (drives the lid). */
    private int openers, openCount;
    /** Client: how far the lid is open, 0 to 1, now and a tick ago. */
    private float openness, lastOpenness;
    private static final int OPEN_EVENT = 1;

    public LeadLinedChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LEAD_LINED_CHEST.get(), pos, state);
    }

    /** This chest's own 27 slots. */
    public ItemStackHandler items() {
        return items;
    }

    /** This chest's slots, or both halves' 54 when it is joined into a double chest: the RIGHT half's first, as vanilla. */
    public IItemHandlerModifiable handler() {
        LeadLinedChestBlockEntity other = level == null ? null : LeadLinedChestBlock.partner(level, worldPosition, getBlockState());
        if (other == null) {
            return items;
        }
        return getBlockState().getValue(LeadLinedChestBlock.TYPE) == ChestType.RIGHT ? new CombinedInvWrapper(items, other.items) : new CombinedInvWrapper(other.items, items);
    }

    /** A join or a split changes what the capability reaches, so pipes holding the old one are told to ask again. */
    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState state) {
        boolean changed = getBlockState().hasProperty(LeadLinedChestBlock.TYPE) && state.hasProperty(LeadLinedChestBlock.TYPE)
                && getBlockState().getValue(LeadLinedChestBlock.TYPE) != state.getValue(LeadLinedChestBlock.TYPE);
        super.setBlockState(state);
        if (changed) {
            itemCap.invalidate();
            itemCap = LazyOptional.of(() -> joined);
        }
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemCap.cast(); // every side, as a vanilla chest: hoppers and pipes go in and out anywhere
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemCap = LazyOptional.of(() -> joined);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(ITEMS_KEY, items.serializeNBT());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        for (int i = 0; i < SLOTS; i++) {
            items.setStackInSlot(i, ItemStack.EMPTY);
        }
        if (tag.contains(ITEMS_KEY)) {
            // read through a scratch handler, so a saved size other than 27 can never resize this chest
            ItemStackHandler saved = new ItemStackHandler();
            saved.deserializeNBT(tag.getCompound(ITEMS_KEY));
            for (int i = 0; i < Math.min(SLOTS, saved.getSlots()); i++) {
                items.setStackInSlot(i, saved.getStackInSlot(i));
            }
        }
    }

    /**
     * Empties the chest without spilling: {@code Clearable.tryClear} calls this before a block is replaced by a structure, a
     * {@code /clone ... move}, {@code /setblock} or Region Guard moving a plot, all of which copy the contents first. Without it the
     * replaced chest would spill its items as well, a copy. {@code Clearable} is not {@code Container}: Voltaic still never counts it.
     */
    @Override
    public void clearContent() {
        for (int i = 0; i < SLOTS; i++) {
            items.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    /** Spills every stack at the chest's position and empties the slots: the chest is being broken. */
    public void dropContents(Level level, BlockPos pos) {
        for (int i = 0; i < SLOTS; i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
                items.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    // ------------------------------------------------------------------ who has it open (the lid)

    /** A player opened a screen showing this chest's slots; {@code sound} for the half the player clicked. */
    public void startOpen(Player player, boolean sound) {
        if (player.isSpectator() || level == null) {
            return;
        }
        openers++;
        if (openers == 1 && sound) {
            sound(net.minecraft.sounds.SoundEvents.CHEST_OPEN);
        }
        signal();
    }

    /** A player closed such a screen. */
    public void stopOpen(Player player, boolean sound) {
        if (player.isSpectator() || level == null || openers <= 0) {
            return;
        }
        openers--;
        if (openers == 0 && sound) {
            sound(net.minecraft.sounds.SoundEvents.CHEST_CLOSE);
        }
        signal();
    }

    public int openers() {
        return openers;
    }

    /** The count the last block event carried: on clients what draws the lid, and on the server the same number once the event is processed. */
    public int openCount() {
        return openCount;
    }

    private void sound(net.minecraft.sounds.SoundEvent event) {
        // a heavier lid than a wooden chest's: the same sound, pitched down
        level.playSound(null, worldPosition, event, net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, level.random.nextFloat() * 0.1F + 0.75F);
    }

    private void signal() {
        level.blockEvent(worldPosition, getBlockState().getBlock(), OPEN_EVENT, openers);
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == OPEN_EVENT) {
            openCount = param;
            return true;
        }
        return super.triggerEvent(id, param);
    }

    /**
     * Server, every 5 ticks while anyone has it open: counts again the players near it whose open screen shows this chest, so a
     * screen that went without closing (a lost connection) cannot hold the lid open.
     */
    void serverTick() {
        if (openers <= 0 || level == null || level.getGameTime() % 5 != 0) {
            return;
        }
        int n = 0;
        for (Player p : level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(worldPosition).inflate(5))) {
            if (p.containerMenu instanceof LeadLinedChestMenu m && m.shows(worldPosition)) {
                n++;
            }
        }
        if (n != openers) {
            openers = n;
            if (n == 0) {
                sound(net.minecraft.sounds.SoundEvents.CHEST_CLOSE);
            }
            signal();
        }
    }

    /** Client, every tick: the lid moves a tenth of the way a tick, as a vanilla chest's. */
    void clientTick() {
        lastOpenness = openness;
        float target = openCount > 0 ? 1 : 0;
        openness = openness < target ? Math.min(target, openness + 0.1F) : Math.max(target, openness - 0.1F);
    }

    /** The open lid reaches above the block and out behind it: drawn while any of it can be seen. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(0.25, 0, 0.25).expandTowards(0, 0.6, 0);
    }

    public float openness(float partial) {
        return net.minecraft.util.Mth.lerp(partial, lastOpenness, openness);
    }

    @Override
    public Component getDisplayName() {
        boolean double_ = level != null && LeadLinedChestBlock.partner(level, worldPosition, getBlockState()) != null;
        return Component.translatable(double_ ? "container.bsp_core.lead_lined_chest_double" : "block.bsp_core.lead_lined_chest");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        IItemHandlerModifiable handler = handler();
        BlockPos other = handler == items ? null : worldPosition.relative(LeadLinedChestBlock.connectedDirection(getBlockState()));
        return new LeadLinedChestMenu(id, inv, handler, handler.getSlots() / 9, worldPosition, other);
    }
}
