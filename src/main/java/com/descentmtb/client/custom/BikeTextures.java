package com.descentmtb.client.custom;

import com.descentmtb.custom.BikeParts.Finish;
import com.descentmtb.entity.BikeType;
import net.minecraft.resources.ResourceLocation;
import java.util.Locale;

public final class BikeTextures {
    private static final ResourceLocation[][] TEXTURES = new ResourceLocation[2][4];
    static {
        for (BikeType type : BikeType.values()) for (Finish finish : Finish.values()) {
            String name=type == BikeType.ENDURO ? "enduro_bike" : "hardtail_bike";
            String suffix=finish == Finish.GLOSS ? "" : "_" + finish.name().toLowerCase(Locale.ROOT);
            TEXTURES[type.ordinal()][finish.ordinal()]=ResourceLocation.fromNamespaceAndPath("descentmtb","textures/entity/"+name+suffix+".png");
        }
    }
    public static ResourceLocation base(BikeType type) { return TEXTURES[type.ordinal()][0]; }
    public static ResourceLocation finish(BikeType type, Finish finish) { return TEXTURES[type.ordinal()][finish.ordinal()]; }
    private BikeTextures() {}
}
