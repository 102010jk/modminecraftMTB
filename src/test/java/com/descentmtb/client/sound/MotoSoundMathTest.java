package com.descentmtb.client.sound;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MotoSoundMathTest {
    @Test void crossfadeKeepsConstantPower() {
        for (double rpm = 1000; rpm <= 12000; rpm += 250) {
            double[] w = MotoSoundMath.weights(rpm);
            double power = 0;
            int audible = 0;
            for (double v : w) { power += v * v; if (v > 1e-9) audible++; }
            assertEquals(1, power, 1e-9, "equal power at " + rpm);
            assertTrue(audible <= 2, "at most two neighbouring layers at " + rpm);
        }
    }

    @Test void eachLayerIsAloneAtItsOwnRevs() {
        for (int i = 0; i < MotoSoundMath.LAYER_RPM.length; i++) {
            double[] w = MotoSoundMath.weights(MotoSoundMath.LAYER_RPM[i]);
            assertEquals(1, w[i], 1e-9);
            assertEquals(1, MotoSoundMath.pitch(i, MotoSoundMath.LAYER_RPM[i]), 1e-9);
        }
    }

    @Test void pitchStaysInsideMinecraftsRange() {
        for (int i = 0; i < 4; i++) for (double rpm = 0; rpm <= 15000; rpm += 500) {
            double p = MotoSoundMath.pitch(i, rpm);
            assertTrue(p >= 0.5 && p <= 2.0);
        }
    }

    @Test void openThrottleIsLouderThanCoasting() {
        assertTrue(MotoSoundMath.loudness(8000, 1) > MotoSoundMath.loudness(8000, 0) + 0.2);
        assertTrue(MotoSoundMath.loudness(11000, 1) <= 1.0 + 1e-9);
        assertTrue(MotoSoundMath.loudness(1500, 0) > 0.2, "idle is audible");
    }
}
