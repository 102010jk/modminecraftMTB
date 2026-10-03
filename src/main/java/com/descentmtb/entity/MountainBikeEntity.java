package com.descentmtb.entity;

import com.descentmtb.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A rideable mountain bike with arcade physics modelled on the Automobility mod's
 * vehicle feel (foundationgames), adapted for a downhill bike.
 *
 * <p>Authority model (mirrors vanilla {@code Boat}): when ridden by the local
 * player the controlling client runs the full physics and vanilla streams the
 * position to the server; everywhere else the copy interpolates.
 *
 * <p>Feel, in short:
 * <ul>
 *   <li><b>Steering</b> is a smoothed angular-speed: the turn rate eases toward
 *       {@code steer * (TURN_SPEED*min(speed,1) + TURN_BASE)}, so you turn more
 *       at speed, can't spin on the spot, and direction changes are never
 *       twitchy. The rider's view turns with the bike.</li>
 *   <li><b>Momentum</b> comes from a grip blend of the new velocity with the
 *       previous one (full grip on normal blocks, slides on ice).</li>
 *   <li><b>Terrain</b> is handled by gravity + a real step height (climbs blocks
 *       and stairs); the actual per-tick rise/fall feeds downhill acceleration
 *       and, when you ride off a lip, becomes a real launch - no slope sampling,
 *       so it never pitches to silly angles at block edges.</li>
 *   <li><b>Bunny hop</b>: tap / hold-to-charge / pull-back preload, stacked.</li>
 * </ul>
 *
 * <p>All feel constants are at the top for tuning.
 */
public class MountainBikeEntity extends Entity {

    // ----- longitudinal (blocks / tick) -----
    private static final double GRAVITY = 0.08;
    private static final double TERMINAL = -1.20;
    private static final double COMFORT_SPEED = 0.55;     // pedal target on flat
    private static final double ACCEL = 0.018;
    private static final double OVER_ACCEL_MUL = 0.15;    // accel past comfortable speed
    private static final double REVERSE_ACCEL_MUL = 0.5;
    private static final double BRAKE = 0.06;
    private static final double COAST = 0.02;             // engine zeroing when idle
    private static final double ROLL_FRICTION = 0.994;
    private static final double MAX_SPEED = 1.20;         // downhill can reach this
    private static final double REVERSE_SPEED = -0.20;
    private static final double SLOPE_GAIN = 0.7;         // how much real rise/fall changes speed

    // ----- steering (Automobility-style smoothed angular speed) -----
    private static final float STEER_SMOOTH = 0.30f;      // input smoothing per tick
    private static final float TURN_BASE = 2.0f;          // deg/tick base turn target
    private static final float TURN_SPEED = 4.0f;         // extra deg/tick scaled by speed
    private static final float TURN_ACCEL = 6.0f;         // how fast angular speed eases
    private static final float HANDLING = 1.0f;
    private static final float STEER_SIGN = -1.0f;        // flip if A/D feel mirrored
    private static final float SPEED_DIR_OFFSET = 6.0f;   // deg the body points into a turn
    private static final float AIR_TURN_MUL = 0.35f;      // reduced heading control airborne

    // ----- vertical / terrain -----
    private static final float STEP_HEIGHT = 1.0f;        // climb blocks up to this tall
    private static final double LAUNCH_FACTOR = 0.6;      // lip launch = climbRate capped by speed*this

    // ----- bunny hop -----
    private static final int HOP_CHARGE_TICKS = 10;
    private static final double HOP_BASE = 0.40;
    private static final double HOP_CHARGE_EXTRA = 0.28;
    private static final double HOP_MANUAL_EXTRA = 0.22;

    // ----- visuals -----
    private static final double WHEEL_RADIUS = 0.42;
    private static final float BAR_ANGLE = 0.60f;         // how far the bars visibly turn (rad)
    private static final float LEAN_MAX = 0.32f;          // body tilt from the lean stick (rad)
    private static final float LEAN_SIGN = 1.0f;
    private static final float PITCH_GROUND_GAIN = 2.5f;
    private static final float PITCH_MAX = 0.40f;

