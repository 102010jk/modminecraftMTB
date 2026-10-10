package com.descentmtb.registry;

import com.descentmtb.DescentMtb;
import com.descentmtb.client.sound.BikeSoundMath.RollFamily;
import com.descentmtb.custom.BikeParts.HubType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Riding sounds (freehub, wind, tyres, bells, rider voice, tricks, crashes).
 * The samples are imported from Descenders; events are declared in sounds.json.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, DescentMtb.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> LIVE_AUDIO = event("audio.live");

    // Freehub
    public static final DeferredHolder<SoundEvent, SoundEvent> HUB_CLICK = event("bike.hub.click");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUB_BUZZ_6 = event("bike.hub.buzz.6");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUB_BUZZ_10 = event("bike.hub.buzz.10");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUB_BUZZ_16 = event("bike.hub.buzz.16");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUB_BUZZ_25 = event("bike.hub.buzz.25");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUB_BUZZ_40 = event("bike.hub.buzz.40");

    // Wind & Ride
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND = event("bike.wind");
    public static final DeferredHolder<SoundEvent, SoundEvent> PEDAL = event("bike.pedal");
    public static final DeferredHolder<SoundEvent, SoundEvent> BUNNYHOP = event("bike.bunnyhop");

    // Roll (Soft, Hard, Wood)
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLL_SOFT_1 = event("bike.roll.soft.1");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLL_SOFT_2 = event("bike.roll.soft.2");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLL_SOFT_3 = event("bike.roll.soft.3");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLL_SOFT_4 = event("bike.roll.soft.4");

    public static final DeferredHolder<SoundEvent, SoundEvent> ROLL_HARD_1 = event("bike.roll.hard.1");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLL_HARD_2 = event("bike.roll.hard.2");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLL_HARD_3 = event("bike.roll.hard.3");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLL_HARD_4 = event("bike.roll.hard.4");

    public static final DeferredHolder<SoundEvent, SoundEvent> ROLL_WOOD_2 = event("bike.roll.wood.2");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLL_WOOD_3 = event("bike.roll.wood.3");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLL_WOOD_4 = event("bike.roll.wood.4");

    // Landings
    public static final DeferredHolder<SoundEvent, SoundEvent> LAND_FRONT_SOFT = event("bike.land.front.soft");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAND_FRONT_MED = event("bike.land.front.medium");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAND_FRONT_HARD = event("bike.land.front.hard");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAND_BACK_SOFT = event("bike.land.back.soft");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAND_BACK_MED = event("bike.land.back.medium");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAND_BACK_HARD = event("bike.land.back.hard");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAND_BIGDROP = event("bike.land.bigdrop");

    // Crashes
    public static final DeferredHolder<SoundEvent, SoundEvent> CRASH_BIKE_SOFT = event("bike.crash.bike.soft");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRASH_BIKE_MED = event("bike.crash.bike.medium");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRASH_BIKE_HARD = event("bike.crash.bike.hard");

    // Tricks
    public static final DeferredHolder<SoundEvent, SoundEvent> TRICK_FLICK = event("bike.trick.flick");

    // Bells
    public static final DeferredHolder<SoundEvent, SoundEvent> BELL_DING = event("bike.bell.ding");
    public static final DeferredHolder<SoundEvent, SoundEvent> BELL_MINI = event("bike.bell.mini");
    public static final DeferredHolder<SoundEvent, SoundEvent> BELL_CLASSIC = event("bike.bell.classic");
    public static final DeferredHolder<SoundEvent, SoundEvent> BELL_HORN = event("bike.bell.horn");
    public static final DeferredHolder<SoundEvent, SoundEvent> BELL_DUCK = event("bike.bell.duck");

    // Rider Voice - Male
    public static final DeferredHolder<SoundEvent, SoundEvent> RIDER_MALE_SCREAM = event("bike.rider.male.scream");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIDER_MALE_BAIL = event("bike.rider.male.bail");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIDER_MALE_IMPACT_SOFT = event("bike.rider.male.impact.soft");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIDER_MALE_IMPACT_MED = event("bike.rider.male.impact.med");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIDER_MALE_IMPACT_HARD = event("bike.rider.male.impact.hard");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIDER_MALE_LANDED_NORMAL = event("bike.rider.male.landed.normal");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIDER_MALE_LANDED_BIG = event("bike.rider.male.landed.big");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIDER_MALE_LANDED_HUGE = event("bike.rider.male.landed.huge");

    // Rider Voice - Female
    public static final DeferredHolder<SoundEvent, SoundEvent> RIDER_FEMALE_SCREAM = event("bike.rider.female.scream");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIDER_FEMALE_BAIL = event("bike.rider.female.bail");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIDER_FEMALE_IMPACT_SOFT = event("bike.rider.female.impact.soft");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIDER_FEMALE_IMPACT_MED = event("bike.rider.female.impact.med");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIDER_FEMALE_IMPACT_HARD = event("bike.rider.female.impact.hard");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIDER_FEMALE_LANDED_NORMAL = event("bike.rider.female.landed.normal");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIDER_FEMALE_LANDED_BIG = event("bike.rider.female.landed.big");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIDER_FEMALE_LANDED_HUGE = event("bike.rider.female.landed.huge");

    // Slides / skids (rear tyre scrubbing sideways or locked under the brake)
    public static final DeferredHolder<SoundEvent, SoundEvent> SLIDE_SOFT = event("bike.slide.soft");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLIDE_SKID_SOFT = event("bike.slide.skid.soft");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLIDE_HARD = event("bike.slide.hard");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLIDE_WOOD = event("bike.slide.wood");

    // Dirt bike engine: four rpm layers, the rev limiter and the backfire
    public static final DeferredHolder<SoundEvent, SoundEvent> MOTO_IDLE = event("moto.engine.idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOTO_LOW = event("moto.engine.low");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOTO_MID = event("moto.engine.mid");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOTO_HIGH = event("moto.engine.high");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOTO_LIMITER = event("moto.engine.limiter");
    public static final DeferredHolder<SoundEvent, SoundEvent> MOTO_BACKFIRE = event("moto.engine.backfire");

    // Suspension hitting its bump stop
    public static final DeferredHolder<SoundEvent, SoundEvent> BOTTOM_OUT = event("bike.suspension.bottom_out");

    // Skis: the glide hiss on snow (four speed samples), the snowplough / hockey-stop spray, and rock knocks while
    // the bases grind over stone (the events and samples were already in sounds.json)
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLL_SNOW_1 = event("bike.roll.snow.1");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLL_SNOW_2 = event("bike.roll.snow.2");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLL_SNOW_3 = event("bike.roll.snow.3");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLL_SNOW_4 = event("bike.roll.snow.4");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLIDE_SNOW = event("bike.slide.snow");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRASH_STONE_SMALL = event("bike.crash.stone.small");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRASH_STONE_MED = event("bike.crash.stone.med");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRASH_STONE_HARD = event("bike.crash.stone.hard");

    // Misc
    public static final DeferredHolder<SoundEvent, SoundEvent> TAPE_BREAK = event("tape.break");

    private static DeferredHolder<SoundEvent, SoundEvent> event(String id) {
        return SOUNDS.register(id, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, id)));
    }

    /** Single click of a hub (null for the silent hub). */
    public static SoundEvent hubClick(HubType hub) {
        if (hub == null || hub.silent()) return null;
        return HUB_CLICK.get();
    }

    /** Looping buzz of a hub's freewheel (null for the silent hub). */
    public static SoundEvent hubBuzz(HubType hub) {
        if (hub == null || hub.silent()) return null;
        return switch (hub) {
            case INDUSTRY_NINE_HYDRA -> HUB_BUZZ_40.get();
            case CHRIS_KING -> HUB_BUZZ_25.get();
            case DT_SWISS_RATCHET -> HUB_BUZZ_16.get();
            case HOPE_PRO -> HUB_BUZZ_10.get();
            case ONYX_VESPER -> null;
        };
    }

    /** Tyre scrubbing over a surface family: a locked, braking skid on dirt sounds different from a sideways drift. */
    public static SoundEvent slideEvent(RollFamily family, boolean locked) {
        return switch (family) {
            case SOFT -> locked ? SLIDE_SKID_SOFT.get() : SLIDE_SOFT.get();
            case HARD -> SLIDE_HARD.get();
            case WOOD -> SLIDE_WOOD.get();
        };
    }

    /** Rolling sound for a surface family and speed level (1..4). */
    public static SoundEvent rollEvent(RollFamily family, int level) {
        return switch (family) {
            case SOFT -> switch (level) {
                case 1 -> ROLL_SOFT_1.get();
                case 2 -> ROLL_SOFT_2.get();
                case 3 -> ROLL_SOFT_3.get();
                default -> ROLL_SOFT_4.get();
            };
            case HARD -> switch (level) {
                case 1 -> ROLL_HARD_1.get();
                case 2 -> ROLL_HARD_2.get();
                case 3 -> ROLL_HARD_3.get();
                default -> ROLL_HARD_4.get();
            };
            case WOOD -> switch (Math.max(2, level)) {
                case 2 -> ROLL_WOOD_2.get();
                case 3 -> ROLL_WOOD_3.get();
                default -> ROLL_WOOD_4.get();
            };
        };
    }

    public static SoundEvent rollEvent(RollFamily family) {
        return rollEvent(family, 2);
    }

    /** Skis gliding over snow, by speed level (1..4, see {@code BikeSoundMath.rollLevel}). */
    public static SoundEvent glideEvent(int level) {
        return switch (level) {
            case 1 -> ROLL_SNOW_1.get();
            case 2 -> ROLL_SNOW_2.get();
            case 3 -> ROLL_SNOW_3.get();
            default -> ROLL_SNOW_4.get();
        };
    }

    private ModSounds() {}

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
