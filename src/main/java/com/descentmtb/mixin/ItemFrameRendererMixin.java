package com.descentmtb.mixin;

import com.descentmtb.map.TrailMapItem;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A trail map also needs the full-sized map backboard, rather than the small item-frame model. */
@Mixin(ItemFrameRenderer.class)
public abstract class ItemFrameRendererMixin {
    @Inject(method="getFrameModelResourceLoc",at=@At("HEAD"),cancellable=true)
    private void descentmtb$mapFrame(ItemFrame frame, ItemStack stack, CallbackInfoReturnable<ModelResourceLocation> result) {
        if (stack.getItem() instanceof TrailMapItem) {
            result.setReturnValue(ModelResourceLocation.vanilla(
                    frame.getType()==EntityType.GLOW_ITEM_FRAME?"glow_item_frame":"item_frame","map=true"));
        }
    }
}
