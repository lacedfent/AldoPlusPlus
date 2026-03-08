package keystrokesmod.utility;

/**
 * Persistent Expo-Out scroll animation.
 *
 * Behaviour mirrors OneConfig/PolyUI:
 *   - call setTarget() or extend() at any time; the animation continues from
 *     the current live position toward the new destination
 *   - call getValue() every frame to get the current rendered position
 *   - the animation idles (returns target exactly) when finished
 */
public class ScrollAnimation {
    private float from;
    private float to;
    private long startMs;
    private final long durationMs;

    /**
     * @param durationMs total animation length in milliseconds (e.g. 200)
     */
    public ScrollAnimation(long durationMs) {
        this.durationMs = durationMs;
        this.from = 0f;
        this.to = 0f;
        this.startMs = 0L;
    }

    /** Immediately place both ends at {@code value} with no animation. */
    public void reset(float value) {
        this.from = value;
        this.to = value;
        this.startMs = 0L;
    }

    /**
     * Set a new target, starting the animation from the current live position.
     * If called mid-animation the current rendered value is read and used as the
     * new start, so movement is always continuous with no snap.
     */
    public void setTarget(float newTarget) {
        this.from = getValue();          // capture current animated position
        this.to = newTarget;
        this.startMs = System.currentTimeMillis();
    }

    /**
     * Extend the existing destination by {@code delta} without restarting from
     * scratch. The live position keeps animating; only the destination moves.
     * This is the PolyUI "extend" behaviour: another notch simply moves the
     * target further instead of spawning a second animation.
     */
    public void extend(float delta) {
        this.from = getValue();   // capture current position before changing target
        this.to += delta;
        this.startMs = System.currentTimeMillis();
    }

    /**
     * Clamp the destination into [min, max].  Call this after extend() or
     * setTarget() so the animation never overshoots the scroll bounds.
     */
    public void clampTarget(float min, float max) {
        this.to = Math.max(min, Math.min(max, this.to));
    }

    /**
     * Returns the current Expo-Out eased position.
     * When the animation is finished (or was never started) returns {@code to} exactly.
     */
    public float getValue() {
        if (startMs == 0L) {
            return to;
        }
        long elapsed = System.currentTimeMillis() - startMs;
        if (elapsed >= durationMs) {
            startMs = 0L;   // idle
            from = to;
            return to;
        }
        float t = (float) elapsed / (float) durationMs;
        float ease = expoOut(t);
        return from + (to - from) * ease;
    }

    /** Whether there is an active animation still running. */
    public boolean isAnimating() {
        if (startMs == 0L) return false;
        return System.currentTimeMillis() - startMs < durationMs;
    }

    public float getTarget() {
        return to;
    }

    // expo out: 1 - 2^(-10t)
    private static float expoOut(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return 1f - (float) Math.pow(2.0, -10.0 * t);
    }
}
