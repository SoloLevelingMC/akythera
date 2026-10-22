package com.yesman.akythera.core.networking;

import com.yesman.akythera.api.animation.animator.Animator;
import com.yesman.akythera.core.animation.playable.AnimationMontage;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundPlayAnimMontage;
import com.yesman.akythera.core.networking.payload.clientbound.ClientBoundStopAnimMontage;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/// Server side replication of montages: tells every client tracking an entity, the entity's own client
/// included when it is a player, to play or stop a montage on that entity.
///
/// [Animator#playAnimMontage] and [Animator#stopPlayingMontage] already call these when they run on a
/// logical server. Call them directly for an entity that has no armature on the server, whose montages
/// only ever play on clients.
public final class AnimMontageSync {
    /// Plays `montage` from `section` on `entity` in every client that sees it, at `playRate`
    /// (see [Animator#playAnimMontage(ResourceKey, String, float)]).
    public static void play(
        final Entity entity,
        final ResourceKey<@NonNull AnimationMontage> montage,
        final String section,
        final float playRate
    ) {
        NetworkingHelper.getInstance().sendToPlayersTrackingEntityWithSelfIfPlayer(
            entity,
            new ClientBoundPlayAnimMontage(montage.identifier(), section, entity.getId(), playRate)
        );
    }

    /// Plays `montage` from `section` on `entity` in every client that sees it, at its own pace.
    public static void play(
        final Entity entity,
        final ResourceKey<@NonNull AnimationMontage> montage,
        final String section
    ) {
        play(entity, montage, section, 1.0F);
    }

    /// Plays `montage` from its first section on `entity` in every client that sees it.
    public static void play(final Entity entity, final ResourceKey<@NonNull AnimationMontage> montage) {
        play(entity, montage, AnimationMontage.GENERIC_SECTION_NAME);
    }

    /// Stops `montage` on `entity` in every client that sees it, or any montage when `montage` is null.
    /// The clients fade it out as [Animator#stopPlayingMontage] does.
    public static void stop(final Entity entity, final @Nullable ResourceKey<@NonNull AnimationMontage> montage) {
        NetworkingHelper.getInstance().sendToPlayersTrackingEntityWithSelfIfPlayer(
            entity,
            new ClientBoundStopAnimMontage(Optional.ofNullable(montage).map(ResourceKey::identifier), entity.getId())
        );
    }

    private AnimMontageSync() {}
}
