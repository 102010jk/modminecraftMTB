package com.descentmtb.physics;

import java.util.ArrayList;
import java.util.List;
import com.descentmtb.entity.BikeType;
import com.descentmtb.trick.Trick;
import com.descentmtb.trick.TrickAnimation;

/**
 * The bike: a rigid frame on two raycast wheels with spring-damper suspension,
 * friction-limited tyres, and a separate rider mass on "leg" and "arm"
 * actuators. Pure Java, no Minecraft.
 *
 * <h2>Model</h2>
 * <ul>
 *   <li><b>Frame</b> - rigid body (16 kg). Orientation is yaw + pitch; roll is
 *       never simulated (a real rider balances it away) and the visible lean is
 *       derived from cornering acceleration instead - this is what keeps it
 *       rideable on blocky terrain, just like Descenders.</li>
 *   <li><b>Wheels</b> - massless, cast along the frame's up axis onto the
 *       (smoothed) ground. Suspension force acts along the ground normal at the
 *       contact patch. Bottom-out is a hard constraint.</li>
 *   <li><b>Tyres</b> - velocity constraints solved with sequential impulses:
 *       lateral grip clamped by μN (static → kinetic when sliding), longitudinal
 *       brake + rolling resistance; pedalling is a force at the rear patch.</li>
 *   <li><b>Rider</b> - a 75 kg particle tied to the frame: laterally rigid,
 *       vertically by force-limited "legs", fore/aft by "arms". Crouch → extend
 *       pushes the frame into the ground and then yanks it up at full
 *       extension: pop, bunny hop and pumping all emerge from this.</li>
 *   <li><b>Assists</b> (Descenders feel) - air control of flips/spins, nose
 *       following the flight path, landing snap, manual helper, speed cap drag.</li>
 * </ul>
 *
 * Conventions: world is Minecraft's (y up, metres). {@code yaw} uses Minecraft's
 * sign: forward = (-sin yaw, 0, cos yaw), increasing yaw turns right. Pitch &gt; 0 = nose up.
 */
public final class BikeSim {
    public final BikeParams p;
    public BikeType bikeType = BikeType.ENDURO;
    public final TrickAnimation tricks = new TrickAnimation();
    private final Terrain terrain;

    // ---------------- frame ----------------
    public V3 pos = V3.ZERO;          // frame COM
    public V3 vel = V3.ZERO;
    public double yaw, pitch;         // rad
    /** World angular velocity; only ever has components along world-up and the frame's right axis. */
    public V3 omega = V3.ZERO;

    // ---------------- rider ----------------
    public V3 riderPos = V3.ZERO, riderVel = V3.ZERO;
    /** Rider offset from neutral along frame up / forward (m) - for animation. */
    public double riderUp, riderFwd;

    // ---------------- wheels & controls ----------------
    public final Wheel front, rear;
    /** Handlebar angle, rad, + = right. */
    public double steerAngle;
    /** Visual lean (roll), rad, + = leaning right. */
    public double lean;
    public double crankAngle, crankRate;
    private double groundFactor;
    private double legTarget, armTarget;
    /** Recent active leg-extension speed (m/s) - how hard the rider popped. */
    private double popMeter;
    /** 0..1 rotation authority for this air session, earned by the pop at take-off. */
    public double airBudget;
    private final Terrain.GroundHit bodyHit = new Terrain.GroundHit();
    /** Pitch of the ground where the current jump will land (ballistic prediction). */
    private double landingPitch;
    private boolean landingKnown;
    private int predictTimer;
    private V3 lastAirVel = V3.ZERO;

    // ---------------- air / landing ----------------
    public boolean airborne;
    /** Rear deliberately stepping out (lean forward + hard steer). */
    public boolean leanDrift;
    public double airTime;
    private double landAssistTimer;
    /** Accumulated rotation in the current air session (rad) - for trick scoring. */
    public double airPitchTravel, airYawTravel;
    public double maxWhip, maxTable, brake;
    public int trickMask;
    public boolean wallRide;
    private int wallSide;
    private V3 wallNormal = V3.ZERO, wallPoint = V3.ZERO, wallVelocity = V3.ZERO;
    private double wallCooldown;
    private double substepTime;
    private final Terrain.RayHit[] wallHits = {new Terrain.RayHit(), new Terrain.RayHit(),
            new Terrain.RayHit(), new Terrain.RayHit(), new Terrain.RayHit()};
    private final Terrain.RayHit collisionHit = new Terrain.RayHit();

    public boolean bailed;
    /**
     * No rider: the bike just rolls, tumbles and settles (after a bail, or parked).
     * Rider forces, assists and bail detection are off; it lies down on its side at rest.
     */
    public boolean riderless;
    private double restSide = 1;
    private boolean bodyGrounded;
    private V3 bodyNormal = V3.Y;
    public String bailReason = "";
    public V3 crashRiderPos = V3.ZERO, crashRiderVel = V3.ZERO;

    /** Things that happened since the caller last drained the list. */
    public final List<Event> events = new ArrayList<>();
    private double loopTimer;

    // ---------------- per-substep frame axes ----------------
    private V3 fH, right, fwd, up;

    public BikeSim(BikeParams params, Terrain terrain) {
        this.p = params;
        this.terrain = terrain;
        this.front = new Wheel(true);
        this.rear = new Wheel(false);
        axes();
    }

    // =====================================================================
    //  Setup
    // =====================================================================

    /** Places the bike standing on the ground at (x, groundY, z), at rest, with suspension at sag. */
    public void place(double x, double groundY, double z, double yawRad) {
        this.yaw = yawRad;
        this.pitch = 0;
        this.lean = 0;
        bodyGrounded = false;
        this.omega = V3.ZERO;
        this.vel = V3.ZERO;
        this.riderVel = V3.ZERO;
        double sag = 0.28 * p.forkTravel;
        axes();
        double gy = Double.NEGATIVE_INFINITY;
        Terrain.GroundHit gh = new Terrain.GroundHit();
        for (int s = -1; s <= 1; s += 2) {
            V3 w = new V3(x, 0, z).addScaled(fH, s * p.halfWheelbase);
            if (terrain.ground(w.x, w.z, groundY + 2.0, groundY - 4.0, gh)) gy = Math.max(gy, gh.height);
        }
        if (gy == Double.NEGATIVE_INFINITY) gy = groundY;
        this.pos = new V3(x, gy + p.wheelRadius - p.axleDrop - sag, z);
        this.riderPos = riderAnchor();
        this.bailed = false;
        this.bailReason = "";
        this.airborne = false;
        this.airTime = 0;
        this.groundFactor = 1;
        this.steerAngle = 0;
        this.lean = 0;
        tricks.reset();
        loopTimer = landAssistTimer = popMeter = 0;
        airPitchTravel = airYawTravel = airBudget = 0;
        maxWhip = maxTable = brake = 0;
        trickMask = 0;
        wallRide = false; wallCooldown = 0;
        wallNormal = wallPoint = wallVelocity = V3.ZERO;
        events.clear();
    }

