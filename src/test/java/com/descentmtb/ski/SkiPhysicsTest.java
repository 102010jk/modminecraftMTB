package com.descentmtb.ski;

import com.descentmtb.entity.BikeType;
import com.descentmtb.physics.BikeParams;
import com.descentmtb.physics.BikeSim;
import com.descentmtb.physics.BlockTerrain;
import com.descentmtb.physics.Controls;
import com.descentmtb.physics.Terrain;
import com.descentmtb.physics.V3;
import org.junit.jupiter.api.Test;

import java.util.function.DoubleBinaryOperator;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** Headless ski physics: glide on snow, grinding on rock, carving, pole push, tuck, landings, riderless skis. */
class SkiPhysicsTest {

    // ------------------------------------------------------------------ helpers

    static BikeSim skis(SkiBrand brand, Terrain t) {
        BikeParams p = brand.type.params();
        SkiPhysics.tune(p, brand);
        BikeSim s = new BikeSim(p, t);
        s.bikeType = brand.type;
        return s;
    }

    static Controls ctl(double steer, double lean, double pedal, double brake, double body) {
        return new Controls((float) steer, (float) lean, (float) pedal, (float) brake, (float) body, 0, false, 0, 0);
    }

    /** Smooth analytic ground: height h(x, z), surface by position. Everything below is solid. */
    static Terrain field(DoubleBinaryOperator h, Function<V3, Terrain.Surface> surface) {
        return new Terrain() {
            @Override
            public boolean ground(double x, double z, double yTop, double yBottom, GroundHit out) {
                double y = h.applyAsDouble(x, z);
                if (y > yTop || y < yBottom) return false;
                double e = 0.02;
                double dx = (h.applyAsDouble(x + e, z) - h.applyAsDouble(x - e, z)) / (2 * e);
                double dz = (h.applyAsDouble(x, z + e) - h.applyAsDouble(x, z - e)) / (2 * e);
                out.set(y, new V3(-dx, 1, -dz).normalize(), surface.apply(new V3(x, y, z)));
                return true;
            }

            @Override
            public boolean solidAt(double x, double y, double z) {
                return y < h.applyAsDouble(x, z) - 0.05;
            }
        };
    }

    static Terrain flat(Terrain.Surface s) {
        return field((x, z) -> 64, q -> s);
    }

    /** Flat ground at y = 64 whose surface depends on z only. */
    static Terrain strip(Function<Double, Terrain.Surface> byZ) {
        return field((x, z) -> 64, q -> byZ.apply(q.z));
    }

    /** A straight slope falling toward +z at the given angle. */
    static Terrain slope(double degrees, Terrain.Surface s) {
        double k = Math.tan(Math.toRadians(degrees));
        return field((x, z) -> 2000 - k * z, q -> s);
    }

    /** Full-block world through the real smoother (like Minecraft): integer column tops. */
    static Terrain blocks(java.util.function.IntBinaryOperator heights, Terrain.Surface s) {
        return new BlockTerrain(new BlockTerrain.Columns() {
            @Override
            public double top(int x, int z, double yTop, double yBottom) {
                double t = heights.applyAsInt(x, z);
                if (t > yTop) return Double.NaN;
                return t >= yBottom ? t : Double.NaN;
            }

            @Override
            public Terrain.Surface surface(int x, int z, double topY) {
                return s;
            }

            @Override
            public boolean solid(double x, double y, double z) {
                return y < heights.applyAsInt((int) Math.floor(x), (int) Math.floor(z));
            }
        });
    }

    static void launch(BikeSim s, double speed) {
        s.vel = s.riderVel = s.forward().mul(speed);
    }

    static double heading(V3 v) {
        return Math.atan2(-v.x, v.z);
    }

    // ------------------------------------------------------------------ presets

