package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.shield.ShieldJoins;
import com.mrgregles.bsp_core.shield.ShieldingWindowBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The joined look of Radiation Shielding Panels and Windows, wrapped round their block models. Each face that joins (all six of a
 * panel; the front and back of a window pane) is cut into four quarters, and each quarter takes the same quarter of one of five
 * textures ({@link ShieldJoins.Piece}: full, top and bottom edges, side edges, inside corners, plain), chosen from the face's
 * {@link ShieldJoins#mask}. So a wall of panels shows one riveted border round the whole group, and joined windows one frame round one
 * sheet of glass. The masks are worked out where the block is drawn ({@link #getModelData}); the quads are made once per face and mask
 * and kept. The window's four lead edges are its block model's, dropped between joined windows by {@link ShieldingWindowBlock}.
 * Items and blocks with no world around them show every edge. A coated block ({@link com.mrgregles.bsp_core.shield.TotemCoating})
 * takes the same five pieces with the turquoise glaze ({@code <texture>_coated<suffix>}).
 */
public class ShieldJoinedModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Long> MASKS = new ModelProperty<>();
    private static final String[] SUFFIX = {"", "_h", "_v", "_inner", "_plain"}; // in ShieldJoins.Piece order
    private static final FaceBakery BAKERY = new FaceBakery();

    private final String texture;
    private final boolean window;
    private final Map<Integer, List<BakedQuad>> cache = new ConcurrentHashMap<>();
    @Nullable
    private volatile TextureAtlasSprite[] sprites, coatedSprites;

    /** {@code texture} is the base name, such as {@code radiation_shielding_panel}; the five pieces are it and its suffixes. */
    public ShieldJoinedModel(BakedModel original, String texture, boolean window) {
        super(original);
        this.texture = texture;
        this.window = window;
    }

    private TextureAtlasSprite[] sprites(boolean coated) {
        TextureAtlasSprite[] s = coated ? coatedSprites : sprites;
        if (s == null) {
            var atlas = Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
            s = new TextureAtlasSprite[SUFFIX.length];
            for (int i = 0; i < SUFFIX.length; i++) {
                s[i] = atlas.getSprite(new ResourceLocation(BSPCore.MODID, "block/" + texture + (coated ? "_coated" : "") + SUFFIX[i]));
            }
            if (coated) {
                coatedSprites = s;
            } else {
                sprites = s;
            }
        }
        return s;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        long masks = 0;
        for (Direction d : Direction.values()) {
            masks |= (long) ShieldJoins.mask(level, pos, state, d) << (d.ordinal() * 8);
        }
        return data.derive().with(MASKS, masks).build();
    }

    private static int mask(ModelData data, Direction face) {
        Long masks = data.get(MASKS);
        return masks == null ? ShieldJoins.ALONE : (int) ((masks >> (face.ordinal() * 8)) & 0xFF);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data, @Nullable RenderType type) {
        boolean coated = state != null && com.mrgregles.bsp_core.shield.TotemCoating.coated(state);
        if (!window) {
            return side == null ? List.of() : faces(side, mask(data, side), Direction.Axis.X, coated); // a panel's faces all lie on the block's sides
        }
        Direction.Axis runs = state != null && state.hasProperty(ShieldingWindowBlock.AXIS) ? state.getValue(ShieldingWindowBlock.AXIS) : Direction.Axis.X;
        Direction.Axis thin = runs == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        if (side != null) {
            return originalModel.getQuads(state, side, rand, data, type); // the lead edges on the pane's four sides
        }
        List<BakedQuad> out = new ArrayList<>();
        for (Direction d : new Direction[] {Direction.get(Direction.AxisDirection.NEGATIVE, thin), Direction.get(Direction.AxisDirection.POSITIVE, thin)}) {
            out.addAll(faces(d, mask(data, d), runs, coated));
        }
        return out;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        return getQuads(state, side, rand, ModelData.EMPTY, null);
    }

    /** The four quarters of face {@code face} for {@code mask}; for a window, {@code runs} places the pane (6 pixels deep, 5 to 11). */
    private List<BakedQuad> faces(Direction face, int mask, Direction.Axis runs, boolean coated) {
        int key = ((coated ? 1 : 0) << 12) | (face.ordinal() << 9) | ((runs == Direction.Axis.Z ? 1 : 0) << 8) | mask;
        return cache.computeIfAbsent(key, k -> {
            TextureAtlasSprite[] s = sprites(coated);
            float[] lo = {0, 0, 0}, hi = {16, 16, 16};
            if (window) {
                int a = runs == Direction.Axis.X ? 2 : 0; // the pane is thin along z when it runs along x
                lo[a] = 5;
                hi[a] = 11;
            }
            List<BakedQuad> quads = new ArrayList<>(4);
            for (int qv = 0; qv < 2; qv++) {
                for (int qu = 0; qu < 2; qu++) {
                    ShieldJoins.Piece piece = ShieldJoins.piece(mask, qv == 0, qu == 0);
                    float u0 = qu * 8, u1 = u0 + 8, v0 = qv * 8, v1 = v0 + 8;
                    float[] from = lo.clone(), to = hi.clone();
                    place(face, from, to, u0, u1, v0, v1);
                    BlockElementFace f = new BlockElementFace(window ? null : face, -1, "", new BlockFaceUV(new float[] {u0, v0, u1, v1}, 0));
                    quads.add(BAKERY.bakeQuad(new Vector3f(from[0], from[1], from[2]), new Vector3f(to[0], to[1], to[2]), f, s[piece.ordinal()], face,
                            BlockModelRotation.X0_Y0, null, true, new ResourceLocation(BSPCore.MODID, texture)));
                }
            }
            return List.copyOf(quads);
        });
    }

    /**
     * Sets the in-plane extent of the quarter of {@code face} that Minecraft's default uvs map to {@code u0..u1, v0..v1} (north: u runs
     * west and v down; south: u east; east: u north; west: u south; up: u east and v south; down: u east and v north). The face's own
     * depth stays as the element has it.
     */
    private static void place(Direction face, float[] from, float[] to, float u0, float u1, float v0, float v1) {
        switch (face) {
            case NORTH -> { from[0] = 16 - u1; to[0] = 16 - u0; from[1] = 16 - v1; to[1] = 16 - v0; }
            case SOUTH -> { from[0] = u0; to[0] = u1; from[1] = 16 - v1; to[1] = 16 - v0; }
            case EAST -> { from[2] = 16 - u1; to[2] = 16 - u0; from[1] = 16 - v1; to[1] = 16 - v0; }
            case WEST -> { from[2] = u0; to[2] = u1; from[1] = 16 - v1; to[1] = 16 - v0; }
            case UP -> { from[0] = u0; to[0] = u1; from[2] = v0; to[2] = v1; }
            case DOWN -> { from[0] = u0; to[0] = u1; from[2] = 16 - v1; to[2] = 16 - v0; }
        }
    }
}
