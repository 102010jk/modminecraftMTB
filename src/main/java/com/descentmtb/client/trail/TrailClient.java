package com.descentmtb.client.trail;

import com.descentmtb.client.ModKeyMappings;
import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.network.ShapeTunePayload;
import com.descentmtb.network.TrailUndoPayload;
import com.descentmtb.trail.ShapeMode;
import com.descentmtb.trail.ShapeToolItem;
import com.descentmtb.trail.TrailSignBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client side of the Trail Shaper: the radial menu key, the undo key, Shift + wheel mode cycling, the HUD and
 * the sign editor hook.
 */
public final class TrailClient {
    private static final int HUD_BACKGROUND = 0xbe18252b, HUD_ACCENT = 0xffdfb65e;

    public static void setup() {
        TrailSignBlock.editor = be -> Minecraft.getInstance().setScreen(new SignEditorScreen(be));
    }

    private static boolean holdingShaper(Minecraft mc) {
        return mc.player != null && ShapeToolItem.usable(mc.player.getMainHandItem())
                && !(mc.player.getVehicle() instanceof MountainBikeEntity);
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.screen != null || !holdingShaper(mc)) {
            return;
        }
        if (ModKeyMappings.TRAIL_MENU.consumeClick()) {
            mc.setScreen(new ShapeRadialScreen());
        }
        if (ModKeyMappings.TRAIL_UNDO.consumeClick()) {
            PacketDistributor.sendToServer(new TrailUndoPayload());
        }
    }

    /** Shift + wheel cycles the modes of the current category. */
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        var mc = Minecraft.getInstance();
        if (mc.screen != null || !Screen.hasShiftDown() || !holdingShaper(mc) || event.getScrollDeltaY() == 0) {
            return;
        }
        event.setCanceled(true);
        var stack = mc.player.getMainHandItem();
        ShapeMode next = ShapeToolItem.mode(stack).cycled(event.getScrollDeltaY() > 0 ? -1 : 1);
        ShapeToolItem.mode(stack, next);
        PacketDistributor.sendToServer(new ShapeTunePayload(next));
    }

    /** Bottom-left box: icon, name, what the mode does and one hint line. */
    public static void hud(GuiGraphics g) {
        var mc = Minecraft.getInstance();
        if (mc.options.hideGui || !holdingShaper(mc)) {
            return;
        }
        ShapeMode mode = ShapeToolItem.mode(mc.player.getMainHandItem());
        Component name = Component.translatable(mode.key());
        Component description = Component.translatable(mode.descriptionKey());
        Component hint = Component.translatable("descentmtb.shape.hud.hint." + mode.category,
                ModKeyMappings.TRAIL_MENU.getTranslatedKeyMessage());
        int width = Math.max(mc.font.width(description), Math.max(mc.font.width(hint), mc.font.width(name) + 21));
        int x = 12, y = g.guiHeight() - 62;
        g.fill(x - 5, y - 5, x + width + 6, y + 46, HUD_BACKGROUND);
        g.fill(x - 5, y - 5, x - 3, y + 46, HUD_ACCENT);
        g.blit(ShapeRadialScreen.icon(mode), x, y, 0, 0, 16, 16, 16, 16);
        g.drawString(mc.font, name, x + 21, y + 4, 0xffe7d7ad);
        g.drawString(mc.font, description, x, y + 20, 0xff91cbbb);
        g.drawString(mc.font, hint, x, y + 33, 0xff9aa6a8);
    }

    private TrailClient() {}
}
