package com.yesman.akythera.api.world.ability;

import org.jetbrains.annotations.ApiStatus;

/// What a client holds in a slot whose ability Java built on the server.
///
/// Such an ability has no blueprint key, so the client cannot rebuild it. The server names it by
/// its slot instead, and this stand-in follows what the server sends: the activation lifecycle and
/// the cooldown and duration timers, which [AbilitySystemComponent#getCooldown(AbilityInstance)]
/// reads. It runs no behavior, never activates by itself, and its state stays
/// [AbilityState#EMPTY]: the server's state never leaves the server.
public final class MirroredAbility extends Ability<AbilityState> {
    @ApiStatus.Internal
    public MirroredAbility() {
    }

    /// The client never decides an activation: the server's lifecycle packets drive this one.
    @Override
    public boolean canActivate(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final AbilityState abilityState
    ) {
        return false;
    }

    @Override
    public void onActivated(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final AbilityState abilityState
    ) {

    }

    @Override
    public void onRecast(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final AbilityState abilityState
    ) {

    }

    @Override
    public void onCanceled(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final AbilityState abilityState
    ) {

    }

    @Override
    public void onFinished(
        final AbilitySystemComponent abilitySystemComponent,
        final AbilityInstance abilityInstance,
        final AbilityState abilityState
    ) {

    }
}