    @Test void presetsAreSkis() {
        for (SkiBrand b : SkiBrand.values()) {
            BikeParams p = b.type.params();
            SkiPhysics.tune(p, b);
            assertTrue(p.ski, b.id);
            assertFalse(p.wallRides, b.id + ": no wallrides on skis");
            assertEquals(0, p.manualAssist, b.id + ": no manual assist");
            assertEquals(-0.52, p.axleDrop - p.wheelRadius, 1e-9, b.id + ": contact 0.52 m below the frame COM");
            assertEquals(b.radiusM, p.skiSidecut, 1e-9);
            assertEquals(b.twinTip(), p.skiSwitch, b.id + ": only twin tips ride switch");
        }
        BikeParams race = BikeType.SKI_RACE.params(), free = BikeType.SKI_FREESTYLE.params();
        assertTrue(race.softSpeedCap >= 33 && race.softSpeedCap <= 36);
        assertTrue(free.softSpeedCap >= 22 && free.softSpeedCap <= 25);
        assertTrue(free.spinRate > race.spinRate, "freestyle spins faster");
        // bikes keep their own physics
        assertFalse(BikeType.ENDURO.params().ski);
    }

    // ------------------------------------------------------------------ blocks as a slope

    @Test void snowStaircaseIsASmoothAcceleratingSlope() {
        // 1-block steps every 2 blocks (a 27° hill in Minecraft blocks) and every 3 blocks (18°) down toward +z
        Terrain steep = blocks((x, z) -> 100 - Math.max(0, Math.min(30, Math.floorDiv(z - 4, 2))), Terrain.Surface.SNOW);
        Terrain gentle = blocks((x, z) -> 100 - Math.max(0, Math.min(30, Math.floorDiv(z - 4, 3))), Terrain.Surface.SNOW);
        for (SkiBrand brand : new SkiBrand[]{SkiBrand.ATOMIC_REDSTER_G9, SkiBrand.ARMADA_ARV_96})
        for (Terrain stairs : new Terrain[]{steep, gentle}) {
            BikeSim s = skis(brand, stairs);
            s.place(0.5, 100, 1.5, 0);
            launch(s, 3);
            double air = 0, speedAt20 = -1, minAfter10 = 99;
            int t = 0;
            for (; t < 400 && s.pos.z < 62; t++) {
                s.tick(Controls.NONE, 0.05);
                s.events.clear();
                if (s.airborne) air += 0.05;
                if (speedAt20 < 0 && s.pos.z > 20) speedAt20 = s.speed();
                if (s.pos.z > 10) minAfter10 = Math.min(minAfter10, s.speed());
                assertFalse(s.bailed, brand.id + " bailed on the staircase: " + s.bailReason);
            }
            System.out.printf("staircase %s %s: z=%.1f t=%.1fs v20=%.1f end=%.1f m/s air=%.2fs scrape=%.2f%n", brand.id, stairs == steep ? "1x2" : "1x3",
                    s.pos.z, t * 0.05, speedAt20, s.speed(), air, s.skiScrapeMeter);
            assertTrue(s.pos.z >= 62, brand.id + " must slide all the way down, stopped at z=" + s.pos.z);
            assertTrue(s.speed() > speedAt20 + 3, brand.id + " must keep accelerating down the steps");
            assertTrue(minAfter10 > 4, brand.id + " must not stall on a step");
            assertTrue(air < 0.15, brand.id + " must stay on the snow, airborne " + air + " s");
            assertEquals(0, s.skiScrapeMeter, 1e-9, "snow never scrapes");
        }
    }

    @Test void diagonalStaircaseRidesAsOneSlope() {
        // steps in both directions (a hillside of full blocks, falling toward +z and +x): the edges cross the skis at
        // an angle, the worst case for the smoother
        Terrain hill = blocks((x, z) -> 100 - Math.max(0, Math.min(40, Math.floorDiv(z - 4, 2)))
                - Math.max(0, Math.min(20, Math.floorDiv(x, 4))), Terrain.Surface.SNOW);
        BikeSim s = skis(SkiBrand.ATOMIC_REDSTER_G9, hill);
        s.place(0.5, 100, 1.5, 0);
        launch(s, 3);
        double airborne = 0;
        for (int t = 0; t < 300 && s.pos.z < 70; t++) {
            s.tick(Controls.NONE, 0.05);
            s.events.clear();
            if (s.airborne) airborne += 0.05;
            assertFalse(s.bailed, "bailed on the hillside: " + s.bailReason);
        }
        System.out.printf("diagonal staircase: z=%.1f airborne %.2f s%n", s.pos.z, airborne);
        assertTrue(s.pos.z >= 70, "slides down the hillside, stopped at z=" + s.pos.z);
        assertTrue(airborne < 0.3, "stays on the snow over diagonal steps, airborne " + airborne + " s");
    }