    // =====================================================================
    //  Stepping
    // =====================================================================

    /** Advances one game tick ({@code dt} seconds, normally 0.05) in {@code p.substeps} substeps. */
    public void tick(Controls c, double dt) {
        Controls in = bailed ? Controls.NONE : c;
        double h = dt / p.substeps;
        substepTime = 0;
        for (int i = 0; i < p.substeps; i++) {
            substep(in, h);
            substepTime += h;
        }
    }

    private void substep(Controls c, double h) {
        axes();
        double g = p.gravity;
        double vFwd = vel.dot(fH);
        double speed = vel.length();

        // ---------- steering: angle limit shrinks with speed (grip-limited carve) ----------
        double avgGrip = 0.5 * (front.grip + rear.grip);
        double demand = p.steerGripDemand * p.corneringGrip * g * Math.max(avgGrip, 0.1);
        // rear brake + full lock asks for more than the tyres give: a deliberate, controlled slide
        if (c.brake > 0.5 && Math.abs(c.steer) > 0.8) {
            demand *= p.driftDemandBoost;
        }
        // Right stick X on the ground leans the body sideways. Into the turn: a tighter carve.
        // Against the turn (bike leaned in, body out) with hard steering: the rear steps out into a drift.
        // weight shifted forward unloads the rear: ask a little less of the tyres so it doesn't wash out by itself
        if (c.lean > 0) {
            demand *= 1 - 0.25 * c.lean;
        }
        double bodyLean = c.trickMod ? 0 : c.tweak;
        double into = bodyLean * Math.signum(c.steer);
        leanDrift = into < -0.5 && Math.abs(c.steer) > 0.5 && Math.abs(vFwd) > 3;
        if (leanDrift) {
            demand *= p.driftDemandBoost;
        } else if (into > 0) {
            demand *= 1 + p.carveBoost * into;
        }
        double v2 = Math.max(vFwd * vFwd, 0.25);
        double maxSteer = clamp(Math.atan(2 * p.halfWheelbase * demand / v2), p.minSteerAngle, p.maxSteerAngle);
        // Already cornering at the limit (yaw rate x speed): ease the lock instead of tightening further.
        // Without this a slowing bike winds the bars in (the limit grows as v falls) and spins out.
        double cornering = Math.abs(omega.dot(V3.Y)) * Math.abs(vFwd) / Math.max(demand, 1e-6);
        if (cornering > .85) {
            maxSteer *= clamp((1.2 - cornering) / .35, .25, 1);
        }
        double steerTarget = c.steer * maxSteer;
        steerAngle = p.steerResponse <= 0 ? steerTarget : steerAngle+(steerTarget-steerAngle)*(1-Math.exp(-h/p.steerResponse));

        // ---------- wheel contacts + suspension forces ----------
        contact(front, h);
        contact(rear, h);
        boolean grounded = front.contact || rear.contact;
        wallContact(c, grounded, h);
        groundFactor += ((grounded ? 1 : 0) - groundFactor) * (1 - Math.exp(-h / 0.05));

        // ---------- rider actuators ----------
        if (!riderless) riderForces(c, grounded, h);

        // ---------- gravity ----------
        vel = vel.addScaled(V3.Y, -g * h);
        riderVel = riderVel.addScaled(V3.Y, -g * h);
        if (wallRide) {
            double tangentSpeed = vel.sub(wallVelocity).reject(wallNormal).horizontalLength();
            double support = clamp(tangentSpeed * tangentSpeed / (13 * 13), 0, .95);
            vel = vel.addScaled(V3.Y, g * support * h);
            riderVel = riderVel.addScaled(V3.Y, g * support * h);
            vel = wallVelocity.add(vel.sub(wallVelocity).mul(Math.exp(-h * .18)));
        }

        // ---------- aero + soft speed cap (uniform decel on bike & rider) ----------
        double drag = 0.5 * p.airDensity * p.dragArea * speed * speed;
        if (speed > p.softSpeedCap) {
            double over = speed - p.softSpeedCap;
            drag += p.softCapDrag * over * over;
        }
        if (speed > 1e-6) {
            V3 dv = vel.mul(-drag / (riderless ? p.bikeMass * 3 : p.totalMass()) / speed * h);
            vel = vel.add(dv);
            riderVel = riderVel.add(dv);
        }

        // ---------- pedalling ----------
        if (rear.contact && c.pedal > 0.01) {
            double vLong = Math.max(pointVel(rear.patch).dot(rear.tF), 0);
            double f = Math.min(p.pedalMaxForce, p.pedalPower / Math.max(vLong, 1.0)) * c.pedal;
            if (vLong > p.pedalSpinOut) f *= Math.max(0, 1 - (vLong - p.pedalSpinOut) / 1.5);
            f = Math.min(f, rear.grip * rear.load);
            applyImpulse(rear.patch, rear.tF.mul(f * h));
            rear.driveForce = f;
        } else {
            rear.driveForce = 0;
        }

        // ---------- manual helper (lean back on the rear wheel) ----------
        if (p.manualAssist > 0 && c.lean < -0.15 && rear.contact && vFwd > 1.5) {
            double want = -c.lean * p.manualTargetPitch;
            double rel = pitch - groundPitch(rear.normal);
            double tq = p.manualAssist * (-c.lean)
                    * clamp((want - rel) * 3.0 - omega.dot(right) * .65, -1, 1);
            omega = omega.add(angularDelta(right.mul(tq * h)));
        }

        // ---------- velocity constraints (sequential impulses) ----------
        front.resetAccum();
        rear.resetAccum();
        // a riderless bike lying on its side just scrapes along the dirt
        double brakeF = (riderless && Math.abs(lean) > 0.9) ? p.brakeForce : c.brake * p.brakeForce;
        for (int it = 0; it < 8; it++) {
            solveWheel(front, brakeF * p.brakeFrontShare, h);
            solveWheel(rear, brakeF * (1 - p.brakeFrontShare), h);
            if (!riderless) solveRider(h, grounded);
        }
        front.updateSliding();
        rear.updateSliding();
        bodyContacts(h);
        if (grounded || bodyGrounded) wallRide = false;
        if (wallRide) constrainWall();

        // ---------- roll lock ----------
        omega = omega.reject(fH);

        // ---------- air control / landing assist ----------
        if (riderless) {
            // nothing steers or balances a bike nobody is riding
        } else if (wallRide) {
            setOmega(0, -pitch * 3);
        } else if (!grounded) {
            airControl(c, h);
        } else if (landAssistTimer > 0) {
            landAssistTimer -= h;
            Wheel w = front.contact ? front : rear;
            double e = wrap(pitch - groundPitch(w.normal));
            double b = omega.dot(right);
            double bT = Math.abs(e) < p.landingAssistAngle ? -e * p.landingAssistRate : b;
            double a = omega.dot(V3.Y);
            double aT = a;
            if (vel.horizontalLength() > 3) {
                double ye = wrap(Math.atan2(-vel.x, vel.z) - yaw);
                if (Math.abs(ye) < p.bailYawError) aT = -ye * p.landingAssistRate; // yaw' = -a
            }
            double k = 1 - Math.exp(-h / 0.03);
            setOmega(a + (aT - a) * k, b + (bT - b) * k);
        }

        // ---------- obviously over: on its back or standing on its nose ----------
        if (!riderless && (grounded || bodyGrounded)) {
            V3 n = grounded ? (rear.contact ? rear.normal : front.normal) : bodyNormal;
            double rel = Math.abs(wrap(pitch - groundPitch(n)));
            loopTimer = rel > Math.toRadians(80) ? loopTimer + h : 0;
            if (loopTimer > 0.25) bail(rel > Math.PI / 2 ? "flipped over" : "looped out");
        } else loopTimer = 0;

        // An airbag dissipates energy returned by the rider/suspension constraints too.
        // Only an intentional body extension can pop away from the cushion.
        if ((front.contact && front.surface==Terrain.Surface.AIRBAG || rear.contact && rear.surface==Terrain.Surface.AIRBAG) && c.body<.3) {
            vel=new V3(vel.x,Math.min(0,vel.y),vel.z);
            riderVel=new V3(riderVel.x,Math.min(0,riderVel.y),riderVel.z);
        }
        // ---------- integrate positions (with wall probes) ----------
        integrate(h);
        if (riderless) {
            riderPos = pos.addScaled(up, p.riderHeight);
            riderVel = vel;
        }

        // ---------- wheel spin / cranks / lean ----------
        bookkeeping(c, h);
        airState(grounded || (riderless && bodyGrounded), h);
        tricks.tick(c.trickMod ? bikeType.trickFor(c.trickX, c.trickY) : Trick.NONE,
                c.trickX < 0 ? -1 : 1, airborne && !bailed && !riderless, h);
        if (tricks.trick != Trick.NONE && (tricks.amount > .75 || tricks.progress > .85)) trickMask |= 1 << tricks.trick.ordinal();
    }

