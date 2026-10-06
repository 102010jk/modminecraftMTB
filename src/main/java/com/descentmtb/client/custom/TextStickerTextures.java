package com.descentmtb.client.custom;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/** Text uses exactly the same metre-sized surface quad as PNG decals, rather than a world name tag. */
final class TextStickerTextures {
    private static final Map<String,StickerAssets.Asset> CACHE=new LinkedHashMap<>(16,.75f,true);
    static StickerAssets.Asset get(String text) {
        var cached=CACHE.get(text);if(cached!=null) return cached;
        Font font=new Font(Font.MONOSPACED,Font.BOLD,10);
        var measure=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);var graphics=measure.createGraphics();
        graphics.setFont(font);var metrics=graphics.getFontMetrics();
        int width=Math.max(4,metrics.stringWidth(text)+2),height=metrics.getHeight()+2;
        graphics.dispose();
        var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);graphics=image.createGraphics();
        graphics.setFont(font);graphics.setColor(Color.WHITE);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        graphics.drawString(text,1,metrics.getAscent()+1);graphics.dispose();
        var pixels=new NativeImage(width,height,false);
        for(int y=0;y<height;y++) for(int x=0;x<width;x++) {
            int argb=image.getRGB(x,y);
            pixels.setPixelRGBA(x,y,(argb&0xFF00FF00)|((argb>>16)&255)|((argb&255)<<16));
        }
        var manager=Minecraft.getInstance().getTextureManager();
        var id=manager.register("descentmtb_text_decal",new DynamicTexture(pixels));
        var asset=new StickerAssets.Asset(id,width,height,text,false);
        CACHE.put(text,asset);
        if(CACHE.size()>128) {
            var oldest=CACHE.entrySet().iterator();var entry=oldest.next();manager.release(entry.getValue().id());oldest.remove();
        }
        return asset;
    }
    static void clear() {
        var manager=Minecraft.getInstance().getTextureManager();
        for(var asset:CACHE.values()) manager.release(asset.id());CACHE.clear();
    }
    private TextStickerTextures() {}
}
