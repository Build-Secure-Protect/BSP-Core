package com.mrgregles.bsp_core.machine;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Magnetic Centrifuge controller. A multiblock one block high and three by three: the controller
 * in the middle of the front edge, the Rotor in the centre, an Item Hatch on the left, a Power
 * Port on the right and Casing elsewhere. Up to five more layers (a Rotor ringed by eight Casing)
 * can be stacked on top.
 *
 * <p>It always needs RF, and does two jobs:
 * <ul>
 *   <li><b>Separating</b>: one Magnatite Ore becomes Magnatite Nuggets and one Carbon Dust. More
 *       layers give more nuggets ({@code centrifuge.nuggetsMin/Max}).</li>
 *   <li><b>Charging</b> (needs a Copper Tetrium Coil in the upgrade slot): a Magnatite Ingot has a
 *       chance, one in {@code centrifuge.chargeOdds} for the number of layers, to become a Charged
 *       Magnatite Ingot. A failed try leaves the ingot where it is, to be tried again. With six
 *       layers each try is quicker.</li>
 * </ul>
 */
public class MagneticCentrifugeBlockEntity extends MultiblockControllerBlockEntity {
    public static final TagKey<Item> MAGNATITE_ORES = TagKey.create(Registries.ITEM, new ResourceLocation(BSPCore.MODID, "magnatite_ores"));
    public static final int MAX_LAYERS = 6;
    private static final int IN = 0, COIL = 1, OUT = 2, BYPRODUCT = 3;
    private static final List<Slot> LAYOUT = List.of(
            new Slot(Role.INPUT, 44, 34), new Slot(Role.UPGRADE, 80, 58), new Slot(Role.OUTPUT, 120, 24), new Slot(Role.OUTPUT, 120, 46));
    // rows back to front as seen from the front: casing; hatch, rotor, power port; casing, controller, casing
    private static final String[][] PATTERN = {{"CCC", "HRP", "CXC"}};
    private static final String[] UPPER = {"CCC", "CRC", "CCC"};

    /** Complete layers including the bottom one, 1 to 6. Synced to the client for the renderer and the screen. */
    private int layers = 1;

