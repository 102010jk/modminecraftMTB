package com.descentmtb.physics;

import org.junit.jupiter.api.Test;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;

class WallRideTest {
    private static final Controls INTO = new Controls(0, 0, 0, 0, 0, 1, false, 0, 0);
    private static final Controls AWAY = new Controls(0, 0, 0, 0, 0, -1, false, 0, 0);

    private Terrain geometry(Predicate<V3> solid) {
        Terrain floor = TestTerrains.flat(64, Terrain.Surface.ROCK);
        return new Terrain() {
            public boolean ground(double x, double z, double top, double bottom, GroundHit out) {
                return floor.ground(x, z, top, bottom, out);
            }
            public boolean solidAt(double x, double y, double z) {
                return floor.solidAt(x, y, z) || solid.test(new V3(x, y, z));
            }
        };
    }

    private Terrain wall(V3 normal) {
        return geometry(point -> point.y > 64 && point.dot(normal) < 0);
    }

    private BikeSim flight(Terrain terrain, V3 position, double height, double yaw, V3 velocity) {
        BikeSim bike = new BikeSim(new BikeParams(), terrain);
        bike.place(position.x, 64, position.z, yaw);
        bike.pos = bike.pos.addScaled(V3.Y, height);
        bike.riderPos = bike.riderPos.addScaled(V3.Y, height);
        bike.vel = bike.riderVel = velocity;
        return bike;
    }

    @Test void aGlancingJumpAcquiresEitherSideWithoutAHeldButton() {
        for (int side : new int[]{-1, 1}) {
            V3 normal = new V3(side, 0, 0);
            BikeSim bike = flight(wall(normal), normal.mul(.55), 4, 0, new V3(-side * 2, 1, 12));
            bike.tick(Controls.NONE, .05);
            assertTrue(bike.wallRide, "a clear jump into a side wall should catch immediately, side=" + side);
            for (int i = 0; i < 14; i++) {
                bike.tick(Controls.NONE, .05);
                assertTrue(bike.wallRide, "holding a button must not be necessary to stay on the face");
                assertTrue(bike.pos.dot(normal) > .4, "the tyres must not carry the frame through the wall");
                assertFalse(bike.bailed, bike.bailReason);
            }
            assertTrue(Math.abs(bike.lean) > 1.1);
        }
    }

    @Test void anAngledWallUsesItsFaceNormalInsteadOfTheBikeHeading() {
        double angle = Math.toRadians(25);
        V3 normal = new V3(Math.cos(angle), 0, Math.sin(angle));
        V3 tangent = normal.cross(V3.Y);
        BikeSim bike = flight(wall(normal), normal.mul(.53), 4, angle + .15,
                tangent.mul(13).addScaled(normal, -2));
        for (int i = 0; i < 15; i++) {
            bike.tick(Controls.NONE, .05);
            assertTrue(bike.wallRide, "wall contact must remain stable when the bike is slightly yawed");
            assertTrue(bike.pos.dot(normal) > .4);
            assertTrue(bike.vel.dot(tangent) > 10);
            assertEquals(0, bike.vel.dot(normal), .05);
            assertFalse(bike.bailed, bike.bailReason);
        }
    }

    @Test void groundRidingAndSmallBumpsNeverBecomeWallrides() {
        for (double height : new double[]{0, .08, .14}) {
            BikeSim bike = flight(wall(new V3(1, 0, 0)), new V3(.5, 0, 0), height, 0, new V3(0, 0, 14));
            for (int i = 0; i < 15; i++) {
                bike.tick(INTO, .05);
                assertFalse(bike.wallRide, "a ground ride or tiny loss of tyre contact is not a wallride");
            }
        }
    }

    @Test void distantFlyBysAndLowSpeedDoNotAcquire() {
        for (double distance : new double[]{.55, 1.0}) {
            BikeSim bike = flight(wall(new V3(1, 0, 0)), new V3(distance, 0, 0), 4, 0, new V3(0, 0, 12));
            bike.tick(distance == 1 ? INTO : Controls.NONE, .05);
            assertFalse(bike.wallRide, "a parallel fly-by must not get magnetic support from a nearby wall");
        }
        BikeSim slow = flight(wall(new V3(1, 0, 0)), new V3(.5, 0, 0), 4, 0, new V3(-1, 0, 4));
        slow.tick(INTO, .05);
        assertFalse(slow.wallRide);
    }

