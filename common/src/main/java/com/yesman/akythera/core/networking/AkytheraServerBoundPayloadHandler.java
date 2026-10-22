package com.yesman.akythera.core.networking;

import com.yesman.akythera.api.world.ability.Ability;
import com.yesman.akythera.api.world.ability.AbilityInstance;
import com.yesman.akythera.api.world.ability.AbilityState;
import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.networking.payload.serverbound.ServerBoundAbilityTrigger;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;

public interface AkytheraServerBoundPayloadHandler {
    /// Receiver is always a [ServerPlayer] for client bound payloads
    static ServerPlayer getServerPlayer(PayloadContext context) {
        return (ServerPlayer)context.receiver();
    }

    /// Reads the client's input into the slot's state, then turns the trigger into whatever the
    /// slot's ability makes of it. An empty slot is a miss.
    ///
    /// The target is always the sender's own player, and the read reaches only the fields the
    /// server bound codec carries. Triggers past [AbilityTriggerRateLimiter]'s budget are dropped.
    static void handleAbilityTrigger(final ServerBoundAbilityTrigger payload, final PayloadContext context) {
        ServerPlayer player = getServerPlayer(context);

        AkytheraPatches.getEntityPatchOptional(player).ifPresent(entityPatch -> {
            AbilitySystemComponent component = entityPatch.getComponent(AbilitySystemComponent.class);

            if (component == null) return;

            if (!component.getTriggerRateLimiter().tryAcquire(payload.slot(), player.level().getServer().getTickCount())) {
                Akythera.LOGGER.debug("Dropped a trigger of {} from {}: too many this tick", payload.slot(), player.getScoreboardName());
                return;
            }
            if (payload.state().length > 0 && !readClientInput(component, payload, player)) return;

            component.activateAbility(payload.slot());
        });
    }

    /// Decodes into a copy and applies it only when the bytes were exactly one encoding, so a bad
    /// packet leaves the live state untouched.
    ///
    /// @return whether the bytes were exactly one server bound encoding of the slot's state
    private static boolean readClientInput(
        final AbilitySystemComponent component,
        final ServerBoundAbilityTrigger payload,
        final ServerPlayer player
    ) {
        AbilityInstance instance = component.getInstance(payload.slot());

        if (instance == null) return false;

        Ability<?> ability = instance.getAbility();

        // A client holds no instance of an ability it was never sent, and mirrors a Java built one
        // without any state it could write.
        if (ability == null || ability.getBlueprintKey() == null) return false;
        if (!ability.getInstantiationScope().reachesOwner()) return false;
        if (instance.getState().serverBoundCodec() == null) return false;

        return AbilityState.decodeAtomically(
            instance.getState(), ability.createState(), PacketFlow.SERVERBOUND, payload.state(), player.level()
        );
    }
}
