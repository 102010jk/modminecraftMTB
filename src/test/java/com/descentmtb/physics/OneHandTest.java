package com.descentmtb.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Ringing the bell one-handed: the pure rules, and the sim consequences. */
class OneHandTest {
    static final double FLOOR = 64;

    @Test void landingRuleNeedsAirTimeAndAHardEnoughImpact() {
        BikeParams p = new BikeParams();
        double limit = OneHand.LANDING_FRACTION * p.bailImpactSpeed;
        assertTrue(OneHand.landingBails(0.6, limit + .5, p.bailImpactSpeed));
        assertFalse(OneHand.landingBails(0.6, limit - .5, p.bailImpactSpeed), "soft landing is fine");
        assertFalse(OneHand.landingBails(0.2, limit + 3, p.bailImpactSpeed), "a hop is not a jump");
        assertTrue(limit < p.bailImpactSpeed, "one hand is less forgiving than two");
    }

    @Test void bumpRuleIsStricterThanTheCrashSpeed() {
        BikeParams p = new BikeParams();
        assertTrue(OneHand.bumpBails(p.crashSpeed * OneHand.BUMP_FRACTION + .1, 0, p.crashSpeed));
        assertFalse(OneHand.bumpBails(1.0, 0, p.crashSpeed), "ordinary roughness");
        assertTrue(OneHand.bumpBails(0, OneHand.BOTTOM_OUT + .01, p.crashSpeed), "bottoming out");
        assertTrue(OneHand.BUMP_FRACTION < 1);
    }

    @Test void handComesOffAndBack() {
        assertEquals(0, OneHand.reach(0), 1e-9);
        assertEquals(1, OneHand.reach(OneHand.TIME / 2), 1e-9);
        assertEquals(0, OneHand.reach(OneHand.TIME), 1e-9);
        assertTrue(OneHand.reach(0.05) > 0 && OneHand.reach(0.05) < 1);
        assertEquals(0, OneHand.squeeze(0.0), 1e-9);
        assertTrue(OneHand.squeeze(0.17) > 0.95, "peak squeeze mid pulse");
        assertEquals(0, OneHand.squeeze(0.4), 1e-9);
    }

    static BikeSim dropped(double height, boolean ring, Terrain t) {
        BikeParams p = new BikeParams();
        BikeSim sim = new BikeSim(p, t);
        sim.place(0, FLOOR, 0, 0);
        V3 lift = new V3(0, height, 0);
        sim.pos = sim.pos.add(lift);
        sim.riderPos = sim.riderPos.add(lift);
        sim.vel = sim.riderVel = new V3(0, 0, 5);
        boolean rang = false;
        for (int i = 0; i < 60 && !sim.bailed; i++) {
            // ring when the wheels are about to touch down (landing ~0.1 s later)
            if (ring && !rang && sim.airborne && sim.wheelClearance() < 0.9) rang = sim.ringBell();
            sim.tick(Controls.NONE, 0.05);
            sim.events.clear();
        }
        return sim;
    }

    @Test void ringingBeforeAMediumLandingThrowsTheRider() {
        Terrain flat = TestTerrains.flat(FLOOR, Terrain.Surface.DIRT);
        BikeSim two = dropped(3.2, false, flat);
        assertFalse(two.bailed, "two hands ride out a ~8 m/s landing: " + two.bailReason);
        BikeSim one = dropped(3.2, true, flat);
        assertTrue(one.bailed, "one hand does not");
        assertEquals("rang the bell one-handed", one.bailReason);
    }

    @Test void oneHandedLandingOnAnAirbagIsSafe() {
        BikeSim sim = dropped(3.2, true, TestTerrains.flat(FLOOR, Terrain.Surface.AIRBAG));
        assertFalse(sim.bailed, sim.bailReason);
    }

    @Test void ringingOnSmoothGroundIsHarmless() {
        BikeSim sim = new BikeSim(new BikeParams(), TestTerrains.flat(FLOOR, Terrain.Surface.DIRT));
        sim.place(0, FLOOR, 0, 0);
        sim.vel = sim.riderVel = new V3(0, 0, 8);
        assertTrue(sim.ringBell());
        assertFalse(sim.ringBell(), "second press while the first is on");
        for (int i = 0; i < 40; i++) {
            sim.tick(new Controls(0, 0, 1, 0, 0, 0, false, 0, 0), 0.05);
            sim.events.clear();
        }
        assertFalse(sim.bailed, sim.bailReason);
        assertEquals(0, sim.oneHandTimer, 1e-9, "the hand is back on the grip");
        assertTrue(sim.ringBell(), "can ring again after the cooldown");
    }

    @Test void aStepWhileOneHandedThrowsTheRider() {
        // a 0.45 m high vertical-ish kerb across the track at z = 6, ridden at 9 m/s
        Terrain step = TestTerrains.fn((x, z) -> z < 6 ? FLOOR : FLOOR + 0.45, Terrain.Surface.DIRT);
        BikeSim control = new BikeSim(new BikeParams(), step);
        control.place(0, FLOOR, 0, 0);
        control.vel = control.riderVel = new V3(0, 0, 9);
        for (int i = 0; i < 40; i++) { control.tick(Controls.NONE, 0.05); control.events.clear(); }
        BikeSim one = new BikeSim(new BikeParams(), step);
        one.place(0, FLOOR, 0, 0);
        one.vel = one.riderVel = new V3(0, 0, 9);
        for (int i = 0; i < 40 && !one.bailed; i++) {
            if (one.pos.z > 4.2) one.ringBell();
            one.tick(Controls.NONE, 0.05);
            one.events.clear();
        }
        System.out.println("step: control bailed=" + control.bailed + " (" + control.bailReason + "), one-handed bailed=" + one.bailed + " (" + one.bailReason + ")");
        assertFalse(control.bailed, "two hands take the kerb: " + control.bailReason);
        assertTrue(one.bailed, "one hand does not");
    }
}
