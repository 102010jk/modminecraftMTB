package com.descentmtb.client.sound;

import com.descentmtb.physics.Terrain;
import com.descentmtb.physics.V3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The crash prediction, the scream state machine and the landing predictor behind them. */
class ScreamTest {
    private static final double BAIL_PITCH = Math.toRadians(100), BAIL_YAW = Math.toRadians(115), BAIL_IMPACT = 13.5;

    private static boolean crash(V3 vel, double pitchErr, double yawErr, double t, double pitchRate, double yawRate) {
        return CrashPredictor.unavoidable(vel, pitchErr, yawErr, t, pitchRate, yawRate, BAIL_PITCH, BAIL_YAW, BAIL_IMPACT);
    }

    // ---- prediction ----

    @Test
    void aNormalJumpIsNotACrash() {
        assertFalse(crash(new V3(8, -3, 0), 0.1, 0.05, 0.4, 0, 0), "small gap");
        assertFalse(crash(new V3(10, -3, 0), 0.3, 0.1, 0.6, 0.5, 0), "mid jump, landing nearly aligned");
        assertFalse(crash(new V3(8, 2, 0), 0, 0, 0.6, 0, 0), "still going up");
    }

    @Test
    void aBigDropScreamsInTheWarningWindow() {
        V3 falling = new V3(6, -13, 0);
        assertTrue(crash(falling, 0, 0, 0.6, 0, 0), "vertical speed below -12 m/s, 0.6 s to go");
        assertTrue(crash(falling, 0, 0, 1.0, 0, 0), "1.0 s before impact is still in the window");
        assertFalse(crash(falling, 0, 0, 1.5, 0, 0), "too early: wait until 1 s before impact");
        assertFalse(crash(falling, 0, 0, 0.0, 0, 0), "already down");
        assertFalse(crash(falling, 0, 0, Double.POSITIVE_INFINITY, 0, 0), "no ground in sight");
        assertFalse(crash(falling, 0, 0, Double.NaN, 0, 0));
    }

    @Test
    void aFallThatWillBeTooFastAtImpactScreamsBeforeItReachesTwelve() {
        // -8 m/s now with 0.7 s to go: -14.9 m/s at the ground, past the bail limit
        assertTrue(crash(new V3(5, -8, 0), 0, 0, 0.7, 0, 0));
        // -8 m/s with 0.2 s to go: only -10 m/s at the ground, fine
        assertFalse(crash(new V3(5, -8, 0), 0, 0, 0.2, 0, 0));
    }

    @Test
    void landingNoseFirstOrOnTheBackScreams() {
        assertTrue(crash(new V3(8, -5, 0), Math.toRadians(110), 0, 0.5, 0, 0), "already 110 degrees off the slope");
        assertTrue(crash(new V3(8, -5, 0), Math.toRadians(40), 0, 0.6, Math.toRadians(150), 0),
                "rotating over: 40 deg + 150 deg/s x 0.6 s lands on the back");
        assertTrue(crash(new V3(8, -5, 0), Math.toRadians(-40), 0, 0.6, Math.toRadians(-150), 0), "the other way (nose down)");
        assertFalse(crash(new V3(8, -5, 0), Math.toRadians(40), 0, 0.6, Math.toRadians(-60), 0), "recovering");
        // a clean backflip about to come around: error wraps to ~0
        assertFalse(crash(new V3(8, -5, 0), Math.toRadians(300), 0, 0.5, Math.toRadians(40), 0), "300 deg = 60 deg short of a full flip");
    }

    @Test
    void landingSidewaysScreamsButASmallWhipDoesNot() {
        assertTrue(crash(new V3(8, -5, 0), 0, Math.toRadians(130), 0.4, 0, 0));
        assertFalse(crash(new V3(8, -5, 0), 0, Math.toRadians(60), 0.4, 0, 0), "a whip");
        assertTrue(crash(new V3(8, -5, 0), 0, Math.toRadians(70), 0.8, 0, Math.toRadians(180)), "spinning on: 70 deg + half of 180 deg/s x 0.8 s");
    }

    @Test
    void frameConvenienceNeedsAnAirborneBikeThatHasBeenUpAWhile() {
        BikeAudioFrame f = new BikeAudioFrame();
        f.vel = new V3(5, -14, 0);
        f.timeToGround = 0.5;
        f.airborne = true;
        f.airTime = 0.8;
        assertTrue(CrashPredictor.unavoidable(f));
        f.airTime = 0.1;
        assertFalse(CrashPredictor.unavoidable(f), "just left the ground");
        f.airTime = 0.8;
        f.bailed = true;
        assertFalse(CrashPredictor.unavoidable(f), "already crashed");
        f.bailed = false;
        f.airborne = false;
        assertFalse(CrashPredictor.unavoidable(f));
    }

    @Test
    void aLooserBailLimitScreamsLater() {
        V3 v = new V3(5, -9, 0);
        // bail tolerance 0.5 -> impact limit 6.75: a -9 m/s fall is an obvious crash
        assertTrue(CrashPredictor.unavoidable(v, 0, 0, 0.5, 0, 0, BAIL_PITCH, BAIL_YAW, 6.75));
        assertFalse(CrashPredictor.unavoidable(v, 0, 0, 0.1, 0, 0, BAIL_PITCH, BAIL_YAW, 40), "tolerance 3: -9 m/s is nothing");
    }

