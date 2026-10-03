package com.descentmtb.physics;

import java.util.ArrayList;
import java.util.List;

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

    // ---------------- air / landing ----------------
    public boolean airborne;
    public double airTime;
    private double landAssistTimer;
    /** Accumulated rotation in the current air session (rad) - for trick scoring. */
    public double airPitchTravel, airYawTravel;

    public boolean bailed;
    public String bailReason = "";

    /** Things that happened since the caller last drained the list. */
    public final List<Event> events = new ArrayList<>();

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
    }

    // =====================================================================
    //  Stepping
    // =====================================================================

    /** Advances one game tick ({@code dt} seconds, normally 0.05) in {@code p.substeps} substeps. */
    public void tick(Controls c, double dt) {
        Controls in = bailed ? Controls.NONE : c;
        double h = dt / p.substeps;
        for (int i = 0; i < p.substeps; i++) {
            substep(in, h);
        }
    }

    private void substep(Controls c, double h) {
        axes();
        double g = p.gravity;
        double vFwd = vel.dot(fH);
        double speed = vel.length();

        // ---------- steering: angle limit shrinks with speed (grip-limited carve) ----------
        double avgGrip = 0.5 * (front.grip + rear.grip);
        double demand = p.steerGripDemand * g * Math.max(avgGrip, 0.1);
        double v2 = Math.max(vFwd * vFwd, 0.25);
        double maxSteer = clamp(Math.atan(2 * p.halfWheelbase * demand / v2), p.minSteerAngle, p.maxSteerAngle);
        double steerTarget = c.steer * maxSteer;
        steerAngle += (steerTarget - steerAngle) * (1 - Math.exp(-h / p.steerResponse));

        // ---------- wheel contacts + suspension forces ----------
        contact(front, h);
        contact(rear, h);
        boolean grounded = front.contact || rear.contact;
        groundFactor += ((grounded ? 1 : 0) - groundFactor) * (1 - Math.exp(-h / 0.05));

        // ---------- rider actuators ----------
        riderForces(c, grounded, h);

        // ---------- gravity ----------
        vel = vel.addScaled(V3.Y, -g * h);
        riderVel = riderVel.addScaled(V3.Y, -g * h);

        // ---------- aero + soft speed cap (uniform decel on bike & rider) ----------
        double drag = 0.5 * p.airDensity * p.dragArea * speed * speed;
        if (speed > p.softSpeedCap) {
            double over = speed - p.softSpeedCap;
            drag += p.softCapDrag * over * over;
        }
        if (speed > 1e-6) {
            V3 dv = vel.mul(-drag / p.totalMass() / speed * h);
            vel = vel.add(dv);
            riderVel = riderVel.add(dv);
        }

        // ---------- pedalling ----------
        if (rear.contact && c.pedal > 0.01) {
            double vLong = Math.max(pointVel(rear.patch).dot(rear.tF), 0);
            double f = Math.min(p.pedalMaxForce, p.pedalPower / Math.max(vLong, 1.0)) * c.pedal;
            if (vLong > p.pedalSpinOut) f *= Math.max(0, 1 - (vLong - p.pedalSpinOut) / 3.0);
            f = Math.min(f, rear.grip * rear.load);
            applyImpulse(rear.patch, rear.tF.mul(f * h));
            rear.driveForce = f;
        } else {
            rear.driveForce = 0;
        }

        // ---------- manual helper (lean back on the rear wheel) ----------
        if (c.lean < -0.3 && rear.contact && vFwd > 1.5) {
            double want = -c.lean * p.manualTargetPitch;
            double rel = pitch - groundPitch(rear.normal);
            double tq = p.manualAssist * (-c.lean) * clamp((want - rel) * 4.0, -1, 1);
            omega = omega.add(angularDelta(right.mul(tq * h)));
        }

        // ---------- velocity constraints (sequential impulses) ----------
        front.resetAccum();
        rear.resetAccum();
        double brakeF = c.brake * p.brakeForce;
        for (int it = 0; it < 8; it++) {
            solveWheel(front, brakeF * p.brakeFrontShare, h);
            solveWheel(rear, brakeF * (1 - p.brakeFrontShare), h);
            solveRider(h, grounded);
        }
        front.sliding = front.contact && front.latSaturated;
        rear.sliding = rear.contact && rear.latSaturated;
        bodyContacts(h);

        // ---------- roll lock ----------
        omega = omega.reject(fH);

        // ---------- air control / landing assist ----------
        if (!grounded) {
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

        // ---------- integrate positions (with wall probes) ----------
        integrate(h);

        // ---------- wheel spin / cranks / lean ----------
        bookkeeping(c, h);
        airState(grounded, h);
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

        if (!terrain.ground(ext.x, ext.z, ext.y + 1.3, ext.y - p.wheelRadius - travel - 1.5, w.hit)) {
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
        double cdot = -pointVel(ext).dot(n) / upn;
        double cc = Math.min(c, travel);
        double rate = w.isFront ? p.forkRate : p.shockRate;
        double prog = w.isFront ? p.forkProgression : p.shockProgression;
        double x = cc / travel;
        double spring = rate * cc * (1 + prog * x * x);
        double damp = cdot > 0
                ? (w.isFront ? p.forkCompDamp : p.shockCompDamp) * cdot
                : (w.isFront ? p.forkRebDamp : p.shockRebDamp) * cdot;
        double load = Math.max(0, spring + damp);

        w.contact = true;
        w.justLanded = !was;
        w.compression = cc;
        w.compVel = cdot;
        w.overshoot = c - travel;     // > 0 = bottomed out / wheel penetrating
        w.penetration = Math.max(0, w.overshoot) * upn;
        w.normal = n;
        w.grip = w.hit.grip;
        w.rollRes = w.hit.rollRes;
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

    private void solveWheel(Wheel w, double brakeF, double h) {
        if (!w.contact) return;
        V3 P = w.patch;

        // bottom-out / penetration: no closing velocity along the normal
        if (w.overshoot > 0) {
            double vn = pointVel(P).dot(w.normal);
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
        double vl = pointVel(P).dot(w.tF);
        double kl = invMass(P, w.tF);
        double limL = Math.min(brakeF + w.rollRes * nLoad, mu * nLoad) * h;
        double jl = -vl / kl;
        double oldL = w.accL;
        w.accL = clamp(oldL + jl, -limL, limL);
        jl = w.accL - oldL;
        if (jl != 0) applyImpulse(P, w.tF.mul(jl));

        // lateral grip, friction ellipse with whatever longitudinal force is used
        double vs = pointVel(P).dot(w.tL);
        double ks = invMass(P, w.tL);
        double fLong = Math.abs(w.accL) / h + (w == rear ? rear.driveForce : 0);
        double muEff = w.sliding ? mu * p.slideFriction : mu;
        double budget = Math.sqrt(Math.max(0, sq(muEff * nLoad) - sq(fLong)));
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
        V3[] pts = {
                pos.addScaled(fwd, -0.19).addScaled(up, -0.30),                    // bottom bracket
                pos.addScaled(fwd, -0.35).addScaled(up, 0.45),                     // saddle
                pos.addScaled(fwd, 0.45).addScaled(up, 0.55),                      // bars
                front.contact ? null : pos.addScaled(fwd, p.halfWheelbase).addScaled(up, p.axleDrop),
                rear.contact ? null : pos.addScaled(fwd, -p.halfWheelbase).addScaled(up, p.axleDrop),
        };
        for (int i = 0; i < pts.length; i++) {
            V3 pt = pts[i];
            if (pt == null || !terrain.ground(pt.x, pt.z, pt.y + 1.0, pt.y - 1.5, bodyHit)) continue;
            double pen = bodyHit.height + 0.02 - pt.y;
            if (pen <= 0) continue;
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
            pos = pos.addScaled(n, Math.min(pen * 0.3, 0.05));
            if ((i == 1 || i == 2) && !bailed && -vn > p.crashSpeed) bail("hit the ground with the frame");
        }
        // the rider's body never sinks into the ground either
        if (terrain.ground(riderPos.x, riderPos.z, riderPos.y + 1.0, riderPos.y - 1.5, bodyHit)) {
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
        double aT = Math.abs(c.steer) > 0.15 ? -c.steer * p.spinRate * authority : a * Math.exp(-h / 0.35);

        double na = a + (aT - a) * k;
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
                // Rotations must be earned at take-off: a real rider cannot start a
                // spin in mid-air. Popping hard (and launching off a lip) buys authority.
                double pop = clamp(popMeter / 1.6, 0, 1);
                double launch = clamp(vel.y / 5.0, 0, 1);
                airBudget = clamp(p.airBudgetBase + 0.85 * pop + 0.35 * launch, p.airBudgetBase, 1);
                predictTimer = 0;
                events.add(new Event(Event.Type.TAKEOFF, vel.length(), ""));
            }
            airTime += h;
            airPitchTravel += omega.dot(right) * h;
            airYawTravel += -omega.dot(V3.Y) * h;
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
        double impact = Math.max(0, -vel.dot(w.normal));
        if (airTime > 0.25) {
            if (impact > p.bailImpactSpeed) {
                bail("landed too hard (" + String.format(java.util.Locale.ROOT, "%.1f", impact) + " m/s into the ground)");
            } else if (pitchErr > p.bailPitchError) {
                bail("landed with the nose " + (int) Math.toDegrees(pitchErr) + "° off");
            } else if (yawErr > p.bailYawError) {
                bail("landed sideways (" + (int) Math.toDegrees(yawErr) + "°)");
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
        if (bailed) return;
        bailed = true;
        bailReason = reason;
        events.add(new Event(Event.Type.BAIL, vel.length(), reason));
    }

    // =====================================================================
    //  Integration & world collision
    // =====================================================================

    private void integrate(double h) {
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
            V3 b = a.add(step);
            if (!terrain.solidAt(b.x, b.y, b.z) || terrain.solidAt(a.x, a.y, a.z)) continue;
            if (needsTall[i] && !terrain.solidAt(b.x, b.y + 1.0, b.z)) continue; // 1-block step: ride it
            // find the face we crossed
            V3 nrm;
            if (terrain.solidAt(a.x + step.x, a.y, a.z)) nrm = new V3(-Math.signum(step.x), 0, 0);
            else if (terrain.solidAt(a.x, a.y, a.z + step.z)) nrm = new V3(0, 0, -Math.signum(step.z));
            else nrm = new V3(0, -Math.signum(step.y), 0);
            double into = -vel.dot(nrm);
            if (into <= 0) continue;
            if (nrm.y != 0) {
                // a floor/ceiling, not a wall: tyres handle the ground, but the head or
                // bars hitting it means you went over the bars / landed upside down
                if (i == 0) continue;
                if (into > p.crashSpeed) bail(i == 2 ? "head first into the ground" : "went over the bars");
            } else if (i == 2 && speedAgainst(nrm) > p.crashSpeed) {
                bail("head strike");
            } else if (into > p.wallCrashSpeed) {
                bail("crashed into a wall at " + Math.round(into * 3.6) + " km/h");
            }
            vel = vel.addScaled(nrm, into);
            riderVel = riderVel.addScaled(nrm, Math.max(0, -riderVel.dot(nrm)));
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
    }

    private double speedAgainst(V3 n) {
        return Math.max(0, -riderVel.dot(n));
    }

    private void bookkeeping(Controls c, double h) {
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
        if (!airborne) {
            double latAcc = -omega.dot(V3.Y) * vel.horizontalLength();
            leanT = clamp(Math.atan2(latAcc, p.gravity), -0.95, 0.95);
        } else {
            leanT = 0;
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
    public boolean grounded() { return front.contact || rear.contact; }

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
    }

    public record Event(Type type, double value, String info) {
        public enum Type { TAKEOFF, LAND, BAIL }
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