    // =====================================================================
    //  Wheels
    // =====================================================================

    private void contact(Wheel w, double h) {
        double sign = w.isFront ? 1 : -1;
        V3 ext = pos.addScaled(fwd, sign * p.halfWheelbase).addScaled(up, p.axleDrop);
        double travel = w.isFront ? p.forkTravel : p.shockTravel;
        w.ext = ext;
        boolean was = w.contact;
        w.contact = false;
        w.load = 0;

        if (!wheelGround(ext, travel, w.hit)) {
            w.compression = Math.max(0, w.compression - h * 3.0); // extends in the air
            w.compVel = 0;
            return;
        }
        V3 n = w.hit.normal;
        double upn = up.dot(n);
        if (upn < 0.25) {
            w.compression = Math.max(0, w.compression - h * 3.0);
            return;
        }
        double distE = ext.sub(new V3(ext.x, w.hit.height, ext.z)).dot(n);
        double c = (p.wheelRadius - distE) / upn;
        if (c <= 0) {
            w.compression = Math.max(0, w.compression - h * 3.0);
            w.compVel = 0;
            return;
        }
        double cdot = -pointVel(ext).sub(w.hit.velocity).dot(n) / upn;
        double cc = Math.min(c, travel);
        double rate = w.isFront ? p.forkRate : p.shockRate;
        double prog = w.isFront ? p.forkProgression : p.shockProgression;
        double x = cc / travel;
        double spring = rate * cc * (1 + prog * x * x);
        double damp = cdot > 0
                ? (w.isFront ? p.forkCompDamp : p.shockCompDamp) * cdot
                : (w.isFront ? p.forkRebDamp : p.shockRebDamp) * cdot;
        double load = Math.max(0, spring + damp);
        if(w.hit.surface==Terrain.Surface.AIRBAG){
            // The cushion removes impact energy, instead of storing it for a rebound.
            if(vel.y<0)vel=new V3(vel.x,vel.y*Math.exp(-h*12),vel.z);
            if(riderVel.y<0)riderVel=new V3(riderVel.x,riderVel.y*Math.exp(-h*12),riderVel.z);
            omega=omega.mul(Math.exp(-h*4));
            load=Math.min(load,(p.bikeMass+p.riderMass)*p.gravity*.51);
        }

        w.contact = true;
        w.justLanded = !was;
        w.compression = cc;
        w.compVel = cdot;
        w.overshoot = c - travel;     // > 0 = bottomed out / wheel penetrating
        w.penetration = Math.max(0, w.overshoot) * upn;
        w.normal = n;
        w.grip = w.hit.grip * p.tyreGrip;
        w.rollRes = w.hit.rollRes * p.tyreRolling;
        w.surface = w.hit.surface;
        V3 centre = ext.addScaled(up, cc);
        w.patch = centre.addScaled(n, -p.wheelRadius);

        // tyre directions in the contact plane
        V3 heading = w.isFront
                ? fwd.mul(Math.cos(steerAngle)).addScaled(right, Math.sin(steerAngle))
                : fwd;
        V3 tF = heading.reject(n).normalize();
        if (tF.lengthSq() < 0.5) tF = fH.reject(n).normalize();
        w.tF = tF;
        w.tL = n.cross(tF).mul(-1).normalize(); // points to the frame's right

        w.load = load;
        applyImpulse(w.patch, n.mul(load * h));
    }

    /** Acquire an actual continuous wall face at the tyres, then retain that face without held input. */
    private void wallContact(Controls c, boolean grounded, double h) {
        boolean wasRiding = wallRide;
        wallRide = false;
        wallCooldown = Math.max(0, wallCooldown - h);
        if (!p.wallRides || riderless || bailed || grounded || wallCooldown > 0
                || Math.abs(pitch) > Math.toRadians(60) || !clearOfFloor()) return;
        WallFace best = null;
        for (int side : new int[]{-1, 1}) {
            boolean retaining = wasRiding && side == wallSide;
            V3 toward = retaining ? wallNormal.horizontal().normalize().mul(-1) : right.mul(side);
            WallFace face = wallFace(toward, side, h, retaining ? wallVelocity : V3.ZERO);
            if (face == null || retaining && face.normal.dot(wallNormal) < .9) continue;
            V3 relative = vel.sub(face.velocity);
            double tangentSpeed = relative.reject(face.normal).horizontalLength();
            double into = -relative.dot(face.normal);
            double input = Math.max(c.steer * side, c.tweak * side);
            double awayInput = Math.min(c.steer * side, c.tweak * side);
            if (tangentSpeed < p.wallRideMinSpeed * (retaining ? .85 : 1)
                    || into < -.6 || into > tangentSpeed * .65 || into > p.wallCrashSpeed
                    || Math.abs(fH.dot(face.normal)) > .65) continue;
            if (retaining && awayInput < -.35) { wallCooldown = .15; return; }
            if (!retaining && awayInput < -.35) continue;
            double reach = p.wheelRadius - p.axleDrop + .04;
            if (face.distance > reach + Math.max(0, into * h) + (retaining ? .04 : 0)) continue;
            // A parallel fly-by needs deliberate lean/steer or actual tyre proximity.
            if (!retaining && into < .2 && input < .15 && face.distance > reach - .12) continue;
            if (best == null || face.distance < best.distance) best = face;
        }
        if (best == null) return;
        wallRide = true; wallSide = best.side;
        wallNormal = best.normal; wallPoint = best.point; wallVelocity = best.velocity;
    }

