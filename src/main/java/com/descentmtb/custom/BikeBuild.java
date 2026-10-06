package com.descentmtb.custom;

import com.descentmtb.custom.BikeParts.Anodized;
import com.descentmtb.custom.BikeParts.Bars;
import com.descentmtb.custom.BikeParts.Bell;
import com.descentmtb.custom.BikeParts.Brakes;
import com.descentmtb.custom.BikeParts.Finish;
import com.descentmtb.custom.BikeParts.Fork;
import com.descentmtb.custom.BikeParts.FrameShape;
import com.descentmtb.custom.BikeParts.LightColor;
import com.descentmtb.custom.BikeParts.Shock;
import com.descentmtb.custom.BikeParts.Soft;
import com.descentmtb.custom.BikeParts.StickerDesign;
import com.descentmtb.custom.BikeParts.Tube;
import com.descentmtb.custom.BikeParts.TyreWall;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Everything the player chose for one bike. Lives on the bike item as a data component, travels with the bike
 * entity (synced to every viewer) and is edited on the bike stand. Immutable; use the {@code with...} methods.
 *
 * <p>Colours are 0xRRGGBB ints. Enums are stored by name (unknown names fall back to the defaults), so the
 * catalogue can grow without breaking saved bikes. {@link #sanitized(boolean)} clamps everything a client could
 * send into the allowed ranges - the server calls it on every edit.
 */
public record BikeBuild(
        FrameShape shape, int frameColor, int accentColor, Finish finish,
        Fork fork, int forkLowerColor, Shock shock,
        Anodized rims, Anodized hubs, TyreWall tyres,
        Bars bars, Soft grips, Soft saddle, Anodized pedals, Brakes brakes, Anodized brakeColor,
        Bell bell, boolean frontLight, boolean rearLight, LightColor lightColor,
        List<Sticker> stickers, String name) {

    /** Source compatibility with existing stock builds and old integrations. */
    public BikeBuild(FrameShape shape,int frameColor,int accentColor,Finish finish,Fork fork,int forkLowerColor,Shock shock,
                     Anodized rims,Anodized hubs,TyreWall tyres,Bars bars,Soft grips,Soft saddle,Anodized pedals,Brakes brakes,
                     Anodized brakeColor,Bell bell,boolean frontLight,boolean rearLight,LightColor lightColor,List<Sticker> stickers) {
        this(shape,frameColor,accentColor,finish,fork,forkLowerColor,shock,rims,hubs,tyres,bars,grips,saddle,pedals,brakes,
                brakeColor,bell,frontLight,rearLight,lightColor,stickers,"");
    }

    public static final int MAX_STICKERS = 24;

    /** A sticker on a frame tube: t along the tube (0..1), side (-1 left, 0 both, 1 right), rotation, scale, tint. */
    public record Sticker(StickerDesign design, Tube tube, float t, int side, float rotation, float scale, int tint,
                          String text,float across,boolean mirrored) {
        public Sticker(StickerDesign design,Tube tube,float t,int side,float rotation,float scale,int tint) {
            this(design,tube,t,side,rotation,scale,tint,"",0,false);
        }
        public static final Codec<Sticker> CODEC = RecordCodecBuilder.create(i -> i.group(
                enumCodec(StickerDesign.class, StickerDesign.LOGO_DESCENT).fieldOf("design").forGetter(Sticker::design),
                enumCodec(Tube.class, Tube.DOWN).fieldOf("tube").forGetter(Sticker::tube),
                Codec.FLOAT.fieldOf("t").forGetter(Sticker::t),
                Codec.INT.fieldOf("side").forGetter(Sticker::side),
                Codec.FLOAT.optionalFieldOf("rotation", 0f).forGetter(Sticker::rotation),
                Codec.FLOAT.optionalFieldOf("scale", 1f).forGetter(Sticker::scale),
                Codec.INT.optionalFieldOf("tint", 0xFFFFFF).forGetter(Sticker::tint),
                Codec.STRING.optionalFieldOf("text", "").forGetter(Sticker::text),
                Codec.FLOAT.optionalFieldOf("across", 0f).forGetter(Sticker::across),
                Codec.BOOL.optionalFieldOf("mirrored", false).forGetter(Sticker::mirrored)
        ).apply(i, Sticker::new));

        Sticker sanitized() {
            return new Sticker(design, tube, clamp(t, 0, 1), Math.max(-1, Math.min(1, side)),
                    Float.isFinite(rotation) ? ((rotation % 360) + 360) % 360 : 0, clamp(scale, .4f, 2.5f), tint & 0xFFFFFF,
                    cleanText(text,32),clamp(across,-.9f,.9f),mirrored);
        }
        /** Preserve the additional decal settings when a legacy seven-field editor changes a core value. */
        public Sticker changed(StickerDesign design,Tube tube,float t,int side,float rotation,float scale,int tint) {
            return new Sticker(design,tube,t,side,rotation,scale,tint,text,across,mirrored);
        }
        public Sticker withText(String value) { return new Sticker(design,tube,t,side,rotation,scale,tint,cleanText(value,32),across,mirrored); }
        public Sticker withAcross(float value) { return new Sticker(design,tube,t,side,rotation,scale,tint,text,value,mirrored); }
        public Sticker flipImage() { return new Sticker(design,tube,t,side,rotation,scale,tint,text,across,!mirrored); }
        public Sticker opposite() { return new Sticker(design,tube,t,-side,rotation,scale,tint,text,across,mirrored); }
    }

    // ------------------------------------------------------------------ defaults (the stock look of each bike)

    public static final BikeBuild ENDURO_DEFAULT = new BikeBuild(
            FrameShape.ENDURO_CLASSIC, 0x1F8A8F, 0xF2F0EA, Finish.GLOSS,
            Fork.FOX_38_FACTORY, 0x16171A, Shock.FLOAT_X2_FACTORY,
            Anodized.BLACK, Anodized.BLACK, TyreWall.BLACK,
            Bars.CARBON, Soft.BLACK, Soft.BLACK, Anodized.BLACK, Brakes.SHIMANO_SAINT, Anodized.BLACK,
            Bell.NONE, false, false, LightColor.WHITE, List.of());

    public static final BikeBuild HARDTAIL_DEFAULT = new BikeBuild(
            FrameShape.DJ_CLASSIC, 0xC73A2E, 0x23262B, Finish.GLOSS,
            Fork.DJ_PIKE, 0x15161A, Shock.FLOAT_X_PERFORMANCE,
            Anodized.SILVER, Anodized.BLACK, TyreWall.TAN,
            Bars.BLACK_ALLOY, Soft.BLACK, Soft.BLACK, Anodized.BLACK, Brakes.SHIMANO_XT, Anodized.BLACK,
            Bell.NONE, false, false, LightColor.WHITE, List.of());

    public static BikeBuild defaultFor(boolean fullSuspension) {
        return fullSuspension ? ENDURO_DEFAULT : HARDTAIL_DEFAULT;
    }

    // ------------------------------------------------------------------ codecs

    static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type, E fallback) {
        return Codec.STRING.xmap(s -> BikeParts.byName(type, s, fallback), Enum::name);
    }

    /** Map codec in two halves: RecordCodecBuilder groups are limited to 16 fields. */
    public static final Codec<BikeBuild> CODEC = RecordCodecBuilder.create(i -> i.group(
            Frame.CODEC.fieldOf("frame").forGetter(Frame::of),
            Parts.CODEC.fieldOf("parts").forGetter(Parts::of),
            Sticker.CODEC.listOf().optionalFieldOf("stickers", List.of()).forGetter(BikeBuild::stickers),
            Codec.STRING.optionalFieldOf("name", "").forGetter(BikeBuild::name)
    ).apply(i, (f, p, s, n) -> new BikeBuild(f.shape, f.frameColor, f.accentColor, f.finish, p.fork, p.forkLowerColor, p.shock,
            p.rims, p.hubs, p.tyres, p.bars, p.grips, p.saddle, p.pedals, p.brakes, p.brakeColor,
            f.bell, f.frontLight, f.rearLight, f.lightColor, s, n)));

    public static final StreamCodec<ByteBuf, BikeBuild> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    /** Frame + accessories half of the codec. */
    private record Frame(FrameShape shape, int frameColor, int accentColor, Finish finish,
                         Bell bell, boolean frontLight, boolean rearLight, LightColor lightColor) {
        static final Codec<Frame> CODEC = RecordCodecBuilder.create(i -> i.group(
                enumCodec(FrameShape.class, FrameShape.ENDURO_CLASSIC).fieldOf("shape").forGetter(Frame::shape),
                Codec.INT.fieldOf("color").forGetter(Frame::frameColor),
                Codec.INT.optionalFieldOf("accent", 0xF2F0EA).forGetter(Frame::accentColor),
                enumCodec(Finish.class, Finish.GLOSS).optionalFieldOf("finish", Finish.GLOSS).forGetter(Frame::finish),
                enumCodec(Bell.class, Bell.NONE).optionalFieldOf("bell", Bell.NONE).forGetter(Frame::bell),
                Codec.BOOL.optionalFieldOf("front_light", false).forGetter(Frame::frontLight),
                Codec.BOOL.optionalFieldOf("rear_light", false).forGetter(Frame::rearLight),
                enumCodec(LightColor.class, LightColor.WHITE).optionalFieldOf("light_color", LightColor.WHITE).forGetter(Frame::lightColor)
        ).apply(i, Frame::new));

        static Frame of(BikeBuild b) {
            return new Frame(b.shape, b.frameColor, b.accentColor, b.finish, b.bell, b.frontLight, b.rearLight, b.lightColor);
        }
    }

    /** Components half of the codec. */
    private record Parts(Fork fork, int forkLowerColor, Shock shock, Anodized rims, Anodized hubs, TyreWall tyres,
                         Bars bars, Soft grips, Soft saddle, Anodized pedals, Brakes brakes, Anodized brakeColor) {
        static final Codec<Parts> CODEC = RecordCodecBuilder.create(i -> i.group(
                enumCodec(Fork.class, Fork.FOX_38_FACTORY).fieldOf("fork").forGetter(Parts::fork),
                Codec.INT.optionalFieldOf("fork_lower", 0x16171A).forGetter(Parts::forkLowerColor),
                enumCodec(Shock.class, Shock.FLOAT_X2_FACTORY).optionalFieldOf("shock", Shock.FLOAT_X2_FACTORY).forGetter(Parts::shock),
                enumCodec(Anodized.class, Anodized.BLACK).optionalFieldOf("rims", Anodized.BLACK).forGetter(Parts::rims),
                enumCodec(Anodized.class, Anodized.BLACK).optionalFieldOf("hubs", Anodized.BLACK).forGetter(Parts::hubs),
                enumCodec(TyreWall.class, TyreWall.BLACK).optionalFieldOf("tyres", TyreWall.BLACK).forGetter(Parts::tyres),
                enumCodec(Bars.class, Bars.CARBON).optionalFieldOf("bars", Bars.CARBON).forGetter(Parts::bars),
                enumCodec(Soft.class, Soft.BLACK).optionalFieldOf("grips", Soft.BLACK).forGetter(Parts::grips),
                enumCodec(Soft.class, Soft.BLACK).optionalFieldOf("saddle", Soft.BLACK).forGetter(Parts::saddle),
                enumCodec(Anodized.class, Anodized.BLACK).optionalFieldOf("pedals", Anodized.BLACK).forGetter(Parts::pedals),
                enumCodec(Brakes.class, Brakes.SHIMANO_SAINT).optionalFieldOf("brakes", Brakes.SHIMANO_SAINT).forGetter(Parts::brakes),
                enumCodec(Anodized.class, Anodized.BLACK).optionalFieldOf("brake_color", Anodized.BLACK).forGetter(Parts::brakeColor)
        ).apply(i, Parts::new));

        static Parts of(BikeBuild b) {
            return new Parts(b.fork, b.forkLowerColor, b.shock, b.rims, b.hubs, b.tyres, b.bars, b.grips, b.saddle,
                    b.pedals, b.brakes, b.brakeColor);
        }
    }

    // ------------------------------------------------------------------ validation

    /** Everything clamped into what the catalogue allows for this kind of bike. */
    public BikeBuild sanitized(boolean fullSuspension) {
        FrameShape s = shape.fullSuspension == fullSuspension ? shape : BikeParts.shapesFor(fullSuspension).get(0);
        Fork f = fork.enduro == fullSuspension ? fork : defaultFor(fullSuspension).fork;
        int lower = forkLowerColor & 0xFFFFFF;
        boolean stock = false;
        for (int c : f.lowerColors) {
            stock |= c == lower;
        }
        if (!stock) {
            lower = f.lowerColors[0];
        }
        List<Sticker> list = new ArrayList<>();
        for (Sticker st : stickers) {
            if (list.size() >= MAX_STICKERS) {
                break;
            }
            if (st != null) {
                list.add(st.sanitized());
            }
        }
        return new BikeBuild(s, frameColor & 0xFFFFFF, accentColor & 0xFFFFFF, finish, f, lower, shock,
                rims, hubs, tyres, bars, grips, saddle, pedals, brakes, brakeColor,
                bell, frontLight, rearLight, lightColor, List.copyOf(list), cleanText(name,48));
    }

    /** A cheap stable hash for caches (item icon, baked tints). */
    public int visualHash() {
        return hashCode();
    }

    // ------------------------------------------------------------------ withers (used by the workshop)

    public BikeBuild with(Function<Builder, Builder> edit) {
        return edit.apply(new Builder(this)).build();
    }

    /** Mutable copy for convenient edits: {@code build.with(b -> b.frameColor(0xff0000).bell(Bell.RUBBER_DUCK))}. */
    public static final class Builder {
        FrameShape shape; int frameColor, accentColor; Finish finish; Fork fork; int forkLowerColor; Shock shock;
        Anodized rims, hubs; TyreWall tyres; Bars bars; Soft grips, saddle; Anodized pedals; Brakes brakes;
        Anodized brakeColor; Bell bell; boolean frontLight, rearLight; LightColor lightColor; List<Sticker> stickers;
        String name;

        Builder(BikeBuild b) {
            shape = b.shape; frameColor = b.frameColor; accentColor = b.accentColor; finish = b.finish; fork = b.fork;
            forkLowerColor = b.forkLowerColor; shock = b.shock; rims = b.rims; hubs = b.hubs; tyres = b.tyres; bars = b.bars;
            grips = b.grips; saddle = b.saddle; pedals = b.pedals; brakes = b.brakes; brakeColor = b.brakeColor; bell = b.bell;
            frontLight = b.frontLight; rearLight = b.rearLight; lightColor = b.lightColor; stickers = new ArrayList<>(b.stickers);
            name=b.name;
        }

        public Builder shape(FrameShape v) { shape = v; return this; }
        public Builder name(String v) { name=cleanText(v,48); return this; }
        public Builder frameColor(int v) { frameColor = v; return this; }
        public Builder accentColor(int v) { accentColor = v; return this; }
        public Builder finish(Finish v) { finish = v; return this; }
        public Builder fork(Fork v) { fork = v; forkLowerColor = v.lowerColors[0]; return this; }
        public Builder forkLowerColor(int v) { forkLowerColor = v; return this; }
        public Builder shock(Shock v) { shock = v; return this; }
        public Builder rims(Anodized v) { rims = v; return this; }
        public Builder hubs(Anodized v) { hubs = v; return this; }
        public Builder tyres(TyreWall v) { tyres = v; return this; }
        public Builder bars(Bars v) { bars = v; return this; }
        public Builder grips(Soft v) { grips = v; return this; }
        public Builder saddle(Soft v) { saddle = v; return this; }
        public Builder pedals(Anodized v) { pedals = v; return this; }
        public Builder brakes(Brakes v) { brakes = v; return this; }
        public Builder brakeColor(Anodized v) { brakeColor = v; return this; }
        public Builder bell(Bell v) { bell = v; return this; }
        public Builder frontLight(boolean v) { frontLight = v; return this; }
        public Builder rearLight(boolean v) { rearLight = v; return this; }
        public Builder lightColor(LightColor v) { lightColor = v; return this; }
        public Builder stickers(List<Sticker> v) { stickers = new ArrayList<>(v); return this; }
        public List<Sticker> stickers() { return stickers; }

        public BikeBuild build() {
            return new BikeBuild(shape, frameColor, accentColor, finish, fork, forkLowerColor, shock, rims, hubs, tyres,
                    bars, grips, saddle, pedals, brakes, brakeColor, bell, frontLight, rearLight, lightColor, List.copyOf(stickers),name);
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return Float.isFinite(v) ? Math.max(lo, Math.min(hi, v)) : lo;
    }

    public static String cleanText(String text,int limit) {
        if(text==null) return "";
        String safe=text.replaceAll("[\\p{Cntrl}§]","").strip();
        return safe.substring(0,Math.min(limit,safe.length()));
    }
}
