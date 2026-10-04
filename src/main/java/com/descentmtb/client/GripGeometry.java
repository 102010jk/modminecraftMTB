package com.descentmtb.client;

import com.descentmtb.client.model.EnduroBikeModel;
import com.descentmtb.client.model.HardtailBikeModel;
import com.descentmtb.entity.BikeType;
import org.joml.Vector3f;

/**
 * Where the grips really are when the bars are turned, so the rider's hands stay on them.
 *
 * <p>The bars turn about the (raked) head tube, not about a vertical line: turning right brings the right grip
 * back and a little down, the left grip forward and up. Bike-model and player-model pixel spaces are rendered with
 * the same yaw and the same axis flip, so a displacement computed in the bike model applies to the arm targets
 * unchanged. All vectors are in model pixels (Y down, -Z forward, +X = rider's left).
 */
public final class GripGeometry {
    private static final float PX = 16f;

    private record Steering(Vector3f pivot, Vector3f axisDown, Vector3f gripLeft) {}

    private static final Steering ENDURO = new Steering(
            new Vector3f(0f, -15.5319f, -4.5454f),
            new Vector3f(0f, (float) Math.cos(Math.toRadians(26.5)), (float) -Math.sin(Math.toRadians(26.5))),
            toPx(EnduroBikeModel.GRIP_LEFT));
    private static final Steering HARDTAIL = new Steering(
            HardtailBikeModel.STEER_PIVOT_PX, HardtailBikeModel.STEER_AXIS_DOWN, toPx(HardtailBikeModel.GRIP_LEFT));

    /** Metres, Y up (model constants) to model pixels, Y down. */
    private static Vector3f toPx(Vector3f m) {
        return new Vector3f(m.x * PX, -m.y * PX, m.z * PX);
    }

    /**
     * Displacement of a grip from its straight-bars position at steering angle {@code steer}
     * (radians, positive = right, the same angle the bike model uses).
     */
    public static Vector3f gripOffset(BikeType type, boolean left, float steer) {
        Steering s = type == BikeType.HARDTAIL ? HARDTAIL : ENDURO;
        Vector3f grip = new Vector3f(s.gripLeft());
        if (!left) {
            grip.x = -grip.x;
        }
        Vector3f r = grip.sub(s.pivot());
        Vector3f turned = new Vector3f(r).rotateAxis(steer, s.axisDown().x, s.axisDown().y, s.axisDown().z);
        return turned.sub(r);
    }

    /**
     * A point fixed to the bike, expressed in the rider's model space when the rider's body rolls differently from
     * the bike (in the air the bike lays over in a table while the rider stays upright). Pivot: the feet (y = 24).
     */
    public static Vector3f intoRiderRoll(Vector3f point, float riderLean, float bikeLean) {
        Vector3f p = new Vector3f(point.x, point.y - 24f, point.z).rotateZ(riderLean - bikeLean);
        p.y += 24f;
        return p;
    }

    private GripGeometry() {}
}
