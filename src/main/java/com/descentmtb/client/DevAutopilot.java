package com.descentmtb.client;

import com.descentmtb.DescentMtb;
import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.physics.Controls;
import com.descentmtb.physics.V3;
import com.descentmtb.trick.Trick;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import java.util.Locale;

/** Development-only end-to-end ride: both bikes, jump, tricks, crash, get up and remount. */
public final class DevAutopilot {
    public static final boolean ENABLED = Boolean.getBoolean("descentmtb.autopilot");
    private static final boolean KEEP_OPEN = Boolean.getBoolean("descentmtb.autopilot.keepOpen");
    private static final boolean WALL_ONLY = Boolean.getBoolean("descentmtb.wallrideOnly");
    private static final int CRASH_PHASE = 8;
    private static boolean finished, sawManual, sawWall;
    private static int tick, phase = WALL_ONLY ? 7 : 0, rideTick = -1, mountAt = -1, afterBail = -1, sableWait = -1;
    private static BlockPos o;
    private static boolean flew, shotAir, shotTrick, sawRagdoll, remountSent;
    private static MountainBikeEntity crashedBike;
    private static boolean pumpSent;

    static boolean active() { return ENABLED && !finished && tick >= 60 && rideTick >= 0 && afterBail < 0; }

    static void prepareBike(MountainBikeEntity bike) {
        if (!ENABLED || o == null || tick < 60 || afterBail >= 0) return;
        bike.respawnAt(o.getX() + .5, o.getY(), o.getZ() + (phase == CRASH_PHASE ? 50.5 : 2.5), 0);
        if (phase == 3) {
            V3 lift = new V3(0, 2.5, 0);
            bike.sim().pos = bike.sim().pos.add(lift);
            bike.sim().riderPos = bike.sim().riderPos.add(lift);
        }
        bike.sim().vel = bike.sim().riderVel = new V3(0, 0, phase == CRASH_PHASE ? 12 : phase == 0 ? 9.2 : phase==7?18:11);
        rideTick = 0;
        BikeCamera.debugSide = phase==7?-1:1;
    }

