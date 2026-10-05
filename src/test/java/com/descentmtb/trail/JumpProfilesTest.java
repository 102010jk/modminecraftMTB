package com.descentmtb.trail;

import com.descentmtb.trail.JumpProfiles.Params;
import com.descentmtb.trail.JumpProfiles.Type;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The jump builder's height functions: one continuous profile, the right ends, the lip angle that was asked for. */
class JumpProfilesTest {
    private static final double EPS = 1e-9;

    /** A spread of settings of every type, all within the allowed ranges. */
    static List<Params> samples() {
        List<Params> out = new ArrayList<>();
        for (Type type : Type.values()) {
            for (int length : new int[]{2, 3, 5, 12}) {
                for (double height : new double[]{.25, 1, 1.5, 4}) {
                    for (int lip : new int[]{10, 35, 60}) {
                        out.add(new Params(type, length, 3, height, lip, 3, 5));
                    }
                }
            }
        }
        return out;
    }

    @Test
    void theProfileHasNoStepsAnywhereAlongTheJump() {
        for (Params p : samples()) {
            double step = 1.0 / 256;
            double steepest = Math.tan(Math.toRadians(p.maxSlope())) * 1.02 + 1e-6;
            for (double u = 0; u < p.total(); u += step) {
                double rise = Math.abs(p.heightAt(u + step) - p.heightAt(u));
                // the step-up's lip is a kink, not a step: still bounded by the steepest slope
                assertTrue(rise <= steepest * step + 1e-9, p + " jumps by " + rise + " at " + u);
            }
            for (int edge = 1; edge < p.total(); edge++) {
                assertEquals(p.heightAt(edge - 1e-9), p.heightAt(edge + 1e-9), 1e-6, p + ": blocks meet at " + edge);
            }
        }
    }

