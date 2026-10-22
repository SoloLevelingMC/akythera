package com.yesman.akythera.api.world.ability;

import com.yesman.akythera.api.world.patch.entity.LivingEntityPatch;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.jspecify.annotations.Nullable;

/// One live value an [AbilityEffect] can read and write that is not an
/// [Attribute]: health, stamina, a weapon charge.
///
/// The owner's [AbilitySystemComponent] can also refill a registered resource on its own, see
/// [#regeneration], and send it to the owner's client, see [#syncThreshold]. Both are off by default.
public interface AbilityResource {
    /// The target's current value.
    float read(final LivingEntityPatch<?> owner);

    /// Writes `value` onto the target, through the API that owns it.
    void write(final LivingEntityPatch<?> owner, final float value);

    /// `value` bounded to what the target can actually hold.
    float clamp(final LivingEntityPatch<?> owner, final float value);

    /// Whether this entry means anything for `owner` at all.
    boolean appliesTo(final LivingEntityPatch<?> owner);

    Kind kind();

    /// The most `owner` can hold. Defaults to the upper bound [#clamp] enforces.
    default float max(final LivingEntityPatch<?> owner) {
        return clamp(owner, Float.MAX_VALUE);
    }

    /// How this resource refills on its own, or null when it does not, the default.
    ///
    /// Read on every server tick by the owner's [AbilitySystemComponent], for a resource registered
    /// in [AbilityResources].
    default @Nullable Regeneration regeneration() {
        return null;
    }

    /// The smallest move of the value or of [#max] worth sending to the owner's client, or a
    /// negative number to keep the resource on the server, the default.
    ///
    /// A value that reaches zero or the max is always sent, so a client never shows a gauge one
    /// threshold short of full. The client reads what it was sent through
    /// [AbilitySystemComponent#getResourceValue] and [AbilitySystemComponent#getResourceMax].
    default float syncThreshold() {
        return -1.0F;
    }

    /// Restarts the [#regeneration] delay of `owner`. [#consume] calls it; an override of
    /// [#consume], or a spend that bypasses it, calls it too.
    default void onSpent(final LivingEntityPatch<?> owner) {
        AbilitySystemComponent component = owner.getComponent(AbilitySystemComponent.class);

        if (component != null) component.restartRegenDelay(this);
    }

    /// Whether this resource may be spent down to zero.
    default boolean canEmpty() {
        return true;
    }

    /// Whether the owner holds enough to pay `amount`. A resource that cannot be emptied needs
    /// strictly more than the cost. Consumes nothing.
    ///
    /// Runs on both sides: a cost is validated on the client and on the server.
    default boolean hasResource(final LivingEntityPatch<?> owner, final float amount) {
        if (!appliesTo(owner)) return false;

        return canEmpty() ? read(owner) >= amount : read(owner) > amount;
    }

    /// Spends `amount`, leaving the value untouched when the owner holds less than that.
    ///
    /// Server side only.
    ///
    /// @return whether the amount was spent
    default boolean consume(final LivingEntityPatch<?> owner, final float amount) {
        if (!hasResource(owner, amount)) return false;

        add(owner, -amount);
        onSpent(owner);

        return true;
    }

    /// Moves the value by `amount`, bounded by [#clamp].
    ///
    /// @return the delta actually applied, which differs from `amount` when the value was clamped
    default float add(final LivingEntityPatch<?> owner, final float amount) {
        if (!appliesTo(owner)) return 0.0F;

        float before = read(owner);
        float after = clamp(owner, before + amount);

        write(owner, after);

        return after - before;
    }

    /// How a resource refills once its owner stops spending it.
    ///
    /// The owner's [AbilitySystemComponent] counts the ticks since the last spend, see
    /// [AbilityResource#onSpent]. Once [#delayTicks] have passed, each tick adds [#amountPerTick]
    /// through [AbilityResource#add]. A value above the max, after the max dropped, is brought back
    /// down. Server side only.
    interface Regeneration {
        /// Ticks after a spend before refilling resumes. [Integer#MAX_VALUE] never resumes.
        int delayTicks(LivingEntityPatch<?> owner);

        /// What one tick adds, given the current value and the max. Zero or less adds nothing.
        float amountPerTick(LivingEntityPatch<?> owner, float value, float max);

        /// Whether refilling is held back this tick, as a raised guard holds back stamina. The delay
        /// restarts on every suspended tick.
        default boolean isSuspended(final LivingEntityPatch<?> owner) {
            return false;
        }

        /// A flat refill of `amountPerTick` once `delayTicks` have passed.
        static Regeneration linear(final int delayTicks, final float amountPerTick) {
            return new Regeneration() {
                @Override
                public int delayTicks(final LivingEntityPatch<?> owner) {
                    return delayTicks;
                }

                @Override
                public float amountPerTick(final LivingEntityPatch<?> owner, final float value, final float max) {
                    return amountPerTick;
                }
            };
        }
    }

    /// Whether a resource banks charges over time or is spent outright.
    enum Kind {
        /// Fills up over time or on an event, and can bank charges: a cooldown, a weapon charge.
        ACCUMULATIVE,

        /// Spent on activation and never banks charges: stamina, health.
        CONSUMPTIVE
    }
}
