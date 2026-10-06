package com.descentmtb.entity;

import com.descentmtb.network.BikeStateLimits;
import com.descentmtb.network.BikeStatePayload;
import com.descentmtb.network.RiderSessions;
import com.descentmtb.physics.BikeParams;
import com.descentmtb.physics.BikeSim;
import com.descentmtb.physics.BlockTerrain;
import com.descentmtb.physics.Controls;
import com.descentmtb.physics.V3;
import com.descentmtb.registry.ModItems;
import com.descentmtb.world.McColumns;
import com.descentmtb.custom.BikeBuild;
import com.descentmtb.custom.BikeLights;
import com.descentmtb.item.MountainBikeItem;
import com.descentmtb.registry.ModComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * The bike entity. It is only a carrier: the physics lives in {@link BikeSim}.
 *
 * <p><b>Authority.</b> The rider's own client simulates the bike (240 Hz, see
 * BikeSim) and streams the result to the server in a {@link BikeStatePayload};
 * the server sanity-checks it, moves the entity and shares the visual state with
 * everyone else through synced data. Nobody else simulates a ridden bike - they
 * interpolate. We deliberately do NOT use vanilla vehicle packets: the server
 * would re-collide the hitbox against raw blocks and rubber-band us off the
 * smoothed terrain.
 *
 * <p>The entity position is the ground point under the frame's centre of mass
 * ({@link #COM_HEIGHT} below it).
 */
public class MountainBikeEntity extends Entity {
    /** Entity position → frame centre of mass, metres. */
    public static final double COM_HEIGHT = 0.52;
    public static final float DEFAULT_FRONT_PSI = 26f, DEFAULT_REAR_PSI = 28f, DEFAULT_FORK_PSI = 80f;

    /** Installed by the client mod; runs the local rider's simulation or remote interpolation. */
    public static Consumer<MountainBikeEntity> clientTicker = b -> {};

    private static final EntityDataAccessor<Float> D_PITCH = def(EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> D_LEAN = def(EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> D_STEER = def(EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> D_COMP_F = def(EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> D_COMP_R = def(EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> D_RIDER_UP = def(EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> D_RIDER_FWD = def(EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> D_CRANK = def(EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> D_FLAGS = def(EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> D_TYPE = def(EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> D_TRICK = def(EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> D_TRICK_AMOUNT = def(EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> D_TRICK_PROGRESS = def(EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> D_TRICK_SIDE = def(EntityDataSerializers.INT);
    private BikeParams bikeParams;
    private static final EntityDataAccessor<Float> D_FRONT_PSI = def(EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> D_REAR_PSI = def(EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> D_FORK_PSI = def(EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> D_BRAKE = def(EntityDataSerializers.FLOAT);
    float dBrake() { return entityData.get(D_BRAKE); }
    public float frontPsi() { return entityData.get(D_FRONT_PSI); }
    public float rearPsi() { return entityData.get(D_REAR_PSI); }
    public float forkPsi() { return entityData.get(D_FORK_PSI); }
    /** Sets the pressures (PSI); NaN or infinite values, e.g. from edited item NBT, fall back to the defaults. */
    public void setPressure(float front, float rear, float fork) {
        entityData.set(D_FRONT_PSI, BikeStateLimits.pressure(front, DEFAULT_FRONT_PSI, 5, 65));
        entityData.set(D_REAR_PSI, BikeStateLimits.pressure(rear, DEFAULT_REAR_PSI, 5, 65));
        entityData.set(D_FORK_PSI, BikeStateLimits.pressure(fork, DEFAULT_FORK_PSI, 20, 180));
        serverSim = null; restTicks = 0;
    }

    /** What the player chose for this bike (see {@link BikeBuild}), synced as NBT. Empty tag = the stock look of the type. */
    private static final EntityDataAccessor<CompoundTag> D_BUILD = def(EntityDataSerializers.COMPOUND_TAG);
    private BikeBuild build;

    /** The bike's customisation (never null): the stock look of its type until one was set. */
    public BikeBuild build() {
        if (build == null) {
            CompoundTag tag = entityData.get(D_BUILD);
            build = tag.isEmpty() ? null : BikeBuild.CODEC.parse(NbtOps.INSTANCE, tag).result().map(b -> b.sanitized(isEnduro())).orElse(null);
            if (build == null) build = BikeBuild.defaultFor(isEnduro());
        }
        return build;
    }

    /** Stores a build (clamped to what this bike type allows) and syncs it to every viewer. */
    public void setBuild(BikeBuild b) {
        BikeBuild clean = b.sanitized(isEnduro());
        build = clean;
        entityData.set(D_BUILD, (CompoundTag) BikeBuild.CODEC.encodeStart(NbtOps.INSTANCE, clean).getOrThrow());
    }

    private boolean isEnduro() { return bikeType() == BikeType.ENDURO; }

    /** Takes the build and the tyre / fork pressures an item carries (placing a bike). */
    public void applyFromItem(ItemStack stack) {
        setBuild(MountainBikeItem.buildOf(stack));
        CompoundTag tune = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tune.contains("FrontPsi")) setPressure(tune.getFloat("FrontPsi"), tune.getFloat("RearPsi"), tune.getFloat("ForkPsi"));
    }

    /** The item form of this bike, with its build and pressures: what breaking or picking it up gives back. */
    public ItemStack toItemStack() {
        ItemStack stack = new ItemStack(ModItems.itemFor(bikeType()));
        stack.set(ModComponents.BIKE_BUILD.get(), build());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putFloat("FrontPsi", frontPsi()); tag.putFloat("RearPsi", rearPsi()); tag.putFloat("ForkPsi", forkPsi());
        });
        return stack;
    }

    public BikeType bikeType() { return BikeType.byId(entityData.get(D_TYPE)); }
    public void setBikeType(BikeType type) { entityData.set(D_TYPE, type.ordinal()); bikeParams = null; }
    public BikeParams params() {
        if (bikeParams == null) bikeParams = bikeType().params();
        return bikeParams;
    }
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == D_TYPE) { bikeParams = null; build = null; }
        if (key == D_BUILD) build = null;
    }

    @SuppressWarnings("unchecked")
    private static <T> EntityDataAccessor<T> def(net.minecraft.network.syncher.EntityDataSerializer<T> s) {
        return SynchedEntityData.defineId(MountainBikeEntity.class, s);
    }

    // ---------------- local simulation (rider's client only) ----------------
    private BikeSim sim;
    private McColumns columns;
    private boolean simulating;
    private boolean pendingTeleport;

    // ---------------- server: riderless physics ----------------
    private BikeSim serverSim;
    private McColumns serverColumns;
    private int restTicks;
    /** How often (ticks) a settled bike is woken for one simulation step to check its ground is still there. */
    private static final int SETTLED_TICKS = 60, GROUND_PROBE_INTERVAL = 20, SUPPORT_CHECK_INTERVAL = 5;
    private V3 lastVel = V3.ZERO;
    private double lastPitch, lastLean;
    /** Angular velocity about the vertical axis and about the bike's right axis (BikeSim.omega), from the rider's last packets. */
    private double lastYawOmega, lastPitchOmega;
    private double prevReportYaw, prevReportPitch;
    private long prevReportTick;
    private boolean hasPrevReport;
    /** The blocks under a settled bike, to notice when someone digs them away. */
    private BlockState settledBelow, settledAt;

    // ---------------- render snapshots (all sides that render) ----------------
    public final BikeRenderState rsPrev = new BikeRenderState();
    public final BikeRenderState rsCur = new BikeRenderState();

    // ---------------- remote interpolation ----------------
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ, lerpYRot, lerpXRot;

    public MountainBikeEntity(EntityType<? extends MountainBikeEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    // =====================================================================
    //  Tick
    // =====================================================================

    @Override
    public void tick() {
        super.tick();
        rsPrev.copyFrom(rsCur);
        if (level().isClientSide) {
            clientTicker.accept(this);
        } else {
            serverTick();
        }
    }

    /** Rider's client: advance the physics one tick with this frame's controls. */
    public void driveLocal(Controls c) {
        if (!simulating) startSim();
        columns.newTick();
        sim.tick(c, 0.05);
        syncEntityFromSim();
        rsCur.fromSim(sim, rsPrev);
    }

    private void startSim() {
        if (columns == null) columns = new McColumns(level());
        columns.setLevel(level());
        sim = new BikeSim(params(), columns.terrain());
        sim.bikeType = bikeType();
        sim.place(getX(), getY(), getZ(), Math.toRadians(getYRot()));
        simulating = true;
        rsCur.fromSim(sim, null);
        rsPrev.copyFrom(rsCur);
    }

    /**
     * Puts the bike back on the ground at a spot (the server's respawn / resync answer, or the dev autopilot).
     * Rider's client only. The next state packet carries the TELEPORT flag, which only a development
     * server honours; a real server has already moved the bike itself.
     */
    public void respawnAt(double x, double groundY, double z, double yawRad) {
        if (!simulating) startSim();
        pendingTeleport = true;
        sim.place(x, groundY, z, yawRad);
        syncEntityFromSim();
        rsCur.fromSim(sim, null);
        rsPrev.copyFrom(rsCur);
        setOldPosAndRot();
    }

    public void stopSim() {
        simulating = false;
    }

    public boolean isSimulating() {
        return simulating;
    }

    public BikeSim sim() {
        return sim;
    }

    private void syncEntityFromSim() {
        setPos(sim.pos.x, sim.pos.y - COM_HEIGHT, sim.pos.z);
        setYRot((float) Math.toDegrees(sim.yaw));
        setXRot((float) -Math.toDegrees(sim.pitch));
        setDeltaMovement(sim.vel.x * 0.05, sim.vel.y * 0.05, sim.vel.z * 0.05);
    }

    /** Builds the payload describing this tick's state for the server. */
    public BikeStatePayload statePayload(int epoch) {
        byte flags = 0;
        if (sim.airborne) flags |= BikeStatePayload.AIRBORNE;
        if (sim.bailed) flags |= BikeStatePayload.BAILED;
        if (pendingTeleport) flags |= BikeStatePayload.TELEPORT;
        pendingTeleport = false;
        if (sim.wallRide) flags |= BikeStatePayload.WALL_RIDE;
        return new BikeStatePayload(getId(), sim.pos.x, sim.pos.y, sim.pos.z, (float) sim.yaw, (float) sim.pitch,
                (float) sim.lean, (float) sim.steerAngle, (float) sim.front.compression, (float) sim.rear.compression,
                (float) sim.riderUp, (float) sim.riderFwd, (float) sim.crankAngle, flags,
                (float) sim.vel.x, (float) sim.vel.y, (float) sim.vel.z,
                sim.tricks.trick.ordinal(), (float) sim.tricks.amount, (float) sim.tricks.progress, sim.tricks.side, (float) sim.brake,
                epoch);
    }

    /**
     * Server: accept the rider's simulated state. The caller has checked the position and velocity; every
     * visual value is clamped here (see {@link BikeStateLimits}) because it is shown to all other players.
     */
    public void applyRiderState(BikeStatePayload m) {
        double yaw = BikeStateLimits.wrapAngle(m.yaw());
        float pitch = BikeStateLimits.wrapAngle(m.pitch());
        setPos(m.x(), m.y() - COM_HEIGHT, m.z());
        setYRot((float) Math.toDegrees(yaw));
        setXRot((float) -Math.toDegrees(pitch));
        float lean = BikeStateLimits.lean(m.lean());
        entityData.set(D_PITCH, pitch);
        entityData.set(D_LEAN, lean);
        entityData.set(D_STEER, BikeStateLimits.steer(m.steer()));
        entityData.set(D_COMP_F, BikeStateLimits.compression(m.compF()));
        entityData.set(D_COMP_R, BikeStateLimits.compression(m.compR()));
        entityData.set(D_RIDER_UP, BikeStateLimits.rider(m.riderUp()));
        entityData.set(D_RIDER_FWD, BikeStateLimits.rider(m.riderFwd()));
        entityData.set(D_CRANK, BikeStateLimits.crank(m.crank()));
        entityData.set(D_FLAGS, BikeStateLimits.maskFlags(m.flags()));
        entityData.set(D_TRICK, BikeStateLimits.trickId(m.trickId(), com.descentmtb.trick.Trick.values().length));
        entityData.set(D_TRICK_AMOUNT, BikeStateLimits.unit(m.trickAmount()));
        entityData.set(D_TRICK_PROGRESS, BikeStateLimits.unit(m.trickProgress()));
        entityData.set(D_TRICK_SIDE, BikeStateLimits.trickSide(m.trickSide()));
        entityData.set(D_BRAKE, BikeStateLimits.unit(m.brake()));
        double vx = m.vx(), vy = m.vy(), vz = m.vz();
        double scale = BikeStateLimits.speedScale(vx * vx + vy * vy + vz * vz, BikeStateLimits.MAX_HANDOFF_SPEED);
        lastVel = new V3(vx * scale, vy * scale, vz * scale);
        lastPitch = pitch;
        lastLean = lean;
        trackAngularVelocity(yaw, pitch);
    }

    /**
     * Estimates the bike's angular velocity from consecutive state packets, for the moment a bailed bike is
     * handed to the riderless simulation (otherwise it would stop tumbling the instant the rider leaves).
     */
    private void trackAngularVelocity(double yaw, double pitch) {
        long now = level().getGameTime();
        if (hasPrevReport && now == prevReportTick) return;       // several packets in one tick: wait for the next
        if (hasPrevReport) {
            int ticks = (int) Math.min(now - prevReportTick, 100);
            lastYawOmega = -BikeStateLimits.angularRate(prevReportYaw, yaw, ticks);   // BikeSim: omega.y = -yaw rate
            lastPitchOmega = BikeStateLimits.angularRate(prevReportPitch, pitch, ticks);
        }
        prevReportYaw = yaw;
        prevReportPitch = pitch;
        prevReportTick = now;
        hasPrevReport = true;
    }

    /** Server: the rider asked to respawn; put the bike (and so its rider) on the ground at the chosen spot. */
    public void moveForRespawn(double x, double groundY, double z, double yawDeg) {
        setPos(x, groundY, z);
        setYRot((float) yawDeg);
        setXRot(0);
        setOldPosAndRot();
        entityData.set(D_PITCH, 0f);
        entityData.set(D_LEAN, 0f);
        entityData.set(D_STEER, 0f);
        entityData.set(D_FLAGS, (byte) 0);
        entityData.set(D_TRICK, 0);
        entityData.set(D_TRICK_AMOUNT, 0f);
        lastVel = V3.ZERO;
        lastYawOmega = lastPitchOmega = 0;
        hasPrevReport = false;
    }

    /** Everyone who is not riding this bike: interpolate the server's view of it. */
    public void remoteTick() {
        simulating = false;
        if (lerpSteps > 0) {
            lerpPositionAndRotationStep(lerpSteps, lerpX, lerpY, lerpZ, lerpYRot, lerpXRot);
            lerpSteps--;
        }
        rsCur.fromSynced(this, rsPrev);
    }

    private void serverTick() {
        rsCur.fromSynced(this, rsPrev); // keeps the server-side passenger placed correctly
        BikeLights.tick(this);
        if (getControllingPassenger() != null) {   // the rider's client drives it
            serverSim = null;
            restTicks = 0;
            return;
        }
        // nobody on it: it rolls, tumbles and falls over by itself, on the same smoothed
        // terrain as when ridden (vanilla box collision let parked bikes sink into blocks)
        if (serverSim == null) {
            if (serverColumns == null) serverColumns = new McColumns(level());
            com.descentmtb.physics.BikeTuning.apply(params(),bikeType().params(),frontPsi(),rearPsi(),forkPsi(),1);
            serverSim = new BikeSim(params(), serverColumns.terrain());
            serverSim.riderless = true;
            serverSim.place(getX(), getY(), getZ(), Math.toRadians(getYRot()));
            if (lastVel.lengthSq() > 0.01) {    // just bailed / hopped off: keep the motion, spin included
                serverSim.pos = new V3(getX(), getY() + COM_HEIGHT, getZ());
                serverSim.pitch = lastPitch;
                serverSim.lean = lastLean;
                serverSim.vel = lastVel;
                V3 right = new V3(-Math.sin(serverSim.yaw), 0, Math.cos(serverSim.yaw)).cross(V3.Y);
                serverSim.omega = V3.Y.mul(lastYawOmega).addScaled(right, lastPitchOmega);
            }
            lastVel = V3.ZERO;
            lastYawOmega = lastPitchOmega = 0;
            hasPrevReport = false;
            restTicks = 0;
        }
        // A settled bike sleeps, but not for good: every GROUND_PROBE_INTERVAL ticks, or as soon as a block
        // next to it changes, it takes one simulation step to find out whether its ground is still there.
        boolean settled = restTicks > SETTLED_TICKS;
        if (settled && tickCount % GROUND_PROBE_INTERVAL != 0
                && !(tickCount % SUPPORT_CHECK_INTERVAL == 0 && supportChanged())) return;
        serverColumns.newTick();
        serverSim.tick(Controls.NONE, 0.05);
        serverSim.events.clear();
        BikeSim s = serverSim;
        setPos(s.pos.x, s.pos.y - COM_HEIGHT, s.pos.z);
        setYRot((float) Math.toDegrees(s.yaw));
        setXRot((float) -Math.toDegrees(s.pitch));
        entityData.set(D_PITCH, (float) s.pitch);
        entityData.set(D_LEAN, (float) s.lean);
        entityData.set(D_STEER, 0f);
        entityData.set(D_COMP_F, (float) s.front.compression);
        entityData.set(D_COMP_R, (float) s.rear.compression);
        entityData.set(D_FLAGS, s.airborne ? BikeStatePayload.AIRBORNE : 0);
        entityData.set(D_TRICK, 0);
        entityData.set(D_TRICK_AMOUNT, 0f);
        boolean atRest = s.speed() < 0.05 && s.grounded() && Math.abs(Math.abs(s.lean) - 1.38) < 0.05;
        restTicks = atRest ? Math.min(restTicks + 1, SETTLED_TICKS + 1) : 0;   // moved: awake again
        if (restTicks > SETTLED_TICKS) rememberSupport();
    }

    private void rememberSupport() {
        settledAt = level().getBlockState(blockPosition());
        settledBelow = level().getBlockState(blockPosition().below());
    }

    private boolean supportChanged() {
        return level().getBlockState(blockPosition()) != settledAt || level().getBlockState(blockPosition().below()) != settledBelow;
    }

    // synced visual accessors for remote rendering
    float dPitch() { return entityData.get(D_PITCH); }
    float dLean() { return entityData.get(D_LEAN); }
    float dSteer() { return entityData.get(D_STEER); }
    float dCompF() { return entityData.get(D_COMP_F); }
    float dCompR() { return entityData.get(D_COMP_R); }
    float dRiderUp() { return entityData.get(D_RIDER_UP); }
    float dRiderFwd() { return entityData.get(D_RIDER_FWD); }
    float dCrank() { return entityData.get(D_CRANK); }
    public boolean dBailed() { return (entityData.get(D_FLAGS) & BikeStatePayload.BAILED) != 0; }
    byte entityDataFlags() { return entityData.get(D_FLAGS); }
    int dTrick() { return entityData.get(D_TRICK); }
    float dTrickAmount() { return entityData.get(D_TRICK_AMOUNT); }
    float dTrickProgress() { return entityData.get(D_TRICK_PROGRESS); }
    int dTrickSide() { return entityData.get(D_TRICK_SIDE); }

    // =====================================================================
    //  Rider
    // =====================================================================

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        if (!hasPassenger(passenger)) return;
        // feet on the pedals; the body pose (bend, lean) is drawn by RiderPose
        V3 f = rsCur.bailed ? rsCur.riderPos.addScaled(V3.Y, -0.9) : rsCur.feet();
        move.accept(passenger, f.x, f.y, f.z);
        if (passenger instanceof LivingEntity le) {
            float yaw = (float) Math.toDegrees(rsCur.yaw);
            le.setYBodyRot(yaw);
            le.setYHeadRot(yaw);
            le.setYRot(yaw);
            le.yRotO = yaw;
            le.yBodyRotO = yaw;
            le.yHeadRotO = yaw;
            le.resetFallDistance();
        }
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (!level().isClientSide && passenger instanceof net.minecraft.server.level.ServerPlayer player) {
            hasPrevReport = false;
            RiderSessions.onMount(player);
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide && passenger instanceof net.minecraft.server.level.ServerPlayer player) {
            RiderSessions.onDismount(player);
        }
    }

    @Override
    public boolean shouldRiderSit() {
        return false; // stand on the pedals
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player p ? p : null;
    }

    /** Never vanilla-"controlled" on a client: we sync with our own payload instead. */
    @Override
    public boolean isControlledByLocalInstance() {
        return !level().isClientSide && getControllingPassenger() == null;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Vec3 side = Vec3.directionFromRotation(0, getYRot() + 90).scale(0.8);
        return com.descentmtb.world.SafeDismount.find(level(), passenger, position().add(side).add(0, 0.1, 0));
    }

    // =====================================================================
    //  Interaction / lifecycle
    // =====================================================================

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof com.descentmtb.item.BikePumpItem) {
            if (isVehicle()) return InteractionResult.FAIL;
            if (!level().isClientSide) {
                int valve = com.descentmtb.item.BikePumpItem.valve(player.getItemInHand(hand));
                float step = (player.isShiftKeyDown() ? -1 : 1) * (valve == 2 ? 5 : 2);
                setPressure(frontPsi() + (valve == 0 ? step : 0), rearPsi() + (valve == 1 ? step : 0), forkPsi() + (valve == 2 ? step : 0));
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("descentmtb.pump.pressure",
                        com.descentmtb.item.BikePumpItem.valveName(valve), valve == 0 ? frontPsi() : valve == 1 ? rearPsi() : forkPsi()), true);
                level().playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.PISTON_EXTEND, net.minecraft.sounds.SoundSource.PLAYERS, .35f, 1.5f);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (player.isSecondaryUseActive() && !isVehicle()) {      // sneak + click a parked bike: straight into the inventory
            if (!player.mayBuild()) return InteractionResult.FAIL;
            if (!level().isClientSide) pickUp(player);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (player.isSecondaryUseActive() || isVehicle()) return InteractionResult.PASS;
        if (!level().isClientSide) {
            return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    /** Server: the bike goes into the player's inventory (or drops at their feet when it is full), build and pressures kept. */
    public void pickUp(Player player) {
        ItemStack stack = toItemStack();
        if (!player.getInventory().add(stack)) {
            player.spawnAtLocation(stack);
        }
        level().playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, .4f, 1.1f);
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isRemoved() || level().isClientSide) return false;
        // only players who may break things (not adventure / spectator); creative breaks it instantly
        if (source.getEntity() instanceof Player player && player.mayBuild() && !isVehicle()) {
            if (!player.getAbilities().instabuild) {
                spawnAtLocation(toItemStack());
            }
            discard();
            return true;
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (simulating) return; // our own simulation is authoritative here
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpXRot = xRot;
        lerpSteps = steps;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(D_PITCH, 0f);
        b.define(D_LEAN, 0f);
        b.define(D_STEER, 0f);
        b.define(D_COMP_F, 0.04f);
        b.define(D_COMP_R, 0.04f);
        b.define(D_RIDER_UP, 0f);
        b.define(D_RIDER_FWD, 0f);
        b.define(D_CRANK, 0f);
        b.define(D_FLAGS, (byte) 0);
        b.define(D_TYPE, 0);
        b.define(D_TRICK, 0);
        b.define(D_TRICK_AMOUNT, 0f);
        b.define(D_TRICK_PROGRESS, 0f);
        b.define(D_TRICK_SIDE, 1);
        b.define(D_FRONT_PSI, DEFAULT_FRONT_PSI); b.define(D_REAR_PSI, DEFAULT_REAR_PSI); b.define(D_FORK_PSI, DEFAULT_FORK_PSI);
        b.define(D_BRAKE, 0f);
        b.define(D_BUILD, new CompoundTag());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setBikeType(BikeType.byId(tag.getInt("BikeType")));
        if (tag.contains("FrontPsi")) setPressure(tag.getFloat("FrontPsi"), tag.getFloat("RearPsi"), tag.getFloat("ForkPsi"));
        if (tag.contains("Build", Tag.TAG_COMPOUND)) {   // older bikes have none: they keep the stock look
            BikeBuild.CODEC.parse(NbtOps.INSTANCE, tag.getCompound("Build")).result().ifPresent(this::setBuild);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("BikeType", bikeType().ordinal());
        tag.putFloat("FrontPsi", frontPsi()); tag.putFloat("RearPsi", rearPsi()); tag.putFloat("ForkPsi", forkPsi());
        tag.put("Build", BikeBuild.CODEC.encodeStart(NbtOps.INSTANCE, build()).getOrThrow());
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (!level().isClientSide) BikeLights.release(this);
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        rsCur.fromSynced(this, null);
        rsPrev.copyFrom(rsCur);
    }
}
