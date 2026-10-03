package com.descentmtb.trail;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
/** All player dimensions are bounded on the server; units are metres. */
public record WandSettings(WandMode mode,double width,double height,double spacing,int repeats,double radius,double strength,double softness) {
 public static WandSettings defaults(){return new WandSettings(WandMode.FLOW,5,.75,5,5,3,.3,.75);}
 public WandSettings bounded(){return new WandSettings(mode,clamp(width,2,9),clamp(height,.1,3),clamp(spacing,2,12),Math.max(1,Math.min(24,repeats)),clamp(radius,1,12),clamp(strength,.05,2),clamp(softness,0,1));}
 private static double clamp(double v,double a,double b){return Double.isFinite(v)?Math.max(a,Math.min(b,v)):a;}
 public CompoundTag tag(){var t=new CompoundTag();t.putInt("Mode",mode.ordinal());t.putDouble("Width",width);t.putDouble("Height",height);t.putDouble("Spacing",spacing);t.putInt("Repeats",repeats);t.putDouble("Radius",radius);t.putDouble("Strength",strength);t.putDouble("Softness",softness);return t;}
 public static WandSettings read(CompoundTag t){if(!t.contains("Width"))return defaults();return new WandSettings(WandMode.from(t.getInt("Mode")),t.getDouble("Width"),t.getDouble("Height"),t.getDouble("Spacing"),t.getInt("Repeats"),t.getDouble("Radius"),t.getDouble("Strength"),t.getDouble("Softness")).bounded();}
 public static WandSettings read(ItemStack s){return read(s.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getCompound("Wand"));}
 public void store(ItemStack s){CustomData.update(DataComponents.CUSTOM_DATA,s,t->t.put("Wand",bounded().tag()));}
}