    @Test void linkedTurnsDownTheStaircase() {
        Terrain stairs = blocks((x, z) -> 100 - Math.max(0, Math.min(60, Math.floorDiv(z - 4, 2))), Terrain.Surface.SNOW);
        for (SkiBrand brand : new SkiBrand[]{SkiBrand.ROSSIGNOL_HERO_ST, SkiBrand.FACTION_PRODIGY_2}) {
            BikeSim s = skis(brand, stairs);
            s.place(0.5, 100, 1.5, 0);
            launch(s, 3);
            double top = 0;
            for (int i = 0; i < 200; i++) {
                // short-radius turns: 1 s each way after a straight run-in (the first one half as long)
                double steer = i < 40 ? 0 : 0.6 * Math.signum(Math.sin((i - 30) * 0.05 * Math.PI));
                s.tick(ctl(steer, 0, 0, 0, 0), 0.05);
                s.events.clear();
                top = Math.max(top, s.speed());
                assertFalse(s.bailed, brand.id + " linked turns: " + s.bailReason);
            }
            System.out.printf("linked turns %s: z=%.1f x=%.1f top=%.1f m/s%n", brand.id, s.pos.z, s.pos.x, top);
            assertTrue(s.pos.z > 60, brand.id + " keeps going down the fall line");
            assertTrue(Math.abs(s.pos.x - 0.5) < 25, brand.id + " turns both ways");
        }
    }

    @Test void runsOutOntoTheFlatAtTheBottomOfTheStaircase() {
        Terrain stairs = blocks((x, z) -> 100 - Math.max(0, Math.min(12, Math.floorDiv(z - 4, 2))), Terrain.Surface.SNOW);
        BikeSim s = skis(SkiBrand.FISCHER_RC4_WC, stairs);
        s.place(0.5, 100, 1.5, 0);
        launch(s, 3);
        for (int t = 0; t < 600; t++) {
            s.tick(Controls.NONE, 0.05);
            s.events.clear();
        }
        System.out.printf("runout: z=%.1f v=%.2f bail=%s%n", s.pos.z, s.speed(), s.bailReason);
        assertFalse(s.bailed, s.bailReason);
        assertTrue(s.pos.z > 40, "glides far out onto the flat");
    }

    // ------------------------------------------------------------------ bare ground

    @Test void snowOntoRockScrapesAndBails() {
        Terrain t = strip(z -> z < 20 ? Terrain.Surface.SNOW : Terrain.Surface.ROCK);
        BikeSim s = skis(SkiBrand.ATOMIC_REDSTER_G9, t);
        s.place(0.5, 64, 5, 0);
        launch(s, 14);
        double onRock = -1, maxScrape = 0;
        for (int i = 0; i < 100 && !s.bailed; i++) {
            s.tick(Controls.NONE, 0.05);
            s.events.clear();
            if (onRock < 0 && s.front.contact && s.front.surface == Terrain.Surface.ROCK) onRock = i * 0.05;
            maxScrape = Math.max(maxScrape, s.skiScrape);
        }
        System.out.printf("snow->rock: bailed=%s (%s) v=%.1f meter=%.2f scrape=%.2f%n", s.bailed, s.bailReason,
                s.speed(), s.skiScrapeMeter, maxScrape);
        assertTrue(onRock >= 0, "reached the rock");
        assertTrue(s.bailed, "a sustained grind over rock is a fall");
        assertEquals("skis caught on rock", s.bailReason);
        assertTrue(s.speed() < 12, "the grind brakes hard: " + s.speed());
        assertTrue(maxScrape > 0.5, "scrape read-out for sound/particles");
    }

    @Test void sustainedGrindBailsWithinAboutASecondAtSpeed() {
        Terrain t = strip(z -> z < 20 ? Terrain.Surface.SNOW : Terrain.Surface.GRAVEL);
        BikeSim s = skis(SkiBrand.LINE_CHRONIC_101, t);
        s.place(0.5, 64, 5, 0);
        launch(s, 16);
        double enter = -1, bail = -1;
        for (int i = 0; i < 100 && !s.bailed; i++) {
            s.tick(Controls.NONE, 0.05);
            s.events.clear();
            if (enter < 0 && s.rear.contact && s.rear.surface == Terrain.Surface.GRAVEL) enter = i * 0.05;
            if (s.bailed) bail = i * 0.05;
        }
        System.out.printf("grind: on gravel at %.2f s, bail at %.2f s%n", enter, bail);
        assertTrue(s.bailed);
        assertTrue(bail - enter > 0.4 && bail - enter < 1.6, "falls after a short grind: " + (bail - enter) + " s");
    }