    public MagneticCentrifugeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MAGNETIC_CENTRIFUGE.get(), pos, state, LAYOUT, Fluids.EMPTY);
        setSideMode(0, com.mrgregles.bsp_core.coin.CoinFactoryBlockEntity.SideMode.BOTH); // the single Item Hatch
    }

    // ------------------------------------------------------------------ structure

    @Override
    protected String[][] pattern() {
        return PATTERN;
    }

    @Nullable
    @Override
    protected Block blockFor(char c) {
        return c == 'C' ? ModBlocks.CENTRIFUGE_CASING.get() : c == 'R' ? ModBlocks.CENTRIFUGE_ROTOR.get()
                : c == 'H' ? ModBlocks.ITEM_HATCH.get() : c == 'P' ? ModBlocks.CENTRIFUGE_POWER_PORT.get() : null;
    }

    public int layers() {
        return layers;
    }

    /** The blocks of stacked layer {@code n} (1 = the first layer above the bottom one), from the controller's facing. */
    public List<Part> upperLayer(int n) {
        List<Part> out = new ArrayList<>();
        Direction facing = getBlockState().hasProperty(MachineBlock.FACING) ? getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
        Direction back = facing.getOpposite(), right = back.getClockWise();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                Block want = UPPER[r].charAt(c) == 'R' ? ModBlocks.CENTRIFUGE_ROTOR.get() : ModBlocks.CENTRIFUGE_CASING.get();
                out.add(new Part(worldPosition.above(n).relative(back, 2 - r).relative(right, c - 1), want));
            }
        }
        return out;
    }

    private boolean complete(ServerLevel level, List<Part> layer) {
        for (Part part : layer) {
            if (!level.getBlockState(part.pos()).is(part.block())) {
                return false;
            }
        }
        return true;
    }

    /** Hides or shows the blocks of every stacked layer: those that count are hidden, anything above a gap is shown again. */
    private void showUpper(ServerLevel level, int counted) {
        for (int n = 1; n < MAX_LAYERS; n++) {
            boolean hide = n < counted;
            for (Part part : upperLayer(n)) {
                BlockState st = level.getBlockState(part.pos());
                if (st.is(part.block()) && st.hasProperty(StructurePartBlock.FORMED) && st.getValue(StructurePartBlock.FORMED) != hide) {
                    level.setBlock(part.pos(), st.setValue(StructurePartBlock.FORMED, hide), 3);
                }
            }
        }
    }

    @Override
    public void showParts(boolean formedNow) {
        super.showParts(formedNow);
        if (!formedNow && level instanceof ServerLevel sl) {
            showUpper(sl, 1); // the machine came apart: stacked layers become ordinary blocks again
        }
    }

    @Override
    public void serverTick(ServerLevel level) {
        super.serverTick(level);
        if (level.getGameTime() % 20 == 0) {
            int count = 1;
            if (isFormed()) {
                while (count < MAX_LAYERS && complete(level, upperLayer(count))) {
                    count++;
                }
                showUpper(level, count);
            }
            if (count != layers) {
                layers = count;
                setChanged();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(3, 0, 3).expandTowards(0, MAX_LAYERS, 0);
    }

    @Override
    protected boolean hidesParts() {
        return true;
    }

    @Override
    protected boolean isItemPort(Block block) {
        return block == ModBlocks.ITEM_HATCH.get();
    }

    @Override
    public String itemPortKey(int i) {
        return "gui.bsp_core.port.hatch";
    }

    @Override
    public String fluidPortKey() {
        return "gui.bsp_core.port.power";
    }

    @Override
    protected boolean portTakesFluid(BlockPos port) {
        return false;
    }

    @Override
    protected int tankCapacity() {
        return 0;
    }

    @Override
    protected Fluid fluid() {
        return Fluids.EMPTY;
    }

    @Override
    protected void refill() {
    }

    // ------------------------------------------------------------------ work

    public boolean hasCoil() {
        return items.getStackInSlot(COIL).is(ModItems.COPPER_TETRIUM_COIL.get());
    }

    /** True when the current input is an ingot to charge rather than ore to separate. */
    public boolean charging() {
        return hasCoil() && items.getStackInSlot(IN).is(ModItems.MAGNATITE_INGOT.get());
    }

    private boolean separating() {
        return items.getStackInSlot(IN).is(MAGNATITE_ORES);
    }

    private static int at(List<? extends Integer> list, int layers, int fallback) {
        return BSPConfig.levelValue(list, layers, fallback);
    }

    public int nuggetsMin() {
        return at(BSPConfig.getOr(BSPConfig.CENT_NUGGETS_MIN, List.<Integer>of()), layers, 3);
    }

    public int nuggetsMax() {
        return Math.max(nuggetsMin(), at(BSPConfig.getOr(BSPConfig.CENT_NUGGETS_MAX, List.<Integer>of()), layers, 3));
    }

    /** A charge succeeds one time in this many. */
    public int chargeOdds() {
        return Math.max(1, at(BSPConfig.getOr(BSPConfig.CENT_CHARGE_ODDS, List.<Integer>of()), layers, 6));
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return switch (slot) {
            case IN -> stack.is(MAGNATITE_ORES) || stack.is(ModItems.MAGNATITE_INGOT.get());
            case COIL -> stack.is(ModItems.COPPER_TETRIUM_COIL.get());
            default -> false;
        };
    }

    @Override
    protected boolean needsRf() {
        return true;
    }

    /** Every layer draws power, and charging draws far more than separating. */
    @Override
    protected int rfPerWorkTick() {
        return layers * (charging() ? BSPConfig.getOr(BSPConfig.CENT_RF_CHARGING, 240) : BSPConfig.getOr(BSPConfig.CENT_RF_SEPARATING, 60));
    }

    @Override
    protected boolean canWork() {
        if (charging()) {
            return fits(OUT, new ItemStack(ModItems.CHARGED_MAGNATITE_INGOT.get()));
        }
        return separating() && fits(OUT, new ItemStack(ModItems.MAGNATITE_NUGGET.get(), nuggetsMax())) && fits(BYPRODUCT, new ItemStack(ModItems.CARBON_DUST.get()));
    }

    @Override
    public int workTime() {
        if (charging()) {
            return layers >= MAX_LAYERS ? BSPConfig.getOr(BSPConfig.CENT_CHARGE_TICKS_FULL, 500) : BSPConfig.getOr(BSPConfig.CENT_CHARGE_TICKS, 900);
        }
        return BSPConfig.getOr(BSPConfig.CENT_SEPARATE_TICKS, 900);
    }

    @Override
    protected void finishJob() {
        ItemStack in = items.getStackInSlot(IN);
        java.util.Random random = new java.util.Random();
        if (charging()) {
            if (random.nextInt(chargeOdds()) == 0) {
                items.setStackInSlot(IN, in.copyWithCount(in.getCount() - 1));
                addOutput(OUT, new ItemStack(ModItems.CHARGED_MAGNATITE_INGOT.get()));
            }
            return; // a failed try keeps the ingot: the next run tries again
        }
        items.setStackInSlot(IN, in.copyWithCount(in.getCount() - 1));
        addOutput(OUT, new ItemStack(ModItems.MAGNATITE_NUGGET.get(), nuggetsMin() + random.nextInt(nuggetsMax() - nuggetsMin() + 1)));
        addOutput(BYPRODUCT, new ItemStack(ModItems.CARBON_DUST.get()));
    }

    @Override
    public List<Need> missing(int fluidMb, boolean burning) {
        List<Need> out = new ArrayList<>();
        if (!isFormed()) {
            out.add(need(ModBlocks.CENTRIFUGE_CASING.get(), "need.bsp_core.structure"));
        }
        ItemStack in = items.getStackInSlot(IN);
        if (in.is(ModItems.MAGNATITE_INGOT.get()) && !hasCoil()) {
            out.add(need(ModItems.COPPER_TETRIUM_COIL.get(), "need.bsp_core.coil"));
        } else if (!separating() && !charging()) {
            out.add(need(ModBlocks.MAGNATITE_ORE.get(), "need.bsp_core.magnatite"));
        }
        if (energyStored() < rfPerWorkTick()) {
            out.add(need(ModBlocks.CENTRIFUGE_POWER_PORT.get(), "need.bsp_core.rf", rfPerWorkTick()));
        }
        if ((separating() || charging()) && !canWork()) {
            out.add(need(ModItems.MAGNATITE_NUGGET.get(), "need.bsp_core.output_full"));
        }
        return out;
    }

    @Nullable
    @Override
    public Component statusLine() {
        return hasCoil() ? Component.translatable("gui.bsp_core.centrifuge.status_coil", layers, chargeOdds())
                : Component.translatable("gui.bsp_core.centrifuge.status", layers, nuggetsMin(), nuggetsMax());
    }

    @Override
    public ItemStack slotIcon(int slot) {
        return switch (slot) {
            case IN -> new ItemStack(ModBlocks.MAGNATITE_ORE.get());
            case COIL -> new ItemStack(ModItems.COPPER_TETRIUM_COIL.get());
            case OUT -> new ItemStack(ModItems.MAGNATITE_NUGGET.get());
            default -> new ItemStack(ModItems.CARBON_DUST.get());
        };
    }

    @Override
    public Component slotHint(int slot) {
        return Component.translatable(switch (slot) {
            case IN -> "hint.bsp_core.centrifuge_in";
            case COIL -> "hint.bsp_core.centrifuge_coil";
            case OUT -> "hint.bsp_core.centrifuge_out";
            default -> "hint.bsp_core.centrifuge_carbon";
        });
    }

    @Override
    public String titleKey() {
        return "block.bsp_core.magnetic_centrifuge";
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Layers", layers);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        layers = Math.max(1, Math.min(MAX_LAYERS, tag.getInt("Layers")));
    }
}
