package com.mrgregles.bsp_core.bank;

import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.BankDataPacket;
import com.mrgregles.bsp_core.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The banking screen's menu: no slots, the account comes down in {@link BankDataPacket}s and
 * actions go up in {@code BankActionPacket}s. Opened at an ATM (the player must stay within reach)
 * or from the Portable Banker (no position).
 */
public class BankMenu extends AbstractContainerMenu implements BankClientMenu {
    @Nullable
    public final BlockPos atm;
    /** Client-side copies, filled by the data packet. */
    public final BankAccount account = new BankAccount();
    public final List<BankTransaction> log = new ArrayList<>();
    public final List<String> found = new ArrayList<>();
    public String messageKey = "";
    public String[] messageArgs = new String[0];
    public int messageSerial;

    public BankMenu(int id, Inventory inv, @Nullable BlockPos atm) {
        super(ModMenus.BANK.get(), id);
        this.atm = atm;
    }

    @Override
    public void receive(BankAccount account, List<BankTransaction> log, List<String> found, String key, String[] args) {
        this.account.copyFrom(account);
        this.log.clear();
        this.log.addAll(log);
        this.found.clear();
        this.found.addAll(found);
        if (!key.isEmpty()) {
            messageKey = key;
            messageArgs = args;
            messageSerial++;
        }
    }

    public boolean portable() {
        return atm == null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return atm == null || player.distanceToSqr(atm.getX() + 0.5, atm.getY() + 1, atm.getZ() + 0.5) < 64;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (atm != null && player instanceof ServerPlayer sp && sp.level().getBlockEntity(atm) instanceof BankAtmBlockEntity be) {
            be.userClosed();
        }
    }

    /** Server: sends the player's account, log, a message and search results to their open banking screen. */
    public static void sync(ServerPlayer player, String messageKey, List<String> found, @Nullable String[] messageArgs) {
        if (!(player.containerMenu instanceof BankMenu)) {
            return;
        }
        Bank.log(player.server, player.getUUID(), log -> {
            if (player.containerMenu instanceof BankMenu) {
                BSPNetwork.sendTo(player, new BankDataPacket(Bank.account(player.server, player.getUUID()), log, messageKey, messageArgs == null ? new String[0] : messageArgs, found));
            }
        });
    }
}
