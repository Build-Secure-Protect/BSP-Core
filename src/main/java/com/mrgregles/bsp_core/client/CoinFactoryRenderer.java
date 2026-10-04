package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.client.MachineKit.Mat;
import com.mrgregles.bsp_core.coin.CoinFactoryBlockEntity;
import com.mrgregles.bsp_core.coin.CoinTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.model.data.ModelData;

/**
 * Shatter Coin Factory slice ("Cascade Mill", tools/preview/factory_concepts.html round two).
 *
 * <p>Unformed: every missing block is drawn as a small pulsing ghost where it must be placed.
 * Formed: the blocks hide themselves and this draws the slice in a 16 x 32 x 48 pixel space, front
 * at z = 0: three belts stepping down from the back, a stamping press over the middle belt,
 * machinery under every belt, glass on outside walls only, and the coin tray in front. Where a
 * slice joins a neighbour its wall is replaced by one slim rib, and the drive shaft, roof rail and
 * tray run through with a glowing coupling. A single slice carries its screen on the front; a
 * joined machine gets one podium screen centred on the row. Motivators on top are drawn half a
 * block high, pulsing faster the more a slice has, and linked to their neighbours.
 */
public class CoinFactoryRenderer implements BlockEntityRenderer<CoinFactoryBlockEntity> {
    private static final int TQ = 0x19D3B0, ITEM = 0x3A8BFF, RF = 0xFFD23A, VIO = 0xB58CFF, BELT = 0x14171D, SCREEN = 0x0C1116, WARN = 0xE3B341,
            GLASS = 0xBFE6FF, MARK = 0x2A2F3A, BAD = 0xD63B2F;
    /** Belt-top waypoints {z, y} from the hopper to the tray; blanks become coins once past the press. */
    private static final float[][] PATH = {{39, 25}, {29, 25}, {28, 17}, {15, 17}, {14, 9}, {2, 9}, {-2.5f, 3.4f}};
    private static final float PRESS_Z = 22;
    /** Size of the blanks and coins on the belts and in the tray, in model pixels. */
    private static final float ITEM_SCALE = 2;
    private static final float[][] BELTS = {{28, 40, 25}, {14, 30, 17}, {1, 16, 9}};

    private final ItemRenderer items;
    private MachineKit k;
    private MultiBufferSource buffers;
    private PoseStack pose;

