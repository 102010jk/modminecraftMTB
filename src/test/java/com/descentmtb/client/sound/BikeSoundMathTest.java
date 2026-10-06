package com.descentmtb.client.sound;

import com.descentmtb.custom.BikeParts.HubType;
import com.descentmtb.physics.Terrain.Surface;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The sound rules (no Minecraft classes involved). */
class BikeSoundMathTest {
    private static final double R = 0.375;      // wheel radius (m)

    private static double omega(double kmh) {
        return kmh / 3.6 / R;
    }

    // ---- hubs ----

    @Test
    void hubCatalogueMatchesTheDesign() {
        assertEquals(690, HubType.INDUSTRY_NINE_HYDRA.poe);
        assertEquals(54, HubType.DT_SWISS_RATCHET.poe);
        assertTrue(HubType.ONYX_VESPER.silent());
        for (HubType h : HubType.values()) {
            assertEquals(h.silent(), h.sound.isEmpty(), h + ": only the silent hub has no sound id");
            if (!h.silent()) assertTrue(h.loopHz > 0 && h.displayName.length() > 3);
        }
        // the buzz loop is baked at the click rate of the hub at 25 km/h
        for (HubType h : HubType.values()) {
            if (h.silent()) continue;
            double atRef = BikeSoundMath.clickRate(h, BikeSoundMath.REF_SPEED / R);
            assertEquals(atRef, h.loopHz, atRef * 0.06, h + " loop rate vs its click rate at 25 km/h");
        }
    }

    @Test
    void hubsAreSavedByNameAndUnknownNamesFallBackToTheDefault() {
        for (HubType h : HubType.values()) {
            assertSame(h, com.descentmtb.custom.BikeParts.byName(HubType.class, h.name(), HubType.DT_SWISS_RATCHET));
        }
        assertSame(HubType.DT_SWISS_RATCHET, com.descentmtb.custom.BikeParts.byName(HubType.class, "FUTURE_HUB_9000", HubType.DT_SWISS_RATCHET));
        assertSame(HubType.DT_SWISS_RATCHET, com.descentmtb.custom.BikeParts.byName(HubType.class, null, HubType.DT_SWISS_RATCHET));
    }

    @Test
    void clickRateIsPoeTimesRevolutions() {
        double revPerSec = omega(25) / (2 * Math.PI);
        assertEquals(690 * revPerSec, BikeSoundMath.clickRate(HubType.INDUSTRY_NINE_HYDRA, omega(25)), 1e-9);
        assertEquals(54 * revPerSec, BikeSoundMath.clickRate(HubType.DT_SWISS_RATCHET, omega(25)), 1e-9);
        assertEquals(BikeSoundMath.clickRate(HubType.CHRIS_KING, 10), BikeSoundMath.clickRate(HubType.CHRIS_KING, -10), 1e-12);
        assertEquals(0, BikeSoundMath.clickRate(HubType.ONYX_VESPER, 50), 1e-12);
    }

    @Test
    void higherPoeIsHigherPitchedAtTheSameSpeed() {
        double w = omega(25);
        double i9 = BikeSoundMath.clickRate(HubType.INDUSTRY_NINE_HYDRA, w);
        double dt = BikeSoundMath.clickRate(HubType.DT_SWISS_RATCHET, w);
        double ck = BikeSoundMath.clickRate(HubType.CHRIS_KING, w);
        assertTrue(i9 > 5 * ck && ck > dt, "very high pitched I9, then Chris King, then the 54T ratchet");
    }

    @Test
    void buzzPitchFollowsSpeedWithinTheSoundEngineRange() {
        for (HubType h : HubType.values()) {
            if (h.silent()) continue;
            double slow = BikeSoundMath.buzzPitch(h, BikeSoundMath.clickRate(h, omega(6)));
            double mid = BikeSoundMath.buzzPitch(h, BikeSoundMath.clickRate(h, omega(25)));
            double fast = BikeSoundMath.buzzPitch(h, BikeSoundMath.clickRate(h, omega(60)));
            assertEquals(1.0, mid, 0.07, h + " plays at pitch ~1 at 25 km/h");
            assertTrue(slow < mid && mid < fast, h + " pitch rises with speed");
            assertTrue(slow >= 0.5 && fast <= 2.0, h + " stays inside 0.5..2");
        }
        assertEquals(0.5, BikeSoundMath.buzzPitch(HubType.DT_SWISS_RATCHET, 0.01), 1e-12);
        assertEquals(2.0, BikeSoundMath.buzzPitch(HubType.DT_SWISS_RATCHET, 1e6), 1e-12);
    }

