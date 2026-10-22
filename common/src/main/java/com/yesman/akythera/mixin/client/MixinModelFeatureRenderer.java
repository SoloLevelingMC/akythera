package com.yesman.akythera.mixin.client;

import com.yesman.akythera.client.api.component.impl.ModelPartPoseBinder;
import com.yesman.akythera.client.core.renderer.ModelPartPoser;
import com.yesman.akythera.mixinaccessor.client.LivingEntityRenderStateAccessor;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelFeatureRenderer.class)
public abstract class MixinModelFeatureRenderer {
    /// Models are shared between entities and set up again right before they are drawn: moves the parts by
    /// the [ModelPartPoseBinder] of the entity the state belongs to, after the model's own animation.
    @Inject(
        method = "prepareModel(Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$Submit;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
            shift = At.Shift.AFTER
        )
    )
    private void akythera$poseModelParts(final ModelFeatureRenderer.Submit<?> submit, final CallbackInfo callbackInfo) {
        if (submit.state() instanceof LivingEntityRenderStateAccessor stateAccessor) {
            ModelPartPoseBinder.Snapshot partPose = stateAccessor.akythera$getPartPose();

            if (partPose != null) ModelPartPoser.apply(submit.model(), partPose);
        }
    }
}
