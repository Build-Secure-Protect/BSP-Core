package com.mrgregles.bsp_core.machine;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;

import java.util.List;

/** Tetrium Ore in, Tetrium Nuggets and Tetrium Slag out. Burns lava from its tank, or any furnace fuel. */
public class TetriumCrucibleBlockEntity extends LavaTankMachineBlockEntity {
    public static final TagKey<Item> TETRIUM_ORES = TagKey.create(Registries.ITEM, new ResourceLocation(BSPCore.MODID, "tetrium_ores"));
    private static final int ORE = 0, FUEL = 1, NUGGETS = 2, SLAG = 3, FIRST_YIELD = 5;
    /** Crucible Yield Upgrade slots. They come after the RF slot so crucibles saved with five slots keep every item where it was. */
    public static final int YIELD_SLOTS = 3;
    private static final List<Slot> LAYOUT = List.of(
            new Slot(Role.INPUT, 44, 22), new Slot(Role.FUEL, 44, 58), new Slot(Role.OUTPUT, 110, 30), new Slot(Role.OUTPUT, 132, 30), new Slot(Role.RF, 152, 60),
            new Slot(Role.UPGRADE, 0, 0), new Slot(Role.UPGRADE, 0, 0), new Slot(Role.UPGRADE, 0, 0));

