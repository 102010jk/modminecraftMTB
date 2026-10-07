package com.descentmtb.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Water drag/buoyancy, fakie landings and the extra collision probes. */
class WaterAndLandingTest {
    /** Flat ground at y=64 with a water surface {@code depth} metres above it everywhere. */
    private static Terrain lake(double depth) {
        Terrain ground = TestTerrains.flat(64, Terrain.Surface.DIRT);
        return new Terrain() {
            public boolean ground(double x, double z, double top, double bottom, GroundHit out) { return ground.ground(x, z, top, bottom, out); }
            public boolean solidAt(double x, double y, double z) { return ground.solidAt(x, y, z); }
            public double waterSurface(double x, double z, double top, double bottom) {
                double s = 64 + depth;
                return s >= bottom ? s : Double.NaN;
            }
        };
    }

    private static double coast(Terrain t, double seconds) {
        BikeSim s = new BikeSim(new BikeParams(), t);
        s.place(0, 64, 0, 0);
        s.vel = s.riderVel = new V3(0, 0, 16);
        for (int i = 0; i < seconds * 20; i++) s.tick(Controls.NONE, .05);
        assertFalse(s.bailed);
        return s.speed();
    }

    @Test void lakeBottomIsNoRoad() {
        double dry = coast(lake(-5), 2), deep = coast(lake(1.2), 2);
        assertTrue(dry > 12, "dry coast " + dry);
        assertTrue(deep < 4, "deep water must stop a 60 km/h bike within two seconds: " + deep);
    }

    @Test void shallowFordStillRides() {
        double ford = coast(lake(0.2), 2);
        assertTrue(ford > 6 && ford < coast(lake(-5), 2), "ankle-deep water brakes but does not stop: " + ford);
    }

    @Test void pedallingThroughAShallowFordKeepsMoving() {
        BikeSim s = new BikeSim(new BikeParams(), lake(0.25));
        s.place(0, 64, 0, 0);
        for (int i = 0; i < 100; i++) s.tick(new Controls(0, 0, 1, 0, 0, 0, false, 0, 0), .05);
        assertTrue(s.speed() > 2, "can still pedal across: " + s.speed());
    }

    @Test void landingBackwardsRollsAwayFakie() {
        BikeSim s = new BikeSim(new BikeParams(), TestTerrains.flat(64, Terrain.Surface.DIRT));
        s.place(0, 64, 0, Math.PI);           // facing -z ...
        s.pos = s.pos.add(new V3(0, 1.0, 0));
        s.riderPos = s.riderPos.add(new V3(0, 1.0, 0));
        s.vel = s.riderVel = new V3(0, -2, 6); // ... while flying +z: a 180 that stopped at 180
        s.airborne = true;
        for (int i = 0; i < 40; i++) s.tick(Controls.NONE, .05);
        assertFalse(s.bailed, "a clean fakie landing is not a sideways crash");
    }
}
