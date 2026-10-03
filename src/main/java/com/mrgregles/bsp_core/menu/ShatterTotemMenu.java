package com.mrgregles.bsp_core.menu;

import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.registry.ModMenus;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * The placed totem's access panel. Has no item slots yet (the defensive upgrade inventory comes
 * later); it exists so the client can open a screen tied to a specific totem.
 */
public class ShatterTotemMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final Player player;

    public ShatterTotemMenu(int id, Inventory inventory, BlockPos pos) {
        super(ModMenus.SHATTER_TOTEM.get(), id);
        this.pos = pos;
        this.player = inventory.player;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
    }

    public BlockPos getPos() {
        return pos;
    }

    /** The totem this menu belongs to, from whichever side is asking. */
    @Nullable
    public ShatterTotemBlockEntity getTotem() {
        return player.level().getBlockEntity(pos) instanceof ShatterTotemBlockEntity totem ? totem : null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.SHATTER_TOTEM.get());
    }
}