    private boolean clearOfFloor() {
        for (int sign : new int[]{-1, 1}) {
            V3 axle = pos.addScaled(fwd, sign * p.halfWheelbase).addScaled(up, p.axleDrop);
            double bottom = axle.y - p.wheelRadius;
            if (terrain.floor(axle.x, axle.z, axle.y + .05, bottom - .15, bodyHit)
                    && bodyHit.height >= bottom - .15) return false;
        }
        return true;
    }

    private record WallFace(V3 point, V3 normal, V3 velocity, double distance, int side) {}

    private WallFace wallFace(V3 toward, int side, double h, V3 previousVelocity) {
        double reach = p.wheelRadius - p.axleDrop + .08 + Math.min(.12, vel.horizontalLength() * h);
        V3 centre = pos.addScaled(up, p.axleDrop);
        V3[] origins = {centre, pos.addScaled(fwd, p.halfWheelbase).addScaled(up, p.axleDrop + front.compression),
                pos.addScaled(fwd, -p.halfWheelbase).addScaled(up, p.axleDrop + rear.compression),
                centre.addScaled(V3.Y, .35), centre.addScaled(V3.Y, .75)};
        // Minecraft/Sable poses update once a tick; the bike runs several substeps inside it.
        // Query in that frozen pose, then advance the returned contact by its surface velocity.
        V3 offset = previousVelocity.mul(substepTime);
        for (int i = 0; i < origins.length; i++) {
            V3 origin = origins[i].sub(offset);
            if (!terrain.raycast(origin, origin.addScaled(toward, reach), wallHits[i])) return null;
            Terrain.RayHit hit = wallHits[i];
            if (i == 0 && previousVelocity.lengthSq() == 0 && hit.velocity.lengthSq() > 0) {
                offset = hit.velocity.mul(substepTime);
                origin = origins[i].sub(offset);
                if (!terrain.raycast(origin, origin.addScaled(toward, reach), hit)) return null;
            }
            hit.point = hit.point.add(offset);
            if (Math.abs(hit.normal.y) > .35 || hit.normal.dot(toward) > -.5) return null;
            if (i > 0 && (hit.normal.dot(wallHits[0].normal) < .96
                    || Math.abs(hit.point.sub(wallHits[0].point).dot(wallHits[0].normal)) > .06
                    || hit.velocity.sub(wallHits[0].velocity).length() > 1)) return null;
        }
        Terrain.RayHit hit = wallHits[0];
        return new WallFace(hit.point, hit.normal, hit.velocity,
                centre.sub(hit.point).dot(hit.normal), side);
    }

    private void constrainWall() {
        vel = vel.addScaled(wallNormal, -Math.min(0, vel.sub(wallVelocity).dot(wallNormal)));
        riderVel = riderVel.addScaled(wallNormal, -Math.min(0, riderVel.sub(wallVelocity).dot(wallNormal)));
        double distance = pos.addScaled(up, p.axleDrop).sub(wallPoint).dot(wallNormal);
        double tyreReach = (p.wheelRadius - p.axleDrop) * Math.abs(Math.sin(lean));
        double correction = Math.min(.04, Math.max(0, tyreReach - distance));
        if (correction > 0) {
            pos = pos.addScaled(wallNormal, correction);
            riderPos = riderPos.addScaled(wallNormal, correction);
        }
    }

    /** Keep the tyre supported by a lip until its round tread clears the edge. */
    private boolean wheelGround(V3 ext, double travel, Terrain.GroundHit hit) {
        boolean found = terrain.ground(ext.x, ext.z, ext.y + 1.3,
                ext.y - p.wheelRadius - travel - 1.5, hit);
        double best = found ? (p.wheelRadius - (ext.y - hit.height) * hit.normal.y)
                / Math.max(0.25, up.dot(hit.normal)) : -Double.MAX_VALUE;
        // On an ordinary slope the central plane is already exact. Edge samples
        // supply a rounded tread contact only when a surface stops abruptly.
        for (int i = -4; i <= 4; i++) {
            if (i == 0) continue;
            double offset = p.wheelRadius * i / 5.0;
            V3 sample = ext.addScaled(fH, offset);
            if (!terrain.ground(sample.x, sample.z, ext.y + 1.3,
                    ext.y - p.wheelRadius - travel - 1.5, bodyHit)) continue;
            double support = bodyHit.height + Math.sqrt(p.wheelRadius * p.wheelRadius - offset * offset);
            double c = (support - ext.y) / Math.max(0.25, up.y);
            if (c <= best) continue;
            best = c;
            // Express the rounded contact in the wheel's usual plane format.
            hit.set(ext.y + (c * up.dot(bodyHit.normal) - p.wheelRadius) / bodyHit.normal.y,
                    bodyHit.normal, bodyHit.surface);
            hit.velocity = bodyHit.velocity;
            found = true;
        }
        return found;
    }

