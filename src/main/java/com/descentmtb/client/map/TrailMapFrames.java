package com.descentmtb.client.map;

import com.descentmtb.map.TrailMapItem;
import com.descentmtb.map.WallLayout;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderItemInFrameEvent;

/**
 * Uses the same full-block pixel coordinates, depth and quarter-turns as a vanilla framed map. A framed map shows only the
 * routes saved on it; item frames side by side that all hold a trail map form one big map ({@link WallMaps}).
 */
@EventBusSubscriber(modid="descentmtb",value=Dist.CLIENT)
public final class TrailMapFrames {
    private TrailMapFrames() {}

    /** A map covering several dimensions shows, on the wall, the trails of the dimension it hangs in. */
    static com.descentmtb.map.BikeparkMap shownHere(com.descentmtb.map.BikeparkMap data,net.minecraft.resources.ResourceLocation dimension) {
        var here=data.routes().stream().filter(r->r.dimension().equals(dimension)).toList();
        return here.isEmpty()||here.size()==data.routes().size()?data:new com.descentmtb.map.BikeparkMap(here);
    }

    @SubscribeEvent public static void render(RenderItemInFrameEvent event) {
        if (!(event.getItemStack().getItem() instanceof TrailMapItem)) return;
        var frame=event.getItemFrameEntity();
        var pose=event.getPoseStack();
        pose.pushPose();
        // Vanilla has already applied generic item rotation; replace it with map rotation.
        int rotation=frame.getRotation();
        pose.mulPose(Axis.ZP.rotationDegrees((rotation % 4)*90f-rotation*45f+180f));
        pose.scale(1/128f,1/128f,1/128f);
        pose.translate(-64,-64,-1);
        int light=frame.getType()==EntityType.GLOW_ITEM_FRAME?15728850:event.getPackedLight();
        var wall=WallMaps.of(frame,TrailMapItem.data(event.getItemStack()));
        var data=shownHere(wall.data(),frame.level().dimension().location());
        // one frame: the usual 128 px map; a wall: one picture of 128 px per frame, this frame draws its tile of it
        var texture=wall.single()?MapTexture.get(data)
                :MapTexture.get(data,WallLayout.textureSide(wall.across()),WallLayout.textureSide(wall.down()),WallLayout.PIXELS_PER_FRAME);
        float u0=wall.column()/(float)wall.across(),u1=(wall.column()+1)/(float)wall.across(),
              v0=wall.row()/(float)wall.down(),v1=(wall.row()+1)/(float)wall.down();
        var vertices=event.getMultiBufferSource().getBuffer(RenderType.text(texture));
        var matrix=pose.last().pose();
        vertices.addVertex(matrix,0,128,-.01f).setColor(-1).setUv(u0,v1).setLight(light);
        vertices.addVertex(matrix,128,128,-.01f).setColor(-1).setUv(u1,v1).setLight(light);
        vertices.addVertex(matrix,128,0,-.01f).setColor(-1).setUv(u1,v0).setLight(light);
        vertices.addVertex(matrix,0,0,-.01f).setColor(-1).setUv(u0,v0).setLight(light);
        pose.popPose();
        event.setCanceled(true);
    }
}
