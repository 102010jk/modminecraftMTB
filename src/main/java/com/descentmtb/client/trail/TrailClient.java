package com.descentmtb.client.trail;

import com.descentmtb.client.ClientConfig;
import com.descentmtb.client.ui.UiTheme;

import com.descentmtb.client.ModKeyMappings;
import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.network.ShapeTunePayload;
import com.descentmtb.network.TrailUndoPayload;
import com.descentmtb.trail.BermBuilder;
import com.descentmtb.trail.CursorSettings;
import com.descentmtb.trail.DownhillBuilder;
import com.descentmtb.trail.JumpBuilder;
import com.descentmtb.trail.ClearPathBuilder;
import com.descentmtb.trail.JumpProfiles;
import com.descentmtb.trail.LinePoints;
import com.descentmtb.trail.LineSettings;
import com.descentmtb.trail.StraightLines;
import com.descentmtb.trail.TrailEdit;
import com.descentmtb.trail.ShapeMode;
import com.descentmtb.trail.ShapePresets;
import com.descentmtb.trail.ShapeToolItem;
import com.descentmtb.trail.TrailSignBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;
import java.util.List;

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
     * style of the line (flow, jumps, mixed), Ctrl + Shift + wheel the width and Alt + wheel the grade (gentle, medium,
     * steep, wild). In the line tools (clear path, straight line) Ctrl + Shift + wheel chooses the width. With the cursor it chooses what a
     * click picks (by zone, corner, edge, whole block) and Ctrl + Shift + wheel the step (1/16, 1/8, 1/4).
     */
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        var mc = Minecraft.getInstance();
        boolean alt = Screen.hasAltDown() && !Screen.hasShiftDown();
        if (mc.screen != null || !(Screen.hasShiftDown() || alt) || !holdingShaper(mc) || event.getScrollDeltaY() == 0) {
            return;
        }
        var stack = mc.player.getMainHandItem();
        ShapeMode mode = ShapeToolItem.mode(stack);
        int step = event.getScrollDeltaY() > 0 ? 1 : -1;
        if (alt) {   // Alt + wheel: the grade of the downhill line, nothing else uses it
            if (mode.kind == ShapeMode.Kind.DOWNHILL) {
                event.setCanceled(true);
                var settings = DownhillBuilder.Settings.read(stack).graded(step);
                settings.store(stack);
                PacketDistributor.sendToServer(new ShapeTunePayload(mode, settings));
            }
            return;
        }
        event.setCanceled(true);
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
        if ((mode.kind == ShapeMode.Kind.CLEAR || mode.kind == ShapeMode.Kind.LINE) && Screen.hasControlDown()) {
            var settings = LineSettings.read(stack).wider(step);
            settings.store(stack);
            PacketDistributor.sendToServer(new ShapeTunePayload(mode, settings));
            return;
        }
        ShapeMode next = mode.cycled(-step);
        ShapeToolItem.mode(stack, next);
        PacketDistributor.sendToServer(new ShapeTunePayload(next));
    }

    /** Compact card above the hotbar; Shift reveals the description and full instructions. */
    public static void hud(GuiGraphics g) {
        var mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null || !holdingShaper(mc)
                || ClientConfig.SPEC.isLoaded() && !ClientConfig.SHOW_TRAIL_HINTS.get()) return;
        var stack = mc.player.getMainHandItem();
        ShapeMode mode = ShapeToolItem.mode(stack);
        Component name = Component.translatable(mode.key());
        boolean expanded = Screen.hasShiftDown() || ClientConfig.SPEC.isLoaded() && !ClientConfig.COMPACT_TRAIL_HINTS.get();
        Component hint = Component.translatable(expanded ? mode.hintKey() : "descentmtb.shape.hud.compact",
                ModKeyMappings.TRAIL_MENU.getTranslatedKeyMessage());
        Component status = switch (mode.kind) {
            case BERM -> bermStatus(stack);
            case DOWNHILL -> downhillStatus(stack);
            case JUMP -> jumpStatus(stack);
            case CLEAR, LINE -> lineStatus(mc, stack, mode);
            default -> mode == ShapeMode.AUTO ? cursorStatus(stack) : null;
        };
        int maxWidth = Math.min(300, g.guiWidth() - 32);
        var hintLines = mc.font.split(hint, maxWidth);
        List<FormattedCharSequence> description = expanded
                ? mc.font.split(Component.translatable(mode.descriptionKey()), maxWidth) : List.of();
        int contentWidth = Math.min(maxWidth, mc.font.width(name) + 21);
        for (var line : hintLines) contentWidth = Math.max(contentWidth, mc.font.width(line));
        for (var line : description) contentWidth = Math.max(contentWidth, mc.font.width(line));
        if (status != null) contentWidth = Math.min(maxWidth, Math.max(contentWidth, mc.font.width(status)));
        int descriptionRows = Math.min(2, description.size()), hintRows = Math.min(5, hintLines.size());
        int boxHeight = 28 + (status == null ? 0 : 11) + descriptionRows * 10 + hintRows * 10;
        int x = 12, y = g.guiHeight() - 62 - boxHeight;
        g.fill(x - 5, y, x + contentWidth + 6, y + boxHeight, HUD_BACKGROUND);
        g.fill(x - 5, y, x - 3, y + boxHeight, HUD_ACCENT);
        g.blit(mode == ShapeMode.AUTO ? cursorIcon(CursorSettings.read(stack)) : ShapeRadialScreen.icon(mode), x, y + 4, 0, 0, 16, 16, 16, 16);
        g.drawString(mc.font, UiTheme.fit(mc.font, name.getString(), contentWidth - 21), x + 21, y + 7, UiTheme.ACCENT, false);
        int textY = y + 23;
        if (status != null) {
            g.drawString(mc.font, UiTheme.fit(mc.font, status.getString(), contentWidth), x, textY, UiTheme.TEXT, false);
            textY += 11;
        }
        for (int i = 0; i < descriptionRows; i++, textY += 10) {
            g.drawString(mc.font, description.get(i), x, textY, 0xff91cbbb, false);
        }
        for (int i = 0; i < hintRows; i++, textY += 10) {
            g.drawString(mc.font, hintLines.get(i), x, textY, UiTheme.MUTED, false);
        }
    }

    /** "Bank: Steep (60°) • Width 4 m • Points 1/3" */
    private static Component bermStatus(net.minecraft.world.item.ItemStack stack) {
        var settings = BermBuilder.Settings.read(stack);
        return Component.translatable("descentmtb.berm.hud",
                Component.translatable(settings.steepness().key()), (int) settings.steepness().degrees,
                settings.width(), BermBuilder.points(stack).length, BermBuilder.POINTS);
    }

    /** "Style: Mixed • Grade: Medium (10°) • Width 3 m • Points 1/2" */
    private static Component downhillStatus(net.minecraft.world.item.ItemStack stack) {
        var settings = DownhillBuilder.Settings.read(stack);
        Component grade = Component.translatable(settings.grade().key());
        if (settings.grade().limited()) {
            grade = Component.empty().append(grade).append(" (" + settings.grade().degrees + "°)");
        }
        return Component.translatable("descentmtb.downhill.hud", Component.translatable(settings.style().key()), grade,
                settings.width(), DownhillBuilder.points(stack).length, DownhillBuilder.POINTS);
    }

    /**
     * "Width 3 m - Points 1/2" and, once point A is placed and a block is aimed at, "Length 23 m - Grade 12 % (6.8 deg)" (the
     * straight line) or "Length 23 m" (the path clearing), in red when the player may not build that line.
     */
    private static Component lineStatus(Minecraft mc, net.minecraft.world.item.ItemStack stack, ShapeMode mode) {
        LineSettings settings = LineSettings.read(stack);
        boolean line = mode.kind == ShapeMode.Kind.LINE;
        int width = line ? settings.width() : settings.clearWidth();
        BlockPos a = LinePoints.first(stack);
        MutableComponent status = Component.translatable("descentmtb.line.hud", width, a == null ? 0 : 1, 2);
        if (a != null && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos b = hit.getBlockPos();
            var layout = new StraightLines.Layout(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ(), width);
            if (layout.horizontal() >= 1) {
                int creative = StraightLines.MAX_LENGTH_CREATIVE;
                boolean bulk = TrailEdit.mayBulkEdit(mc.player);
                boolean refused;
                Component measure;
                if (line) {
                    refused = layout.problem(bulk ? creative : StraightLines.MAX_LENGTH) != null;
                    measure = Component.translatable("descentmtb.line.hud.measure", String.format(Locale.ROOT, "%.0f", layout.length()),
                            String.format(Locale.ROOT, "%.0f", layout.percent()), String.format(Locale.ROOT, "%.1f", layout.degrees()));
                } else {
                    refused = layout.horizontal() > (bulk ? creative : ClearPathBuilder.SURVIVAL_LENGTH);
                    measure = Component.translatable("descentmtb.line.hud.distance", String.format(Locale.ROOT, "%.0f", layout.horizontal()));
                }
                status.append(" • ").append(refused ? measure.copy().withStyle(ChatFormatting.RED) : measure);
            }
        }
        return status;
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