    private void solveWheel(Wheel w, double brakeF, double h) {
        if (!w.contact) return;
        V3 P = w.patch;

        // bottom-out / penetration: no closing velocity along the normal
        if (w.overshoot > 0) {
            double vn = pointVel(P).sub(w.hit.velocity).dot(w.normal);
            double k = invMass(P, w.normal);
            double j = -vn / k;
            double old = w.accN;
            w.accN = Math.max(0, old + j);
            j = w.accN - old;
            if (j != 0) applyImpulse(P, w.normal.mul(j));
        }
        double nLoad = w.load + w.accN / h;
        double mu = w.grip;

        // longitudinal: brakes + rolling resistance (static hold at zero speed)
        double vl = pointVel(P).sub(w.hit.velocity).dot(w.tF);
        double kl = invMass(P, w.tF);
        double limL = Math.min(brakeF + w.rollRes * nLoad, mu * nLoad) * h;
        double jl = -vl / kl;
        double oldL = w.accL;
        w.accL = clamp(oldL + jl, -limL, limL);
        jl = w.accL - oldL;
        if (jl != 0) applyImpulse(P, w.tF.mul(jl));

        // lateral grip, friction ellipse with whatever longitudinal force is used
        double vs = pointVel(P).sub(w.hit.velocity).dot(w.tL);
        double ks = invMass(P, w.tL);
        double fLong = Math.abs(w.accL) / h + (w == rear ? rear.driveForce : 0);
        double muEff = w.sliding ? mu * p.slideFriction : mu;
        if (leanDrift && w == rear) {
            muEff *= p.driftRearGrip;
        }
        double budget = Math.sqrt(Math.max(0, sq(muEff * p.corneringGrip * nLoad) - sq(fLong)));
        double limS = budget * h;
        double js = -vs / ks * p.lateralStiffness;
        double oldS = w.accS;
        w.accS = clamp(oldS + js, -limS, limS);
        w.latSaturated = Math.abs(w.accS) >= limS * 0.999 && limS > 0;
        js = w.accS - oldS;
        if (js != 0) applyImpulse(P, w.tL.mul(js));
    }

    // =====================================================================
    //  Rider
    // =====================================================================

    private V3 riderAnchor() {
        return pos.addScaled(up, p.riderHeight).addScaled(fwd, p.riderForward);
    }

    private void riderForces(Controls c, boolean grounded, double h) {
        V3 anchor = riderAnchor();
        V3 d = riderPos.sub(anchor);
        double hr = d.dot(up);
        double sr = d.dot(fwd);
        riderUp = hr;
        riderFwd = sr;
        V3 vr = riderVel.sub(pointVel(anchor));
        double hd = vr.dot(up);
        double sd = vr.dot(fwd);

        // ---- leg target (right stick Y) ----
        double hT;
        if (c.trickMod) {
            hT = grounded ? 0 : p.riderTuck;
        } else if (c.body < -0.05) {
            hT = -c.body * p.riderCrouch;
        } else if (c.body > 0.05) {
            hT = c.body * p.riderStretch;
        } else {
            hT = grounded ? 0 : p.riderTuck;
        }
        // ---- arm target (left stick Y on the ground; flips in the air) ----
        double sT = 0;
        if (grounded) sT = c.lean >= 0 ? c.lean * p.riderLeanFwd : -c.lean * p.riderLeanBack;

        legTarget = hT;
        armTarget = sT;
        popMeter *= Math.exp(-h / 0.25);
        if (!grounded) return; // in the air the rider is held rigidly (see solveRider)
        if (!c.trickMod && c.body > 0.3 && hd > popMeter) popMeter = hd;

        double g = p.gravity;
        // muscles: little damping while driving toward the target (explosive pop),
        // heavy damping when being pushed away from it (soaking up a hit)
        // (only when the player actively asks - a passive rider recovers slowly, never "pops")
        boolean legActive = !c.trickMod && Math.abs(c.body) > 0.05;
        boolean armActive = grounded && Math.abs(c.lean) > 0.05;
        double legC = (legActive && hd * (hT - hr) > 0) ? p.legDriveDamping : p.legDamping;
        double armC = (armActive && sd * (sT - sr) > 0) ? p.armDriveDamping : p.armDamping;
        double fUp = p.riderMass * g * V3.Y.dot(up) * groundFactor
                + clamp(p.legStiffness * (hT - hr) - legC * hd, -p.legPullMax, p.legPushMax);
        double fFwd = p.riderMass * g * V3.Y.dot(fwd) * groundFactor
                + clamp(p.armStiffness * (sT - sr) - armC * sd, -p.armMax, p.armMax);

        riderVel = riderVel.addScaled(up, fUp / p.riderMass * h).addScaled(fwd, fFwd / p.riderMass * h);
        double xr = clamp(p.riderForward + sr, -p.halfWheelbase, p.halfWheelbase);
        applyImpulse(pos.addScaled(fwd, xr), up.mul(-fUp * h));
        applyImpulse(pos, fwd.mul(-fFwd * h));
    }

    private void solveRider(double h, boolean grounded) {
        V3 anchor = riderAnchor();
        V3 d = riderPos.sub(anchor);
        double sr = d.dot(fwd);
        if (!grounded) {
            // airborne: bike + rider rotate as one body; the rider only slowly
            // tucks / extends toward the leg target (internal, momentum-conserving)
            double hr = d.dot(up);
            riderConstraint(riderPos, right, clamp(-0.25 * d.dot(right) / h, -0.6, 0.6), false, 0);
            riderConstraint(riderPos, up, clamp((legTarget - hr) * 7.0, -1.6, 1.6), false, 0);
            riderConstraint(riderPos, fwd, clamp((armTarget - sr) * 5.0, -1.0, 1.0), false, 0);
            return;
        }
        V3 attach = pos.addScaled(fwd, clamp(p.riderForward + sr, -p.halfWheelbase, p.halfWheelbase))
                .addScaled(up, 0.3);

        // lateral: rigid (with gentle drift correction)
        double lat = d.dot(right);
        riderConstraint(attach, right, clamp(-0.25 * lat / h, -0.6, 0.6), false, 0);

        // leg / arm hard limits
        double hr = d.dot(up);
        if (hr <= p.riderMin) riderConstraint(attach, up, Math.min(0.2 * (p.riderMin - hr) / h, 0.6), true, 1);
        if (hr >= p.riderMax) riderConstraint(attach, up, -Math.min(0.2 * (hr - p.riderMax) / h, 0.6), true, -1);
        if (sr <= p.riderForeAftMin) riderConstraint(attach, fwd, Math.min(0.2 * (p.riderForeAftMin - sr) / h, 0.6), true, 1);
        if (sr >= p.riderForeAftMax) riderConstraint(attach, fwd, -Math.min(0.2 * (sr - p.riderForeAftMax) / h, 0.6), true, -1);
    }

    /**
     * Drives the rider's velocity relative to the frame point {@code at} along {@code dir}
     * toward {@code target}. One-sided constraints only push in {@code side} direction.
     */
    private void riderConstraint(V3 at, V3 dir, double target, boolean oneSided, int side) {
        double rel = riderVel.sub(pointVel(at)).dot(dir);
        if (oneSided && (side > 0 ? rel >= target : rel <= target)) return;
        double k = 1.0 / p.riderMass + invMass(at, dir);
        double j = (target - rel) / k;
        riderVel = riderVel.addScaled(dir, j / p.riderMass);
        applyImpulse(at, dir.mul(-j));
    }

    // =====================================================================
    //  Frame / rider vs ground (crashes, bike on its side, upside-down landings)
    // =====================================================================