    @Test void singleRockBlockAtSpeedDoesNotBail() {
        Terrain t = strip(z -> z >= 20 && z < 21 ? Terrain.Surface.ROCK : Terrain.Surface.SNOW);
        BikeSim s = skis(SkiBrand.ROSSIGNOL_HERO_ST, t);
        s.place(0.5, 64, 5, 0);
        launch(s, 14);
        double peak = 0;
        for (int i = 0; i < 60; i++) {
            s.tick(Controls.NONE, 0.05);
            s.events.clear();
            peak = Math.max(peak, s.skiScrapeMeter);
        }
        System.out.printf("rock block: v=%.1f peak meter=%.2f end meter=%.2f%n", s.speed(), peak, s.skiScrapeMeter);
        assertFalse(s.bailed, s.bailReason);
        assertTrue(peak > 0.05 && peak < 0.5, "a brief scrape: " + peak);
        assertEquals(0, s.skiScrapeMeter, 1e-9, "drains back on the snow");
        assertTrue(s.speed() > 11, "loses a little speed only: " + s.speed());
    }

    @Test void slowOntoRockStopsAndCanWalkOff() {
        Terrain t = strip(z -> z < 10 ? Terrain.Surface.SNOW : Terrain.Surface.ROCK);
        BikeSim s = skis(SkiBrand.ARMADA_ARV_96, t);
        s.place(0.5, 64, 5, 0);
        launch(s, 4);
        for (int i = 0; i < 60; i++) {
            s.tick(Controls.NONE, 0.05);
            s.events.clear();
        }
        assertFalse(s.bailed, s.bailReason);
        assertTrue(s.speed() < 0.1, "scraped to a stop on the rock: " + s.speed());
        double z0 = s.pos.z, maxSpeed = 0;
        for (int i = 0; i < 160; i++) {
            s.tick(ctl(0, 0, 1, 0, 0), 0.05);
            s.events.clear();
            maxSpeed = Math.max(maxSpeed, s.speed());
        }
        System.out.printf("walk: %.1f m, max %.2f m/s, meter %.2f%n", s.pos.z - z0, maxSpeed, s.skiScrapeMeter);
        assertFalse(s.bailed, "walking over rock never fills the scrape meter: " + s.bailReason);
        assertTrue(s.skiWalking);
        assertTrue(s.pos.z - z0 > 4, "walks on");
        assertTrue(maxSpeed < 1.5, "at walking pace: " + maxSpeed);
    }

    @Test void landingOnRockBailsWhereSnowIsForgiving() {
        for (Terrain.Surface ground : new Terrain.Surface[]{Terrain.Surface.SNOW, Terrain.Surface.ROCK}) {
            BikeSim s = skis(SkiBrand.FISCHER_RC4_WC, flat(ground));
            s.place(0.5, 64, 0, 0);
            s.pos = s.pos.add(new V3(0, 4.2, 0));
            s.riderPos = s.riderPos.add(new V3(0, 4.2, 0));
            s.vel = s.riderVel = new V3(0, 0, 5);
            for (int i = 0; i < 40 && !s.bailed; i++) {
                s.tick(Controls.NONE, 0.05);
                s.events.clear();
            }
            System.out.printf("drop onto %s: bailed=%s %s%n", ground, s.bailed, s.bailReason);
            if (ground == Terrain.Surface.SNOW) assertFalse(s.bailed, "a 4 m drop to flat snow: " + s.bailReason);
            else assertTrue(s.bailed && s.bailReason.startsWith("landed too hard"), "the same onto rock: " + s.bailReason);
        }
    }

    // ------------------------------------------------------------------ grip, carving, brake