    // ----- control input (set by the controlling client each tick) -----
    private BikeInput controlInput = BikeInput.NONE;

    // ----- physics state -----
    private double speed;            // signed horizontal speed along the heading
    private double vSpeed;           // vertical speed
    private float angularSpeed;      // deg/tick yaw rate, smoothed
    private float steerSmooth;       // smoothed steer input -1..1
    private float grip = 1f;         // 1 = full grip, lower on slippery blocks
    private Vec3 lastVel = Vec3.ZERO;
    private final double[] prevYDisp = new double[3];
    private boolean prevGrounded;
    private int hopHeldTicks;
    private boolean hopArmed;
    private float backLoad;          // 0..1 recent pull-back, for the manual/bunny preload

    // ----- visual state (current + previous, for render interpolation) -----
    private float wheelRot, wheelRotO;
    private float roll, rollO;
    private float visPitch, visPitchO;
    private float fork, forkO;
    private float steerVis, steerVisO;

    // ----- interpolation for non-authoritative copies -----
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ, lerpYRot, lerpXRot;

    public MountainBikeEntity(EntityType<? extends MountainBikeEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    public void setControlInput(BikeInput in) {
        this.controlInput = in;
    }

    @Override
    public float maxUpStep() {
        return STEP_HEIGHT;
    }

    // ================================================================
    //  Tick
    // ================================================================
    @Override
    public void tick() {
        this.wheelRotO = this.wheelRot;
        this.rollO = this.roll;
        this.visPitchO = this.visPitch;
        this.forkO = this.fork;
        this.steerVisO = this.steerVis;

        super.tick(); // baseTick

        if (this.isControlledByLocalInstance()) {
            BikeInput in = (this.getControllingPassenger() instanceof Player) ? this.controlInput : BikeInput.NONE;
            this.physicsTick(in);
        } else {
            this.interpolateTick();
        }
    }

    private void physicsTick(BikeInput in) {
        double startY = this.getY();
        this.grip = computeGrip();
        this.steerSmooth += (in.steer - this.steerSmooth) * STEER_SMOOTH;

        boolean grounded = this.prevGrounded
                ? hasFloor(0.40)
                : (hasFloor(0.10) && this.getDeltaMovement().y <= 0.02);
        grounded = grounded || this.onGround();

        // ---- longitudinal speed ----
        if (in.brake > 0.01f) {
            this.speed -= Math.signum(this.speed) * BRAKE * in.brake;
            if (Math.abs(this.speed) < 0.02) this.speed = 0;
        } else if (in.throttle > 0) {
            double a = ACCEL * (this.speed > COMFORT_SPEED ? OVER_ACCEL_MUL : 1.0) * (0.4 + 0.6 * this.grip);
            this.speed += in.throttle * a;
        } else if (in.throttle < 0) {
            this.speed += in.throttle * ACCEL * REVERSE_ACCEL_MUL;
        } else {
            this.speed = zero(this.speed, COAST);
        }
        if (grounded) this.speed += -this.prevYDisp[0] * SLOPE_GAIN; // real downhill accel / uphill drag
        this.speed *= ROLL_FRICTION;
        this.speed = Mth.clamp(this.speed, REVERSE_SPEED, MAX_SPEED);

        // ---- bunny hop (tap / hold-charge / pull-back preload) ----
        if (grounded) this.backLoad = Math.max(this.backLoad * 0.9f, in.throttle < 0 ? -in.throttle : 0f);
        double hopImpulse = updateHop(in, grounded);

        // ---- steering: smoothed angular speed ----
        float turnTarget = 0f;
        if (Math.abs(this.speed) > 0.001) {
            double mag = TURN_SPEED * Math.min(Math.abs(this.speed), 1.0) + TURN_BASE;
            float dirMul = this.speed >= 0 ? 1f : -1f; // reversing inverts steering
            turnTarget = (float) (this.steerSmooth * mag * HANDLING) * STEER_SIGN * dirMul;
            if (!grounded) turnTarget *= AIR_TURN_MUL;
        }
        float traction = (float) (1.0 / (1.0 + 4.0 * Math.abs(this.speed)) + 0.3 * this.grip);
        this.angularSpeed = shift(this.angularSpeed, TURN_ACCEL * traction, turnTarget);
        if (Math.abs(this.angularSpeed) < 1.0e-4f) this.angularSpeed = 0f;
        float yawInc = this.angularSpeed;
        this.setYRot(this.getYRot() + yawInc);

        // ---- vertical ----
        if (hopImpulse > 0) {
            this.vSpeed = hopImpulse;
            grounded = false;
        } else {
            this.vSpeed = Math.max(this.vSpeed - GRAVITY, TERMINAL);
        }

        // ---- assemble velocity (grip/momentum blend with last tick) ----
        double dirDeg = this.getYRot() + this.steerSmooth * SPEED_DIR_OFFSET * STEER_SIGN;
        double ang = Math.toRadians(dirDeg);
        double dvx = -Math.sin(ang) * this.speed;
        double dvz = Math.cos(ang) * this.speed;
        double bx = dvx * this.grip + this.lastVel.x * (1.0 - this.grip);
        double bz = dvz * this.grip + this.lastVel.z * (1.0 - this.grip);
        this.setDeltaMovement(bx, this.vSpeed, bz);

        Vec3 before = this.getDeltaMovement();
        this.move(MoverType.SELF, before);
        this.lastVel = new Vec3(this.getDeltaMovement().x, 0, this.getDeltaMovement().z);

        double yDisp = this.getY() - startY;

        // wall hit -> bleed speed
        if (this.horizontalCollision) {
            this.speed *= 0.4;
            if (Math.abs(this.speed) < 0.03) this.speed = 0;
            this.lastVel = Vec3.ZERO;
        }

        boolean nowFloor = hasFloor(0.12);
        boolean nowGrounded = this.onGround() || nowFloor;

        // ride off a lip while climbing -> convert the climb rate into a launch
        if (this.prevGrounded && !nowGrounded && !nowFloor) {
            double highest = Math.max(0.0, Math.max(this.prevYDisp[0], Math.max(this.prevYDisp[1], this.prevYDisp[2])));
            if (highest > 0) {
                this.vSpeed = Mth.clamp(highest, 0.0, Math.abs(this.speed) * LAUNCH_FACTOR + 0.05);
            }
        }
        if (!this.prevGrounded && nowGrounded) onLanding(this.vSpeed);
        if (nowGrounded && this.vSpeed < 0) this.vSpeed = 0;

        this.prevYDisp[2] = this.prevYDisp[1];
        this.prevYDisp[1] = this.prevYDisp[0];
        this.prevYDisp[0] = yDisp;
        this.prevGrounded = nowGrounded;

        // turn the rider's view with the bike (the "connected" Automobility feel)
        if (this.getControllingPassenger() instanceof Player rider) {
            rider.setYRot(rider.getYRot() + yawInc);
            rider.setYHeadRot(rider.getYRot());
            rider.setYBodyRot(this.getYRot());
        }

        updateVisuals(in, nowGrounded, yDisp);
    }

    /** @return upward impulse to apply this tick (0 = none). */
    private double updateHop(BikeInput in, boolean grounded) {
        double impulse = 0;
        if (in.hop) {
            if (grounded) {
                this.hopHeldTicks = Math.min(this.hopHeldTicks + 1, HOP_CHARGE_TICKS);
                this.fork = Mth.clamp(this.fork + 0.15f, 0f, 1f);
            }
            this.hopArmed = true;
        } else {
            if (this.hopArmed && grounded) {
                float charge = (float) this.hopHeldTicks / HOP_CHARGE_TICKS;
                float manual = this.backLoad;
                impulse = HOP_BASE + charge * HOP_CHARGE_EXTRA + manual * HOP_MANUAL_EXTRA;
                this.speed += 0.04 * (0.5 + charge); // small forward pump
                this.fork = 1.0f;
                this.backLoad = 0f;
            }
            this.hopArmed = false;
            this.hopHeldTicks = 0;
        }
        return impulse;
    }

    private void onLanding(double impactVy) {
        float impact = (float) Math.min(1.0, Math.abs(impactVy) / 1.0);
        this.fork = Math.max(this.fork, impact);
        this.speed *= (1.0 - 0.15 * impact);
    }

    private void updateVisuals(BikeInput in, boolean grounded, double yDisp) {
        this.wheelRot += (float) (this.speed / WHEEL_RADIUS);
        float targetRoll = in.lean * LEAN_MAX * LEAN_SIGN;
        this.roll += (targetRoll - this.roll) * 0.2f;
        float targetPitch = grounded
                ? Mth.clamp((float) (-yDisp * PITCH_GROUND_GAIN), -PITCH_MAX, PITCH_MAX)
                : Mth.clamp((float) (this.vSpeed * 0.6), -0.6f, 0.6f);
        this.visPitch += (targetPitch - this.visPitch) * 0.25f;
        this.fork += (0f - this.fork) * 0.18f;
        float targetSteer = this.steerSmooth * BAR_ANGLE;
        this.steerVis += (targetSteer - this.steerVis) * 0.4f;
    }

    private float computeGrip() {
        BlockPos below = BlockPos.containing(this.getX(), this.getY() - 0.1, this.getZ());
        float slip = this.level().getBlockState(below).getBlock().getFriction(); // 0.6 normal, ~0.98 ice
        float g = 1.0f - Mth.clamp((slip - 0.6f) / 0.4f, 0f, 1f) * 0.85f;
        return g * g;
    }

    /** True if solid ground is within {@code dist} below the wheels. */
    private boolean hasFloor(double dist) {
        double g = sampleGround(this.getX(), this.getY(), this.getZ());
        if (Double.isNaN(g)) return false;
        double d = this.getY() - g;
        return d <= dist && d >= -0.05;
    }

    /** Precise ground height at (x,z) using a short downward collision ray. NaN if none. */
    private double sampleGround(double x, double y, double z) {
        Vec3 start = new Vec3(x, y + 0.6, z);
        Vec3 end = new Vec3(x, y - 1.2, z);
        BlockHitResult hit = this.level().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.MISS) return Double.NaN;
        return hit.getLocation().y;
    }

