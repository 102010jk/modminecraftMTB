package com.descentmtb.client.custom;

import com.descentmtb.custom.BikeBuild.Sticker;
import com.descentmtb.custom.BikeParts.StickerDesign;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import java.util.*;

/** Runtime PNG discovery from the mod and all enabled resource packs; IDs remain stable in saved builds. */
@EventBusSubscriber(modid="descentmtb",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class StickerAssets {
    public record Asset(ResourceLocation id,int width,int height,String label,boolean missing) {}
    private static final ResourceLocation MISSING_ID=ResourceLocation.fromNamespaceAndPath("descentmtb","textures/sticker/missing.png");
    private static final Asset MISSING=new Asset(MISSING_ID,16,16,"Missing PNG",true);
    private static volatile Map<ResourceLocation,Asset> assets=Map.of();
    static volatile int revision;
    @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener)StickerAssets::reload);
    }
    private static void reload(ResourceManager manager) {
        Map<ResourceLocation,Asset> found=new TreeMap<>();
        for(String prefix:List.of("textures/sticker","textures/stickers"))
        manager.listResources(prefix,id->id.getPath().endsWith(".png") && !id.equals(MISSING_ID)).forEach((id,resource)->{
            if(!id.getPath().startsWith("textures/sticker/") && !id.getPath().startsWith("textures/stickers/")) return;
            try(var in=resource.open();var image=NativeImage.read(in)) {
                if(image.getWidth()>1024 || image.getHeight()>1024) return;
                String path=id.getPath(),file=path.substring(path.lastIndexOf('/')+1,path.length()-4);
                found.put(id,new Asset(id,image.getWidth(),image.getHeight(),file.replace('_',' '),false));
            } catch(Exception e) { com.descentmtb.DescentMtb.LOG.warn("Skipping unreadable sticker {}",id); }
        });
        assets=Map.copyOf(found);revision++;StickerIcons.clear();
        net.minecraft.client.Minecraft.getInstance().execute(TextStickerTextures::clear);
    }
    public static Asset of(Sticker sticker) {
        if(sticker.isText()) return TextStickerTextures.get(sticker.text());
        ResourceLocation id=sticker.texture().isBlank() ? ResourceLocation.fromNamespaceAndPath("descentmtb",sticker.design().texture())
                : ResourceLocation.tryParse(sticker.texture());
        return assets.getOrDefault(id,MISSING);
    }
    static List<Asset> extra() {
        Set<ResourceLocation> builtin=new HashSet<>();
        for(var d:StickerDesign.values()) builtin.add(ResourceLocation.fromNamespaceAndPath("descentmtb",d.texture()));
        return assets.values().stream().filter(a->!builtin.contains(a.id)).sorted(Comparator.comparing(a->a.id.toString())).toList();
    }
    static void paint(GuiGraphics g,Asset asset,int x,int y,int size) {
        int box=size-4;
        float scale=box/(float)Math.max(asset.width,asset.height);
        int w=Math.max(1,Math.round(asset.width*scale)),h=Math.max(1,Math.round(asset.height*scale));
        g.blit(asset.id,x+(size-w)/2,y+(size-h)/2,w,h,0,0,asset.width,asset.height,asset.width,asset.height);
    }
    private StickerAssets() {}
}
