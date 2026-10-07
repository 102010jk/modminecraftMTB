package com.descentmtb.map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** A portable, dimension-aware collection of recorded routes. Bounded for inventory synchronization. */
public record BikeparkMap(List<Route> routes) {
    public static final int MAX_ROUTES=12, MAX_ROUTE_POINTS=512;
    /** Routes a map may show at once: the stack and the network keep to {@link #MAX_ROUTES}, but a held or wall map also draws the loaded signs' trails. */
    public static final int MAX_VIEW_ROUTES=32;
    public static final BikeparkMap EMPTY = new BikeparkMap(List.of());
    public record Route(String name, ResourceLocation dimension, TrailTrack track) {
        public static final Codec<Route> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.string(0,64).fieldOf("name").forGetter(Route::name),
                ResourceLocation.CODEC.fieldOf("dimension").forGetter(Route::dimension),
                TrailTrack.CODEC.fieldOf("track").forGetter(Route::track)).apply(i,Route::new));
        public static final StreamCodec<ByteBuf,Route> STREAM = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64),Route::name,ResourceLocation.STREAM_CODEC,Route::dimension,
                TrailTrack.STREAM_CODEC,Route::track,Route::new);
        public Route {
            name=name.length()>64?name.substring(0,64):name;
            track=new TrailTrack(TrackGeometry.simplifyTo(track.pts(),.15,MAX_ROUTE_POINTS));
        }
    }
    public static final Codec<BikeparkMap> CODEC = Route.CODEC.listOf(0,MAX_ROUTES).xmap(BikeparkMap::new,BikeparkMap::routes);
    public static final StreamCodec<ByteBuf,BikeparkMap> STREAM_CODEC = Route.STREAM.apply(ByteBufCodecs.list(MAX_ROUTES)).map(BikeparkMap::new,BikeparkMap::routes);
    public BikeparkMap { routes=List.copyOf(routes.subList(0,Math.min(MAX_VIEW_ROUTES,routes.size()))); }
    /** The same trail: one dimension, and either an equal track or the same name. */
    public static boolean sameRoute(Route a,Route b) { return a.dimension().equals(b.dimension()) && (a.track().equals(b.track()) || a.name().equals(b.name())); }
    public BikeparkMap add(Route route) {
        List<Route> out=new ArrayList<>(routes);
        out.removeIf(r -> sameRoute(r,route));
        if(out.size()>=MAX_ROUTES)out.removeFirst();out.add(route);return new BikeparkMap(out);
    }
}
