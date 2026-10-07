package com.descentmtb.custom;

import com.descentmtb.custom.MotoBuild.Exhaust;
import com.descentmtb.custom.MotoBuild.Suspension;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The motorbike workshop data: always valid, whatever a client or edited save sends. (The codecs need Minecraft: dev-checked.) */
class MotoBuildTest {
    @Test void defaultIsStockEverywhere() {
        MotoBuild b = MotoBuild.DEFAULT;
        assertTrue(b.isStock());
        assertEquals(MotoBuild.GROUPS.length, b.colors().size());
        for (String g : MotoBuild.GROUPS) assertEquals(MotoBuild.STOCK_COLOUR, b.colorOf(g));
        assertEquals(MotoBuild.STOCK_COLOUR, b.colorOf("no such group"));
        assertEquals(0, b.sprocket());
        assertEquals(Exhaust.STOCK, b.exhaust());
        assertEquals(Suspension.STOCK, b.suspension());
        assertEquals(0, b.number());
    }

    @Test void theConstructorClampsEverythingAClientCouldSend() {
        List<Integer> junk = new ArrayList<>(List.of(0x1234567, -5, 0xFF8800, 0, 7, 8, 9, 10, 11, 12));
        MotoBuild b = new MotoBuild(junk, 99, null, null, 1000);
        assertEquals(MotoBuild.GROUPS.length, b.colors().size(), "extra colours are dropped");
        assertEquals(0x234567, b.colors().get(0), "colours are 24 bit");
        assertEquals(MotoBuild.STOCK_COLOUR, b.colors().get(1), "negative = stock");
        assertEquals(0xFF8800, b.colors().get(2));
        assertEquals(0, b.colors().get(3), "black is a colour, not stock");
        assertEquals(MotoBuild.MAX_SPROCKET, b.sprocket());
        assertEquals(Exhaust.STOCK, b.exhaust());
        assertEquals(Suspension.STOCK, b.suspension());
        assertEquals(MotoBuild.MAX_NUMBER, b.number());
        assertEquals(MotoBuild.MIN_SPROCKET, new MotoBuild(null, -50, Exhaust.RACE, Suspension.SOFT, -4).sprocket());
        assertEquals(0, new MotoBuild(null, 0, null, null, -4).number());
    }

    @Test void shortColourListsArePaddedWithStock() {
        MotoBuild b = new MotoBuild(List.of(0xAA0000), 0, Exhaust.STOCK, Suspension.STOCK, 0);
        assertEquals(0xAA0000, b.colorOf("plastic"));
        assertEquals(MotoBuild.STOCK_COLOUR, b.colorOf("anodized"));
        assertFalse(b.isStock());
    }

    @Test void withersChangeOneThingAndStayValid() {
        MotoBuild b = MotoBuild.DEFAULT.withColor(MotoBuild.groupIndex("rim"), 0x00FF00).withSprocket(2)
                .withExhaust(Exhaust.RACE).withSuspension(Suspension.STIFF).withNumber(46);
        assertEquals(0x00FF00, b.colorOf("rim"));
        assertEquals(MotoBuild.STOCK_COLOUR, b.colorOf("plastic"));
        assertEquals(2, b.sprocket());
        assertEquals(46, b.number());
        assertEquals(MotoBuild.MAX_SPROCKET, b.withSprocket(5).sprocket());
        assertEquals(MotoBuild.STOCK_COLOUR, b.withColor(MotoBuild.groupIndex("rim"), MotoBuild.STOCK_COLOUR).colorOf("rim"));
        MotoBuild repainted = b.withStockPaint();
        assertEquals(MotoBuild.STOCK_COLOUR, repainted.colorOf("rim"));
        assertEquals(Exhaust.RACE, repainted.exhaust(), "stock paint keeps the tuning");
    }

    @Test void groupNamesMatchTheModelsOrder() {
        assertArrayEquals(new String[]{"plastic", "fender", "frame", "seat", "rim", "spring", "anodized"}, MotoBuild.GROUPS);
        assertEquals(4, MotoBuild.groupIndex("rim"));
        assertEquals(-1, MotoBuild.groupIndex(null));
    }
}