    private void bodyContacts(double h) {
        bodyGrounded = false;
        V3 rolledRight = fwd.cross(up).normalize();
        double tyreHeight = riderless ? p.wheelRadius * Math.hypot(fwd.y, up.y)
                + 0.04 * Math.abs(rolledRight.y) : 0;
        V3[] pts = {
                pos.addScaled(fwd, -0.19).addScaled(up, -0.30),                    // bottom bracket
                pos.addScaled(fwd, -0.35).addScaled(up, 0.45),                     // saddle
                pos.addScaled(fwd, 0.45).addScaled(up, 0.55),                      // bars
                front.contact ? null : pos.addScaled(fwd, p.halfWheelbase).addScaled(up, p.axleDrop).addScaled(V3.Y, -tyreHeight),
                rear.contact ? null : pos.addScaled(fwd, -p.halfWheelbase).addScaled(up, p.axleDrop).addScaled(V3.Y, -tyreHeight),
                riderless ? pos.addScaled(fwd, 0.45).addScaled(up, 0.55).addScaled(rolledRight, 0.40) : null,
                riderless ? pos.addScaled(fwd, 0.45).addScaled(up, 0.55).addScaled(rolledRight, -0.40) : null,
        };
        for (int i = 0; i < pts.length; i++) {
            V3 pt = pts[i];
            if (pt == null || !terrain.ground(pt.x, pt.z, pt.y + 1.0, pt.y - 1.5, bodyHit)) continue;
            double pen = bodyHit.height + 0.02 - pt.y;
            if (pen <= 0) continue;
            bodyGrounded = true;
            bodyNormal = bodyHit.normal;
            V3 n = bodyHit.normal;
            double vn = pointVel(pt).dot(n);
            double jn = 0;
            if (vn < 0) {
                jn = -vn / invMass(pt, n);
                applyImpulse(pt, n.mul(jn));
            }
            V3 vt = pointVel(pt).reject(n);
            double vtl = vt.length();
            if (vtl > 1e-4) {
                V3 td = vt.mul(1 / vtl);
                double jt = Math.min(vtl / invMass(pt, td), 0.6 * (jn + p.bikeMass * p.gravity * h));
                applyImpulse(pt, td.mul(-jt));
            }
            pos = pos.addScaled(n, Math.min(pen * (riderless ? .8 : .3), riderless ? .15 : .05));
            // saddle/bars on the ground only counts as a crash if the bike is clearly over
            // (looped out or nose-planted), not when it just scrapes a steep bank
            boolean over = Math.abs(wrap(pitch - groundPitch(n))) > Math.toRadians(65);
            if ((i == 1 || i == 2) && over && -vn > p.crashSpeed) bail("went over the bars");
        }
        // the rider's body never sinks into the ground either
        if (!riderless && terrain.ground(riderPos.x, riderPos.z, riderPos.y + 1.0, riderPos.y - 1.5, bodyHit)) {
            double pen = bodyHit.height + 0.25 - riderPos.y;
            if (pen > 0) {
                double vn = riderVel.dot(bodyHit.normal);
                if (vn < 0) riderVel = riderVel.addScaled(bodyHit.normal, -vn);
                riderVel = riderVel.mul(Math.exp(-h * 3));  // scrub along the dirt
                riderPos = riderPos.addScaled(bodyHit.normal, Math.min(pen * 0.3, 0.05));
            }
        }
    }

    // =====================================================================
    //  Air
    // =====================================================================

    private void airControl(Controls c, double h) {
        double a = omega.dot(V3.Y);
        double b = omega.dot(right);
        double k = 1 - Math.exp(-h / p.airControlResponse);

        double authority = airBudget * clamp(airTime / p.airRampTime, 0, 1);
        double bT;
        if (Math.abs(c.lean) > 0.15) {
            bT = -c.lean * p.flipRate * authority;      // stick up = frontflip (nose down)
        } else if (Math.cos(pitch) > 0.3 || Math.abs(airPitchTravel) < 1.2) {
            // Descenders-style: the bike settles onto the slope it is going to land on
            if (--predictTimer <= 0) {
                predictLanding();
                predictTimer = 6;
            }
            double target = landingKnown ? landingPitch
                    : Math.atan2(vel.y, Math.max(vel.horizontalLength(), 0.1));
            bT = wrap(target - pitch) * p.airAlignRate * p.airAlignAssist;
        } else {
            bT = b;                                     // mid-flip: keep rotating
        }
        double na;
        if (Math.abs(c.steer) > 0.15) {
            double aT = -c.steer * p.spinRate * authority;
            na = a + (aT - a) * k;
        } else {
            // no spin input: stop turning, and ease the nose toward the direction of flight (yaw error < 60°)
            na = a * Math.exp(-h / p.airSpinDamping);
            double hs = vel.horizontalLength();
            if (hs > 2) {
                double err = wrap(Math.atan2(-vel.x, vel.z) - yaw);
                if (Math.abs(err) < Math.toRadians(60)) {
                    double align = -err * p.airYawAlignRate * p.airAlignAssist;   // omega.Y is -yaw rate
                    na += (align - na) * (1 - Math.exp(-h / 0.12));
                }
            }
        }
        double nb = b + (bT - b) * k;
        V3 old = omega;
        setOmega(na, nb);
        V3 dOmega = omega.sub(old);
        // spin bike + rider together about their common centre of mass
        V3 com = pos.mul(p.bikeMass).addScaled(riderPos, p.riderMass).mul(1.0 / p.totalMass());
        vel = vel.add(dOmega.cross(pos.sub(com)));
        riderVel = riderVel.add(dOmega.cross(riderPos.sub(com)));
    }

    /** Steps the flight path forward to find where (and on what slope) we will touch down. */
    private void predictLanding() {
        landingKnown = false;
        V3 pt = pos;
        V3 v = vel;
        double dt = 0.05;
        for (int i = 0; i < 50; i++) {
            v = v.addScaled(V3.Y, -p.gravity * dt);
            pt = pt.addScaled(v, dt);
            if (terrain.ground(pt.x, pt.z, pt.y + 0.5, pt.y - 1.5, bodyHit)
                    && pt.y - (p.wheelRadius - p.axleDrop) <= bodyHit.height + 0.05) {
                landingPitch = groundPitch(bodyHit.normal);
                landingKnown = true;
                return;
            }
        }
    }

    private void airState(boolean grounded, double h) {
        if (!grounded) {
            if (!airborne) {
                airborne = true;
                airTime = 0;
                airPitchTravel = 0;
                airYawTravel = 0;
                maxWhip = maxTable = 0;
                trickMask = 0;
                // Rotations must be earned at take-off: a real rider cannot start a
                // spin in mid-air. Popping hard (and launching off a lip) buys authority.
                double pop = clamp(popMeter / 1.6, 0, 1);
                double launch = clamp(vel.y / 5.0, 0, 1);
                airBudget = clamp(p.airBudgetBase + 0.85 * pop + 0.35 * launch, p.airBudgetBase, 1);
                predictTimer = 0;
                events.add(new Event(Event.Type.TAKEOFF, vel.length(), ""));
            }
            airTime += h;
            lastAirVel = vel;
            airPitchTravel += omega.dot(right) * h;
            airYawTravel += -omega.dot(V3.Y) * h;
            if (vel.horizontalLength() > 2) maxWhip = Math.max(maxWhip, Math.abs(wrap(yaw - Math.atan2(-vel.x, vel.z))));
            maxTable = Math.max(maxTable, Math.abs(lean));
            return;
        }
        if (airborne) {
            airborne = false;
            onTouchdown();
        }
    }

