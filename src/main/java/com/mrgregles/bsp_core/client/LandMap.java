package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import javax.annotation.Nullable;

/**
 * A top-down map of the land around a point, drawn from the terrain the client has loaded, the way
 * a vanilla map is (lit from the north). Square, {@code across} blocks a side, redrawn every few
 * seconds while the screen is open so chunks that arrive fill in. The Land Office draws plots on top.
 */
public final class LandMap {
    public static final int MAX = 192;
    private static final ResourceLocation TEXTURE = new ResourceLocation(BSPCore.MODID, "land_map");
    @Nullable
    private static DynamicTexture texture;
    private static int builtX = Integer.MIN_VALUE, builtZ, builtAcross;
    private static long builtAt;

    private LandMap() {}

    private static void ensure(Level level, int cx, int cz, int across) {
        Minecraft mc = Minecraft.getInstance();
        if (texture == null) {
            texture = new DynamicTexture(MAX, MAX, false);
            mc.getTextureManager().register(TEXTURE, texture);
        }
        long now = level.getGameTime();
        if (cx == builtX && cz == builtZ && across == builtAcross && now - builtAt < 60 && now >= builtAt) {
            return;
        }
        builtX = cx;
        builtZ = cz;
        builtAcross = across;
        builtAt = now;
        NativeImage image = texture.getPixels();
        if (image == null) {
            return;
        }
        int x0 = cx - across / 2, z0 = cz - across / 2;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int px = 0; px < across; px++) {
            for (int pz = 0; pz < across; pz++) {
                int wx = x0 + px, wz = z0 + pz, rgb = 0x0A1A10;
                if (level.hasChunk(wx >> 4, wz >> 4)) {
                    int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
                    MapColor colour = MapColor.NONE;
                    for (int y = top - 1; y >= Math.max(level.getMinBuildHeight(), top - 8) && colour == MapColor.NONE; y--) {
                        colour = level.getBlockState(p.set(wx, y, wz)).getMapColor(level, p);
                    }
                    if (colour != MapColor.NONE) {
                        int north = level.hasChunk(wx >> 4, (wz - 1) >> 4) ? level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz - 1) : top;
                        int shade = top > north ? 255 : top < north ? 180 : 220;
                        rgb = ((colour.col >> 16 & 0xFF) * shade / 255) << 16 | ((colour.col >> 8 & 0xFF) * shade / 255) << 8 | (colour.col & 0xFF) * shade / 255;
                    }
                }
                image.setPixelRGBA(px, pz, 0xFF000000 | (rgb & 0xFF) << 16 | (rgb & 0xFF00) | (rgb >> 16 & 0xFF)); // stored as ABGR
            }
        }
        texture.upload();
    }

    /** Draws the land {@code across} blocks a side around {@code cx, cz} into the box at {@code x, y} of size {@code size}. North is up. */
    public static void draw(GuiGraphics g, Level level, int cx, int cz, int across, int x, int y, int size) {
        across = Math.max(16, Math.min(MAX, across));
        ensure(level, cx, cz, across);
        g.blit(TEXTURE, x, y, size, size, 0f, 0f, across, across, MAX, MAX);
    }
}