    @Test void iceHoldsLessThanSnow() {
        double[] turned = new double[2], stop = new double[2];
        Terrain.Surface[] g = {Terrain.Surface.SNOW, Terrain.Surface.ICE};
        for (int k = 0; k < 2; k++) {
            BikeSim s = skis(SkiBrand.FISCHER_RC4_WC, flat(g[k]));
            s.place(0.5, 64, 0, 0);
            launch(s, 12);
            for (int i = 0; i < 20; i++) { s.tick(ctl(1, 0, 0, 0, 0), 0.05); s.events.clear(); }
            turned[k] = Math.abs(heading(s.vel));
            assertFalse(s.bailed, g[k] + ": " + s.bailReason);

            BikeSim b = skis(SkiBrand.FISCHER_RC4_WC, flat(g[k]));
            b.place(0.5, 64, 0, 0);
            launch(b, 12);
            for (int i = 0; i < 400 && b.speed() > 0.2; i++) { b.tick(ctl(0, 0, 0, 1, 0), 0.05); b.events.clear(); }
            stop[k] = b.pos.z;
            assertFalse(b.bailed, g[k] + " hockey stop: " + b.bailReason);
        }
        System.out.printf("turn in 1 s: snow %.0f°, ice %.0f°; stop: snow %.1f m, ice %.1f m%n",
                Math.toDegrees(turned[0]), Math.toDegrees(turned[1]), stop[0], stop[1]);
        assertTrue(turned[0] > turned[1] * 1.5, "edges bite on snow, skid on ice");
        assertTrue(stop[0] < 16, "a hockey stop on snow from 43 km/h: " + stop[0]);
        assertTrue(stop[1] > stop[0] * 2, "and slides far on ice");
    }

    /** Full-stick turn for {@code ticks}: {heading change (rad), peak skid, end speed, peak carve}. */
    static double[] turn(SkiBrand brand, double speed, int ticks) {
        BikeSim s = skis(brand, flat(Terrain.Surface.SNOW));
        s.place(0.5, 64, 0, 0);
        launch(s, speed);
        double skid = 0, carve = 0;
        for (int i = 0; i < ticks; i++) {
            s.tick(ctl(1, 0, 0, 0, 0), 0.05);
            s.events.clear();
            skid = Math.max(skid, s.skiSkid);
            carve = Math.max(carve, s.skiCarve);
        }
        assertFalse(s.bailed, s.bailReason);
        return new double[]{Math.abs(heading(s.vel)), skid, s.speed(), carve};
    }

    @Test void slalomSkiCarvesTighterThanGiantSlalomSki() {
        // 14 m/s: both carve on their sidecut, the 13 m slalom radius is the tighter arc
        double[] sl = turn(SkiBrand.ROSSIGNOL_HERO_ST, 14, 10), gs = turn(SkiBrand.ATOMIC_REDSTER_G9, 14, 10);
        System.out.printf("14 m/s 0.5 s: SL %.0f° skid %.2f carve %.2f, GS %.0f° skid %.2f carve %.2f%n",
                Math.toDegrees(sl[0]), sl[1], sl[3], Math.toDegrees(gs[0]), gs[1], gs[3]);
        assertTrue(sl[1] < 0.1 && gs[1] < 0.1, "clean carves at speed");
        assertTrue(sl[3] > 0.5 && gs[3] > 0.4, "edged over hard");
        assertTrue(sl[0] > gs[0] * 1.1, "the slalom sidecut turns tighter");
        // 10 m/s: the slalom ski still carves, the GS ski has to skid the same turn and scrubs speed
        sl = turn(SkiBrand.ROSSIGNOL_HERO_ST, 10, 8);
        gs = turn(SkiBrand.ATOMIC_REDSTER_G9, 10, 8);
        System.out.printf("10 m/s 0.4 s: SL %.0f° skid %.2f %.1f m/s, GS %.0f° skid %.2f %.1f m/s%n",
                Math.toDegrees(sl[0]), sl[1], sl[2], Math.toDegrees(gs[0]), gs[1], gs[2]);
        assertTrue(sl[1] < 0.1, "the slalom ski carves cleanly at 10 m/s");
        assertTrue(gs[1] > 0.3, "the GS ski skids");
        assertTrue(sl[2] > gs[2] + 0.5, "carving keeps speed, skidding scrubs it");
    }

