package com.mrgregles.bsp_core.api.event;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Live trades at the Trade Post, posted on {@code MinecraftForge.EVENT_BUS} on the server thread after the pieces and the
 * items have moved. For other mods (ranks, Discord, statistics); listeners only read. The items are copies.
 */
public abstract class TradeEvent extends Event {
    private final MinecraftServer server;

    protected TradeEvent(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer getServer() {
        return server;
    }

    /** A trade was made: what each side gave (items and pieces) went to the other. */
    public static class Completed extends TradeEvent {
        private final UUID first, second;
        private final String firstName, secondName;
        private final List<ItemStack> firstGave, secondGave;
        private final long firstPieces, secondPieces;

        public Completed(MinecraftServer server, UUID first, String firstName, List<ItemStack> firstGave, long firstPieces,
                         UUID second, String secondName, List<ItemStack> secondGave, long secondPieces) {
            super(server);
            this.first = first;
            this.firstName = firstName;
            this.firstGave = copy(firstGave);
            this.firstPieces = firstPieces;
            this.second = second;
            this.secondName = secondName;
            this.secondGave = copy(secondGave);
            this.secondPieces = secondPieces;
        }

        private static List<ItemStack> copy(List<ItemStack> items) {
            List<ItemStack> out = new ArrayList<>();
            items.forEach(s -> out.add(s.copy()));
            return out;
        }

        /** The player who asked for the trade. */
        public UUID getFirst() {
            return first;
        }

        public String getFirstName() {
            return firstName;
        }

        public List<ItemStack> getFirstGave() {
            return firstGave;
        }

        public long getFirstPieces() {
            return firstPieces;
        }

        /** The player who accepted. */
        public UUID getSecond() {
            return second;
        }

        public String getSecondName() {
            return secondName;
        }

        public List<ItemStack> getSecondGave() {
            return secondGave;
        }

        public long getSecondPieces() {
            return secondPieces;
        }
    }
}
