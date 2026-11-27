package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.shop.Shop;
import com.mrgregles.bsp_core.shop.Shops;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;

/** GameTest helpers for shops. */
final class ShopTestKit {
    private ShopTestKit() {}

    /** {@link Shops#create}, then switched on: the tests of trading and stalls want a shop players can reach (a new shop is made off). */
    @Nullable
    static Shop on(MinecraftServer server, String id, String name, Shop.Kind kind) {
        Shop s = Shops.create(server, id, name, kind);
        if (s != null) {
            s.enabled = true;
            Shops.save(server, s);
        }
        return s;
    }
}
