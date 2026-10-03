package com.descentmtb.trail;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;
/** Player-owned templates persisted inside the world, bounded to eight designs per player. */
public final class TrailLibrary extends SavedData {
 private final Map<String,CompoundTag> designs=new LinkedHashMap<>();
 public static TrailLibrary get(ServerLevel l){return l.getServer().overworld().getDataStorage().computeIfAbsent(new Factory<>(TrailLibrary::new,TrailLibrary::load,null),"descentmtb_trail_templates");}
 public static TrailLibrary load(CompoundTag t,HolderLookup.Provider r){var d=new TrailLibrary();var all=t.getCompound("Designs");for(String k:all.getAllKeys())d.designs.put(k,all.getCompound(k));return d;}
 @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider r){var all=new CompoundTag();designs.forEach(all::put);t.put("Designs",all);return t;}
 public void store(UUID player,String name,Map<BlockPos,TrailEdit.Change> plan,BlockPos origin){
  String key=player+":"+name;if(!designs.containsKey(key)&&designs.keySet().stream().filter(k->k.startsWith(player+":" )).count()>=8)throw new IllegalArgumentException("Máš osm šablon; přepiš existující název");
  designs.put(key,encode(plan,origin));setDirty();
 }
 public Map<BlockPos,TrailEdit.Change> recall(ServerLevel l,UUID p,String name,BlockPos origin){var t=designs.get(p+":"+name);if(t==null)throw new IllegalArgumentException("Šablona s tímto názvem není uložená");return decode(l,t,origin);}
 public static CompoundTag encode(Map<BlockPos,TrailEdit.Change> plan,BlockPos origin){var t=new CompoundTag();var cells=new ListTag();plan.forEach((p,c)->{var v=new CompoundTag();v.putLong("Pos",p.subtract(origin).asLong());v.put("State",NbtUtils.writeBlockState(c.state()));if(c.tag()!=null)v.put("Data",c.tag().copy());if(c.heights()!=null)for(int i=0;i<4;i++)v.putDouble("H"+i,c.heights()[i]);if(c.material()!=null)v.put("Material",NbtUtils.writeBlockState(c.material()));v.putBoolean("Deck",c.deck());cells.add(v);});t.put("Cells",cells);return t;}
 public static Map<BlockPos,TrailEdit.Change> decode(ServerLevel l,CompoundTag t,BlockPos origin){var result=new LinkedHashMap<BlockPos,TrailEdit.Change>();var cells=t.getList("Cells",Tag.TAG_COMPOUND);if(cells.size()>TrailConfig.MAX_BLOCKS.get())throw new IllegalArgumentException("Šablona je větší než limit serveru");for(int j=0;j<cells.size();j++){var v=cells.getCompound(j);double[] h=null;if(v.contains("H0")){h=new double[4];for(int i=0;i<4;i++)h[i]=v.getDouble("H"+i);}var reg=l.registryAccess().lookupOrThrow(Registries.BLOCK);result.put(origin.offset(BlockPos.of(v.getLong("Pos"))),new TrailEdit.Change(NbtUtils.readBlockState(reg,v.getCompound("State")),v.contains("Data")?v.getCompound("Data").copy():null,h,v.contains("Material")?NbtUtils.readBlockState(reg,v.getCompound("Material")):null,v.getBoolean("Deck")));}return result;}
}
