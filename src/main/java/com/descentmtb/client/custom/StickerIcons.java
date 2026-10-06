package com.descentmtb.client.custom;

import com.descentmtb.DescentMtb;
import com.descentmtb.custom.BikeParts.StickerDesign;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** The sticker art (textures/sticker/&lt;design&gt;.png) as icons for the workshop palette; a letter stands in for art that is not there yet. */
final class StickerIcons {
    private record Icon(ResourceLocation texture, int width, int height) {}

    private static final Icon MISSING = new Icon(null, 0, 0);
    private static final Map<StickerDesign, Icon> CACHE = new EnumMap<>(StickerDesign.class);

    static void clear() {
        CACHE.clear();
    }

    /** Shared with world decals so rectangular logos and square art keep their proportions. */
    static float aspectRatio(StickerDesign design) {
        Icon icon=get(design);
        return icon.height>0 ? icon.width/(float)icon.height : 1f;
    }

    private static Icon get(StickerDesign design) {
        return CACHE.computeIfAbsent(design, d -> {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, d.texture());
            try {
                Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
                if (res.isPresent()) {
                    try (InputStream in = res.get().open(); NativeImage image = NativeImage.read(in)) {
                        return new Icon(id, image.getWidth(), image.getHeight());
                    }
                }
            } catch (Exception | LinkageError ignored) {
                // unreadable art: fall back to the letter
            }
            return MISSING;
        });
    }

    /** Draws the design into a {@code size} square cell at (x, y), keeping the proportions of the art. */
    static void paint(GuiGraphics g, StickerDesign design, int x, int y, int size) {
        Icon icon = get(design);
        if (icon == MISSING || icon.width <= 0 || icon.height <= 0) {
            String letter = design.name().substring(0, 1);
            var font = Minecraft.getInstance().font;
            g.drawString(font, letter, x + (size - font.width(letter)) / 2, y + (size - 8) / 2, WorkshopPanel.LABEL, false);
            return;
        }
        int box = size - 4;
        int dw, dh;
        if (icon.width >= icon.height) {
            dw = box;
            dh = Math.max(1, box * icon.height / icon.width);
        } else {
            dh = box;
            dw = Math.max(1, box * icon.width / icon.height);
        }
        g.blit(icon.texture, x + (size - dw) / 2, y + (size - dh) / 2, dw, dh, 0f, 0f, icon.width, icon.height,
                icon.width, icon.height);
    }

    private StickerIcons() {}
}
