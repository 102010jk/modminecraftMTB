package com.descentmtb.client.shaped;

import com.descentmtb.DescentMtb;
import com.descentmtb.ramp.RampBlockEntity;
import com.descentmtb.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

import java.util.function.Function;

/** Model loader {@code descentmtb:shaped} for ramps and trail surfaces (see {@link ShapedBakedModel}). */
public final class ShapedGeometry implements IUnbakedGeometry<ShapedGeometry> {
    private static final IGeometryLoader<ShapedGeometry> LOADER = (json, context) -> new ShapedGeometry();

    public static void register(ModelEvent.RegisterGeometryLoaders event) {
        event.register(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "shaped"), LOADER);
    }

    /** Grass and other tinted materials keep their biome colour. */
    public static void registerColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> {
            if (level == null || pos == null || !(level.getBlockEntity(pos) instanceof RampBlockEntity be)) {
                return -1;
            }
            return Minecraft.getInstance().getBlockColors().getColor(be.getMaterial(), level, pos, tintIndex);
        }, ModBlocks.RAMP.get(), ModBlocks.TRAIL_SURFACE.get());
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter,
                           ModelState modelState, ItemOverrides overrides) {
        return new ShapedBakedModel(spriteGetter.apply(context.getMaterial("particle")));
    }
}