    @Test
    void slowRollingClicksFastRollingBuzzes() {
        HubType dt = HubType.DT_SWISS_RATCHET;
        assertTrue(BikeSoundMath.singleClicks(BikeSoundMath.clickRate(dt, omega(2))), "walking pace: single clicks");
        assertFalse(BikeSoundMath.singleClicks(BikeSoundMath.clickRate(dt, omega(20))), "20 km/h: buzz loop");
        assertEquals(0, BikeSoundMath.buzzBlend(10), 1e-12);
        assertEquals(1, BikeSoundMath.buzzBlend(60), 1e-12);
        double a = BikeSoundMath.buzzBlend(28), b = BikeSoundMath.buzzBlend(34);
        assertTrue(0 < a && a < b && b < 1, "smooth hand-over");
        // the I9 is never in single click mode while the wheel really turns
        assertFalse(BikeSoundMath.singleClicks(BikeSoundMath.clickRate(HubType.INDUSTRY_NINE_HYDRA, BikeSoundMath.MIN_OMEGA)));
    }

    @Test
    void freewheelOnlyWhileCoastingAndNeverOnTheSpragClutch() {
        HubType dt = HubType.DT_SWISS_RATCHET;
        assertTrue(BikeSoundMath.freewheelAudible(dt, omega(20), false, false));
        assertFalse(BikeSoundMath.freewheelAudible(dt, omega(20), true, false), "pedalling engages the pawls: silent");
        assertFalse(BikeSoundMath.freewheelAudible(dt, omega(20), false, true), "crashed bike");
        assertFalse(BikeSoundMath.freewheelAudible(dt, 0.3, false, false), "wheel (almost) stopped");
        assertFalse(BikeSoundMath.freewheelAudible(HubType.ONYX_VESPER, omega(40), false, false), "sprag clutch is silent");
        assertEquals(0, BikeSoundMath.freewheelVolume(HubType.ONYX_VESPER, 10), 1e-12);
        assertTrue(BikeSoundMath.freewheelVolume(HubType.HOPE_PRO, 10) > BikeSoundMath.freewheelVolume(HubType.DT_SWISS_RATCHET, 10),
                "the Hope is the loud one");
    }

    // ---- wind ----

    @Test
    void windGrowsWithSpeedAndRushesInTheAir() {
        double slow = BikeSoundMath.windVolume(5 / 3.6, false);
        double mid = BikeSoundMath.windVolume(25 / 3.6, false);
        double fast = BikeSoundMath.windVolume(50 / 3.6, false);
        double airFast = BikeSoundMath.windVolume(50 / 3.6, true);
        assertEquals(0, slow, 1e-9, "no wind at walking pace");
        assertTrue(mid > 0 && mid < fast && fast < airFast && airFast <= 1);
        assertTrue(fast > 2 * mid, "strong above ~35 km/h");
        assertTrue(BikeSoundMath.windVolume(30 / 3.6, true) > BikeSoundMath.windVolume(30 / 3.6, false), "louder in the air");
        assertEquals(0, BikeSoundMath.windVolume(0, true), 1e-9, "standing still in the air makes no wind");
        assertTrue(BikeSoundMath.windPitch(40) > BikeSoundMath.windPitch(5));
        assertTrue(BikeSoundMath.windPitch(1000) <= 1.25 + 1e-9 && BikeSoundMath.windPitch(0) >= 0.7 - 1e-9);
    }

    // ---- tyres ----

    @Test
    void everySurfaceHasAFamilyAndAGain() {
        for (Surface s : Surface.values()) {
            assertNotNull(BikeSoundMath.family(s), s.name());
            double g = BikeSoundMath.surfaceGain(s);
            assertTrue(g > 0 && g <= 1, s.name());
        }
        assertEquals(BikeSoundMath.RollFamily.SOFT, BikeSoundMath.family(Surface.DIRT));
        assertEquals(BikeSoundMath.RollFamily.SOFT, BikeSoundMath.family(Surface.GRASS));
        assertEquals(BikeSoundMath.RollFamily.HARD, BikeSoundMath.family(Surface.GRAVEL));
        assertEquals(BikeSoundMath.RollFamily.HARD, BikeSoundMath.family(Surface.ROCK));
        assertEquals(BikeSoundMath.RollFamily.WOOD, BikeSoundMath.family(Surface.WOOD));
    }

