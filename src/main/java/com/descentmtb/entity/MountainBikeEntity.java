package com.descentmtb.entity;

import com.descentmtb.network.BikeStatePayload;
import com.descentmtb.physics.BikeParams;
import com.descentmtb.physics.BikeSim;
import com.descentmtb.physics.BlockTerrain;
import com.descentmtb.physics.Controls;
import com.descentmtb.physics.V3;
import com.descentmtb.registry.ModItems;
import com.descentmtb.world.McColumns;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
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

    /** Shared tuning (config hooks into this later). */
    public static final BikeParams PARAMS = new BikeParams();

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

    @SuppressWarnings("unchecked")
    private static <T> EntityDataAccessor<T> def(net.minecraft.network.syncher.EntityDataSerializer<T> s) {
        return SynchedEntityData.defineId(MountainBikeEntity.class, s);
    }

    // ---------------- local simulation (rider's client only) ----------------
    private BikeSim sim;
    private McColumns columns;
    private boolean simulating;

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
        sim = new BikeSim(PARAMS, new BlockTerrain(columns));
        sim.place(getX(), getY(), getZ(), Math.toRadians(getYRot()));
        simulating = true;
        rsCur.fromSim(sim, null);
        rsPrev.copyFrom(rsCur);
    }

    /** Puts the bike back on the ground at a spot (respawn). Rider's client only. */
    public void respawnAt(double x, double groundY, double z, double yawRad) {
        if (!simulating) startSim();
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
    public BikeStatePayload statePayload(boolean teleport) {
        byte flags = 0;
        if (sim.airborne) flags |= BikeStatePayload.AIRBORNE;
        if (sim.bailed) flags |= BikeStatePayload.BAILED;
        if (teleport) flags |= BikeStatePayload.TELEPORT;
        return new BikeStatePayload(getId(), sim.pos.x, sim.pos.y, sim.pos.z, (float) sim.yaw, (float) sim.pitch,
                (float) sim.lean, (float) sim.steerAngle, (float) sim.front.compression, (float) sim.rear.compression,
                (float) sim.riderUp, (float) sim.riderFwd, (float) sim.crankAngle, flags);
    }

    /** Server: accept the rider's simulated state. */
    public void applyRiderState(BikeStatePayload m) {
        setPos(m.x(), m.y() - COM_HEIGHT, m.z());
        setYRot((float) Math.toDegrees(m.yaw()));
        setXRot((float) -Math.toDegrees(m.pitch()));
        entityData.set(D_PITCH, m.pitch());
        entityData.set(D_LEAN, m.lean());
        entityData.set(D_STEER, m.steer());
        entityData.set(D_COMP_F, m.compF());
        entityData.set(D_COMP_R, m.compR());
        entityData.set(D_RIDER_UP, m.riderUp());
        entityData.set(D_RIDER_FWD, m.riderFwd());
        entityData.set(D_CRANK, m.crank());
        entityData.set(D_FLAGS, m.flags());
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
        if (getControllingPassenger() != null) return; // the rider's client drives it
        // parked: just settle onto the ground
        if (!onGround()) {
            setDeltaMovement(getDeltaMovement().add(0, -0.08, 0));
        } else {
            setDeltaMovement(getDeltaMovement().multiply(0.5, 0, 0.5));
        }
        move(MoverType.SELF, getDeltaMovement());
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
        return position().add(side).add(0, 0.1, 0);
    }

    // =====================================================================
    //  Interaction / lifecycle
    // =====================================================================

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive() || isVehicle()) return InteractionResult.PASS;
        if (!level().isClientSide) {
            return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isRemoved() || level().isClientSide) return false;
        if (source.getEntity() instanceof Player player && !isVehicle()) {
            if (!player.getAbilities().instabuild) spawnAtLocation(ModItems.MOUNTAIN_BIKE.get());
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
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        rsCur.fromSynced(this, null);
        rsPrev.copyFrom(rsCur);
    }
}
