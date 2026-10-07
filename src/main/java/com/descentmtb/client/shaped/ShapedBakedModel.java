package com.descentmtb.client.shaped;

import com.descentmtb.ramp.RampBlockEntity;
import com.descentmtb.ramp.ShapeKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Model of ramps and trail surfaces: the geometry is generated from the block's shape (see {@link ShapedQuads})
 * with the sprites of its copycat material, and cached per shape. Because it is a normal model, the blocks are
 * meshed once into the chunk instead of being re-drawn block by block every frame.
 *
 * <p>The quads are handed out for one render type only, the material's own (the first of its chunk render types),
 * so a material with several layers is not meshed twice. The shape cache is a least-recently-used map shared by the
 * chunk-meshing threads; when it is full the shape used longest ago goes, not the whole cache.
 */
final class ShapedBakedModel implements IDynamicBakedModel {
    private static final int MAX_CACHED_SHAPES = 8192;
    private static BlockState defaultMaterial() { return com.descentmtb.registry.ModBlocks.defaultTrailDirt(); }
    private static final ModelProperty<Integer> COVERED_FACES = new ModelProperty<>();

    private record CacheKey(BlockState state, ShapeKey shape) {}

    private final TextureAtlasSprite particle;
    private final Map<BlockState, ShapedQuads.Faces> faceCache = new ConcurrentHashMap<>();
    /** The render type each material's quads go to. */
    private final Map<BlockState, RenderType> layerCache = new ConcurrentHashMap<>();
    private final Map<CacheKey, ShapedQuads.Built> quadCache = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<CacheKey, ShapedQuads.Built> eldest) {
                    return size() > MAX_CACHED_SHAPES;
                }
            });

    ShapedBakedModel(TextureAtlasSprite particle) {
        this.particle = particle;
    }

    private static ShapeKey shapeOf(ModelData data) {
        ShapeKey key = data.get(RampBlockEntity.SHAPE);
        return key != null ? key : ShapeKey.ramp(defaultMaterial());
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        ShapeKey own = shapeOf(data);
        int mask = 0;
        for (Direction face : Direction.values()) {
            BlockPos next = pos.relative(face);
            BlockState neighbor = level.getBlockState(next);
            if (!(neighbor.getBlock() instanceof com.descentmtb.ramp.RampBlock)) continue;
            if (!(level.getBlockEntity(next) instanceof RampBlockEntity be)) continue;
            ShapeKey shape = be.getModelData().get(RampBlockEntity.SHAPE);
            if (shape != null && shape.material().canOcclude()
                    && ShapedOcclusion.covered(state, own, neighbor, shape, face)) mask |= 1 << face.ordinal();
        }
        return data.derive().with(COVERED_FACES, mask).build();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData data, @Nullable RenderType renderType) {
        if (state == null) {
            return List.of();
        }
        Integer covered = data.get(COVERED_FACES);
        if (side != null && covered != null && (covered & 1 << side.ordinal()) != 0) return List.of();
        ShapeKey shape = shapeOf(data);
        if (renderType != null && renderType != layer(shape.material())) {
            return List.of();
        }
        if (!(state.getBlock() instanceof com.descentmtb.trail.TrailSurfaceBlock)) {
            shape = ShapeKey.ramp(shape.material());   // ramps depend only on state + material: share cache entries
        }
        CacheKey key = new CacheKey(state, shape);
        ShapedQuads.Built built = quadCache.get(key);
        if (built == null) {
            // built outside the lock: two threads may build the same shape at once, which is harmless
            built = ShapedQuads.build(state, shape, faces(shape.material()), overlayFaces(shape.overlay()));
            quadCache.put(key, built);
        }
        return built.forSide(side);
    }

    /** Faces of the overlay material (1 = roots, drawn with oak log; 2 = rocks, drawn with stone), or null for none. */
    @Nullable
    private ShapedQuads.Faces overlayFaces(int overlay) {
        if (overlay == 0) {
            return null;
        }
        return faces(overlay == 1 ? Blocks.OAK_LOG.defaultBlockState() : Blocks.STONE.defaultBlockState());
    }

    /** The one render type the material's quads are meshed in. */
    private RenderType layer(BlockState material) {
        return layerCache.computeIfAbsent(material, m -> {
            ChunkRenderTypeSet layers = ItemBlockRenderTypes.getRenderLayers(m);
            return layers.isEmpty() ? RenderType.solid() : layers.iterator().next();
        });
    }

    private ShapedQuads.Faces faces(BlockState material) {
        return faceCache.computeIfAbsent(material, m -> {
            BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(m);
            RandomSource random = RandomSource.create(42L);
            TextureAtlasSprite[] sprites = new TextureAtlasSprite[Direction.values().length];
            int[] tints = new int[sprites.length];
            for (Direction d : Direction.values()) {
                List<BakedQuad> quads = model.getQuads(m, d, random, ModelData.EMPTY, null);
                if (quads.isEmpty()) {
                    sprites[d.ordinal()] = model.getParticleIcon(ModelData.EMPTY);
                    tints[d.ordinal()] = -1;
                } else {
                    BakedQuad first = quads.get(0);
                    sprites[d.ordinal()] = first.getSprite();
                    tints[d.ordinal()] = first.isTinted() ? first.getTintIndex() : -1;
                }
            }
            return new ShapedQuads.Faces(sprites, tints);
        });
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return ChunkRenderTypeSet.of(layer(shapeOf(data).material()));
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        BlockState material = shapeOf(data).material();
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(material).getParticleIcon(ModelData.EMPTY);
    }

    @Override public TextureAtlasSprite getParticleIcon() { return particle; }
    @Override public boolean useAmbientOcclusion() { return true; }
    @Override public boolean isGui3d() { return false; }
    @Override public boolean usesBlockLight() { return true; }
    @Override public boolean isCustomRenderer() { return false; }
    @Override public ItemTransforms getTransforms() { return ItemTransforms.NO_TRANSFORMS; }
    @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
}