    static void clientTick() {
        if (!ENABLED || finished) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        tick++;
        if (tick == 20) cmd(p, "ride @s dismount");
        if (tick == 30) {
            // Test platform above the terrain; only tagged pilot bikes are removed.
            o = new BlockPos(p.blockPosition().getX(), 200, p.blockPosition().getZ());
            int x = o.getX(), y = o.getY(), z = o.getZ();
            cmd(p, "kill @e[type=descentmtb:mountain_bike,tag=mtb_autopilot]");
            cmd(p, "time set 6000"); cmd(p, "weather clear");
            cmd(p, String.format(Locale.ROOT, "tp @s %.1f %d %.1f 0 0", x + .5, y, z + .5));
            cmd(p, String.format("fill %d %d %d %d %d %d minecraft:grass_block", x - 4, y - 1, z - 2, x + 4, y - 1, z + 70));
            cmd(p, String.format("fill %d %d %d %d %d %d minecraft:air", x - 4, y, z - 2, x + 4, y + 14, z + 70));
            cmd(p, String.format("fill %d %d %d %d %d %d descentmtb:ramp[facing=south,start=0,end=6,profile=concave]", x - 1, y, z + 24, x + 1, y, z + 24));
            cmd(p, String.format("fill %d %d %d %d %d %d descentmtb:ramp[facing=south,start=6,end=16,profile=linear]", x - 1, y, z + 25, x + 1, y, z + 25));
            cmd(p, String.format("fill %d %d %d %d %d %d minecraft:stone", x - 4, y, z + 62, x + 4, y + 3, z + 62));
            if (WALL_ONLY) cmd(p, String.format("fill %d %d %d %d %d %d minecraft:stone", x - 1, y, z + 27, x - 1, y + 4, z + 43));

        }
        if (tick == 35) {
            if (WALL_ONLY) mc.getSingleplayerServer().execute(() -> com.descentmtb.world.DevWallTests.run(mc.getSingleplayerServer().overworld(), o));
            else cmd(p,"mtbdevtrail");
        }
        if (tick == 45) spawn(p);
        if (tick == 60) {
            boolean passed = WALL_ONLY ? com.descentmtb.world.DevWallTests.PASSED : com.descentmtb.trail.DevTrailTests.PASSED;
            boolean failed = WALL_ONLY ? com.descentmtb.world.DevWallTests.FAILED : com.descentmtb.trail.DevTrailTests.FAILED;
            if (!passed) { if (failed) fail(mc, "world construction/collision tests failed"); else tick = 59; return; }
        }
        if (tick == 60 || tick == mountAt) {
            mountAt = -1;
            cmd(p, "ride @s mount @e[type=descentmtb:mountain_bike,tag=mtb_autopilot,limit=1,sort=nearest]");
        }
        if (tick < 60 || mountAt >= 0) return;
        if (p.getVehicle() instanceof MountainBikeEntity bike) {
            if (remountSent) {
                if(sableWait<0){cmd(p,"mtbdevsable");sableWait=0;}
                sableWait++;
                if(com.descentmtb.world.DevSableTests.FAILED){fail(mc,"Sable integration test failed");return;}
                if(!com.descentmtb.world.DevSableTests.PASSED){if(sableWait>180)fail(mc,"Sable integration test timed out");return;}
                if(!com.descentmtb.trail.DevPumpTrack.BUILT) {
                    if(com.descentmtb.trail.DevPumpTrack.FAILED){fail(mc,"pumptrack simulation failed");return;}
                    if(!pumpSent){pumpSent=true;cmd(p,"mtbdevpump "+(o.getX()+80)+" "+(o.getZ()+80));}
                    return;
                }
                var pump=com.descentmtb.trail.DevPumpTrack.RIDE;
                if(pump==null||pump.bailed()||pump.laps()<.8){fail(mc,"pumptrack did not complete: "+pump);return;}
                DescentMtb.LOG.info("[autopilot] PASS: enduro jump, hardtail tailwhip/barspin, gentle drop, manual/no-hander, natural whip/table, wallride, ragdoll, standing, remount and a full pumptrack ride");
                finished = true;
                BikeCamera.debugSide = 0;
                mc.options.hideGui = false;
                BikeClientController.toast(net.minecraft.network.chat.Component.translatable("descentmtb.dev.tests_passed").getString());
                shot(mc, "remounted");
                if (!KEEP_OPEN) mc.stop();
                return;
            }
            if (rideTick >= 0) script(mc, bike, ++rideTick);
        } else if (rideTick > 0 && afterBail < 0) {
            if (phase != CRASH_PHASE) { fail(mc, "unexpected bail in jump/trick phase " + phase); return; }
            afterBail = 0;
        }
        if (afterBail >= 0) {
            afterBail++;
            sawRagdoll |= RagdollClient.localActive();
            if (afterBail == 4) shot(mc, "ragdoll_air");
            if (afterBail == 25) shot(mc, "ragdoll_down");
            if (afterBail == 70) shot(mc, "ragdoll_up");
            if (afterBail >= 125 && !remountSent) {
                if (!sawRagdoll || RagdollClient.localLocked() || crashedBike == null) {
                    fail(mc, "ragdoll did not recover: seen=" + sawRagdoll + " locked=" + RagdollClient.localLocked() + " bike=" + (crashedBike != null)); return;
                }
                shot(mc, "after_walk_view");
                cmd(p, String.format(Locale.ROOT, "tp @s %.3f %.3f %.3f", crashedBike.getX(), crashedBike.getY() + 1, crashedBike.getZ()));
                cmd(p, "ride @s mount @e[type=descentmtb:mountain_bike,tag=mtb_autopilot,limit=1,sort=nearest]");
                remountSent = true;
            }
        }
        if (tick > 1800) fail(mc, "timed out in phase " + phase);
    }

    static BikeInputHandler.Frame frame() {
        MountainBikeEntity bike = BikeClientController.riding();
        double z = bike != null ? bike.getZ() - o.getZ() : 0;
        float body = phase == 3 ? 0 : z > 19.5 && z < 24.6 ? -1 : z >= 24.6 && z < 27 ? 1 : 0;
        boolean trick = (phase == 1 || phase == 2 || phase == 4) && bike != null && bike.sim() != null && bike.sim().airborne && z > 25.8;
        double air=bike!=null&&bike.sim()!=null&&bike.sim().airborne?bike.sim().airTime:0;
        float airSteer=phase==5&&z>26.5&&air>.15?(air<.42?.65f:air<.72?-.65f:0):0;
        float tweak=phase==6&&air>.08&&air<.65?1:phase==7&&air>0&&z>26.5&&z<42?1:0;
        float lean = phase == 4 && z > 6 && z < 17 ? -1 : 0;
        return new BikeInputHandler.Frame(new Controls(airSteer, lean, 1, 0, body, tweak,
                trick, trick && phase != 4 ? 1 : 0, trick ? phase == 1 ? -1 : phase == 4 ? 1 : 0 : 0), false, false, false, false);
    }

