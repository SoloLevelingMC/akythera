package com.yesman.akythera.client.core.networking;

import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.animator.BlendingAnimator;
import com.yesman.akythera.core.animation.playable.PlayableAnimation;
import com.yesman.akythera.core.networking.PayloadContext;
import com.yesman.akythera.core.networking.payload.bidirectional.BiDirectionalReplaceAnimation;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;

import java.util.Optional;

public interface AkytheraBiDirectionalPayloadHandler {
    /// Client side only. An entry naming an animation this client does not know is skipped, so a
    /// mismatched data pack costs that entry instead of the connection.
    static void handleReplaceAnimation(BiDirectionalReplaceAnimation payload, PayloadContext context) {
        AkytheraPatches.getEntityPatchOptional(context.receiver()).ifPresent(entityPatch -> {
            if (entityPatch.getArmatureComponent() == null
                || !(entityPatch.getArmatureComponent().getAnimator() instanceof BlendingAnimator blendingAnimator)) {
                return;
            }

            if (payload.reset()) blendingAnimator.resetAllInterfaces();

            RegistryAccess registryAccess = entityPatch.getLevel().registryAccess();

            payload.entries().forEach(replaceEntry -> {
                Optional<? extends Holder.Reference<? extends PlayableAnimation>> animation =
                    registryAccess.get(replaceEntry.animResourceKey());

                if (animation.isEmpty()) {
                    Akythera.LOGGER.warn("Unknown animation {} in replace_animation, skipped", replaceEntry.animResourceKey());
                    return;
                }

                blendingAnimator.replaceAnimation(replaceEntry.interfaceKey(), animation.get());
            });
        });
    }
}
