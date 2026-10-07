package com.descentmtb.client.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeadphoneMixTest {
    @Test
    void worldIsMuffledByMostOfItsLevel() {
        assertTrue(HeadphoneMix.DEFAULT_WORLD >= 0.1 && HeadphoneMix.DEFAULT_WORLD <= 0.2, "80-90 % quieter");
    }

    @Test
    void ownBikeAndVoiceNeverFollowTheWorldToSilence() {
        assertTrue(HeadphoneMix.playerGain(0f) >= 0.35f);
        assertTrue(HeadphoneMix.playerGain((float) HeadphoneMix.DEFAULT_WORLD) > 3 * HeadphoneMix.DEFAULT_WORLD);
        assertEquals(1f, HeadphoneMix.playerGain(1f), 1e-6);
    }
}
