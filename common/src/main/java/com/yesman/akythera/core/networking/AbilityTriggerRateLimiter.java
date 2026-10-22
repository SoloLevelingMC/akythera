package com.yesman.akythera.core.networking;

import com.yesman.akythera.api.world.ability.AbilitySlot;
import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.Map;

/// Bounds how many trigger packets one player gets through per [AbilitySlot].
///
/// One token bucket per slot: [#BURST] tokens at most, [#TOKENS_PER_TICK] back every server tick. A
/// client sends at most one trigger per slot per client tick, so an honest player never runs dry,
/// while packets that bunch up behind a lag spike still get through.
///
/// Server thread only.
@ApiStatus.Internal
public final class AbilityTriggerRateLimiter {
    public static final int BURST = 4;

    public static final int TOKENS_PER_TICK = 1;

    private final Map<AbilitySlot, Bucket> buckets = new HashMap<>();

    /// Spends one token of `slot`'s bucket.
    ///
    /// @param serverTick the server's tick count, which only ever climbs
    /// @return whether the trigger may go through
    public boolean tryAcquire(final AbilitySlot slot, final int serverTick) {
        Bucket bucket = buckets.computeIfAbsent(slot, _ -> new Bucket(BURST, serverTick));

        long elapsed = Math.max(0L, (long)serverTick - bucket.lastTick);
        bucket.tokens = (int)Math.min(BURST, bucket.tokens + elapsed * TOKENS_PER_TICK);
        bucket.lastTick = serverTick;

        if (bucket.tokens <= 0) return false;

        bucket.tokens--;

        return true;
    }

    private static final class Bucket {
        private int tokens;
        private int lastTick;

        private Bucket(final int tokens, final int lastTick) {
            this.tokens = tokens;
            this.lastTick = lastTick;
        }
    }
}
