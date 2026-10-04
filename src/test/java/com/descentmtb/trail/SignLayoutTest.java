package com.descentmtb.trail;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

class SignLayoutTest {
    /** Every character is 6 units wide, like a typical font. */
    private static final ToDoubleFunction<String> WIDTH = s -> s.length() * 6.0;

    private static SignContent sign(SignContent.Type type, String name, SignContent.Arrow arrow, String text) {
        return new SignContent(type, name, SignContent.Difficulty.BLACK, arrow, SignContent.Warning.JUMP, text, null);
    }

    private static List<SignLayout.Label> labels(List<SignLayout.Item> items) {
        return items.stream().filter(SignLayout.Label.class::isInstance).map(SignLayout.Label.class::cast).toList();
    }

    @Test
    void shortNamesStayAtFullSize() {
        SignLayout.Fit fit = SignLayout.fitText("Flow", 36, 48, 2, WIDTH);
        assertEquals(List.of("Flow"), fit.lines());
        assertEquals(1.0, fit.scale(), 1e-9);
    }

    @Test
    void longNamesShrinkAndWrapAtWords() {
        SignLayout.Fit fit = SignLayout.fitText("Rock Garden Super Descent", 36, 48, 2, WIDTH);
        assertTrue(fit.lines().size() <= 2);
        assertTrue(fit.scale() < 1.0);
        for (String line : fit.lines()) {
            assertTrue(WIDTH.applyAsDouble(line) * fit.scale() <= 36 + 1e-9, line);
        }
        assertEquals("Rock Garden Super Descent", String.join(" ", fit.lines()));
    }

    @Test
    void aNameThatFitsInTheBoxNeverOverflowsVertically() {
        SignLayout.Fit fit = SignLayout.fitText("Black Forest Run", 36, 31, 2, WIDTH);
        assertTrue(fit.lines().size() * SignLayout.LINE * fit.scale() <= 31 + 1e-9);
    }

    @Test
    void anUnbreakableLongWordIsSplitAsALastResort() {
        SignLayout.Fit fit = SignLayout.fitText("Supercalifragilistic", 36, 48, 2, WIDTH);
        assertTrue(fit.lines().size() <= 2);
        assertEquals(0.4, fit.scale(), 1e-9);
    }

    @Test
    void trailSignHasDifficultyIconNameAndArrow() {
        List<SignLayout.Item> items = SignLayout.build(sign(SignContent.Type.TRAIL, "Flow", SignContent.Arrow.RIGHT, ""),
                "START", "FINISH", WIDTH);
        List<String> icons = items.stream().filter(SignLayout.Icon.class::isInstance)
                .map(i -> ((SignLayout.Icon) i).name()).toList();
        assertEquals(List.of("diff_black", "arrow_right"), icons);
        assertEquals(1, labels(items).size());
    }

    @Test
    void startAndFinishSignsPrintTheirWord() {
        List<SignLayout.Label> start = labels(SignLayout.build(sign(SignContent.Type.START, "Flow", SignContent.Arrow.NONE, ""),
                "START", "CIL", WIDTH));
        List<SignLayout.Label> finish = labels(SignLayout.build(sign(SignContent.Type.FINISH, "Flow", SignContent.Arrow.NONE, ""),
                "START", "CIL", WIDTH));
        assertEquals("START", start.get(start.size() - 1).text());
        assertEquals("CIL", finish.get(finish.size() - 1).text());
    }

    @Test
    void warningWithoutTextCentresABigIcon() {
        List<SignLayout.Item> items = SignLayout.build(sign(SignContent.Type.WARNING, "", SignContent.Arrow.NONE, ""),
                "START", "FINISH", WIDTH);
        assertEquals(1, items.size());
        SignLayout.Icon icon = (SignLayout.Icon) items.get(0);
        assertEquals("warn_jump", icon.name());
        assertEquals((SignLayout.WIDTH - icon.size()) / 2, icon.x(), 1e-9);
    }

    @Test
    void everythingStaysInsideTheBoard() {
        double inner = SignLayout.FRAME - 1e-9;
        for (SignContent.Type type : SignContent.Type.values()) {
            SignContent sign = sign(type, "A rather long trail name", SignContent.Arrow.UP, "Loose rocks ahead");
            for (SignLayout.Item item : SignLayout.build(sign, "START", "FINISH", WIDTH)) {
                if (item instanceof SignLayout.Icon icon) {
                    assertTrue(icon.x() >= inner && icon.x() + icon.size() <= SignLayout.WIDTH - inner, type + " icon x");
                    assertTrue(icon.y() >= inner && icon.y() + icon.size() <= SignLayout.HEIGHT - inner, type + " icon y");
                } else if (item instanceof SignLayout.Label label) {
                    double half = WIDTH.applyAsDouble(label.text()) * label.scale() / 2;
                    assertTrue(label.centreX() - half >= inner, type + " label left");
                    assertTrue(label.centreX() + half <= SignLayout.WIDTH - inner, type + " label right");
                    assertTrue(label.top() >= inner, type + " label top " + label);
                    assertTrue(label.top() + 8 * label.scale() <= SignLayout.HEIGHT - inner, type + " label bottom " + label);
                }
            }
        }
    }
}