    @Test void stepTurnAtAStandstill() {
        BikeSim s = skis(SkiBrand.ARMADA_ARV_96, flat(Terrain.Surface.SNOW));
        s.place(0.5, 64, 0, 0);
        for (int i = 0; i < 20; i++) { s.tick(ctl(1, 0, 0, 0, 0), 0.05); s.events.clear(); }
        assertTrue(s.yaw > Math.toRadians(45), "turns on the spot by stepping: " + Math.toDegrees(s.yaw));
        assertTrue(s.speed() < 0.3);
    }

    @Test void standingAcrossTheSlopeHoldsAndPointingDownGoes() {
        Terrain hill = slope(15, Terrain.Surface.SNOW);
        BikeSim across = skis(SkiBrand.ATOMIC_REDSTER_G9, hill);
        across.place(0.5, 2000, 0, Math.PI / 2);    // facing -x: across the fall line (+z)
        for (int i = 0; i < 60; i++) { across.tick(Controls.NONE, 0.05); across.events.clear(); }
        assertTrue(across.speed() < 0.3, "the edges hold across the slope: " + across.speed());
        BikeSim down = skis(SkiBrand.ATOMIC_REDSTER_G9, hill);
        down.place(0.5, 2000, 0, 0);
        for (int i = 0; i < 60; i++) { down.tick(Controls.NONE, 0.05); down.events.clear(); }
        assertTrue(down.speed() > 5, "pointing down the fall line it glides away: " + down.speed());
    }

    // ------------------------------------------------------------------ propulsion, aero

    @Test void polePushOnlyHelpsAtLowSpeed() {
        BikeSim s = skis(SkiBrand.FISCHER_RC4_WC, flat(Terrain.Surface.SNOW));
        s.place(0.5, 64, 0, 0);
        double at1 = 0;
        for (int i = 0; i < 200; i++) {
            s.tick(ctl(0, 0, 1, 0, 0), 0.05);
            s.events.clear();
            if (i == 29) at1 = s.speed();
        }
        System.out.printf("pole push: %.2f m/s after 1.5 s, %.2f after 10 s%n", at1, s.speed());
        assertTrue(at1 > 2, "a strong push from a standstill");
        assertTrue(s.speed() > 4 && s.speed() < 7, "but no faster than a skating stride");

        BikeSim fast = skis(SkiBrand.FISCHER_RC4_WC, flat(Terrain.Surface.SNOW));
        fast.place(0.5, 64, 0, 0);
        launch(fast, 10);
        for (int i = 0; i < 60; i++) { fast.tick(ctl(0, 0, 1, 0, 0), 0.05); fast.events.clear(); }
        assertTrue(fast.speed() < 10, "poling does nothing at 36 km/h: " + fast.speed());
    }

    @Test void tuckIsFasterThanStandingAndRaceIsFasterThanFreestyle() {
        double[] top = new double[3];
        Object[][] runs = {{SkiBrand.ATOMIC_REDSTER_G9, false}, {SkiBrand.ATOMIC_REDSTER_G9, true},
                {SkiBrand.ARMADA_ARV_96, true}};
        for (int k = 0; k < 3; k++) {
            BikeSim s = skis((SkiBrand) runs[k][0], slope(15, Terrain.Surface.SNOW));
            s.place(0.5, 2000, 0, 0);
            Controls c = (Boolean) runs[k][1] ? ctl(0, 1, 0, 0, -1) : Controls.NONE;
            for (int i = 0; i < 1600; i++) {
                s.tick(c, 0.05);
                s.events.clear();
                top[k] = Math.max(top[k], s.speed());
            }
            assertFalse(s.bailed, s.bailReason);
            if ((Boolean) runs[k][1]) assertTrue(s.skiTuck > 0.8, "full tuck: " + s.skiTuck);
        }
        System.out.printf("15° slope top speed: race standing %.1f, race tuck %.1f, freestyle tuck %.1f m/s%n",
                top[0], top[1], top[2]);
        assertTrue(top[1] > top[0] * 1.15, "the tuck cuts the drag");
        assertTrue(top[1] > 30 && top[1] < 45, "race tuck tops out around the soft cap: " + top[1]);
        assertTrue(top[2] < top[1] - 5, "park skis are slower");
        assertTrue(top[2] < 28, "freestyle soft cap: " + top[2]);
    }

