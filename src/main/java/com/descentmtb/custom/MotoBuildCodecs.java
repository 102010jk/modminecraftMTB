package com.descentmtb.custom;

import com.descentmtb.custom.MotoBuild.Exhaust;
import com.descentmtb.custom.MotoBuild.Suspension;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * Save and network formats of {@link MotoBuild}. Every field is optional and the constructor clamps what is read, so
 * old, partial or hand-edited data decodes to a valid build; enums are stored by name (unknown names fall back).
 */
public final class MotoBuildCodecs {
    public static final Codec<MotoBuild> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.listOf().optionalFieldOf("colors", List.of()).forGetter(MotoBuild::colors),
            Codec.INT.optionalFieldOf("sprocket", 0).forGetter(MotoBuild::sprocket),
            BikeBuild.enumCodec(Exhaust.class, Exhaust.STOCK).optionalFieldOf("exhaust", Exhaust.STOCK).forGetter(MotoBuild::exhaust),
            BikeBuild.enumCodec(Suspension.class, Suspension.STOCK).optionalFieldOf("suspension", Suspension.STOCK).forGetter(MotoBuild::suspension),
            Codec.INT.optionalFieldOf("number", 0).forGetter(MotoBuild::number)
    ).apply(i, MotoBuild::new));

    public static final StreamCodec<ByteBuf, MotoBuild> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    private MotoBuildCodecs() {}
}