    @Test
    void tyreNoiseNeedsContactAndSpeed() {
        assertEquals(0, BikeSoundMath.rollVolume(0.1, 1, Surface.DIRT), 1e-12);
        assertEquals(0, BikeSoundMath.rollVolume(10, 0, Surface.DIRT), 1e-12, "in the air: nothing");
        double v5 = BikeSoundMath.rollVolume(5, 1, Surface.DIRT), v12 = BikeSoundMath.rollVolume(12, 1, Surface.DIRT);
        assertTrue(0 < v5 && v5 < v12 && v12 <= 0.61);
        assertTrue(BikeSoundMath.rollVolume(8, 1, Surface.SNOW) < BikeSoundMath.rollVolume(8, 1, Surface.GRAVEL));
        assertTrue(BikeSoundMath.rollPitch(15) > BikeSoundMath.rollPitch(3));
    }

    @Test
    void dominantSurfaceIsTheRearTyresUnlessOnlyTheFrontTouches() {
        BikeAudioFrame f = new BikeAudioFrame();
        f.frontContact = f.rearContact = true;
        f.frontSurface = Surface.WOOD;
        f.rearSurface = Surface.GRAVEL;
        assertEquals(Surface.GRAVEL, BikeSoundMath.dominantSurface(f));
        f.rearContact = false;
        assertEquals(Surface.WOOD, BikeSoundMath.dominantSurface(f), "a manual-less wheelie / nose contact");
        assertEquals(0.4, BikeSoundMath.contactFraction(f), 1e-12);
        f.frontContact = false;
        assertEquals(0, BikeSoundMath.contactFraction(f), 1e-12);
    }

    // ---- click pacing ----

    @Test
    void clickPacerHandsOutTheRequestedRate() {
        ClickPacer pacer = new ClickPacer();
        int clicks = 0;
        for (int i = 0; i < 200; i++) if (pacer.tick(8)) clicks++;       // 8 clicks/s for 10 s
        assertEquals(80, clicks, 2);
        pacer.reset();
        int fast = 0;
        for (int i = 0; i < 100; i++) if (pacer.tick(500)) fast++;       // capped at one per tick
        assertEquals(100, fast);
        pacer.reset();
        for (int i = 0; i < 40; i++) assertFalse(pacer.tick(0));
    }

    // ---- suspension ----

    @Test
    void hardCompressionsHissOnceAndLightOnesDoNot() {
        SuspensionHiss hiss = new SuspensionHiss();
        for (int i = 0; i < 30; i++) assertEquals(0, hiss.tick(0.5), "bumps are silent");
        double landing = hiss.tick(3.5);
        assertTrue(landing > 0.5, "a hard landing hisses loud");
        for (int i = 0; i < SuspensionHiss.COOLDOWN_TICKS - 1; i++) assertEquals(0, hiss.tick(3.5), "cooldown");
        assertTrue(hiss.tick(1.5) > 0 && hiss.tick(1.5) == 0);
        SuspensionHiss rebound = new SuspensionHiss();
        double snap = rebound.tick(-3);
        assertTrue(snap > 0 && snap < landing, "the snap back on take-off is quieter than a landing");
        assertEquals(0, new SuspensionHiss().tick(Double.NaN));
    }

    // ---- tricks ----

    @Test
    void trickSoundsMatchByName() {
        assertEquals(TrickSounds.Kind.BARSPIN, TrickSounds.of("BARSPIN"));
        assertEquals(TrickSounds.Kind.TAILWHIP, TrickSounds.of("TAILWHIP"));
        assertEquals(TrickSounds.Kind.HEEL_CLICK, TrickSounds.of("HEELCLICKER"));
        assertEquals(TrickSounds.Kind.HEEL_CLICK, TrickSounds.of("heel_clicker"));
        assertEquals(TrickSounds.Kind.HEEL_CLICK, TrickSounds.of("HEEL_CLICK"));
        assertNull(TrickSounds.of("NO_HANDER"));
        assertNull(TrickSounds.of("NONE"));
        assertNull(TrickSounds.of(null));
    }
}