    @Test void pillarsCornersLowWallsAndDisconnectedRailsAreNotContinuousFaces() {
        Terrain[] obstacles = {
                geometry(point -> point.x < 0 && Math.abs(point.z) < .1 && point.y > 64),
                geometry(point -> point.x < 0 && point.y > 64 && point.y < 65),
                geometry(point -> point.x < 0 && point.z > 0 && point.y > 64),
                geometry(point -> point.x < 0 && (Math.abs(point.y - 68.2774) < .03
                        || Math.abs(point.y - 69.0774) < .03))
        };
        for (Terrain terrain : obstacles) {
            BikeSim bike = flight(terrain, new V3(.5, 0, 0), 4, 0, new V3(-1, 0, 12));
            bike.tick(INTO, .05);
            assertFalse(bike.wallRide, "one solid spot cannot substitute for support along both tyres and the frame");
        }
    }

    @Test void headOnImpactsAreNotConvertedIntoWallrides() {
        BikeSim bike = flight(wall(new V3(1, 0, 0)), new V3(.55, 0, 0), 4,
                Math.toRadians(60), new V3(-12, 0, 2));
        bike.tick(INTO, .05);
        assertFalse(bike.wallRide);
        assertTrue(bike.bailed, "a fast head-on wall strike should still be a crash");
    }

    @Test void releasesAtTheEndOfTheWallAndWhenTheRiderMovesAway() {
        BikeSim bike = flight(geometry(point -> point.x < 0 && point.z < 4 && point.y > 64),
                new V3(.5, 0, 0), 4, 0, new V3(-1, 0, 12));
        bike.tick(INTO, .05);
        assertTrue(bike.wallRide);
        while (bike.pos.z < 5) bike.tick(Controls.NONE, .05);
        assertFalse(bike.wallRide, "wall support must end with the wall");

        bike = flight(wall(new V3(1, 0, 0)), new V3(.5, 0, 0), 4, 0, new V3(-1, 0, 12));
        bike.tick(INTO, .05);
        assertTrue(bike.wallRide);
        bike.tick(AWAY, .05);
        assertFalse(bike.wallRide, "countersteering should release the wall");
        bike.vel = bike.riderVel = new V3(3, 0, 12);
        bike.tick(INTO, .05);
        assertFalse(bike.wallRide, "lean input cannot keep a departing bike stuck to the wall");
    }

    @Test void slowingDownOrDisablingWallridesReleasesSupport() {
        for (boolean disabled : new boolean[]{false, true}) {
            BikeSim bike = flight(wall(new V3(1, 0, 0)), new V3(.5, 0, 0), 4, 0, new V3(-1, 0, 12));
            bike.tick(INTO, .05);
            assertTrue(bike.wallRide);
            if (disabled) bike.p.wallRides = false; else bike.vel = bike.riderVel = new V3(0, 0, 4);
            bike.tick(INTO, .05);
            assertFalse(bike.wallRide);
        }
    }

    @Test void aLongWallDoesNotStopWorkingAfterAnArbitraryTimer() {
        BikeSim bike = flight(wall(new V3(1, 0, 0)), new V3(.5, 0, 0), 30, 0, new V3(-1, 0, 18));
        for (int i = 0; i < 60; i++) {
            bike.tick(i == 0 ? INTO : Controls.NONE, .05);
            assertTrue(bike.wallRide, "valid wall support should last while speed and contact remain");
            assertFalse(bike.bailed, bike.bailReason);
        }
    }

    @Test void bothSidesOfANarrowPassageCanBeSelected() {
        Terrain passage = geometry(point -> (point.x < -.55 || point.x > .55) && point.y > 64);
        for (int side : new int[]{-1, 1}) {
            BikeSim bike = flight(passage, V3.ZERO, 4, 0, new V3(0, 0, 12));
            Controls controls = side == 1 ? INTO : AWAY;
            for (int i = 0; i < 8; i++) bike.tick(controls, .05);
            assertTrue(bike.wallRide, "the first wall tested must not hide the wall the rider selected");
            assertEquals(-side, Math.signum(bike.lean));
        }
    }

    @Test void configChangesTheRequiredSpeedAlongTheWall() {
        BikeSim bike = flight(wall(new V3(1, 0, 0)), new V3(.5, 0, 0), 4, 0, new V3(-1, 0, 12));
        bike.p.wallRideMinSpeed = 15;
        bike.tick(INTO, .05);
        assertFalse(bike.wallRide);
        bike.p.wallRideMinSpeed = 8;
        bike.tick(INTO, .05);
        assertTrue(bike.wallRide);
    }

    @Test void actualBlockColumnsSupportWallridesWithoutSmoothingTheWall() {
        Terrain blocks = TestTerrains.blocks((x, z) -> x < 0 ? 74 : 64, Terrain.Surface.ROCK);
        BikeSim bike = flight(blocks, new V3(.53, 0, 0), 4, 0, new V3(-2, 0, 12));
        for (int i = 0; i < 12; i++) {
            bike.tick(Controls.NONE, .05);
            assertTrue(bike.wallRide);
            assertFalse(bike.bailed, bike.bailReason);
        }
    }

