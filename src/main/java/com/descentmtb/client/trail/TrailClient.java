package com.descentmtb.client.trail;
import com.descentmtb.network.TrailActionPayload;
import com.descentmtb.client.ModKeyMappings;
import com.descentmtb.network.*;
import com.descentmtb.trail.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.*;
public final class TrailClient {
 public static List<TrailPreviewPayload.Cell> ghost=List.of();private static Object level;

 // ---------------------------------------------------------------------------------- setup + keys

 public static void setup() {
  TrailPreviewPayload.clientHandler = p -> ghost = p.cells();
  TrailSignBlock.editor = be -> Minecraft.getInstance().setScreen(new SignEditorScreen(be));
 }

 private static final int[] MOVE_KEYS = {
   org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP,
   org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN, org.lwjgl.glfw.GLFW.GLFW_KEY_UP, org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN};
 /** Offsets (x, y, z) sent for each of {@link #MOVE_KEYS}. */
 private static final int[][] MOVE_OFFSETS = {{-1, 0, 0}, {1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}};
 private static final boolean[] moveHeld = new boolean[MOVE_KEYS.length];

 private static boolean holdingBuilder(Minecraft mc) {
  return mc.player != null && mc.screen == null && mc.player.getMainHandItem().getItem() instanceof TrailWandItem
    && !(mc.player.getVehicle() instanceof com.descentmtb.entity.MountainBikeEntity);
 }

 public static void tick() {
  var mc = Minecraft.getInstance();
  if (mc.level != level) {
   ghost = List.of();
   level = mc.level;
  }
  if(mc.player!=null&&mc.screen==null&&ShapeToolItem.usable(mc.player.getMainHandItem())&&ModKeyMappings.TRAIL_MENU.consumeClick()) {
   mc.setScreen(new ShapeRadialScreen());return;
  }
  if (!holdingBuilder(mc)) {
   return;
  }
  if (ModKeyMappings.TRAIL_MENU.consumeClick()) {
   mc.setScreen(new TrailRadialScreen());
   return;
  }
  if (ghost.isEmpty()) {
   return;   // the preview keys only act while there is a preview
  }
  if (ModKeyMappings.TRAIL_CONFIRM.consumeClick()) {
   send(TrailActionPayload.CONFIRM, 0, 0, 0);
  }
  if (ModKeyMappings.TRAIL_CANCEL.consumeClick()) {
   send(TrailActionPayload.CANCEL, 0, 0, 0);
  }
  if (ModKeyMappings.TRAIL_ROTATE.consumeClick()) {
   send(TrailActionPayload.ROTATE, 0, 0, 0);
  }
  long window = mc.getWindow().getWindow();
  for (int i = 0; i < MOVE_KEYS.length; i++) {
   boolean down = com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, MOVE_KEYS[i]);
   if (down && !moveHeld[i]) {
    send(TrailActionPayload.MOVE, MOVE_OFFSETS[i][0], MOVE_OFFSETS[i][1], MOVE_OFFSETS[i][2]);
   }
   moveHeld[i] = down;
  }
 }

 private static void send(int action, int dx, int dy, int dz) {
  net.neoforged.neoforge.network.PacketDistributor.sendToServer(
    new TrailActionPayload(action, new net.minecraft.nbt.CompoundTag(), "", dx, dy, dz));
 }

 // ---------------------------------------------------------------------------------- HUD

 private static final int HUD_BACKGROUND = 0xbe18252b, HUD_ACCENT = 0xffdfb65e;

 public static void hud(GuiGraphics g) {
  var mc = Minecraft.getInstance();
  if (mc.player == null || mc.options.hideGui || !holdingBuilder(mc)) {
   return;
  }
  var stack = mc.player.getMainHandItem();
  var settings = WandSettings.read(stack);
  var mode = settings.mode();
  int x = 12, y = g.guiHeight() - 72;
  g.fill(x - 5, y - 5, x + 262, y + 44, HUD_BACKGROUND);
  g.fill(x - 5, y - 5, x - 3, y + 44, HUD_ACCENT);

  g.blit(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("descentmtb",
    "textures/gui/trail/" + mode.name().toLowerCase(java.util.Locale.ROOT) + ".png"), x, y, 0, 0, 16, 16, 16, 16);
  var title = net.minecraft.network.chat.Component.translatable(mode.key());
  int placed = placedPoints(stack, mode);
  if (placed > 0) {
   title = title.copy().append(net.minecraft.network.chat.Component.literal("  " + placed + "/" + TrailWandItem.requiredPoints(mode)));
  }
  g.drawString(mc.font, title, x + 21, y + 4, 0xffe7d7ad);
  g.drawString(mc.font, dimensions(settings), x, y + 20, 0xff91cbbb);
  var hint = ghost.isEmpty()
    ? net.minecraft.network.chat.Component.translatable("descentmtb.wand.hud.points", ModKeyMappings.TRAIL_MENU.getTranslatedKeyMessage())
    : net.minecraft.network.chat.Component.translatable("descentmtb.wand.hud.preview", ModKeyMappings.TRAIL_CONFIRM.getTranslatedKeyMessage(),
      ModKeyMappings.TRAIL_CANCEL.getTranslatedKeyMessage(), ModKeyMappings.TRAIL_ROTATE.getTranslatedKeyMessage());
  g.drawString(mc.font, hint, x, y + 32, 0xff9aa6a8);
 }

 private static int placedPoints(net.minecraft.world.item.ItemStack stack, WandMode mode) {
  var guides = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
    net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getCompound("WandGuides");
  return guides.getString("Mode").equals(mode.name()) ? guides.getInt("Count") : 0;
 }

 private static String dimensions(WandSettings s) {
  return switch (s.mode()) {
   case PUMP_LINE, PUMP_LOOP -> String.format(java.util.Locale.ROOT, "%.1f m šířka • vlna %.2f m • rozestup %.1f m • %d×", s.width(), s.height(), s.spacing(), s.repeats());
   case ROOTS, ROCKS, ROCK_GARDEN, SIGN, AIRBAG, BARRIER, SUPPORT, CLONE, TEMPLATE, MEASURE, UNDO, RAMP_TUNE -> "";
   default -> String.format(java.util.Locale.ROOT, "%.1f m šířka • %.2f m výška", s.width(), s.height());
  };
 }

 private static List<GhostMesh> ghostMeshes=List.of();
 private static List<TrailPreviewPayload.Cell> ghostMeshSource = List.of();

 /** Draws the preview: the GPU mesh is rebuilt only when the preview itself changed. */
 public static void renderGhost(RenderLevelStageEvent e) {
  if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
   return;
  }
  if (ghost != ghostMeshSource) {
   ghostMeshes.forEach(GhostMesh::close);
   ghostMeshes = GhostMesh.buildAll(ghost);
   ghostMeshSource = ghost;
  }
  ghostMeshes.forEach(mesh->mesh.draw(e));
 }
}