    @Test
    void neighbouringBlocksShareTheirEdgesExactly() {
        int[][] facings = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] f : facings) {
            Params p = new Params(Type.TABLE, 3, 3, 1.5, 35, 2, 4);
            var layout = new JumpProfiles.Layout(10, 64, -7, f[0], f[1], p);
            String facing = f[0] + "," + f[1];
            int[] box = layout.bounds();
            for (int x = box[0]; x <= box[2]; x++) {
                for (int z = box[1]; z <= box[3]; z++) {
                    assertTrue(layout.contains(x + .5, z + .5), facing + ": " + x + "," + z + " is part of the jump");
                    // the corners this column gets are the ones its neighbours along the jump get on the shared edge
                    double[] mine = corners(layout, x, z);
                    double[] next = corners(layout, x + f[0], z + f[1]);
                    for (int i = 0; i < 4; i++) {
                        for (int j = 0; j < 4; j++) {
                            if (x + i % 2 == x + f[0] + j % 2 && z + i / 2 == z + f[1] + j / 2) {
                                assertEquals(mine[i], next[j], 0, facing + ": shared corner");
                            }
                        }
                    }
                }
            }
            int columns = (box[2] - box[0] + 1) * (box[3] - box[1] + 1);
            assertEquals(p.total() * p.width(), columns, facing + ": footprint");
            // the first block is the clicked one, and its back edge is at the top of the clicked block
            assertTrue(layout.contains(10.5, -6.5));
            assertEquals(0, layout.along(10.5 - f[0] * .5, -6.5 - f[1] * .5), EPS);
            assertEquals(65, layout.height(10.5 - f[0] * .5, -6.5 - f[1] * .5), EPS);
            assertTrue(layout.height(10.5 + f[0] * 2.5, -6.5 + f[1] * 2.5) > 65.5, facing + ": rises away from the player");
            // sides are vertical: the columns beside the footprint are not part of it
            assertFalse(layout.contains(10.5 - f[1] * 2, -6.5 + f[0] * 2));
            assertFalse(layout.contains(10.5 + f[1] * 2, -6.5 - f[0] * 2));
        }
    }

    /** The corner heights the builder gives the column (x, z): the profile sampled at its four vertices. */
    private static double[] corners(JumpProfiles.Layout layout, int x, int z) {
        return new double[]{layout.height(x, z), layout.height(x + 1, z), layout.height(x, z + 1), layout.height(x + 1, z + 1)};
    }

    @Test
    void endpointsAndHeights() {
        Params kicker = new Params(Type.KICKER, 3, 3, 1.5, 35, 3, 5);
        assertEquals(0, kicker.heightAt(0), EPS);
        assertEquals(1.5, kicker.heightAt(3), EPS);
        assertEquals(3, kicker.total());

        Params ramp = kicker.withType(Type.RAMP);
        assertEquals(.75, ramp.heightAt(1.5), EPS);

        Params table = new Params(Type.TABLE, 3, 3, 1.5, 35, 3, 5);
        assertEquals(11, table.total());
        assertEquals(0, table.heightAt(0), EPS);
        assertEquals(0, table.heightAt(11), EPS);

        Params landing = new Params(Type.LANDING, 6, 3, 2, 35, 3, 5);
        assertEquals(2, landing.heightAt(0), EPS);
        assertEquals(0, landing.heightAt(6), EPS);
        assertTrue(landing.heightAt(.25) > 1.95, "a landing is nearly flat at the top");
        assertTrue(landing.heightAt(5.75) < .05, "and flattens out at the bottom");

        Params roller = new Params(Type.ROLLER, 4, 3, 1, 35, 3, 5);
        assertEquals(1, roller.heightAt(2), EPS);
        assertEquals(0, roller.heightAt(4), EPS);

        Params stepUp = new Params(Type.STEP_UP, 3, 3, 2, 30, 3, 5);
        assertEquals(7, stepUp.total());
        assertEquals(19 / 16.0, stepUp.lipHeight(), EPS);    // 0.6 x 2 on the 1/16 grid
        assertEquals(19 / 16.0, stepUp.heightAt(3), EPS);
        assertEquals(2, stepUp.heightAt(4), EPS);
        assertEquals(2, stepUp.heightAt(7), EPS);
    }

    @Test
    void theLipLeavesAtTheChosenAngle() {
        double[][] cases = {{3, 1.5, 35}, {2, .75, 30}, {6, 1, 25}, {12, 4, 45}, {4, 2, 50}, {5, .5, 20}, {3, 1, 60}};
        for (double[] c : cases) {
            Params p = new Params(Type.KICKER, (int) c[0], 1, c[1], (int) c[2], 3, 5);
            double d = 1e-4;
            double measured = Math.toDegrees(Math.atan((p.heightAt(p.length()) - p.heightAt(p.length() - d)) / d));
            assertEquals(c[2], measured, 2, "kicker " + c[0] + " long, " + c[1] + " high");
            assertEquals(c[2], p.lipAngle(), 2);
            assertEquals(c[2], p.withType(Type.TABLE).lipAngle(), 2);
        }
    }

    @Test
    void kickersAndRampsOnlyGoUp() {
        for (Params p : samples()) {
            if (p.type() != Type.KICKER && p.type() != Type.RAMP && p.type() != Type.STEP_UP) {
                continue;
            }
            for (double u = 0; u < p.total(); u += 1.0 / 64) {
                assertTrue(p.heightAt(u + 1.0 / 64) >= p.heightAt(u) - EPS, p + " goes down at " + u);
            }
        }
    }

    @Test
    void theTableDeckIsFlat() {
        Params table = new Params(Type.TABLE, 4, 3, 2, 30, 5, 6);
        for (double u = 4; u <= 9; u += .125) {
            assertEquals(2, table.heightAt(u), EPS, "deck at " + u);
        }
        assertTrue(table.heightAt(12) < 2 && table.heightAt(12) > 0, "the landing comes down after the deck");
    }

    @Test
    void settingsAreClampedAndSnapped() {
        Params wild = new Params(null, 40, -2, 9.03, 90, 0, 99).clamped();
        assertEquals(Type.KICKER, wild.type());
        assertEquals(JumpProfiles.MAX_LENGTH, wild.length());
        assertEquals(JumpProfiles.MIN_WIDTH, wild.width());
        assertEquals(JumpProfiles.MAX_HEIGHT, wild.height(), EPS);
        assertEquals(JumpProfiles.MAX_LIP, wild.lip());
        assertEquals(JumpProfiles.MIN_DECK, wild.deck());
        assertEquals(JumpProfiles.MAX_LANDING, wild.landing());
        assertEquals(1.0625, new Params(Type.RAMP, 3, 3, 1.07, 30, 3, 5).clamped().height(), EPS);
        assertEquals(Params.DEFAULT.height(), new Params(Type.RAMP, 3, 3, Double.NaN, 30, 3, 5).clamped().height(), EPS);
    }
}
