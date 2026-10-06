package com.descentmtb.physics;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

class RideTransitionsTest {
    private BikeSim bike() {
        BikeSim sim = new BikeSim(new BikeParams(), TestTerrains.flat(64, Terrain.Surface.DIRT));
        sim.place(0, 64, 0, 0);
        return sim;
    }

    private void field(BikeSim sim, String name, Object value) throws Exception {
        Field f = BikeSim.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(sim, value);
    }

    private void touchdown(BikeSim sim, double riderSpeed, double deckSpeed) throws Exception {
        sim.airTime = .7;
        sim.vel = new V3(0, 0, 8);
        sim.front.contact = true;
        sim.front.normal = V3.Y;
        sim.front.hit.velocity = new V3(0, deckSpeed, 0);
        field(sim, "lastAirVel", new V3(0, riderSpeed, 8));
        Method m = BikeSim.class.getDeclaredMethod("onTouchdown");
        m.setAccessible(true);
        m.invoke(sim);
    }

    @Test void softLandingOnDescendingDeckDoesNotBail() throws Exception {
        BikeSim sim = bike();
        touchdown(sim, -14, -10);
        assertFalse(sim.bailed, sim.bailReason);
        assertEquals(4, sim.events.getLast().value(), 1e-9);
    }

    @Test void risingDeckCanCauseHardLandingDespiteSlowWorldFall() throws Exception {
        BikeSim sim = bike();
        touchdown(sim, -5, 10);
        assertTrue(sim.bailed);
        assertEquals(15, sim.events.getLast().value(), 1e-9);
    }

    @Test void frameContactDependsOnRelativeMotionOfDeck() throws Exception {
        V3 shift = new V3(8, -10, 0);
        BikeSim stationary = bodyContact(V3.ZERO), moving = bodyContact(shift);
        assertEquals(0, moving.vel.sub(shift).sub(stationary.vel).length(), 1e-9);
        assertEquals(0, moving.omega.sub(stationary.omega).length(), 1e-9);
    }

    private BikeSim bodyContact(V3 deckVelocity) throws Exception {
        Terrain terrain = new Terrain() {
            @Override public boolean ground(double x, double z, double top, double bottom, GroundHit out) {
                if (top < 64 || bottom > 64) return false;
                out.set(64, V3.Y, Surface.WOOD);
                out.velocity = deckVelocity;
                return true;
            }
            @Override public boolean solidAt(double x, double y, double z) { return y < 64; }
        };
        BikeSim sim = new BikeSim(new BikeParams(), terrain);
        sim.place(0, 64, 0, 0);
        sim.riderless = true;
        sim.pos = new V3(0, 63.9, 0);
        sim.vel = new V3(0, -4, 3).add(deckVelocity);
        Method m = BikeSim.class.getDeclaredMethod("bodyContacts", double.class);
        m.setAccessible(true);
        m.invoke(sim, .005);
        return sim;
    }

    @Test void resetMatchesFreshBikeIncludingFirstTick() {
        BikeSim reused = bike();
        reused.vel = reused.riderVel = new V3(0, 0, 7);
        for (int i = 0; i < 30; i++) reused.tick(new Controls(1, -1, 1, 0, -1, 0, false, 0, 0), .05);
        reused.place(0, 64, 0, 0);
        BikeSim fresh = bike();
        assertEquals(fresh.riderUp, reused.riderUp);
        assertEquals(fresh.crankRate, reused.crankRate);
        assertEquals(fresh.front.compression, reused.front.compression);
        assertEquals(fresh.rear.spinRate, reused.rear.spinRate);
        assertEquals(fresh.front.contact, reused.front.contact);
        for (int i = 0; i < 10; i++) {
            reused.tick(Controls.NONE, .05);
            fresh.tick(Controls.NONE, .05);
            assertEquals(0, reused.pos.sub(fresh.pos).length(), 1e-9);
            assertEquals(0, reused.vel.sub(fresh.vel).length(), 1e-9);
            assertEquals(fresh.airborne, reused.airborne);
        }
    }

    @Test void landingAssistDoesNotOverrideFullSteeringAndManual() throws Exception {
        assertSameRideWithTimer(true);
    }

    @Test void disabledLandingAssistDoesNotDampMotion() throws Exception {
        assertSameRideWithTimer(false);
    }

    private void assertSameRideWithTimer(boolean input) throws Exception {
        BikeSim with = bike(), without = bike();
        for (BikeSim sim : new BikeSim[]{with, without}) {
            sim.vel = sim.riderVel = new V3(0, 0, 7);
            for (int i = 0; i < 5; i++) sim.tick(Controls.NONE, .05);
            if (!input) sim.p.landingAssistRate = 0;
        }
        field(with, "landAssistTimer", .18);
        Controls c = input ? new Controls(1, -1, 0, 0, 0, 0, false, 0, 0) : Controls.NONE;
        for (int i = 0; i < 3; i++) {
            with.tick(c, .05);
            without.tick(c, .05);
            assertEquals(without.yaw, with.yaw, 1e-9);
            assertEquals(without.pitch, with.pitch, 1e-9);
        }
    }
}
