package com.yesman.akythera.mixin.client;

import com.yesman.akythera.api.event.BuiltInEventHooks;
import com.yesman.akythera.api.event.entity.TickEvent;
import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.client.config.AkytheraClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelExtractor.class)
public abstract class MixinLevelExtractor {
    /// Past this distance from the camera (squared blocks), an entity is posed 40 times a second at most
    private static final double AKYTHERA_LOD_NEAR_SQR = 32.0D * 32.0D;
    /// Past this distance, 20 times a second at most
    private static final double AKYTHERA_LOD_FAR_SQR = 64.0D * 64.0D;

    @Inject(
        at = @At(value = "HEAD"),
        method = "extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"
    )
    private void extractVisibleEntities(
        Entity entity,
        float partialTickTime,
        CallbackInfoReturnable<EntityRenderState> callbackInfo
    ) {
        // Update the pose before extract
        AkytheraPatches.getEntityPatchOptional(entity).ifPresent(entityPatch -> {
            // Get a real frame seconds by multiplying a tick seconds(0.05) to delta ticks (clamped in 0 ~ 1)
            float deltaTime = Minecraft.getInstance().isPaused() ? 0.0F : Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks() * 0.05F;
            // Far entities are posed less often, over the time they skipped (anim_lod in the client config)
            float renderTime = entityPatch.takeRenderTime(deltaTime, akythera$lodInterval(entity));

            if (renderTime < 0.0F) return;

            BuiltInEventHooks.Entity.RENDER_TICK.postWithListener(new TickEvent.RenderTick(entityPatch, renderTime, partialTickTime), entityPatch.getEntityEventListener());
        });
    }

    /// Shortest time between two poses of `entity`: 0 (every frame) for the camera entity and near entities
    private static float akythera$lodInterval(final Entity entity) {
        if (!AkytheraClientConfig.isAnimationLod()) return 0.0F;

        Entity camera = Minecraft.getInstance().getCameraEntity();

        if (camera == null || camera == entity || entity == Minecraft.getInstance().player) return 0.0F;

        double distanceSqr = entity.distanceToSqr(camera);

        if (distanceSqr > AKYTHERA_LOD_FAR_SQR) return 1.0F / 20.0F;
        if (distanceSqr > AKYTHERA_LOD_NEAR_SQR) return 1.0F / 40.0F;

        return 0.0F;
    }
}
