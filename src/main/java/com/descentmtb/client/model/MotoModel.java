package com.descentmtb.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.ToIntFunction;

/**
 * STUB (the real one comes with the model work): common base of the motorbike models, so the renderer, the stand and
 * the workshop preview treat every motorbike alike.
 */
public abstract class MotoModel extends Model {
    /** The paint groups of a motorbike, in the order {@code MotoBuild} stores their colours. */
    public static final String[] GROUPS = {"plastic", "fender", "frame", "seat", "rim", "spring", "anodized"};

    protected MotoModel() {
        super(RenderType::entityCutoutNoCull);
    }

    /** Stock colour (0xRRGGBB) of a paint group. */
    public static int defaultColor(String group) {
        return switch (group) {
            case "plastic" -> 0xE8541A;
            case "fender" -> 0xF2F0EA;
            case "frame" -> 0x2A2D33;
            case "seat" -> 0x1B1C1F;
            case "rim" -> 0xC9CDD2;
            default -> 0xD9A93A;
        };
    }

    public abstract ResourceLocation texture();

    public abstract void setupPose(float steerRad, float forkTravelM, float rearTravelM, float frontSpin, float rearSpin);

    /** Draws the bike with a colour per group (0xRRGGBB, or -1 = the stock colour). */
    public abstract void renderPainted(PoseStack pose, VertexConsumer vc, int light, int overlay, ToIntFunction<String> color);
}
