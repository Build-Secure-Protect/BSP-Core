package com.mrgregles.bsp_core.tracking;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * What a client needs to show a machine's real screen in its Holo Grid box (owner decisions MS2 to MS5): added to the machine's reading as
 * {@code real}: the menu type and title its own screen opens with, its block entity's type, block state and saved data (the client builds a
 * stand-in from these and opens the mod's own screen on it, read-only), and the values of the menu's data slots (for screens that read
 * those, such as the furnace's flame and arrow). The menu is made once per machine for a fake player, never opened for anyone, and let go
 * when the machine is no longer read.
 *
 * <p>Not for blocks whose menu visibly opens the block (chests, barrels, shulker boxes: anything counting its openers): their boxes keep
 * BSP-Core's own panel. Saved data over {@link #MAX_NBT} bytes is left out (the box then shows BSP-Core's panel).
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class ScreenMirror {
    public static final int MAX_NBT = 32 * 1024;
    /** The made menus, by machine, with when each was last read. */
    private static final Map<BlockEntity, Mirror> MENUS = new WeakHashMap<>();
    /** Block entity types with no menu to make (no way to open one is known): never tried again this run. */
    private static final Set<String> NO_MENU = new HashSet<>();
    /** Machines whose menu could not be made just now (locked, unformed): tried again after {@link #RETRY_MS}. */
    private static final Map<BlockEntity, Long> FAILED = new WeakHashMap<>();
    private static final long RETRY_MS = 60_000L;
    /** By block entity class: whether it opens visibly (worked out once per class). */
    private static final Map<Class<?>, Boolean> VISIBLE = new HashMap<>();
    /**
     * Blocks used to open their menus ({@link #byUse}), AE2 and others: only these, whose use with an empty hand does nothing but open their
     * screen (read from each block's code). Others do more (the charger hands its item out, the crank turns), so they keep BSP-Core's panel.
     */
    private static final Set<String> USE_OPENS = Set.of("ae2:cell_workbench", "ae2:condenser", "ae2:controller", "ae2:drive", "ae2:inscriber",
            "ae2:interface", "ae2:io_port", "ae2:molecular_assembler", "ae2:pattern_provider", "ae2:quantum_link", "ae2:spatial_anchor",
            "ae2:spatial_io_port", "ae2:vibration_chamber", "ae2:wireless_access_point",
            // ExtendedAE (its blocks open their screen, unless a wrench or card is in hand), Advanced AE, ME Requester
            // (not its charger or crystal fixer: used, they hand out or take items, as AE2's charger does)
            "expatternprovider:caner", "expatternprovider:circuit_cutter", "expatternprovider:ex_drive", "expatternprovider:ex_inscriber", "expatternprovider:ex_interface", "expatternprovider:ex_io_port",
            "expatternprovider:ex_molecular_assembler", "expatternprovider:ex_pattern_provider", "expatternprovider:ingredient_buffer",
            "expatternprovider:oversize_interface", "advanced_ae:adv_pattern_provider", "advanced_ae:small_adv_pattern_provider",
            "advanced_ae:reaction_chamber", "advanced_ae:quantum_craft", "merequester:requester",
            // Hostile Neural Networks' simulation chamber (Placebo's menu opening, from its block only)
            "hostilenetworks:sim_chamber");
    /** Refined Storage blocks whose use does more than open their screen. */
    private static final Set<String> USE_DOES_MORE = Set.of("refinedstorage:storage_monitor");
    private static final com.mojang.authlib.GameProfile PROFILE =
            new com.mojang.authlib.GameProfile(java.util.UUID.fromString("6b7c3e1a-2f4d-4c8e-9a51-b5f0d3c2e7a4"), "[BSP-Core Holo Grid]");
    private static Field dataSlots;
    private static long sweptMs;

    /** A made menu, with when it was last read and the saved data last sent (refreshed less often the bigger it is). */
    private static final class Mirror {
        final AbstractContainerMenu menu;
        final FakePlayer player;
        final Component title;
        long usedMs;
        CompoundTag nbt;
        long nbtTick = Long.MIN_VALUE;

        Mirror(AbstractContainerMenu menu, FakePlayer player, Component title, long usedMs) {
            this.menu = menu;
            this.player = player;
            this.title = title;
            this.usedMs = usedMs;
        }
    }

    /** While a block is used for its menu ({@link #byUse}): the fake player it is used by, and the title its menu was opened with. */
    private static FakePlayer using;
    private static Component usedTitle;

    private ScreenMirror() {}

    /** Adds {@code real} to {@code out} for {@code be}, when it has a screen that can be shown. */
    public static void add(ServerLevel level, BlockEntity be, CompoundTag out) {
        ResourceLocation type = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(be.getType());
        if (type == null || NO_MENU.contains(type.toString()) || opensVisibly(be) || family(be.getClass(), "com.simibubi.create.")) {
            return; // Create's screens draw blank in a box (its schematic table and schematicannon): its panel until they draw right
        }
        Mirror m = mirror(level, be, type.toString());
        if (m == null) {
            return;
        }
        // the saved data: read again every 2 ticks when small, up to once a second when big, so a busy machine's box costs little to send
        long tick = level.getGameTime();
        // too big to send last time: looked at again only every 10 seconds
        int every = m.nbt == null ? 0 : m.nbt.sizeInBytes() > MAX_NBT ? 200 : Math.min(20, 2 + m.nbt.sizeInBytes() / 1024);
        if (m.nbt == null || tick - m.nbtTick >= every || tick < m.nbtTick) {
            m.nbt = be.saveWithoutMetadata();
            m.nbtTick = tick;
        }
        if (m.nbt.sizeInBytes() > MAX_NBT) {
            return;
        }
        CompoundTag real = new CompoundTag();
        ResourceLocation menuType = BuiltInRegistries.MENU.getKey(m.menu.getType());
        real.putString("menu", menuType == null ? "" : menuType.toString());
        real.putString("be", type.toString());
        real.put("state", NbtUtils.writeBlockState(be.getBlockState()));
        real.put("nbt", m.nbt); // the same tag until read again: nothing changes it, and an unchanged reading is not sent
        real.putString("title", Component.Serializer.toJson(m.title));
        List<DataSlot> slots = slots(m.menu);
        if (slots != null && !slots.isEmpty()) {
            int[] values = new int[slots.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = slots.get(i).get();
            }
            real.putIntArray("data", values);
        }
        out.put("real", real);
    }

    /** Whether {@code c} or a class it extends is in the package starting {@code prefix} (add-ons build on their base mod's classes). */
    public static boolean family(Class<?> c, String prefix) {
        for (; c != null && c != Object.class; c = c.getSuperclass()) {
            if (c.getName().startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Blocks that open visibly for whoever has their menu open (a lid, a sound, a game event): their menu is never made here. Anything
     * with a lid, shulker boxes, and anything counting its openers (Minecraft's counter, or an open count as shulker-style boxes keep).
     */
    private static boolean opensVisibly(BlockEntity be) {
        return VISIBLE.computeIfAbsent(be.getClass(), cls -> {
            if (LidBlockEntity.class.isAssignableFrom(cls) || ShulkerBoxBlockEntity.class.isAssignableFrom(cls)) {
                return true;
            }
            try {
                return countsOpeners(cls);
            } catch (LinkageError | RuntimeException e) {
                return true; // its fields cannot be read: treated as visible, and never mirrored
            }
        });
    }

    /** Whether the class counts its openers: Minecraft's counter, Create's animated container, or an open count as shulker-style boxes keep. */
    private static boolean countsOpeners(Class<?> cls) {
        for (Class<?> c = cls; c != null && c != BlockEntity.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                String n = f.getName().toLowerCase(java.util.Locale.ROOT);
                if (ContainerOpenersCounter.class.isAssignableFrom(f.getType())
                        || f.getType().getSimpleName().contains("AnimatedContainer") // Create's toolbox lid, postbox flag, Frogport
                        || (f.getType() == int.class && (n.contains("opencount") || n.contains("openers")))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Mirror mirror(ServerLevel level, BlockEntity be, String type) {
        long now = System.currentTimeMillis();
        Mirror m = MENUS.get(be);
        if (m != null) {
            m.usedMs = now;
            return m;
        }
        Long failedAt = FAILED.get(be);
        if (failedAt != null && now - failedAt < RETRY_MS) {
            return null;
        }
        MenuProvider provider = be instanceof MenuProvider p ? p : be.getBlockState().getMenuProvider(level, be.getBlockPos());
        if (provider == null) {
            provider = mekanismProvider(be);
        }
        if (provider == null) {
            provider = voltaicProvider(be);
        }
        if (provider == null) {
            provider = powahProvider(be);
        }
        if (provider == null) {
            provider = mcjtyProvider(be);
        }
        if (provider == null) {
            provider = hammerProvider(be);
        }
        if (provider == null && (USE_OPENS.contains(type)
                || (family(be.getClass(), "com.refinedmods.refinedstorage.") && !USE_DOES_MORE.contains(type)))) {
            provider = byUse(level, be); // these open their menus only through their blocks' use
        }
        if (provider == null) {
            NO_MENU.add(type);
            return null;
        }
        try {
            FakePlayer fake = FakePlayerFactory.get(level, PROFILE); // the Holo Grid's own, so nothing here touches another mod's fake player
            BlockPos pos = be.getBlockPos();
            fake.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5); // within reach, for menus that check
            usedTitle = null;
            AbstractContainerMenu menu = provider.createMenu(0, fake.getInventory(), fake);
            if (menu == null) {
                FAILED.put(be, now); // this one may be locked or unformed now; others of its kind may open
                return null;
            }
            Component title = usedTitle != null ? usedTitle : provider.getDisplayName(); // the title its own screen opens with
            usedTitle = null;
            m = new Mirror(menu, fake, title, now);
            MENUS.put(be, m);
            return m;
        } catch (RuntimeException | LinkageError e) {
            FAILED.put(be, now);
            BSPCore.LOGGER.debug("Holo Grid: no real screen for {} ({})", type, e.toString());
            return null;
        }
    }

    /**
     * Blocks that open their menu only when used ({@link #USE_OPENS} and Refined Storage's): the Holo Grid's fake player uses the block
     * with an empty hand (all those blocks then do is open their screen; nothing is sent anywhere, see {@link #openForMirror}), and the
     * menu it is given is the one kept. Only for blocks known to do nothing else: elsewhere a use may do more.
     */
    private static MenuProvider byUse(ServerLevel level, BlockEntity be) {
        return new net.minecraft.world.SimpleMenuProvider((id, inv, player) -> {
            if (!(player instanceof FakePlayer fake)) {
                return null;
            }
            AbstractContainerMenu before = fake.containerMenu;
            fake.getInventory().clearContent(); // the Holo Grid's fake player uses each block with nothing on it, so a use cannot take anything
            BlockPos pos = be.getBlockPos();
            using = fake;
            boolean ok = false;
            try {
                net.minecraft.world.level.block.state.BlockState state = be.getBlockState();
                net.minecraft.world.phys.Vec3 centre = net.minecraft.world.phys.Vec3.atCenterOf(pos);
                state.use(level, fake, net.minecraft.world.InteractionHand.MAIN_HAND,
                        new net.minecraft.world.phys.BlockHitResult(centre, net.minecraft.core.Direction.UP, pos, false));
                if (fake.containerMenu == before) {
                    // cable attachments (Refined Storage's exporters, importers, constructors) open only from their head, where they face
                    for (var prop : state.getProperties()) {
                        if (prop instanceof net.minecraft.world.level.block.state.properties.DirectionProperty dp) {
                            net.minecraft.core.Direction d = state.getValue(dp);
                            state.use(level, fake, net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(
                                    centre.add(d.getStepX() * 0.4, d.getStepY() * 0.4, d.getStepZ() * 0.4), d, pos, false));
                            break;
                        }
                    }
                }
                ok = true;
            } finally {
                using = null;
                if (!ok && fake.containerMenu != before && fake.containerMenu != fake.inventoryMenu) {
                    try {
                        fake.containerMenu.removed(fake); // the use failed part-way: a menu it opened is closed again
                    } catch (RuntimeException | LinkageError ignored) {
                        // only tidying
                    }
                    fake.containerMenu = fake.inventoryMenu;
                }
            }
            AbstractContainerMenu got = fake.containerMenu;
            fake.containerMenu = fake.inventoryMenu; // the fake player does not keep it open; this mirror holds it
            // a use that handed anything out (none of the listed blocks should): it goes back on the ground at the machine, never lost
            for (int i = 0; i < fake.getInventory().getContainerSize(); i++) {
                net.minecraft.world.item.ItemStack st = fake.getInventory().removeItemNoUpdate(i);
                if (!st.isEmpty()) {
                    net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, st);
                }
            }
            return got != before && got != fake.inventoryMenu ? got : null;
        }, be.getBlockState().getBlock().getName());
    }

    /**
     * Called from {@code FakeMenuOpenMixin} when Forge is asked to open a menu screen for a player: for the fake player using a block in
     * {@link #byUse}, the menu is made and kept here and nothing is sent (Forge would send the opening to the fake player's connection,
     * which never sends, so it would only pile up). Returns false for every other opening, which Forge then does as usual.
     */
    public static boolean openForMirror(net.minecraft.server.level.ServerPlayer player, MenuProvider provider) {
        if (using == null || player != using) {
            return false;
        }
        AbstractContainerMenu menu = provider.createMenu(0, player.getInventory(), player);
        if (menu != null) {
            usedTitle = provider.getDisplayName();
            player.containerMenu = menu;
        }
        return true;
    }

    /** Mekanism's machines open their screens through the GUI attribute of their block: {@code Attribute.get(block, AttributeGui).getProvider(tile)}. */
    private static MenuProvider mekanismProvider(BlockEntity be) {
        if (!family(be.getClass(), "mekanism.")) {
            return null;
        }
        try {
            Class<?> attribute = Class.forName("mekanism.common.block.attribute.Attribute");
            Class<?> gui = Class.forName("mekanism.common.block.attribute.AttributeGui");
            Object a = attribute.getMethod("get", net.minecraft.world.level.block.Block.class, Class.class).invoke(null, be.getBlockState().getBlock(), gui);
            if (a == null) {
                return null;
            }
            Class<?> tile = Class.forName("mekanism.common.tile.base.TileEntityMekanism");
            return tile.isInstance(be) ? (MenuProvider) gui.getMethod("getProvider", tile).invoke(a, be) : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return null;
        }
    }

    /** Electrodynamics and Nuclear Science (Voltaic): a machine's menu is its {@code ContainerProvider} component. */
    private static MenuProvider voltaicProvider(BlockEntity be) {
        try {
            Class<?> tile = Class.forName("voltaic.prefab.tile.GenericTile");
            if (!tile.isInstance(be)) {
                return null;
            }
            Class<?> types = Class.forName("voltaic.prefab.tile.components.IComponentType");
            Object type = types.getField("ContainerProvider").get(null);
            if (!(boolean) tile.getMethod("hasComponent", types).invoke(be, type)) {
                return null;
            }
            return tile.getMethod("getComponent", types).invoke(be, type) instanceof MenuProvider p ? p : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return null;
        }
    }

    /** Powah: its blocks make their menus with {@code getContainer(id, inventory, tile, hit)}. */
    private static MenuProvider powahProvider(BlockEntity be) {
        if (!family(be.getClass(), "owmii.powah.")) {
            return null;
        }
        try {
            Class<?> tile = Class.forName("owmii.powah.lib.block.AbstractTileEntity");
            var block = be.getBlockState().getBlock();
            var make = block.getClass().getMethod("getContainer", int.class, net.minecraft.world.entity.player.Inventory.class, tile,
                    net.minecraft.world.phys.BlockHitResult.class);
            return new net.minecraft.world.SimpleMenuProvider((id, inv, player) -> {
                try {
                    return (AbstractContainerMenu) make.invoke(block, id, inv, be, null);
                } catch (ReflectiveOperationException e) {
                    return null;
                }
            }, block.getName());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return null;
        }
    }

    /** RFTools and XNet (McJtyLib): the menu is the machine's {@code CONTAINER_PROVIDER_CAPABILITY}. */
    @SuppressWarnings("unchecked")
    private static MenuProvider mcjtyProvider(BlockEntity be) {
        if (!family(be.getClass(), "mcjty.")) {
            return null;
        }
        try {
            var cap = (net.minecraftforge.common.capabilities.Capability<MenuProvider>) Class.forName("mcjty.lib.api.container.CapabilityContainerProvider")
                    .getField("CONTAINER_PROVIDER_CAPABILITY").get(null);
            return be.getCapability(cap).resolve().orElse(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return null;
        }
    }

    /** Solar Flux (HammerLib): tiles make their menu with {@code IContainerTile.openContainer(player, id)}. */
    private static MenuProvider hammerProvider(BlockEntity be) {
        try {
            Class<?> tile = Class.forName("org.zeith.hammerlib.api.tiles.IContainerTile");
            if (!tile.isInstance(be)) {
                return null;
            }
            var open = tile.getMethod("openContainer", net.minecraft.world.entity.player.Player.class, int.class);
            return new net.minecraft.world.SimpleMenuProvider((id, inv, player) -> {
                try {
                    return (AbstractContainerMenu) open.invoke(be, player, id);
                } catch (ReflectiveOperationException e) {
                    return null;
                }
            }, be.getBlockState().getBlock().getName());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<DataSlot> slots(AbstractContainerMenu menu) {
        try {
            if (dataSlots == null) {
                dataSlots = ObfuscationReflectionHelper.findField(AbstractContainerMenu.class, "f_38842_");
            }
            return (List<DataSlot>) dataSlots.get(menu);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Every second on the server: lets go of menus of machines not read for ten seconds (the box went, or nobody watches it now). */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !MENUS.isEmpty()) {
            sweep(System.currentTimeMillis(), false);
        }
    }

    /** The server stopping (or a single-player world closing): every menu let go, and every kind tried afresh next time. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        sweep(System.currentTimeMillis(), true);
        NO_MENU.clear();
        FAILED.clear();
        VISIBLE.clear();
    }

    private static void sweep(long now, boolean all) {
        if (!all && now - sweptMs < 1000) {
            return;
        }
        sweptMs = now;
        MENUS.entrySet().removeIf(e -> {
            if (!all && now - e.getValue().usedMs <= 10_000L) {
                return false;
            }
            try {
                e.getValue().menu.removed(e.getValue().player);
            } catch (RuntimeException | LinkageError ignored) {
                // only tidying
            }
            return true;
        });
    }
}
