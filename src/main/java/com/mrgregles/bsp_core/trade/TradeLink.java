package com.mrgregles.bsp_core.trade;

import net.minecraft.nbt.CompoundTag;

/**
 * How trade messages reach the other servers of the network: Redis kind {@code trade} on a live network, a loopback in
 * GameTests. Messages may be lost; the hubs time out and recover from the database. {@code toServer} "*" is every server.
 */
public interface TradeLink {
    void send(String toServer, CompoundTag message);
}
