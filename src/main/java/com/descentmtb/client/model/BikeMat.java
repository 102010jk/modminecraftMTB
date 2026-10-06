package com.descentmtb.client.model;

import com.descentmtb.custom.BikeBuild;
import com.descentmtb.custom.BikeParts.Finish;
import java.util.Locale;

/** Material tags shared with the literal cube tables and texture generator. */
public enum BikeMat {
    FRAME, ACCENT, STANCHION, LOWER, SHOCKBODY, SHOCKRES, SPRING, AIRCAN,
    RIM, HUB, TIRE, TREAD, BARS, GRIP, SADDLE, PEDAL, BRAKE, LENS,
    BLACK, SILVER, SPOKE, ROTOR, CHAIN, CHROME, DUCK, BEAK, EYE, HORN, LIGHTBODY;

    public static BikeMat of(String tag) { return valueOf(tag.toUpperCase(Locale.ROOT)); }

    public int color(BikeBuild b) {
        int rgb = switch (this) {
            case FRAME -> b.finish() == Finish.RAW ? 0xFFFFFF : b.frameColor();
            case ACCENT -> b.finish() == Finish.RAW ? 0xFFFFFF : b.accentColor();
            case STANCHION -> b.fork().stanchions.rgb;
            case LOWER -> b.forkLowerColor();
            case SHOCKBODY -> b.shock().body.rgb;
            case SHOCKRES, AIRCAN -> b.shock().reservoirColor;
            case SPRING -> b.shock().springColor;
            case RIM -> b.rims().rgb;
            case HUB -> b.hubs().rgb;
            case TIRE -> b.tyres().rgb;
            case BARS -> b.bars().rgb;
            case GRIP -> b.grips().rgb;
            case SADDLE -> b.saddle().rgb;
            case PEDAL -> b.pedals().rgb;
            case BRAKE -> b.brakes().rgb < 0 ? b.brakeColor().rgb : b.brakes().rgb;
            case LENS -> b.lightColor().rgb;
            default -> 0xFFFFFF;
        };
        return 0xFF000000 | rgb & 0xFFFFFF;
    }

    public boolean frame() { return this == FRAME || this == ACCENT; }
}
