package com.descentmtb.physics;

import com.descentmtb.entity.BikeType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The motorbikes are far more forgiving in landings than the bicycles (pure sim, no Minecraft). */
class MotoForgivenessTest {
    private static final double FLOOR = 64;

    /** Drops a bike of {@code type} from {@code height} m with a given attitude and speed; returns the sim after 4 s. */
    private static BikeSim drop(BikeType type, double height, double yaw, double pitch, V3 v) {
        BikeParams p = type.params();
        p.riskReward = true;
        p.airAlignAssist = 0;     // no self-levelling: the attitude is the test
        BikeSim sim = new BikeSim(p, TestTerrains.flat(FLOOR, Terrain.Surface.DIRT));
        sim.place(0, FLOOR, 0, yaw);
        sim.pos = sim.pos.add(new V3(0, height, 0));
        sim.pitch = pitch;
        sim.riderPos = sim.pos.addScaled(sim.upAxis(), p.riderHeight).addScaled(sim.forward(), p.riderForward);
        sim.vel = sim.riderVel = v;
        for (int i = 0; i < 80; i++) {
            sim.tick(Controls.NONE, 0.05);
            sim.events.clear();
        }
        return sim;
    }

    @Test void theMotorbikesTolerateMoreThanAbicycleOnEveryThreshold() {
        BikeParams bicycle = BikeType.ENDURO.params();
        for (BikeType type : new BikeType[]{BikeType.DIRT_BIKE, BikeType.PIT_BIKE}) {
            BikeParams m = type.params();
            assertTrue(m.bailImpactSpeed >= 1.6 * bicycle.bailImpactSpeed, type + " impact " + m.bailImpactSpeed);
            assertTrue(m.crashSpeed > 1.5 * bicycle.crashSpeed, type + " crash " + m.crashSpeed);
            assertTrue(m.wallCrashSpeed > 1.4 * bicycle.wallCrashSpeed, type + " wall " + m.wallCrashSpeed);
            assertTrue(m.bailPitchError > bicycle.bailPitchError);
            assertTrue(m.bailYawError > bicycle.bailYawError);
            assertTrue(m.riskYawLimit > bicycle.riskYawLimit);
            assertTrue(m.loopOutAngle > bicycle.loopOutAngle && m.loopOutTime > bicycle.loopOutTime);
            assertTrue(m.overBarsAngle > bicycle.overBarsAngle);
            assertTrue(m.landingAssistRate > bicycle.landingAssistRate);
            assertTrue(m.landingAssistAngle > bicycle.landingAssistAngle && m.landingAssistTime > bicycle.landingAssistTime);
            assertTrue(m.midTrickBailImpact > bicycle.midTrickBailImpact);
        }
    }

    @Test void theBicyclesAreUnchanged() {
        for (BikeType type : new BikeType[]{BikeType.ENDURO, BikeType.HARDTAIL}) {
            BikeParams p = type.params();
            assertEquals(Math.toRadians(100), p.bailPitchError, 1e-9);
            assertEquals(Math.toRadians(55), p.riskYawLimit, 1e-9);
            assertEquals(Math.toRadians(42), p.landingAssistAngle, 1e-9);
            assertEquals(5.5, p.landingAssistRate, 1e-9);
            assertEquals(0.18, p.landingAssistTime, 1e-9);
            assertEquals(Math.toRadians(80), p.loopOutAngle, 1e-9);
            assertEquals(0.25, p.loopOutTime, 1e-9);
            assertEquals(Math.toRadians(65), p.overBarsAngle, 1e-9);
            assertEquals(0, p.midTrickBailImpact, 1e-9);
        }
    }

    @Test void aBigDropToFlatBailsABicycleButNotTheDirtBike() {
        // ~16.5 m/s into the ground: past the bicycles' ~13.5 m/s limit, well inside a motocross bike's travel
        V3 v = new V3(0, 0, 5);
        assertTrue(drop(BikeType.ENDURO, 14, 0, 0, v).bailed, "the enduro bicycle crashes");
        assertTrue(drop(BikeType.HARDTAIL, 14, 0, 0, v).bailed, "the dirt-jump hardtail crashes");
        BikeSim dirt = drop(BikeType.DIRT_BIKE, 14, 0, 0, v);
        assertFalse(dirt.bailed, dirt.bailReason);
        BikeSim pit = drop(BikeType.PIT_BIKE, 14, 0, 0, v);
        assertFalse(pit.bailed, pit.bailReason);
        // ... but there is still a limit
        assertTrue(drop(BikeType.DIRT_BIKE, 45, 0, 0, v).bailed, "a 45 m drop still hurts");
    }

    @Test void landingCrookedBailsABicycleButNotTheDirtBike() {
        V3 v = new V3(0, 0, 8);
        // 75 degrees off the direction of travel: sideways to a bicycle, a whip to a motocross rider
        assertTrue(drop(BikeType.ENDURO, 2, Math.toRadians(75), 0, v).bailed, "bicycle sideways");
        BikeSim crooked = drop(BikeType.DIRT_BIKE, 2, Math.toRadians(75), 0, v);
        assertFalse(crooked.bailed, crooked.bailReason);
    }
}