    @Test void neverFasterThanTheNetworkBudgetOnASteepFace() {
        BikeSim s = skis(SkiBrand.ATOMIC_REDSTER_G9, slope(45, Terrain.Surface.ICE));
        s.place(0.5, 2000, 0, 0);
        double top = 0;
        for (int i = 0; i < 1200; i++) {
            s.tick(ctl(0, 1, 0, 0, -1), 0.05);
            s.events.clear();
            top = Math.max(top, s.speed());
        }
        System.out.printf("45° ice tuck: %.1f m/s%n", top);
        assertTrue(top < 48, "MovementBudget allows 50 m/s: " + top);
    }

    // ------------------------------------------------------------------ landings, switch

    @Test void freestyleLandsSwitchRaceSkisDoNot() {
        for (SkiBrand brand : new SkiBrand[]{SkiBrand.FACTION_PRODIGY_2, SkiBrand.ATOMIC_REDSTER_G9}) {
            BikeSim s = skis(brand, flat(Terrain.Surface.SNOW));
            s.p.riskReward = true;
            s.place(0.5, 64, 0, Math.PI);           // facing -z ...
            s.pos = s.pos.add(new V3(0, 1.5, 0));
            s.riderPos = s.riderPos.add(new V3(0, 1.5, 0));
            s.vel = s.riderVel = new V3(0, -1, 7);  // ... flying +z: a 180 landed switch
            s.airborne = true;
            for (int i = 0; i < 60; i++) { s.tick(Controls.NONE, 0.05); s.events.clear(); }
            System.out.printf("switch landing %s: bailed=%s %s%n", brand.id, s.bailed, s.bailReason);
            if (brand.twinTip()) assertFalse(s.bailed, "twin tips ride away switch: " + s.bailReason);
            else assertTrue(s.bailed && s.bailReason.startsWith("landed backwards"), "race tails dig in: " + s.bailReason);
        }
    }

    // ------------------------------------------------------------------ riderless

    @Test void riderlessSkisLieFlatSlideDownSnowAndSettle() {
        // dropped on a steep snow face: they slide away
        BikeSim steep = skis(SkiBrand.ATOMIC_REDSTER_G9, slope(30, Terrain.Surface.SNOW));
        steep.riderless = true;
        steep.place(0.5, 2000, 0, 0);
        for (int i = 0; i < 40; i++) steep.tick(Controls.NONE, 0.05);
        assertTrue(steep.speed() > 2, "slide down a steep snow slope: " + steep.speed());
        assertEquals(0, steep.lean, 0.05, "on their bases");

        // thrown off at speed on the flat / a gentle slope / rock: they stop and sleep flat
        Terrain[] grounds = {flat(Terrain.Surface.SNOW), slope(8, Terrain.Surface.SNOW), flat(Terrain.Surface.ROCK)};
        for (Terrain g : grounds) {
            BikeSim s = skis(SkiBrand.ARMADA_ARV_96, g);
            s.riderless = true;
            s.place(0.5, g == grounds[1] ? 2000 : 64, 0, 0);
            s.lean = 1.0;
            launch(s, 6);
            s.beginCrash();
            int restTicks = 0;
            for (int i = 0; i < 400; i++) {
                s.tick(Controls.NONE, 0.05);
                s.events.clear();
                boolean atRest = s.speed() < 0.05 && s.grounded() && Math.abs(s.lean) < 0.05;
                restTicks = atRest ? restTicks + 1 : 0;
            }
            System.out.printf("riderless: v=%.3f lean=%.3f rest=%d%n", s.speed(), s.lean, restTicks);
            assertTrue(restTicks > 60, "settles flat and sleeps: v=" + s.speed() + " lean=" + s.lean);
        }
    }

    @Test void riderlessSkisWithoutACrashAlsoSettle() {
        BikeSim s = skis(SkiBrand.LINE_CHRONIC_101, flat(Terrain.Surface.SNOW));
        s.riderless = true;
        s.place(0.5, 64, 0, 0);
        launch(s, 3);
        for (int i = 0; i < 200; i++) { s.tick(Controls.NONE, 0.05); s.events.clear(); }
        assertTrue(s.speed() < 0.05 && Math.abs(s.lean) < 0.05, "ski brakes stop them: " + s.speed());
    }
}
