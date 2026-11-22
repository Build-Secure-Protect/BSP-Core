package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mrgregles.bsp_core.looks.LookLayout;
import com.mrgregles.bsp_core.looks.LookPixels;
import com.mrgregles.bsp_core.looks.TotemLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The 64 by 64 textures of the looks this client has seen, made from {@link LookPixels} on first use and shared by every totem
 * that wears the same look (owner, time and hat do not matter). The least recently used are let go beyond {@link #MAX}.
 * Render thread only.
 */
public final class LookTextures {
    private LookTextures() {}

    private static final int MAX = 256;

    /** A look's key with its hash worked out once (a painting's tag holds 776 bytes). */
    private record Key(CompoundTag tag, int hash) {
        Key(CompoundTag tag) {
            this(tag, tag.hashCode());
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && k.hash == hash && k.tag.equals(tag);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static final Map<Key, ResourceLocation> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, ResourceLocation> eldest) {
            if (size() > MAX) {
                Minecraft.getInstance().getTextureManager().release(eldest.getValue());
                return true;
            }
            return false;
        }
    };

    /** What makes two looks draw the same: everything but whose and when, and the hat (drawn apart). */
    private static CompoundTag key(TotemLook look) {
        CompoundTag t = look.save();
        t.remove("For");
        t.remove("Made");
        t.remove("Hat");
        t.remove("HatTint");
        return t;
    }

    @javax.annotation.Nullable
    private static DynamicTexture bench;
    @javax.annotation.Nullable
    private static ResourceLocation benchId;
    @javax.annotation.Nullable
    private static CompoundTag benchKey;

    /**
     * The paint bench's own texture, rewritten in place when the painting changes (a new texture for every stroke would fill the
     * cache with paintings nobody will see again).
     */
    public static ResourceLocation bench(TotemLook look) {
        if (bench == null) {
            bench = new DynamicTexture(new NativeImage(LookLayout.SIZE, LookLayout.SIZE, true));
            benchId = Minecraft.getInstance().getTextureManager().register("bsp_core_look_bench", bench);
        }
        CompoundTag key = key(look);
        if (!key.equals(benchKey)) {
            fill(bench.getPixels(), LookPixels.texture(look));
            bench.upload();
            benchKey = key;
        }
        return benchId;
    }

    private static void fill(NativeImage img, int[] argb) {
        for (int y = 0; y < LookLayout.SIZE; y++) {
            for (int x = 0; x < LookLayout.SIZE; x++) {
                int c = argb[y * LookLayout.SIZE + x];
                img.setPixelRGBA(x, y, (c & 0xFF000000) | ((c & 0xFF) << 16) | (c & 0xFF00) | ((c >> 16) & 0xFF)); // NativeImage holds ABGR
            }
        }
    }

    public static ResourceLocation get(TotemLook look) {
        Key key = look.renderKey instanceof Key k ? k : new Key(key(look));
        look.renderKey = key;
        ResourceLocation rl = CACHE.get(key);
        if (rl != null) {
            return rl;
        }
        NativeImage img = new NativeImage(LookLayout.SIZE, LookLayout.SIZE, true);
        fill(img, LookPixels.texture(look));
        rl = Minecraft.getInstance().getTextureManager().register("bsp_core_look", new DynamicTexture(img));
        CACHE.put(key, rl);
        return rl;
    }
}
