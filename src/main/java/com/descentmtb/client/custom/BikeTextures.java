package com.descentmtb.client.custom;

import com.descentmtb.custom.BikeParts.Finish;
import com.descentmtb.entity.BikeType;
import net.minecraft.resources.ResourceLocation;
import java.util.Locale;

public final class BikeTextures {
    private static final ResourceLocation[][] TEXTURES = new ResourceLocation[BikeType.values().length][Finish.values().length];
    static {
        for (BikeType type : BikeType.values()) for (Finish finish : Finish.values()) {
            // the motorbikes have no frame finishes (they are painted by group, see MotoBuild): a valid entry, their own texture
            String name=switch (type) { case ENDURO -> "enduro_bike"; case HARDTAIL -> "hardtail_bike"; case DIRT_BIKE -> "dirt_bike"; case PIT_BIKE -> "pit_bike"; };
            if (type.motor() && finish != Finish.GLOSS) { TEXTURES[type.ordinal()][finish.ordinal()]=TEXTURES[type.ordinal()][0]; continue; }
            String suffix=finish == Finish.GLOSS ? "" : "_" + finish.name().toLowerCase(Locale.ROOT);
            TEXTURES[type.ordinal()][finish.ordinal()]=ResourceLocation.fromNamespaceAndPath("descentmtb","textures/entity/"+name+suffix+".png");
        }
    }
    public static ResourceLocation base(BikeType type) { return TEXTURES[type.ordinal()][0]; }
    public static ResourceLocation finish(BikeType type, Finish finish) { return TEXTURES[type.ordinal()][finish.ordinal()]; }
    private BikeTextures() {}
}
