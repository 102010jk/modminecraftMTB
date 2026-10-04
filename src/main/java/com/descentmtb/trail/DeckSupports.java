package com.descentmtb.trail;

import com.descentmtb.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import java.util.Map;

/** Thin posts meet the underside of the deck, and are built only when a real foundation is found. */
public final class DeckSupports {
    public static void add(Level level,ColumnEditor.Column deck,Map<BlockPos,TrailEdit.Change> plan) {
        if(!deck.deck()) return;
        double underside=ColumnShaper.surfaceAt(deck.abs(),.5,.5)-ColumnShaper.DECK_THICKNESS;
        int anchor=(int)Math.floor(underside);
        int ground=anchor-1;
        while(ground>=level.getMinBuildHeight()&&anchor-ground<=32) {
            var p=new BlockPos(deck.x(),ground,deck.z());
            if(!level.isLoaded(p)) return;
            var s=level.getBlockState(p);
            if(!s.getCollisionShape(level,p).isEmpty()) break;
            if(!s.canBeReplaced()) return;
            ground--;
        }
        if(anchor-ground>32||ground<level.getMinBuildHeight()) return;
        for(int y=ground+1;y<anchor;y++) plan.put(new BlockPos(deck.x(),y,deck.z()),TrailEdit.Change.block(ModBlocks.WOOD_SUPPORT.get().defaultBlockState()));
        var p=new BlockPos(deck.x(),anchor,deck.z());
        var change=plan.get(p);
        if(change!=null&&change.heights()!=null) {
            CompoundTag tag=change.tag()==null?new CompoundTag():change.tag().copy();tag.putBoolean("Beam",true);
            plan.put(p,new TrailEdit.Change(change.state(),tag,change.heights(),change.material(),true));
        } else if(level.getBlockState(p).canBeReplaced()) plan.put(p,TrailEdit.Change.block(ModBlocks.WOOD_SUPPORT.get().defaultBlockState()));
    }
    private DeckSupports() {}
}
