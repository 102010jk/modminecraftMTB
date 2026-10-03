package com.descentmtb.trail;
public enum WandMode {
 FLOW(0), RAISE(0), LOWER(0), SMOOTH(0), FLATTEN(0), PUMP_LINE(0), PUMP_LOOP(0),
 DIRT_JUMP(1), WOOD_KICKER(1), WOOD_DROP(1), DROP_EDGE(1),
 BERM(2), ENDURO(2), SHARKFIN(2),
 BOARDWALK(3), SUPPORT(3), CLONE(3), TEMPLATE(3),
 ROOTS(4), ROCKS(4), ROCK_GARDEN(4), BARRIER(4), AIRBAG(4), SIGN(4), MEASURE(4), UNDO(4);
 public final int category;
 WandMode(int category){this.category=category;}
 public String key(){return "descentmtb.wand.mode."+name().toLowerCase(java.util.Locale.ROOT);}
 public static WandMode from(int i){return values()[Math.floorMod(i,values().length)];}
}