    private static void script(Minecraft mc, MountainBikeEntity bike, int t) {
        if (t == 5) {mc.options.hideGui = phase < 5;mc.gui.getChat().clearMessages(false);}
        if (t == 20) shot(mc, phase == 0 ? "enduro_side" : phase == CRASH_PHASE ? "crash_run_in" : phase == 1 || phase == 2 ? "hardtail_side" : "gentle_drop");
        double z = bike.getZ() - o.getZ();
        if (bike.sim() == null) return;
        if (bike.sim().airborne && (z > 25 || phase == 3)) {
            flew = true;
            if (!shotAir && phase == 0 && z > 26.6) { shot(mc, "ramp_air_side"); shotAir = true; }
        }
        if (phase == 4 && !sawManual && z > 10 && z < 17 && !bike.sim().front.contact && bike.sim().rear.contact) {
            shot(mc, "manual"); sawManual = true;
        }
        if (bike.sim().tricks.trick != Trick.NONE && (bike.sim().tricks.progress > .3 || bike.sim().tricks.amount > .8) && !shotTrick) {
            shot(mc, phase == 1 ? "hardtail_tailwhip" : phase == 2 ? "hardtail_barspin" : "no_hander"); shotTrick = true;
        }
        if(phase==5&&bike.sim().maxWhip>.55&&bike.sim().airTime>.4&&!shotTrick){shot(mc,"natural_whip");shotTrick=true;}
        if(phase==6&&bike.sim().maxTable>.85&&bike.sim().airTime>.4&&!shotTrick){shot(mc,"natural_table");shotTrick=true;}
        if(phase==7&&bike.sim().wallRide){sawWall=true;if(Math.abs(bike.sim().lean)>1.1&&!shotTrick){shot(mc,"wallride");shotTrick=true;}}
        if (phase < CRASH_PHASE && z > (phase==7?45:38) && !bike.sim().airborne) {
            if ((phase==7&&!sawWall)||((phase==5||phase==6)&&!shotTrick)||!flew || ((phase == 1 || phase == 2 || phase == 4) && !shotTrick) || (phase == 4 && !sawManual)) {
                fail(mc, "missing jump/trick/manual in phase " + phase + " flew="+flew+" trick="+shotTrick+" wall="+sawWall); return;
            }
            DescentMtb.LOG.info("[autopilot] phase {} passed: jump landed, trick={}", phase, shotTrick);
            if (WALL_ONLY) {
                finished = true;
                DescentMtb.LOG.info("[walltest] CLIENT PASS: ramp takeoff, sustained wallride, wall release and landing");
                shot(mc, "wallride_landed");
                mc.stop();
                return;
            }
            phase++;
            if(phase==7)cmd(mc.player,String.format("fill %d %d %d %d %d %d minecraft:stone",o.getX()-1,o.getY(),o.getZ()+27,o.getX()-1,o.getY()+4,o.getZ()+43));
            if(phase==8)cmd(mc.player,String.format("fill %d %d %d %d %d %d minecraft:air",o.getX()-1,o.getY(),o.getZ()+27,o.getX()-1,o.getY()+4,o.getZ()+43));
            rideTick = -1; flew = shotAir = shotTrick = false;
            cmd(mc.player, "ride @s dismount");
            cmd(mc.player, "kill @e[type=descentmtb:mountain_bike,tag=mtb_autopilot]");
            spawn(mc.player); mountAt = tick + 10;
        }
        if (phase == CRASH_PHASE) crashedBike = bike;
    }

    private static void spawn(LocalPlayer p) {
        cmd(p, String.format(Locale.ROOT,
                "summon descentmtb:mountain_bike %.1f %d %.1f {Rotation:[0f,0f],BikeType:%d,Tags:[\"mtb_autopilot\"]}",
                o.getX() + .5, o.getY(), o.getZ() + (phase == CRASH_PHASE ? 50.5 : 2.5), phase == 1 || phase == 2 ? 1 : 0));
    }
    private static void cmd(LocalPlayer p, String c) { p.connection.sendCommand(c); }
    private static void fail(Minecraft mc, String why) { DescentMtb.LOG.error("[autopilot] FAIL: {}", why); mc.stop(); }
    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "mtb_" + name + ".png", mc.getMainRenderTarget(),
                msg -> DescentMtb.LOG.info("[autopilot] {}", msg.getString()));
    }
    private DevAutopilot() {}
}