    private void onTouchdown() {
        Wheel w = front.contact && rear.contact ? (front.load > rear.load ? front : rear)
                : front.contact ? front : rear;
        double pitchErr = Math.abs(wrap(pitch - groundPitch(w.normal)));
        double hs = vel.horizontalLength();
        double yawErr = 0;
        if (hs > 3) {
            double velYaw = Math.atan2(-vel.x, vel.z);
            yawErr = Math.abs(wrap(velYaw - yaw));
        }
        // speed into the ground just before the tyres touched (the suspension has
        // already started slowing us by the time this runs)
        double impact = Math.max(0, -lastAirVel.dot(w.normal));
        if (airTime > 0.25) {
            if (impact > p.bailImpactSpeed && w.surface!=Terrain.Surface.AIRBAG) {
                bail("landed too hard (" + String.format(java.util.Locale.ROOT, "%.1f", impact) + " m/s into the ground)");
            } else if (pitchErr > p.bailPitchError && w.surface!=Terrain.Surface.AIRBAG) {
                bail("landed with the nose " + (int) Math.toDegrees(pitchErr) + "° off");
            } else {
                // Descenders-style "magnet": ease pitch onto the slope and finish an
                // under/over-rotated spin onto the direction of travel
                if (pitchErr < p.landingAssistAngle || yawErr > 0.02) landAssistTimer = 0.18;
            }
        }
        events.add(new Event(Event.Type.LAND, impact,
                String.format(java.util.Locale.ROOT, "air=%.2fs pitchErr=%.0f° yawErr=%.0f°", airTime,
                        Math.toDegrees(pitchErr), Math.toDegrees(yawErr))));
    }

    private void bail(String reason) {
        if (bailed || riderless) return;
        bailed = true;
        crashRiderPos = riderPos;
        crashRiderVel = riderVel;
        bailReason = reason;
        events.add(new Event(Event.Type.BAIL, vel.length(), reason));
    }

    // =====================================================================
    //  Integration & world collision
    // =====================================================================

    private void integrate(double h) {
        V3 before = pos, oldFwd=fwd, oldUp=up;
        V3 step = vel.mul(h);
        // probes: points that must not enter walls (frame-relative)
        V3[] probes = {
                pos.addScaled(fwd, p.halfWheelbase + p.wheelRadius * 0.9).addScaled(up, p.axleDrop + 0.1), // front tyre nose
                pos.addScaled(fwd, 0.42).addScaled(up, 0.55),                                             // bars
                riderPos.addScaled(up, 0.55),                                                              // head
        };
        boolean[] needsTall = {true, false, false};
        for (int i = 0; i < probes.length; i++) {
            V3 a = probes[i];
            V3 probeVelocity = i == 2 ? riderVel : vel;
            V3 b = a.addScaled(probeVelocity, h);
            if (!terrain.raycast(a, b, collisionHit)) continue;
            V3 nrm = collisionHit.normal;
            boolean wall = Math.abs(nrm.y) < .5;
            V3 inside = collisionHit.point.addScaled(nrm, -.01);
            if (needsTall[i] && wall && !terrain.solidAt(inside.x, inside.y + 1.0, inside.z)) continue; // 1-block step: ride it
            double into = -probeVelocity.sub(collisionHit.velocity).dot(nrm);
            if (into <= 0) continue;
            events.add(new Event(Event.Type.HIT, into, "probe " + i + " n=" + nrm + " at " + b));
            if (!wall) {
                // a floor/ceiling, not a wall: tyres handle the ground, but the head or
                // bars hitting it means you went over the bars / landed upside down
                if (i == 0) continue;
                if (into > p.crashSpeed) bail(i == 2 ? "head first into the ground" : "went over the bars");
            } else if (i == 2 && -riderVel.sub(collisionHit.velocity).dot(nrm) > p.crashSpeed) {
                bail("head strike");
            } else if (into > p.wallCrashSpeed) {
                bail("crashed into a wall at " + Math.round(into * 3.6) + " km/h");
            }
            vel = vel.addScaled(nrm, Math.max(0, -vel.sub(collisionHit.velocity).dot(nrm)));
            riderVel = riderVel.addScaled(nrm, Math.max(0, -riderVel.sub(collisionHit.velocity).dot(nrm)));
            step = vel.mul(h);
        }
        pos = pos.add(step);
        riderPos = riderPos.addScaled(riderVel, h);

        // gentle positional fix if a wheel is buried (fast landings)
        for (Wheel w : new Wheel[]{front, rear}) {
            if (w.contact && w.penetration > 0.02) {
                pos = pos.addScaled(w.normal, Math.min((w.penetration - 0.02) * 0.35, 0.05));
            }
        }

        yaw -= omega.dot(V3.Y) * h;
        pitch = wrap(pitch + omega.dot(right) * h);
        protectFloor(before,oldFwd,oldUp);
    }

    /** Sweep the tyres over the floor so a fast fall cannot miss a thin block. */
    private void protectFloor(V3 before,V3 oldFwd,V3 oldUp) {
        axes();
        V3 rolledRight = fwd.cross(up).normalize();
        double radiusY = riderless ? p.wheelRadius * Math.hypot(fwd.y, up.y)
                + .04 * Math.abs(rolledRight.y) : p.wheelRadius;
        double lift = 0;
        Terrain.Surface floorSurface=Terrain.Surface.DIRT;
        for (Wheel w : new Wheel[]{front, rear}) {
            double sign = w.isFront ? 1 : -1;
            V3 point = pos.addScaled(fwd, sign * p.halfWheelbase)
                    .addScaled(up, p.axleDrop + w.compression).addScaled(V3.Y, -radiusY);
            double oldRadiusY=riderless?p.wheelRadius*Math.hypot(oldFwd.y,oldUp.y)+.04*Math.abs(oldFwd.cross(oldUp).normalize().y):p.wheelRadius;
            double oldY=before.addScaled(oldFwd,sign*p.halfWheelbase).addScaled(oldUp,p.axleDrop+w.compression).y-oldRadiusY;
            double top = Math.max(oldY, point.y) + .08;
            if (terrain.floor(point.x, point.z, top, point.y - .6, bodyHit)) {
                if(bodyHit.height-point.y>lift){lift=bodyHit.height-point.y;floorSurface=bodyHit.surface;}
            }
        }
        if (lift > .12) {
            if (airborne && !riderless && floorSurface!=Terrain.Surface.AIRBAG && -vel.y > p.bailImpactSpeed) bail("landed too hard");
            pos = pos.addScaled(V3.Y, lift);
            if (!riderless) riderPos = riderPos.addScaled(V3.Y, lift);
            if (vel.y < 0) vel = new V3(vel.x, 0, vel.z);
        }
    }

