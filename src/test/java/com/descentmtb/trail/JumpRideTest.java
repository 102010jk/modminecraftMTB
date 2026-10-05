package com.descentmtb.trail;

import com.descentmtb.entity.BikeType;
import com.descentmtb.physics.BikeSim;
import com.descentmtb.physics.Controls;
import com.descentmtb.physics.V3;
import com.descentmtb.trail.JumpProfiles.Params;
import com.descentmtb.trail.JumpProfiles.Type;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleBinaryOperator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rides jumps of the jump builder headless: the exact height functions of {@link JumpProfiles}, sampled at the block
 * corners and bilinear inside each block like the real shaped blocks, three blocks wide on flat ground, ridden
 * straight along their centre line with the bike physics.
 */
class JumpRideTest {
    static final double DT = 0.05;
    static final double BASE = 64;
    /** Where the first jump starts (x of the start edge); the rider starts 30 m before it. */
    static final double START = 40;

    /** One feature of a test line: a profile starting {@code at} metres along x. */
    record Piece(double at, Params params) {}

    /** The ground with the pieces on it: 3 blocks wide (z = -1 .. 2), flat ground around. */
    static DoubleBinaryOperator line(List<Piece> pieces) {
        return (x, z) -> {
            if (z < -1 || z > 2) {
                return BASE;
            }
            double h = 0;
            for (Piece piece : pieces) {
                double u = x - piece.at;
                if (u >= 0 && u <= piece.params.total()) {
                    h = Math.max(h, piece.params.heightAt(u));
                }
            }
            return BASE + h;
        };
    }

    record Result(boolean bailed, String why, double reached, List<double[]> flights, double approachSpeed, double lipSpeed) {
        String summary() {
            StringBuilder s = new StringBuilder();
            for (double[] f : flights) {
                s.append(String.format(Locale.ROOT, " [%.1f s from %.1f to %.1f m]", f[0], f[1], f[2]));
            }
            return String.format(Locale.ROOT, "bail=%s %s | reached x=%.1f | speed %.1f m/s before the jump, %.1f m/s at the lip | flights:%s",
                    bailed, why, reached, approachSpeed, lipSpeed, s);
        }
    }

    /**
     * Rides from {@code START - 15} along z = 0.5, rolling in at {@code target} m/s (as if from a run-in) and pedalling
     * to hold it until {@code coastFrom}, coasting after it, for {@code distance} metres or until a bail.
     */
    static Result ride(String name, DoubleBinaryOperator height, double target, double coastFrom, double lipX, double distance) {
        BikeSim sim = new BikeSim(BikeType.ENDURO.params(), new PumpTrackRideTest.GridTerrain(height));
        double x0 = START - 15;
        sim.place(x0, height.applyAsDouble(x0, .5), .5, Math.atan2(-1, 0));
        sim.vel = sim.forward().mul(target);
        sim.riderVel = sim.vel;
        List<double[]> flights = new ArrayList<>();
        double airTime = 0, takeOff = 0, lipSpeed = 0, approachSpeed = 0;
        boolean wasAir = false;
        for (int i = 0; i < 2000 && !sim.bailed && sim.pos.x < x0 + distance; i++) {
            V3 fwd = sim.forward().horizontal().normalize(), right = sim.rightAxis().horizontal().normalize();
            V3 want = new V3(sim.pos.x + 5 - sim.pos.x, 0, .5 - sim.pos.z).normalize();
            float steer = (float) Math.max(-1, Math.min(1, Math.atan2(want.dot(right), want.dot(fwd)) * 2.2));
            double speed = sim.vel.horizontalLength();
            float pedal = sim.pos.x < coastFrom && speed < target ? 1 : 0;
            if (Math.abs(sim.pos.x - lipX) < .3) {
                lipSpeed = sim.speed();
            }
            if (Math.abs(sim.pos.x - (START - 1)) < .3) {
                approachSpeed = sim.speed();
            }
            sim.tick(new Controls(steer, 0, pedal, 0, 0, 0, false, 0, 0), DT);
            sim.events.clear();
            if (sim.airborne) {
                if (!wasAir) {
                    takeOff = sim.pos.x;
                }
                airTime += DT;
            } else if (wasAir) {
                if (airTime >= .25) {
                    flights.add(new double[]{airTime, takeOff, sim.pos.x});
                }
                airTime = 0;
            }
            wasAir = sim.airborne;
        }
        Result r = new Result(sim.bailed, sim.bailReason, sim.pos.x, flights, approachSpeed, lipSpeed);
        System.out.printf(Locale.ROOT, "[jump] %-22s %s%n", name, r.summary());
        return r;
    }

    static final Params KICKER = new Params(Type.KICKER, 3, 3, 1.5, 35, 3, 5);
    static final Params LANDING = new Params(Type.LANDING, 6, 3, 1.5, 35, 3, 5);

    /**
     * A 3 block kicker, 1.5 m high with a 35 degree lip, a 3 m gap and a 6 m LANDING of the same height: ridden at
     * 36 to 40 km/h (the sensible speed for that kicker), the rider leaves at the lip, clears the gap, lands on the
     * landing and rides away.
     */
    @Test
    void aThreeBlockKickerLandsOnALanding() {
        double gap = 3;
        double landingAt = START + KICKER.total() + gap;
        var height = line(List.of(new Piece(START, KICKER), new Piece(landingAt, LANDING)));
        for (double speed : new double[]{10, 11}) {
            Result r = ride("kicker_landing_" + (int) speed, height, speed, START - 3, START + 3, 70);
            assertFalse(r.bailed, speed + " m/s: bailed: " + r.why);
            assertFalse(r.flights.isEmpty(), speed + " m/s: the kicker launches the rider");
            double[] flight = r.flights.get(0);
            // the bike's centre is about half a wheelbase past the lip when the rear wheel leaves it
            assertTrue(flight[1] > START + 2 && flight[1] < START + 4.2, speed + " m/s: takes off at the lip: " + flight[1]);
            assertTrue(flight[2] > landingAt, speed + " m/s: clears the gap, lands at " + flight[2]);
            assertTrue(flight[2] < landingAt + LANDING.total(), speed + " m/s: lands on the landing, not beyond it: " + flight[2]);
            assertTrue(r.reached > landingAt + LANDING.total() + 10, speed + " m/s: rides away from the landing");
        }
    }

    @Test
    void aTableTopIsClearedOrLandedOn() {
        Params table = new Params(Type.TABLE, 3, 3, 1.25, 30, 4, 6);
        var height = line(List.of(new Piece(START, table)));
        for (double speed : new double[]{6, 8, 10, 12}) {
            Result r = ride("table_" + (int) speed, height, speed, START - 3, START + 3, 70);
            assertFalse(r.bailed, speed + " m/s: bailed: " + r.why);
            assertTrue(r.reached > START + table.total() + 10, speed + " m/s: rides past the table");
        }
    }

    @Test
    void aRollerCanBeRolled() {
        var roller = line(List.of(new Piece(START, new Params(Type.ROLLER, 6, 3, 1, 35, 3, 5))));
        Result r = ride("roller", roller, 6, START - 3, START, 60);
        assertFalse(r.bailed, "roller bailed: " + r.why);
    }
}