    private static double zero(double v, double amount) {
        if (v > 0) return Math.max(0, v - amount);
        if (v < 0) return Math.min(0, v + amount);
        return 0;
    }

    private static float shift(float v, float amount, float target) {
        if (v < target) return Math.min(target, v + amount);
        if (v > target) return Math.max(target, v - amount);
        return v;
    }

    private void interpolateTick() {
        if (this.lerpSteps > 0) {
            this.lerpPositionAndRotationStep(this.lerpSteps,
                    this.lerpX, this.lerpY, this.lerpZ, this.lerpYRot, this.lerpXRot);
            this.lerpSteps--;
        }
        double moved = this.position().subtract(this.xOld, this.yOld, this.zOld).horizontalDistance();
        this.wheelRot += (float) (moved / WHEEL_RADIUS);
        this.fork += (0f - this.fork) * 0.18f;
    }

    // ================================================================
    //  Interaction / lifecycle
    // ================================================================
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide) {
            return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isRemoved() || this.level().isClientSide) {
            return false;
        }
        if (source.getEntity() instanceof Player player) {
            this.ejectPassengers();
            if (!player.getAbilities().instabuild) {
                this.spawnAtLocation(ModItems.MOUNTAIN_BIKE.get());
            }
            this.discard();
            return true;
        }
        return false;
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof LivingEntity rider ? rider : null;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return !this.isRemoved();
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpXRot = xRot;
        this.lerpSteps = steps;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // No synced data needed for v1; visuals are derived locally.
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.speed = tag.getDouble("Speed");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putDouble("Speed", this.speed);
    }

    // ================================================================
    //  Render accessors (interpolated)
    // ================================================================
    public float getWheelRot(float pt) { return Mth.lerp(pt, this.wheelRotO, this.wheelRot); }
    public float getRoll(float pt) { return Mth.lerp(pt, this.rollO, this.roll); }
    public float getPitchVis(float pt) { return Mth.lerp(pt, this.visPitchO, this.visPitch); }
    public float getFork(float pt) { return Mth.lerp(pt, this.forkO, this.fork); }
    public float getSteerVis(float pt) { return Mth.lerp(pt, this.steerVisO, this.steerVis); }
}