    private void bookkeeping(Controls c, double h) {
        brake += (c.brake - brake) * (1 - Math.exp(-h / .06));
        for (Wheel w : new Wheel[]{front, rear}) {
            if (w.contact) {
                w.spinRate = pointVel(w.patch).dot(w.tF) / p.wheelRadius;
                if (w.sliding && c.brake > 0.8) w.spinRate *= 0.2;
            } else {
                double decay = c.brake > 0.1 ? 10 : 0.25;
                w.spinRate *= Math.exp(-h * decay);
                if (w == rear && c.pedal > 0.1) w.spinRate = Math.max(w.spinRate, 18 * c.pedal);
            }
            w.spinAngle += w.spinRate * h;
        }
        if (c.pedal > 0.05) crankRate = Math.max(rear.spinRate, 0) / p.gearRatio;
        else crankRate *= Math.exp(-h * 5);
        crankAngle += crankRate * h;

        double leanT;
        if (riderless) {
            // nobody holding it up: once it slows down it falls over onto its side
            if (Math.abs(lean) > 0.05) restSide = Math.signum(lean);
            leanT = speed() < 6.0 && grounded() ? restSide * 1.38 : lean;
            lean += (leanT - lean) * (1 - Math.exp(-h / 0.35));
            return;
        } else if (!airborne) {
            double latAcc = -omega.dot(V3.Y) * vel.horizontalLength();
            double bodyLean = c.trickMod ? 0 : c.tweak;
            leanT = clamp(Math.atan2(latAcc, p.gravity) * p.leanFactor + bodyLean * p.bodyLeanVisual, -p.leanMax, p.leanMax);
        } else {
            leanT = wallRide ? -wallSide * 1.38 : c.tweak * 1.05;
        }
        lean += (leanT - lean) * (1 - Math.exp(-h / 0.09));
    }

    // =====================================================================
    //  Rigid body helpers
    // =====================================================================

    private void axes() {
        double sy = Math.sin(yaw), cy = Math.cos(yaw);
        fH = new V3(-sy, 0, cy);
        right = fH.cross(V3.Y);
        double sp = Math.sin(pitch), cp = Math.cos(pitch);
        fwd = fH.mul(cp).addScaled(V3.Y, sp);
        up = fH.mul(-sp).addScaled(V3.Y, cp);
        if (riderless) up = up.mul(Math.cos(lean)).addScaled(right, Math.sin(lean));
    }

    private V3 pointVel(V3 point) {
        return vel.add(omega.cross(point.sub(pos)));
    }

    private V3 angularDelta(V3 angImpulse) {
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        double iy = p.inertiaYaw * cp * cp + p.inertiaRoll * sp * sp;
        return V3.Y.mul(angImpulse.dot(V3.Y) / iy).addScaled(right, angImpulse.dot(right) / p.inertiaPitch);
    }

    private void applyImpulse(V3 point, V3 j) {
        vel = vel.addScaled(j, 1.0 / p.bikeMass);
        omega = omega.add(angularDelta(point.sub(pos).cross(j)));
    }

    /** Inverse effective mass of the frame at {@code point} along unit {@code dir}. */
    private double invMass(V3 point, V3 dir) {
        V3 r = point.sub(pos);
        return 1.0 / p.bikeMass + angularDelta(r.cross(dir)).cross(r).dot(dir);
    }

    private void setOmega(double aboutY, double aboutRight) {
        omega = V3.Y.mul(aboutY).addScaled(right, aboutRight);
    }

    /** Pitch of the ground along the current heading, from its normal. */
    private double groundPitch(V3 n) {
        V3 t = fH.reject(n).normalize();
        return Math.asin(clamp(t.y, -1, 1));
    }

    // =====================================================================
    //  Read-outs
    // =====================================================================

    public V3 forward() { axes(); return fwd; }
    public V3 upAxis() { axes(); return up; }
    public V3 rightAxis() { axes(); return right; }
    public double speed() { return vel.length(); }
    public boolean grounded() { return front.contact || rear.contact || (riderless && bodyGrounded); }

    /** Lowest clearance between either tyre and the ground below it (m), for tests/HUD. */
    public double wheelClearance() {
        axes();
        double best = Double.MAX_VALUE;
        Terrain.GroundHit gh = new Terrain.GroundHit();
        for (int s = -1; s <= 1; s += 2) {
            V3 c = pos.addScaled(fwd, s * p.halfWheelbase).addScaled(up, p.axleDrop);
            if (terrain.ground(c.x, c.z, c.y + 0.5, c.y - 30, gh)) {
                best = Math.min(best, c.y - p.wheelRadius - gh.height);
            }
        }
        return best;
    }

    // =====================================================================
    //  Types
    // =====================================================================

    public static final class Wheel {
        public final boolean isFront;
        public boolean contact, justLanded, sliding;
        /** Suspension compression (m) and its rate. */
        public double compression, compVel;
        public double overshoot, penetration;
        public double load;                 // N
        public double grip = 1, rollRes = 0.02;
        public Terrain.Surface surface = Terrain.Surface.DIRT;
        public double spinAngle, spinRate;
        public double driveForce;
        public V3 ext = V3.ZERO, patch = V3.ZERO, normal = V3.Y, tF = V3.ZERO, tL = V3.ZERO;
        final Terrain.GroundHit hit = new Terrain.GroundHit();
        double accN, accL, accS;
        boolean latSaturated;

        Wheel(boolean isFront) {
            this.isFront = isFront;
        }

        void resetAccum() {
            accN = accL = accS = 0;
            latSaturated = false;
        }

        /**
         * A tyre only counts as sliding after it has been at its limit for a few substeps in a row (a single
         * saturated substep is just a bump), and it recovers as soon as it is not saturated any more.
         */
        void updateSliding() {
            satSteps = contact && latSaturated ? Math.min(satSteps + 1, 8) : Math.max(0, satSteps - 2);
            sliding = contact && satSteps >= 3;
        }

        private int satSteps;
    }

    public record Event(Type type, double value, String info) {
        public enum Type { TAKEOFF, LAND, BAIL, HIT }
    }

    // =====================================================================
    //  Maths
    // =====================================================================

    static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    static double sq(double v) {
        return v * v;
    }

    static double wrap(double a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a <= -Math.PI) a += 2 * Math.PI;
        return a;
    }
}