    // ---- trigger ----

    @Test
    void screamNeedsAConfirmedCrashAndStartsOnce() {
        ScreamTrigger t = new ScreamTrigger();
        assertEquals(ScreamTrigger.Action.NONE, t.update(true, true, false));
        assertEquals(ScreamTrigger.Action.NONE, t.update(true, true, false));
        assertEquals(ScreamTrigger.Action.START, t.update(true, true, false), "third tick in a row");
        assertTrue(t.active());
        for (int i = 0; i < 20; i++) assertEquals(ScreamTrigger.Action.NONE, t.update(true, true, false), "only once per jump");
    }

    @Test
    void aFlickeringPredictionNeverScreams() {
        ScreamTrigger t = new ScreamTrigger();
        for (int i = 0; i < 40; i++) assertEquals(ScreamTrigger.Action.NONE, t.update(true, i % 2 == 0, false));
        assertFalse(t.active());
    }

    @Test
    void landingSafelyCutsTheScream() {
        ScreamTrigger t = new ScreamTrigger();
        for (int i = 0; i < 3; i++) t.update(true, true, false);
        assertEquals(ScreamTrigger.Action.STOP, t.update(false, false, false), "rolled away: stop screaming");
        assertFalse(t.active());
        assertEquals(ScreamTrigger.Action.NONE, t.update(false, false, false));
    }

    @Test
    void crashingLeavesTheScreamPlayingIntoTheBail() {
        ScreamTrigger t = new ScreamTrigger();
        for (int i = 0; i < 3; i++) t.update(true, true, false);
        assertEquals(ScreamTrigger.Action.NONE, t.update(false, false, true), "bailed: let it play out");
        assertFalse(t.active());
        // and the next jump can scream again
        for (int i = 0; i < 2; i++) t.update(true, true, false);
        assertEquals(ScreamTrigger.Action.START, t.update(true, true, false));
    }

    @Test
    void screamResetsWhenTheJumpEndsBeforeItStarts() {
        ScreamTrigger t = new ScreamTrigger();
        t.update(true, true, false);
        t.update(true, true, false);
        t.update(false, false, false);          // landed
        assertEquals(ScreamTrigger.Action.NONE, t.update(true, true, false), "the streak starts over");
    }

    // ---- landing predictor ----

    private static Terrain flat(double y) {
        return new Terrain() {
            @Override
            public boolean ground(double x, double z, double yTop, double yBottom, GroundHit out) {
                if (y > yTop || y < yBottom) return false;
                out.set(y, V3.Y, Surface.DIRT);
                return true;
            }

            @Override
            public boolean solidAt(double x, double y2, double z) {
                return y2 < y;
            }
        };
    }

    private static Terrain slope(double riseOverRun) {
        return new Terrain() {
            @Override
            public boolean ground(double x, double z, double yTop, double yBottom, GroundHit out) {
                double y = x * riseOverRun;
                if (y > yTop || y < yBottom) return false;
                out.set(y, new V3(-riseOverRun, 1, 0).normalize(), Surface.DIRT);
                return true;
            }

            @Override
            public boolean solidAt(double x, double y2, double z) {
                return y2 < x * riseOverRun;
            }
        };
    }

    @Test
    void predictsTheFallTimeOverFlatGround() {
        LandingPredictor.Landing l = LandingPredictor.predict(flat(0), new V3(0, 3.0, 0), new V3(6, 0, 0), 0.5, -Math.PI / 2, 9.81);
        // 2.5 m of free fall: t = sqrt(2*2.5/9.81) = 0.71 s
        assertTrue(l.known());
        assertEquals(0.71, l.time(), 0.06);
        assertEquals(0, l.groundPitch(), 1e-9);
    }

    @Test
    void noLandingWithinTheWindowWhenTheGroundIsFarBelow() {
        LandingPredictor.Landing l = LandingPredictor.predict(flat(0), new V3(0, 30, 0), new V3(6, 0, 0), 0.5, 0, 9.81);
        assertFalse(l.known());
        assertEquals(Double.POSITIVE_INFINITY, l.time());
    }

    @Test
    void groundPitchIsTheSlopeAlongTheHeading() {
        // a 10 % slope rising towards +x; yaw such that forward = +x is (-sin yaw, cos yaw) = (1, 0) -> yaw = -90 deg
        double up = LandingPredictor.groundPitch(new V3(-0.1, 1, 0).normalize(), -Math.PI / 2);
        assertEquals(Math.atan(0.1), up, 1e-9);
        double down = LandingPredictor.groundPitch(new V3(-0.1, 1, 0).normalize(), Math.PI / 2);
        assertEquals(-Math.atan(0.1), down, 1e-9);
        assertEquals(0, LandingPredictor.groundPitch(new V3(-0.1, 1, 0).normalize(), 0), 1e-9, "across the slope");
        LandingPredictor.Landing onSlope = LandingPredictor.predict(slope(0.3), new V3(0, 3, 0), new V3(0, 0, 0), 0.5, -Math.PI / 2, 9.81);
        assertTrue(onSlope.known());
        assertEquals(Math.atan(0.3), onSlope.groundPitch(), 1e-6);
    }
}
