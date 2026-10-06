package com.descentmtb.client.trail;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShapeEditHistoryTest {
    private ShapeEditHistory.State shape(int h) {
        return new ShapeEditHistory.State(h,h,h,h,false,false);
    }

    @Test void undoAndRedoRestoreHeightAndDeckTogether() {
        var history = new ShapeEditHistory();
        var before = shape(16);
        var after = new ShapeEditHistory.State(8,16,8,16,true,true);
        history.record(before,after);
        assertEquals(before,history.undo(after));
        assertEquals(after,history.redo(before));
    }

    @Test void newEditDiscardsRedoButNoOpPreservesIt() {
        var history = new ShapeEditHistory();
        history.record(shape(16),shape(17));
        history.undo(shape(17));
        history.record(shape(16),shape(16));
        assertEquals(shape(17),history.redo(shape(16)));
        history.undo(shape(17));
        history.record(shape(16),shape(18));
        assertEquals(shape(18),history.redo(shape(18)));
    }

    @Test void snapshotDoesNotShareMutableHeightsAndHistoryIsBounded() {
        int[] heights = {1,2,3,4};
        var snapshot = new ShapeEditHistory.State(heights,false,false);
        heights[0]=99;
        snapshot.heights()[0]=99;
        assertEquals(1,snapshot.nw());
        var history = new ShapeEditHistory();
        for(int i=0;i<40;i++) history.record(shape(i),shape(i+1));
        var current=shape(40);
        for(int i=0;i<40;i++) current=history.undo(current);
        assertEquals(shape(8),current);
    }
}
