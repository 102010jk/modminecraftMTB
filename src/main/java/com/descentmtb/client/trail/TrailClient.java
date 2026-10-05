package com.descentmtb.client.trail;

import com.descentmtb.client.ModKeyMappings;
import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.network.ShapeTunePayload;
import com.descentmtb.network.TrailUndoPayload;
import com.descentmtb.trail.BermBuilder;
import com.descentmtb.trail.CursorSettings;
import com.descentmtb.trail.DownhillBuilder;
import com.descentmtb.trail.JumpBuilder;
import com.descentmtb.trail.JumpProfiles;
import com.descentmtb.trail.ShapeMode;
import com.descentmtb.trail.ShapePresets;
import com.descentmtb.trail.ShapeToolItem;
import com.descentmtb.trail.TrailSignBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/**
 * Client side of the Trail Shaper: the radial menu key, the undo key (Ctrl+Z), Shift + wheel mode cycling (and the
 * settings of the berm, downhill and cursor modes), the right-clicks that open a screen instead of reaching the server
 * (Ctrl + right-click: block editor; jump builder: jump screen), the HUD and the sign editor hook.
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

    /**
     * Every client tick. Both keys are drained unconditionally: a press made while riding (Z accelerates, Ctrl+Z
     * undoes) or with a screen open must not stay queued and fire later; only a press made while the shaper is held
     * and no screen is open does anything.
     */
    public static void tick() {
        var mc = Minecraft.getInstance();
        boolean menu = false, undo = false;
        while (ModKeyMappings.TRAIL_MENU.consumeClick()) {
            menu = true;
        }
        while (ModKeyMappings.TRAIL_UNDO.consumeClick()) {
            undo = true;
        }
        if (mc.screen != null || !holdingShaper(mc)) {
            return;
        }
        if (menu) {
            mc.setScreen(new ShapeRadialScreen());
        }
        if (undo) {
            PacketDistributor.sendToServer(new TrailUndoPayload());
        }
    }

    /**
     * A right-click with the shaper that opens a screen, and is therefore not sent to the server: Ctrl + right-click on
     * a block the shaper can edit opens the block editor (in any mode); in the jump builder mode a right-click on a
     * block opens the jump screen for a jump starting there, Shift + right-click in the air opens it without a block.
     */
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        var mc = Minecraft.getInstance();
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND || mc.screen != null || !holdingShaper(mc)) {
            return;
        }
        var stack = mc.player.getMainHandItem();
        BlockHitResult block = mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK ? hit : null;
        if (Screen.hasControlDown()) {
            var editable = block == null ? null : ShapePresets.editable(mc.level, block.getBlockPos());
            if (editable != null) {
                event.setCanceled(true);
                event.setSwingHand(false);
                mc.setScreen(new BlockEditorScreen(block.getBlockPos(), editable));
            }
            return;
        }
        if (ShapeToolItem.mode(stack) != ShapeMode.JUMP_BUILD) {
            return;
        }
        if (block != null) {
            event.setCanceled(true);
            event.setSwingHand(false);
            mc.setScreen(new JumpProfileScreen(block.getBlockPos(), mc.player.getDirection(), JumpBuilder.read(stack)));
        } else if (mc.player.isShiftKeyDown()) {
            event.setCanceled(true);
            event.setSwingHand(false);
            mc.setScreen(new JumpProfileScreen(null, mc.player.getDirection(), JumpBuilder.read(stack)));
        }
    }

    /**
     * Shift + wheel cycles the modes of the current category. In the berm mode it sets the steepness of the bank
     * instead (up is steeper), and Ctrl + Shift + wheel the width of the track. In the downhill mode it chooses the
     * style of the line (flow, jumps, mixed) and Ctrl + Shift + wheel the width. With the cursor it chooses what a
     * click picks (by zone, corner, edge, whole block) and Ctrl + Shift + wheel the step (1/16, 1/8, 1/4).
     */
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        var mc = Minecraft.getInstance();
        if (mc.screen != null || !Screen.hasShiftDown() || !holdingShaper(mc) || event.getScrollDeltaY() == 0) {
            return;
        }
        event.setCanceled(true);
        var stack = mc.player.getMainHandItem();
        ShapeMode mode = ShapeToolItem.mode(stack);
        int step = event.getScrollDeltaY() > 0 ? 1 : -1;
        if (mode.kind == ShapeMode.Kind.BERM) {
            var settings = BermBuilder.Settings.read(stack);
            settings = Screen.hasControlDown() ? settings.wider(step) : settings.steeper(step);
            settings.store(stack);
            PacketDistributor.sendToServer(new ShapeTunePayload(mode, settings));
            return;
        }
        if (mode == ShapeMode.AUTO) {
            var settings = CursorSettings.read(stack);
            settings = Screen.hasControlDown() ? settings.stepping(step) : settings.picking(-step);
            settings.store(stack);
            PacketDistributor.sendToServer(new ShapeTunePayload(mode, settings));
            return;
        }
        if (mode.kind == ShapeMode.Kind.DOWNHILL) {
            var settings = DownhillBuilder.Settings.read(stack);
            settings = Screen.hasControlDown() ? settings.wider(step) : settings.styled(step);
            settings.store(stack);
            PacketDistributor.sendToServer(new ShapeTunePayload(mode, settings));
            return;
        }
        ShapeMode next = mode.cycled(-step);
        ShapeToolItem.mode(stack, next);
        PacketDistributor.sendToServer(new ShapeTunePayload(next));
    }

    /** Bottom-left box: icon, name, what the mode does, one hint line and, for the berm and downhill modes, their settings. */
    public static void hud(GuiGraphics g) {
        var mc = Minecraft.getInstance();
        if (mc.options.hideGui || !holdingShaper(mc)) {
            return;
        }
        var stack = mc.player.getMainHandItem();
        ShapeMode mode = ShapeToolItem.mode(stack);
        Component name = Component.translatable(mode.key());
        Component description = Component.translatable(mode.descriptionKey());
        Component hint = Component.translatable(mode.hintKey(), ModKeyMappings.TRAIL_MENU.getTranslatedKeyMessage());
        Component status = switch (mode.kind) {
            case BERM -> bermStatus(stack);
            case DOWNHILL -> downhillStatus(stack);
            case JUMP -> jumpStatus(stack);
            default -> mode == ShapeMode.AUTO ? cursorStatus(stack) : null;
        };
        int width = Math.max(mc.font.width(description), Math.max(mc.font.width(hint), mc.font.width(name) + 21));
        if (status != null) {
            width = Math.max(width, mc.font.width(status));
        }
        int lines = status == null ? 0 : 13;
        int x = 12, y = g.guiHeight() - 62 - lines;
        g.fill(x - 5, y - 5, x + width + 6, y + 46 + lines, HUD_BACKGROUND);
        g.fill(x - 5, y - 5, x - 3, y + 46 + lines, HUD_ACCENT);
        g.blit(mode == ShapeMode.AUTO ? cursorIcon(CursorSettings.read(stack)) : ShapeRadialScreen.icon(mode), x, y, 0, 0, 16, 16, 16, 16);
        g.drawString(mc.font, name, x + 21, y + 4, 0xffe7d7ad);
        g.drawString(mc.font, description, x, y + 20, 0xff91cbbb);
        if (status != null) {
            g.drawString(mc.font, status, x, y + 33, 0xfff5d087);
        }
        g.drawString(mc.font, hint, x, y + 33 + lines, 0xff9aa6a8);
    }

    /** "Bank: Steep (60°) • Width 4 m • Points 1/3" */
    private static Component bermStatus(net.minecraft.world.item.ItemStack stack) {
        var settings = BermBuilder.Settings.read(stack);
        return Component.translatable("descentmtb.berm.hud",
                Component.translatable(settings.steepness().key()), (int) settings.steepness().degrees,
                settings.width(), BermBuilder.points(stack).length, BermBuilder.POINTS);
    }

    /** "Style: Mixed • Width 3 m • Points 1/2" */
    private static Component downhillStatus(net.minecraft.world.item.ItemStack stack) {
        var settings = DownhillBuilder.Settings.read(stack);
        return Component.translatable("descentmtb.downhill.hud", Component.translatable(settings.style().key()),
                settings.width(), DownhillBuilder.points(stack).length, DownhillBuilder.POINTS);
    }

    /** "Picks: Corner • Step 1/8" */
    private static Component cursorStatus(net.minecraft.world.item.ItemStack stack) {
        var settings = CursorSettings.read(stack);
        return Component.translatable("descentmtb.cursor.hud", Component.translatable(settings.pick().key()), settings.step().label);
    }

    /** "Kicker • 3 m long • 1.5 m high • lip 35°" */
    private static Component jumpStatus(net.minecraft.world.item.ItemStack stack) {
        JumpProfiles.Params p = JumpBuilder.read(stack);
        return JumpBuilder.describe(p, "descentmtb.jump.hud");
    }

    /** The icon of a cursor sub-type, {@code textures/gui/shape/cursor_<sub-type>.png}. */
    public static ResourceLocation cursorIcon(CursorSettings settings) {
        return ResourceLocation.fromNamespaceAndPath("descentmtb",
                "textures/gui/shape/cursor_" + settings.pick().name().toLowerCase(Locale.ROOT) + ".png");
    }

    private TrailClient() {}
}