    public CoinFactoryRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.getItemRenderer();
    }

    private void b(Mat mat, float x0, float y0, float z0, float x1, float y1, float z1) {
        k.box(x0, y0, z0, x1, y1, z1, mat);
    }

    private void gl(int rgb, float bright, float x0, float y0, float z0, float x1, float y1, float z1) {
        k.glow(x0, y0, z0, x1, y1, z1, rgb, bright);
    }

    /** Glass is queued and drawn after everything else in the slice, so the workings behind it are tinted by it. */
    private final java.util.List<float[]> panes = new java.util.ArrayList<>();

    private void glass(float x0, float y0, float z0, float x1, float y1, float z1) {
        panes.add(new float[]{x0, y0, z0, x1, y1, z1});
    }

    /** A box turning about the x axis through its own centre. */
    private void spin(Mat mat, float angle, float x0, float y0, float z0, float x1, float y1, float z1) {
        float cy = (y0 + y1) / 2, cz = (z0 + z1) / 2;
        pose.pushPose();
        pose.translate(0, cy, cz);
        pose.mulPose(Axis.XP.rotation(angle));
        k.box(x0, y0 - cy, z0 - cz, x1, y1 - cy, z1 - cz, mat);
        pose.popPose();
    }

    private void item(ItemStack stack, Level level, int light, float x, float y, float z, float yRot) {
        pose.pushPose();
        pose.translate(x, y + 0.07f, z);
        pose.mulPose(Axis.YP.rotation(yRot));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        items.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, level, 0);
        pose.popPose();
    }

    private static int run(CoinFactoryBlockEntity f, Direction dir) {
        int n = 0;
        CoinFactoryBlockEntity prev = f;
        for (int i = 1; i < CoinFactoryBlockEntity.MAX_SLICES; i++) {
            if (!(f.getLevel().getBlockEntity(f.getBlockPos().relative(dir, i)) instanceof CoinFactoryBlockEntity next) || !prev.joins(next)) {
                break;
            }
            prev = next;
            n++;
        }
        return n;
    }

    @Override
    public void render(CoinFactoryBlockEntity f, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = f.getLevel();
        if (level == null) {
            return;
        }
        if (!f.isFormed()) {
            ghosts(f, level, partialTick, pose, buffers);
            return;
        }
        this.pose = pose;
        this.buffers = buffers;
        float t = (level.getGameTime() + partialTick) / 20f;
        boolean working = f.isWorking(), on = f.isEnabled() || f.isDemo();
        float run = working ? t : 0, lamp = on ? 0.7f + 0.3f * Mth.sin(t * 3) : 0.25f;
        float phase = (f.getBlockPos().asLong() & 7) * 0.8f;
        Direction row = f.rowDirection();
        int below = run(f, row.getOpposite()), above = run(f, row), n = below + above + 1;
        boolean first = below == 0, last = above == 0;

        float yRot = switch (f.facing()) {
            case EAST -> -90f;
            case SOUTH -> 180f;
            case WEST -> 90f;
            default -> 0f;
        };
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(yRot));
        pose.translate(-0.5, 0, -0.5);
        pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
        k = new MachineKit(pose, buffers, light);

        // deck with plating
        b(Mat.HULL2, 0, 0, 0, 16, 3, 48);
        b(Mat.MID, .5f, 3, .5f, 15.5f, 3.3f, 15.5f);
        b(Mat.MID, .5f, 3, 16.5f, 15.5f, 3.3f, 31.5f);
        b(Mat.MID, .5f, 3, 32.5f, 15.5f, 3.3f, 39.5f);
        gl(WARN, 1, 0, 1, -.2f, 16, 1.6f, 0);

        // back blocks: hull, hopper mouth, the two sockets (blue blanks low, yellow RF high)
        b(Mat.HULL, 0, 3, 40, 16, 30, 48);
        b(Mat.TRIM, 1, 6, 39.6f, 15, 8, 40);
        b(Mat.TRIM, 1, 26, 39.6f, 15, 27, 40);
        gl(SCREEN, 1, 4.5f, 22, 39.5f, 11.5f, 28, 40);
        gl(ITEM, lamp, 5, 22.4f, 39.3f, 11, 23, 39.5f);
        b(Mat.HULL2, 2, 10, 39.6f, 14, 20, 40);
        for (float y = 11; y < 20; y += 2) {
            b(Mat.MID, 3, y, 39.4f, 13, y + .8f, 39.6f);
        }
        b(Mat.TRIM, 4.5f, 4.5f, 48, 11.5f, 11.5f, 48.6f);
        gl(ITEM, lamp, 5.7f, 5.7f, 48.6f, 10.3f, 10.3f, 48.9f);
        b(Mat.TRIM, 4.5f, 20.5f, 48, 11.5f, 27.5f, 48.6f);
        gl(RF, lamp, 5.7f, 21.7f, 48.6f, 10.3f, 26.3f, 48.9f);
        for (float[] p : new float[][]{{2, 14}, {2, 17}, {14, 14}, {14, 17}}) {
            b(Mat.TRIM, p[0] - .5f, p[1] - .5f, 48, p[0] + .5f, p[1] + .5f, 48.3f);
        }

        // roof: rails, cross beams, glass strip, light bar
        b(Mat.HULL2, 0, 30, 0, 16, 32, 2);
        b(Mat.HULL2, 0, 30, 15, 16, 32, 17);
        b(Mat.HULL2, 0, 30, 31, 16, 32, 33);
        b(Mat.HULL2, 0, 30, 40, 16, 32, 48);
        b(Mat.HULL2, 0, 30, 2, 3, 32, 40);
        b(Mat.HULL2, 13, 30, 2, 16, 32, 40);
        glass(3, 30.6f, 2, 13, 31.2f, 40);
        gl(TQ, lamp, 0, 29.4f, .2f, 16, 30, .6f);

        // walls: glass on an outside wall, one shared rib and couplings on a join
        if (first) {
            sidewall(0, 1, lamp);
        }
        if (last) {
            sidewall(15, 16, lamp);
        }
        if (!first) {
            b(Mat.HULL2, -.6f, 3, 0, .6f, 30, 2);
            b(Mat.HULL2, -.6f, 3, 38, .6f, 30, 40);
            b(Mat.TRIM, -1.2f, 4.6f, 15.6f, 1.2f, 7.4f, 18.4f);
            gl(TQ, lamp, -.5f, 4.3f, 15.3f, .5f, 7.7f, 18.7f);
            b(Mat.TRIM, -1.5f, 31.6f, 6, 1.5f, 32.6f, 10);
            gl(TQ, lamp, -1, 32.6f, 7, 1, 32.9f, 9);
            b(Mat.TRIM, -1.5f, 31.6f, 22, 1.5f, 32.6f, 26);
            gl(TQ, lamp, -1, 32.6f, 23, 1, 32.9f, 25);
            gl(TQ, lamp, -.3f, 8, -.05f, .3f, 28, .3f);
        }

        // belts with rails, end rollers, hangers and moving marks; drop chutes between them
        for (float[] belt : BELTS) {
            float z0 = belt[0], z1 = belt[1], y = belt[2];
            gl(BELT, 1, 4, y - 1.2f, z0, 12, y, z1);
            b(Mat.TRIM, 3.2f, y - 1.8f, z0 - .4f, 4, y + .5f, z1 + .4f);
            b(Mat.TRIM, 12, y - 1.8f, z0 - .4f, 12.8f, y + .5f, z1 + .4f);
            b(Mat.PANEL, 3, y - 1, z0 - .2f, 13, y - .2f, z0 + .8f);
            b(Mat.PANEL, 3, y - 1, z1 - .8f, 13, y - .2f, z1 + .2f);
            for (float z = z0 + 3; z < z1 - 1; z += 4) {
                b(Mat.MID, 3.4f, y - 3.4f, z, 4, y - 1.8f, z + .8f);
                b(Mat.MID, 12, y - 3.4f, z, 12.6f, y - 1.8f, z + .8f);
            }
            gl(TQ, lamp, 3.2f, y + .5f, z0, 3.5f, y + .75f, z1);
            gl(TQ, lamp, 12.5f, y + .5f, z0, 12.8f, y + .75f, z1);
            float span = z1 - z0 - 2;
            for (int i = 0; z0 + 1 + i * 3 < z1 - 1; i++) {
                float z = z0 + 1 + Mth.positiveModulo(i * 3 - run * 5, span);
                gl(MARK, 1, 4.2f, y, z, 11.8f, y + .08f, Math.min(z1, z + .5f));
            }
        }
        b(Mat.MID, 4, 18, 27.2f, 12, 24, 27.6f);
        b(Mat.MID, 4, 10, 13.2f, 12, 16, 13.6f);
        b(Mat.MID, 4, 4, .2f, 12, 8, .6f);

        // press: gantry, guide rails, hydraulic lines, moving ram, head and die
        b(Mat.MID, 2.2f, 17, 18.5f, 4, 30, 25.5f);
        b(Mat.MID, 12, 17, 18.5f, 13.8f, 30, 25.5f);
        b(Mat.HULL, 2.2f, 28, 18.5f, 13.8f, 30, 25.5f);
        for (float[] r : new float[][]{{4, 19.4f}, {4, 24.1f}, {11.6f, 19.4f}, {11.6f, 24.1f}}) {
            b(Mat.PANEL, r[0], 18, r[1], r[0] + .4f, 28, r[1] + .5f);
        }
        b(Mat.TRIM, 1.6f, 19, 21.4f, 2.2f, 29, 22.6f);
        b(Mat.TRIM, 13.8f, 19, 21.4f, 14.4f, 29, 22.6f);
        float lift = 4 * (working ? Math.abs(Mth.sin(t * 2.6f + phase)) : 1);
        b(Mat.PANEL, 6.5f, 22 + lift, 20.5f, 9.5f, 27 + lift, 23.5f);
        b(Mat.HULL2, 4.6f, 18.7f + lift, 19.5f, 11.4f, 21.7f + lift, 24.5f);
        gl(TQ, lamp, 5.6f, 18.15f + lift, 20.5f, 10.4f, 18.65f + lift, 23.5f);
        gl(TQ, lamp, 5, 28.4f, 18.2f, 11, 29.2f, 18.5f);

        // under the back belt: finned motor, flywheel, feed pipes
        b(Mat.HULL, 3, 3.3f, 31, 13, 15, 39);
        for (float z = 32; z < 39; z += 1.6f) {
            b(Mat.MID, 2.6f, 5, z, 13.4f, 14, z + .7f);
        }
        b(Mat.HULL2, 4, 15, 32, 12, 16.5f, 38);
        b(Mat.TRIM, 5, 16.5f, 33.5f, 7, 23, 35.5f);
        b(Mat.TRIM, 9, 16.5f, 33.5f, 11, 23, 35.5f);
        b(Mat.MID, 5, 19, 35.5f, 11, 20.4f, 36.6f);
        spin(Mat.PANEL, run * 3, 6.2f, 6, 29.6f, 9.8f, 13, 30.6f);
        b(Mat.TRIM, 7.2f, 8.5f, 29.2f, 8.8f, 10.5f, 31);
        gl(WARN, lamp, 4, 15.1f, 31.4f, 12, 15.5f, 31.8f);

        // under the middle belt: press anvil, gear train, pumps
        b(Mat.MID, 5, 3.3f, 19, 11, 15.6f, 25);
        for (float y : new float[]{6, 10, 13.4f}) {
            b(Mat.TRIM, 4.6f, y, 18.6f, 11.4f, y + .8f, 25.4f);
        }
        b(Mat.HULL, 4.2f, 3.3f, 18.2f, 11.8f, 5, 25.8f);
        b(Mat.HULL2, 2, 3.3f, 25.6f, 14, 11, 29.2f);
        b(Mat.TRIM, 3, 11, 26.2f, 13, 12, 28.6f);
        spin(Mat.PANEL, -run * 3, 2.4f, 5.5f, 26.2f, 3.2f, 9.5f, 29);
        spin(Mat.PANEL, run * 3, 12.8f, 5.5f, 26.2f, 13.6f, 9.5f, 29);
        for (int i = 0; i < 2; i++) {
            float px = i == 0 ? 2.6f : 11.6f, d = working ? Mth.sin(t * 4 + i * 2) * 1.2f : 0;
            b(Mat.HULL, px, 3.3f, 14.6f, px + 2, 8, 17);
            b(Mat.PANEL, px + .5f, 8 + d, 15.2f, px + 1.5f, 12 + d, 16.4f);
        }

        // under the front belt: drive rollers and chain guard
        for (int i = 0; i < 4; i++) {
            float z = 3 + i * 3.2f;
            spin(Mat.MID, (i % 2 == 0 ? 1 : -1) * run * 3, 4.4f, 4.2f, z, 11.6f, 6.6f, z + 2.2f);
        }
        b(Mat.HULL, 3.4f, 3.3f, 2, 4.2f, 7.4f, 14);
        b(Mat.HULL, 11.8f, 3.3f, 2, 12.6f, 7.4f, 14);
        gl(TQ, lamp, 3.2f, 5.4f, 2, 3.4f, 5.8f, 14);

        // drive shaft through the whole machine, pipes along the deck
        spin(Mat.PANEL, run * 3, 0, 5.2f, 16.2f, 16, 6.8f, 17.8f);
        b(Mat.HULL, 7, 3.3f, 15.6f, 9, 7.6f, 18.4f);
        b(Mat.TRIM, 0, 3.3f, 36.6f, 16, 4.5f, 37.8f);
        b(Mat.MID, 0, 3.3f, 29.6f, 16, 4.3f, 30.4f);
        b(Mat.TRIM, 0, 26, 38.6f, 16, 27, 39.4f);

        // tray: one continuous trough, closed only at the outer ends
        b(Mat.MID, 0, 1.6f, -6, 16, 2.6f, 0);
        b(Mat.TRIM, 0, 2.6f, -6, 16, 4.6f, -5.4f);
        if (first) {
            b(Mat.TRIM, 0, 2.6f, -6, .6f, 4.6f, 0);
        }
        if (last) {
            b(Mat.TRIM, 15.4f, 2.6f, -6, 16, 4.6f, 0);
        }

        // the real items: coins in the tray, blanks and coins of the tier being pressed on the belts
        CoinTier tray = f.isDemo() ? f.visualTier() : f.trayTier();
        if (tray != null) {
            ItemStack coin = new ItemStack(tray.coin());
            float[][] pile = {{7, -3, .3f}, {8.6f, -2.6f, .9f}, {9.4f, -3.4f, .1f}, {7.8f, -2.2f, 1.6f}, {6.4f, -3.8f, 2.2f}, {8.2f, -3.3f, .6f}};
            for (int i = 0; i < pile.length; i++) {
                item(coin, level, light, pile[i][0], 2.6f + (i / 3) * .13f, pile[i][1], pile[i][2]);
            }
        }
        CoinTier job = f.visualTier();
        if (working && job != null) {
            ItemStack blank = new ItemStack(job.blank()), coin = new ItemStack(job.coin());
            float total = 0;
            float[] len = new float[PATH.length - 1];
            for (int i = 0; i < len.length; i++) {
                len[i] = Mth.sqrt(Mth.square(PATH[i + 1][0] - PATH[i][0]) + Mth.square(PATH[i + 1][1] - PATH[i][1]));
                total += len[i];
            }
            for (int i = 0; i < 7; i++) {
                float u = Mth.frac(t * .08f + phase * .1f + i / 7f) * total;
                int seg = 0;
                while (seg < len.length - 1 && u > len[seg]) {
                    u -= len[seg++];
                }
                float q = Math.min(1, u / len[seg]);
                float z = Mth.lerp(q, PATH[seg][0], PATH[seg + 1][0]), y = Mth.lerp(q, PATH[seg][1], PATH[seg + 1][1]);
                item(z < PRESS_Z ? coin : blank, level, light, 8, y, z, 0);
            }
        }

        // screen: on the front of a single slice, or one podium centred on a joined machine
        int screen = on ? TQ : BAD;
        if (n == 1) {
            b(Mat.HULL, 2.5f, 10.4f, -.8f, 13.5f, 16.4f, .2f);
            gl(SCREEN, 1, 3.3f, 11, -1.1f, 12.7f, 15.8f, -.8f);
            gl(screen, 1, 4, 11.8f, -1.3f, 9, 12.6f, -1.1f);
            gl(screen, 1, 4, 13.8f, -1.3f, 12, 14.5f, -1.1f);
            gl(RF, 1, 10.5f, 11.8f, -1.3f, 12, 12.6f, -1.1f);
        } else if (below == n / 2) {
            float cx = n * 8 - below * 16;
            b(Mat.HULL2, cx - 8, 0, -16, cx + 8, 2, -6);
            gl(WARN, 1, cx - 8, 2, -16, cx + 8, 2.4f, -15.6f);
            b(Mat.HULL, cx - 2.5f, 2, -12.5f, cx + 2.5f, 11, -9);
            b(Mat.MID, cx - 2, 4, -12.8f, cx + 2, 9, -12.5f);
            b(Mat.HULL, cx - 7, 10.5f, -13.5f, cx + 7, 19.5f, -11);
            b(Mat.TRIM, cx - 7.4f, 10.1f, -13.2f, cx + 7.4f, 10.6f, -11.2f);
            gl(SCREEN, 1, cx - 6, 11.5f, -13.9f, cx + 6, 18.5f, -13.5f);
            gl(screen, 1, cx - 5, 12.5f, -14.1f, cx + 1, 13.4f, -13.9f);
            gl(screen, 1, cx - 5, 14.6f, -14.1f, cx + 5, 15.3f, -13.9f);
            gl(WARN, 1, cx - 5, 16.4f, -14.1f, cx - 1, 17.1f, -13.9f);
            gl(RF, 1, cx + 3, 12.5f, -14.1f, cx + 5, 13.4f, -13.9f);
            b(Mat.TRIM, cx - .8f, 2, -9, cx + .8f, 3, -6);
        }

        // Motivators: half a block high, linked along the slice and across to the next slice
        int mask = f.motivatorMask(), count = f.motivators();
        int nextMask = !last && level.getBlockEntity(f.getBlockPos().relative(row)) instanceof CoinFactoryBlockEntity next ? next.motivatorMask() : 0;
        float rate = 1.5f + 2.2f * count;
        for (int cell = 0; cell < CoinFactoryBlockEntity.MOTIVATOR_CELLS; cell++) {
            if ((mask & (1 << cell)) == 0) {
                continue;
            }
            float z = cell * 16, y = 32, p = on ? 0.35f + 0.65f * (0.5f + 0.5f * Mth.sin(t * rate - z * .12f)) : 0.2f;
            b(Mat.HULL2, 3, y, z + 3, 13, y + 1.4f, z + 13);
            b(Mat.HULL, 6, y + 1.4f, z + 6, 10, y + 6, z + 10);
            for (float[] c : new float[][]{{3.4f, 3.4f}, {11.6f, 3.4f}, {3.4f, 11.6f}, {11.6f, 11.6f}}) {
                b(Mat.TRIM, c[0], y + 1.4f, z + c[1], c[0] + 1, y + 1.9f, z + c[1] + 1);
            }
            for (float h : new float[]{2.2f, 4.2f}) {
                b(Mat.TRIM, 4, y + h, z + 4, 12, y + h + .6f, z + 12);
                gl(VIO, p, 3.6f, y + h + .6f, z + 3.6f, 12.4f, y + h + 1, z + 12.4f);
            }
            gl(VIO, p, 6.5f, y + 6, z + 6.5f, 9.5f, y + 8, z + 9.5f);
            if ((mask & (1 << (cell + 1))) != 0) {
                b(Mat.TRIM, 6.4f, y, z + 13, 9.6f, y + 1.2f, z + 19);
                gl(VIO, p, 7.2f, y + 1.2f, z + 13, 8.8f, y + 1.5f, z + 19);
                gl(VIO, p, 7.6f, y + 6.7f, z + 9.5f, 8.4f, y + 7.3f, z + 22.5f);
            }
            if ((nextMask & (1 << cell)) != 0) {
                b(Mat.TRIM, 13, y, z + 6.4f, 19, y + 1.2f, z + 9.6f);
                gl(VIO, p, 13, y + 1.2f, z + 7.2f, 19, y + 1.5f, z + 8.8f);
                gl(VIO, p, 9.5f, y + 6.7f, z + 7.6f, 22.5f, y + 7.3f, z + 8.4f);
            }
        }

        for (float[] p : panes) {
            k.translucent(buffers, p[0], p[1], p[2], p[3], p[4], p[5], GLASS, 0.18f);
        }
        panes.clear();
        pose.popPose();
    }

    /** An outside wall: frame, lit vents, bolts and two glass panes. */
    private void sidewall(float x0, float x1, float lamp) {
        b(Mat.HULL, x0, 3, 0, x1, 7, 40);
        b(Mat.HULL, x0, 27, 0, x1, 30, 40);
        for (float[] p : new float[][]{{0, 2}, {19, 21}, {38, 40}}) {
            b(Mat.HULL2, x0 - .2f, 3, p[0], x1 + .2f, 30, p[1]);
        }
        float out = x0 < 8 ? x0 - .25f : x1;
        for (float[] v : new float[][]{{4, 9}, {11, 16}, {23, 28}, {30, 35}}) {
            gl(SCREEN, 1, out, 4, v[0], out + .25f, 6, v[1]);
            for (float z = v[0] + .6f; z < v[1]; z += 1.2f) {
                gl(TQ, lamp, out - .02f, 4.4f, z, out + .27f, 5.6f, z + .4f);
            }
        }
        glass(x0 + .3f, 7, 2, x1 - .3f, 27, 19);
        glass(x0 + .3f, 7, 21, x1 - .3f, 27, 38);
        gl(TQ, lamp, out, 27.6f, 2, out + .25f, 28.2f, 38);
        for (float[] bolt : new float[][]{{1, 8}, {1, 26}, {20, 8}, {20, 26}, {39, 8}, {39, 26}}) {
            b(Mat.TRIM, out - .1f, bolt[1], bolt[0] - .4f, out + .35f, bolt[1] + .8f, bolt[0] + .4f);
        }
    }

    /** Build guide: each missing block at half size, gently pulsing, where it must be placed. */
    private void ghosts(CoinFactoryBlockEntity f, Level level, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        float scale = 0.42f + 0.06f * Mth.sin((level.getGameTime() + partialTick) * 0.15f);
        BlockPos origin = f.getBlockPos();
        var renderer = Minecraft.getInstance().getBlockRenderer();
        for (CoinFactoryBlockEntity.Part part : f.missingParts()) {
            if (!level.getBlockState(part.pos()).canBeReplaced()) {
                continue;
            }
            pose.pushPose();
            pose.translate(part.pos().getX() - origin.getX() + 0.5, part.pos().getY() - origin.getY() + 0.5, part.pos().getZ() - origin.getZ() + 0.5);
            pose.scale(scale, scale, scale);
            pose.translate(-0.5, -0.5, -0.5);
            renderer.renderSingleBlock(part.block().defaultBlockState(), pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY, RenderType.translucent());
            pose.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(CoinFactoryBlockEntity f) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