    public TetriumCrucibleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TETRIUM_CRUCIBLE.get(), pos, state, LAYOUT);
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return switch (slot) {
            case ORE -> stack.is(TETRIUM_ORES);
            case FUEL -> ForgeHooks.getBurnTime(stack, null) > 0;
            case 5, 6, 7 -> stack.is(ModItems.CRUCIBLE_YIELD_UPGRADE.get());
            default -> false;
        };
    }

    /** How many Crucible Yield Upgrades are fitted, 0 to 3. */
    public int yieldUpgrades() {
        int n = 0;
        for (int i = FIRST_YIELD; i < FIRST_YIELD + YIELD_SLOTS; i++) {
            if (items.getStackInSlot(i).is(ModItems.CRUCIBLE_YIELD_UPGRADE.get())) {
                n++;
            }
        }
        return n;
    }

    /** Upgrades fitted when the current job started; -1 while no job is under way. Saved with the crucible. */
    private int jobUpgrades = -1;

    /**
     * The upgrades that count for the current job: the fewer of those fitted when it started and those fitted now, so the same three
     * upgrades cannot be carried from crucible to crucible to finish each one's job. With no job under way, the ones fitted now.
     */
    public int countedUpgrades() {
        int now = yieldUpgrades();
        return jobUpgrades < 0 ? now : Math.min(jobUpgrades, now);
    }

    /** Nuggets per ore: the base plus the configured amount per upgrade, at most one stack. Always nuggets, never an ingot, so the Combination Forge keeps its job. */
    public int nuggetsPerOre() {
        return Math.min(64, BSPConfig.TCRUC_NUGGETS.get() + BSPConfig.TCRUC_PER_UPGRADE.get() * countedUpgrades());
    }

    /** Slag per ore: doubled only when all three upgrades are fitted; at most one stack (a stack's count is saved in one byte). */
    public int slagPerOre() {
        return Math.min(64, BSPConfig.TCRUC_SLAG.get() * (countedUpgrades() >= YIELD_SLOTS ? 2 : 1));
    }

    /** Notes the upgrades fitted as a job starts (progress still 0) and forgets them once no job is under way. */
    @Override
    public void serverTick(net.minecraft.server.level.ServerLevel level) {
        if (progress == 0) {
            jobUpgrades = -1;
        }
        int before = progress;
        super.serverTick(level);
        if (before == 0 && progress > 0) {
            jobUpgrades = yieldUpgrades();
        }
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("JobUpgrades", jobUpgrades);
    }

    private ItemStack nuggets() {
        return new ItemStack(ModItems.TETRIUM_NUGGET.get(), nuggetsPerOre());
    }

    private ItemStack slag() {
        return new ItemStack(ModItems.TETRIUM_SLAG.get(), slagPerOre());
    }

    /**
     * {@code ItemStackHandler.deserializeNBT} resizes the inventory to the saved size, so a crucible saved before the yield slots
     * (five slots) would load with five and fail on the new ones. Keep the layout's size and every saved item in its slot.
     */
    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        jobUpgrades = tag.contains("JobUpgrades") ? tag.getInt("JobUpgrades") : -1;
        if (items.getSlots() < LAYOUT.size()) {
            List<ItemStack> saved = new java.util.ArrayList<>();
            for (int i = 0; i < items.getSlots(); i++) {
                saved.add(items.getStackInSlot(i));
            }
            items.setSize(LAYOUT.size());
            for (int i = 0; i < saved.size(); i++) {
                items.setStackInSlot(i, saved.get(i));
            }
        }
    }

    @Override
    protected boolean canWork() {
        return items.getStackInSlot(ORE).is(TETRIUM_ORES) && fits(NUGGETS, nuggets()) && fits(SLAG, slag());
    }

    @Override
    public int workTime() {
        return BSPConfig.TCRUC_TICKS.get();
    }

    @Override
    protected void finishJob() {
        ItemStack ore = items.getStackInSlot(ORE);
        items.setStackInSlot(ORE, ore.copyWithCount(ore.getCount() - 1));
        addOutput(NUGGETS, nuggets());
        addOutput(SLAG, slag());
        payLava();
    }

    @Override
    protected int lavaPerJob() {
        return BSPConfig.TCRUC_LAVA_PER_ORE.get();
    }

    @Override
    protected int lavaTankSize() {
        return BSPConfig.TCRUC_LAVA_TANK.get();
    }

    @Override
    public List<Need> missing(int fluidMb, boolean burning) {
        List<Need> out = new java.util.ArrayList<>();
        if (!items.getStackInSlot(ORE).is(TETRIUM_ORES)) {
            out.add(need(com.mrgregles.bsp_core.registry.ModBlocks.TETRIUM_ORE.get(), "need.bsp_core.tetrium_ore"));
        }
        if (heatMissing(fluidMb, burning, items.getStackInSlot(FUEL))) {
            out.add(need(net.minecraft.world.item.Items.COAL, "need.bsp_core.fuel_or_lava"));
        }
        if (!fits(NUGGETS, nuggets()) || !fits(SLAG, slag())) {
            out.add(need(ModItems.TETRIUM_NUGGET.get(), "need.bsp_core.output_full"));
        }
        return out;
    }

    @Override
    public ItemStack slotIcon(int slot) {
        if (isRfSlot(slot)) {
            return new ItemStack(ModItems.RF_UPGRADES.get(0).get());
        }
        return switch (slot) {
            case ORE -> new ItemStack(com.mrgregles.bsp_core.registry.ModBlocks.TETRIUM_ORE.get());
            case FUEL -> new ItemStack(net.minecraft.world.item.Items.COAL);
            case 5, 6, 7 -> new ItemStack(ModItems.CRUCIBLE_YIELD_UPGRADE.get());
            case NUGGETS -> new ItemStack(ModItems.TETRIUM_NUGGET.get());
            default -> new ItemStack(ModItems.TETRIUM_SLAG.get());
        };
    }

    @Override
    public net.minecraft.network.chat.Component slotHint(int slot) {
        if (isRfSlot(slot)) {
            return net.minecraft.network.chat.Component.translatable("hint.bsp_core.rf");
        }
        return net.minecraft.network.chat.Component.translatable(switch (slot) {
            case ORE -> "hint.bsp_core.tetrium_ore";
            case FUEL -> "hint.bsp_core.fuel";
            case NUGGETS -> "hint.bsp_core.out_nuggets";
            case 5, 6, 7 -> "hint.bsp_core.yield_upgrade";
            default -> "hint.bsp_core.out_slag";
        });
    }

    @Override
    public String titleKey() {
        return "block.bsp_core.tetrium_crucible";
    }
}
