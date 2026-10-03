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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Model of ramps and trail surfaces: the geometry is generated from the block's shape (see {@link ShapedQuads})
 * with the sprites of its copycat material, and cached per shape. Because it is a normal model, the blocks are
 * meshed once into the chunk instead of being re-drawn block by block every frame.
 */
final class ShapedBakedModel implements IDynamicBakedModel {
    private static final int MAX_CACHED_SHAPES = 8192;
    private static final BlockState DEFAULT_MATERIAL = Blocks.COARSE_DIRT.defaultBlockState();

    private record CacheKey(BlockState state, ShapeKey shape) {}

    private final TextureAtlasSprite particle;
    private final Map<BlockState, ShapedQuads.Faces> faceCache = new ConcurrentHashMap<>();
    private final Map<CacheKey, List<BakedQuad>> quadCache = new ConcurrentHashMap<>();

    ShapedBakedModel(TextureAtlasSprite particle) {
        this.particle = particle;
    }

    private static ShapeKey shapeOf(ModelData data) {
        ShapeKey key = data.get(RampBlockEntity.SHAPE);
        return key != null ? key : ShapeKey.ramp(DEFAULT_MATERIAL);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData data, @Nullable RenderType renderType) {
        if (state == null || side != null) {
            return List.of();   // everything is returned as unculled quads
        }
        ShapeKey shape = shapeOf(data);
        if (!(state.getBlock() instanceof com.descentmtb.trail.TrailSurfaceBlock)) {
            shape = ShapeKey.ramp(shape.material());   // ramps depend only on state + material: share cache entries
        }
        CacheKey key = new CacheKey(state, shape);
        List<BakedQuad> cached = quadCache.get(key);
        if (cached == null) {
            if (quadCache.size() > MAX_CACHED_SHAPES) {
                quadCache.clear();
            }
            cached = ShapedQuads.build(state, shape, faces(shape.material()));
            quadCache.put(key, cached);
        }
        return cached;
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
        return ItemBlockRenderTypes.getRenderLayers(shapeOf(data).material());
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
