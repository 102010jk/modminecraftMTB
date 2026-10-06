package com.descentmtb.audio;

import com.descentmtb.DescentMtb;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Boombox / headphones networking. Client handlers are plugged in by the client ({@link #onState}, {@link #onChunk})
 * so this class stays loadable on a dedicated server.
 */
@EventBusSubscriber(modid = DescentMtb.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class AudioNet {
    private AudioNet() {}

    /** Max bytes of one stream chunk (40 ms of 16 kHz mu-law is 640). */
    public static final int MAX_CHUNK = 1024;

    public static volatile Consumer<BoomboxState> onState = s -> {};
    public static volatile Consumer<ChunkS2C> onChunk = c -> {};

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, path);
    }

    private static final StreamCodec<ByteBuf, byte[]> BYTES = ByteBufCodecs.byteArray(MAX_CHUNK);

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("10");
        r.playToServer(ControlC2S.TYPE, ControlC2S.CODEC, (m, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p) BoomboxServer.control(p, m);
        });
        r.playToServer(ChunkC2S.TYPE, ChunkC2S.CODEC, (m, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p) BoomboxServer.chunk(p, m);
        });
        r.playToServer(HeadphonesC2S.TYPE, HeadphonesC2S.CODEC, (m, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p) BoomboxServer.headphones(p, m);
        });
        r.playToClient(StateS2C.TYPE, StateS2C.CODEC, (m, ctx) -> onState.accept(m.state()));
        r.playToClient(ChunkS2C.TYPE, ChunkS2C.CODEC, (m, ctx) -> onChunk.accept(m));
    }

    /** Owner → server: start (APP or DISC) or stop a boombox. */
    public record ControlC2S(Emitter emitter, boolean start, BoomboxState.Mode mode, Optional<ResourceLocation> disc,
                             int radius, float volume, String label) implements CustomPacketPayload {
        public static final Type<ControlC2S> TYPE = new Type<>(id("boombox_control"));
        private static final StreamCodec<ByteBuf, Optional<ResourceLocation>> DISC = ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC);
        private static final StreamCodec<ByteBuf, String> LABEL = ByteBufCodecs.stringUtf8(64);
        public static final StreamCodec<ByteBuf, ControlC2S> CODEC = StreamCodec.of(
                (b, m) -> {
                    Emitter.CODEC.encode(b, m.emitter);
                    b.writeBoolean(m.start);
                    b.writeBoolean(m.mode == BoomboxState.Mode.DISC);
                    DISC.encode(b, m.disc);
                    ByteBufCodecs.VAR_INT.encode(b, m.radius);
                    b.writeFloat(m.volume);
                    LABEL.encode(b, m.label);
                },
                b -> new ControlC2S(Emitter.CODEC.decode(b), b.readBoolean(),
                        b.readBoolean() ? BoomboxState.Mode.DISC : BoomboxState.Mode.APP, DISC.decode(b),
                        ByteBufCodecs.VAR_INT.decode(b), b.readFloat(), LABEL.decode(b)));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Owner → server: 40 ms of 16 kHz mono mu-law from the owner's desktop program. */
    public record ChunkC2S(Emitter emitter, int seq, byte[] data) implements CustomPacketPayload {
        public static final Type<ChunkC2S> TYPE = new Type<>(id("boombox_chunk"));
        public static final StreamCodec<ByteBuf, ChunkC2S> CODEC = StreamCodec.composite(
                Emitter.CODEC, ChunkC2S::emitter, ByteBufCodecs.VAR_INT, ChunkC2S::seq, BYTES, ChunkC2S::data, ChunkC2S::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server → listeners near a boombox: the owner's chunk, relayed. */
    public record ChunkS2C(Emitter emitter, int seq, byte[] data) implements CustomPacketPayload {
        public static final Type<ChunkS2C> TYPE = new Type<>(id("boombox_chunk_out"));
        public static final StreamCodec<ByteBuf, ChunkS2C> CODEC = StreamCodec.composite(
                Emitter.CODEC, ChunkS2C::emitter, ByteBufCodecs.VAR_INT, ChunkS2C::seq, BYTES, ChunkS2C::data, ChunkS2C::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server → clients of the dimension: a boombox started, changed or stopped. */
    public record StateS2C(BoomboxState state) implements CustomPacketPayload {
        public static final Type<StateS2C> TYPE = new Type<>(id("boombox_state"));
        public static final StreamCodec<ByteBuf, StateS2C> CODEC = BoomboxState.CODEC.map(StateS2C::new, StateS2C::state);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Wearer → server: {@code DROP} the worn headphones into the grass (hard crash with "drop on crash"). */
    public record HeadphonesC2S(int action) implements CustomPacketPayload {
        public static final int DROP = 0;
        public static final Type<HeadphonesC2S> TYPE = new Type<>(id("headphones"));
        public static final StreamCodec<ByteBuf, HeadphonesC2S> CODEC = ByteBufCodecs.VAR_INT.map(HeadphonesC2S::new, HeadphonesC2S::action);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