    @Test void pitchingTheBikeCannotInventSupportBelowTheWall() {
        for (int direction : new int[]{-1, 1}) {
            Terrain ledge = geometry(point -> point.x < 0 && point.y > 68.3);
            BikeSim bike = flight(ledge, new V3(.5, 0, 0), 4, 0, new V3(-1, 0, 12));
            bike.pitch = direction * Math.toRadians(50);
            bike.tick(INTO, .05);
            assertFalse(bike.wallRide, "both actual tyres need a wall, even when the frame is pitched");
        }
    }

    @Test void sweepsCannotSkipAThinWallAtHighSpeed() {
        Terrain thin = geometry(point -> point.x > 0 && point.x < .0625 && point.y > 64);
        BikeSim bike = flight(thin, new V3(.095, 0, 0), 4, Math.PI / 2, new V3(-80, 0, 0));
        bike.tick(Controls.NONE, .05);
        assertTrue(bike.bailed, "a wall crossed within one substep must still collide");
        assertFalse(bike.wallRide);
        assertTrue(bike.pos.x > .06, "the swept collision must stop the frame on the starting side");
    }

    @Test void theRidersHeadCannotCrossAWallWhileTheFrameMovesAlongIt() throws ReflectiveOperationException {
        Terrain terrain = wall(new V3(1, 0, 0));
        BikeSim bike = flight(terrain, new V3(.04, 0, 0), 4, 0, new V3(0, 0, 12));
        bike.riderPos = new V3(.01, bike.riderPos.y, bike.riderPos.z);
        bike.riderVel = new V3(-6, 0, 12);
        // Isolate the collision sweep from rider constraints which redistribute this relative velocity.
        var integrate = BikeSim.class.getDeclaredMethod("integrate", double.class);
        integrate.setAccessible(true);
        integrate.invoke(bike, .004);
        assertTrue(bike.bailed, "a head strike must use the rider's motion, even with zero frame speed into the wall");
        assertTrue(bike.riderPos.x >= 0);
        assertEquals(0, bike.vel.x, .01, "a rider-only strike must not accelerate the frame away from the wall");
    }

    @Test void aShortGrazingSweepStillReturnsTheFace() {
        Terrain terrain = wall(new V3(1, 0, 0));
        Terrain.RayHit hit = new Terrain.RayHit();
        assertTrue(terrain.raycast(new V3(.002, 67, 0), new V3(-.002, 67, .048), hit));
        assertEquals(0, hit.point.x, 1e-5);
        assertTrue(hit.normal.x > .99, "normal fitting must not erase a real grazing collision");
    }

    @Test void aWallBeyondTheSweepCannotChangeTheFirstFaceNormal() {
        Terrain terrain = geometry(point -> point.x < 0 || point.z > .06);
        Terrain.RayHit hit = new Terrain.RayHit();
        assertTrue(terrain.raycast(new V3(.002, 67, 0), new V3(-.002, 67, .048), hit));
        assertEquals(0, hit.point.x, 1e-5);
        assertTrue(hit.normal.x > .999 && Math.abs(hit.normal.z) < .001);
        assertEquals(1, -new V3(-1, 0, 12).dot(hit.normal), .02,
                "another wall past the segment must not turn a gentle brush into a hard crash");
    }

    @Test void speedIsRelativeToAMovingWall() {
        class MovingWall implements Terrain {
            double wallX;
            V3 velocity = new V3(2, 0, 4);
            final Terrain floor = TestTerrains.flat(64, Surface.ROCK);
            public boolean ground(double x, double z, double top, double bottom, GroundHit out) {
                return floor.ground(x, z, top, bottom, out);
            }
            public boolean solidAt(double x, double y, double z) { return x < wallX && y > 64 || floor.solidAt(x, y, z); }
            public boolean raycast(V3 from, V3 to, RayHit out) {
                if (!Terrain.super.raycast(from, to, out)) return false;
                out.velocity = velocity;
                return true;
            }
        }
        MovingWall wall = new MovingWall();
        BikeSim riding = flight(wall, new V3(.55, 0, 0), 4, 0, new V3(0, 0, 16));
        for (int i = 0; i < 12; i++) {
            riding.tick(Controls.NONE, .05);
            assertTrue(riding.wallRide, "tick=" + i + " wallX=" + wall.wallX + " pos=" + riding.pos + " vel=" + riding.vel
                    + " pitch=" + riding.pitch + " bailed=" + riding.bailReason);
            assertEquals(2, riding.vel.x, .1, "normal collision must follow the moving face");
            wall.wallX += .1;
        }
        wall.wallX = 0;
        wall.velocity = new V3(0, 0, 16);
        BikeSim coMoving = flight(wall, new V3(.5, 0, 0), 4, 0, wall.velocity);
        coMoving.tick(INTO, .05);
        assertFalse(coMoving.wallRide, "world speed alone is insufficient on a wall moving alongside the bike");
    }
}
