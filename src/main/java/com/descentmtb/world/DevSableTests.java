package com.descentmtb.world;
import com.descentmtb.DescentMtb;
import com.descentmtb.physics.*;
import dev.ryanhcode.sable.companion.*;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
/** Optional development-only integration proof against an actual assembled Sable deck. */
public final class DevSableTests {
 public static volatile boolean PASSED,FAILED;private static ServerPlayer player;private static SubLevelAccess deck;private static Vec3 localPoint,rampPoint,initialWorld;private static BoundingBox3d area;private static McColumns columns;private static BikeSim sim;private static int age;private static double sawSpeed,deckMass;
 private static void command(String s){
  try{int r=player.getServer().getCommands().getDispatcher().execute(s,player.createCommandSourceStack());DescentMtb.LOG.info("[sabletest] /{} -> {}",s,r);}
  catch(com.mojang.brigadier.exceptions.CommandSyntaxException ex){DescentMtb.LOG.error("[sabletest] /{} FAILED: {}",s,ex.getMessage());}}
 public static void register(RegisterCommandsEvent e){if(!Boolean.getBoolean("descentmtb.autopilot"))return;e.getDispatcher().register(Commands.literal("mtbdevsable").requires(s->s.hasPermission(2)).executes(c->{start(c.getSource().getPlayerOrException());return 1;}));}
 private static void start(ServerPlayer p){PASSED=FAILED=false;player=p;age=0;deck=null;sim=null;sawSpeed=0;deckMass=0;if(!net.neoforged.fml.ModList.get().isLoaded("sable")){PASSED=true;DescentMtb.LOG.info("[sabletest] SKIP: optional Sable is absent");player=null;return;}int x=p.blockPosition().getX()+60,z=p.blockPosition().getZ(),y=210;var l=p.serverLevel();// a stone floor right under the deck: without it the deck free-falls and a free-falling bike feels no friction
 for(int bx=x-1;bx<x+6;bx++)for(int bz=z-1;bz<z+15;bz++)l.setBlock(new BlockPos(bx,y-1,bz),Blocks.STONE.defaultBlockState(),3);for(int bx=x;bx<x+5;bx++)for(int bz=z;bz<z+14;bz++)l.setBlock(new BlockPos(bx,y,bz),Blocks.OAK_PLANKS.defaultBlockState(),3);command("sable paused true");l.setBlock(new BlockPos(x+2,y+1,z+9),com.descentmtb.registry.ModBlocks.RAMP.get().defaultBlockState().setValue(com.descentmtb.ramp.RampBlock.FACING,net.minecraft.core.Direction.SOUTH).setValue(com.descentmtb.ramp.RampBlock.START,0).setValue(com.descentmtb.ramp.RampBlock.END,16),3);command(String.format("sable assemble area %d %d %d %d %d %d",x,y,z,x+4,y+1,z+13));area=new BoundingBox3d(x-2,y-3,z-2,x+7,y+5,z+16);initialWorld=new Vec3(x+2.5,y+1,z+4.5);columns=new McColumns(l);}
 private static void require(boolean b,String s){if(!b)throw new IllegalStateException(s);DescentMtb.LOG.info("[sabletest] PASS: {}",s);}
 public static void tick(ServerTickEvent.Post e){if(player==null)return;try{if(deck==null){age++;for(var sub:SableCompanion.INSTANCE.getAllIntersecting(player.level(),area))if(sub.getName()==null)deck=sub;if(deck==null){if(age>100)failure("assembly timed out");return;}command("sable name set @l mtb_compatibility_test");localPoint=deck.logicalPose().transformPositionInverse(initialWorld);rampPoint=deck.logicalPose().transformPositionInverse(initialWorld.add(0,.5,5));command("sable physics rotation @l set axis 1 0 0 15");age=0;return;}age++;columns.newTick();if(age==10){var world=deck.logicalPose().transformPosition(localPoint);var hit=new Terrain.GroundHit();require(columns.terrain().ground(world.x,world.z,world.y+2,world.y-2,hit),"bike queries find a rotated assembled deck");require(Math.abs(hit.height-world.y)<.04,"moving deck height is transformed into global coordinates");require(hit.normal.y>.9&&Math.abs(hit.normal.z)>.2,"rotated deck normal reaches bike physics");var rw=deck.logicalPose().transformPosition(rampPoint);require(columns.terrain().ground(rw.x,rw.z,rw.y+2,rw.y-2,hit)&&Math.abs(hit.height-rw.y)<.04,"analytic ramp refinement works on a rotated Sable deck");command("sable physics rotation @l set axis 1 0 0 0");}
 if(age==15){var world=deck.logicalPose().transformPosition(localPoint);sim=new BikeSim(new BikeParams(),columns.terrain());sim.place(world.x,world.y,world.z,0);var tracker=deck.getClass().getMethod("getMassTracker").invoke(deck);double mass=((Number)tracker.getClass().getMethod("getMass").invoke(tracker)).doubleValue();deckMass=mass;command("sable paused false");}
 if(age==17&&deckMass>0){command("sable physics impulse @l linear "+(deckMass*3)+" 0 0 global");}
 if(age>15&&sim!=null){var world=deck.logicalPose().transformPosition(localPoint);var v=SableCompanion.INSTANCE.getVelocity(player.level(),deck,localPoint);sawSpeed=Math.max(sawSpeed,Math.abs(v.x));sim.tick(new Controls(0,0,0,1,0,0,false,0,0),.05);if(age==45){DescentMtb.LOG.info("[sabletest] measured platform speed {} (getVelocity units), deck moved {} blocks in x",sawSpeed,world.x-initialWorld.x);require(sawSpeed>1,"actual Sable platform velocity is available");require(Math.abs(sim.pos.x-world.x)<2,"braked bike stays with a moving Sable platform");require(sim.pos.y>world.y-.25,"bike does not pass through the moving deck");PASSED=true;DescentMtb.LOG.info("[sabletest] ALL PASSED");player=null;}}
 }catch(Exception ex){failure(ex.toString());}}
 private static void failure(String why){FAILED=true;DescentMtb.LOG.error("[sabletest] FAIL: {}",why);if(player!=null)command("sable paused false");player=null;}
 private DevSableTests(){}
}
