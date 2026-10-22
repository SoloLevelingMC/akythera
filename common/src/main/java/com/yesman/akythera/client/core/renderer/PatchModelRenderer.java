package com.yesman.akythera.client.core.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.TransformComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.component.RenderableComponent;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// Draws what a patch renders: flattens its component tree into the [RenderableComponent]s under it
/// and runs them ([#collect], [#extract], [#submit]).
///
/// The tree walk is stateless and runs inside `LivingEntityRenderer`.
///
/// SoloLevelingMC fork: the GPU skinning half (compute dispatches queued here and flushed by
/// `FeatureRenderDispatcher.PreparedFrame`, and the simulated cloth) is removed with the compute
/// shaders. Skeletal meshes are skinned on the CPU by their own renderables ([SkeletalMesh#drawPosed]).
public final class PatchModelRenderer {
    /// Every switched-on renderable under `host`, parents before children, each paired with the host
    /// it is attached to and the [TransformComponent] that places it. `space` is the one `host` itself
    /// lives in—null at the patch root.
    public static void collect(
        final ComponentAttachable host,
        final @Nullable TransformComponent space,
        final List<RenderableSubmit> out
    ) {
        for (PatchComponent component : host.getComponents().values()) {
            if (!component.isEnabled()) continue;

            TransformComponent placing = component instanceof TransformComponent transform ? transform : space;

            if (component instanceof RenderableComponent<?> renderable) {
                out.add(new RenderableSubmit(renderable, host, placing));
            }

            if (component instanceof ComponentAttachable attachable) {
                collect(attachable, placing, out);
            }
        }
    }

    /// Runs [RenderableComponent#extract] on every collected renderable the state fits.
    @SuppressWarnings("unchecked")
    public static void extract(
        final List<RenderableSubmit> submits,
        final EntityPatch<?> patch,
        final LivingEntityRenderState state,
        final float partialTick
    ) {
        for (RenderableSubmit submit : submits) {
            RenderableComponent<?> component = submit.component();

            if (component.stateType().isInstance(state)) {
                ((RenderableComponent<LivingEntityRenderState>)component).extract(patch, state, partialTick);
            }
        }
    }

    /// Submits every collected renderable the state fits, each in armature space under its placing transform.
    @SuppressWarnings("unchecked")
    public static void submit(
        final List<RenderableSubmit> submits,
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector,
        final int lightCoords,
        final LivingEntityRenderState state
    ) {
        for (RenderableSubmit submit : submits) {
            RenderableComponent<?> component = submit.component();

            if (!component.stateType().isInstance(state)) continue;

            poseStack.pushPose();
            RenderUtils.toArmatureSpace(poseStack);

            if (submit.space() != null) poseStack.mulPose(submit.space().modelTransform().toMatrix());

            ((RenderableComponent<LivingEntityRenderState>) component)
                .submit(poseStack, submitNodeCollector, lightCoords, state, state.yRot, state.xRot);

            poseStack.popPose();
        }
    }

    private PatchModelRenderer() {
    }

    /// One renderable, what it is attached to, and the transform that places it.
    ///
    /// @param space the renderable itself when it is a [TransformComponent], else its nearest one above; null when
    ///              there is none
    public record RenderableSubmit(
        RenderableComponent<?> component,
        ComponentAttachable host,
        @Nullable TransformComponent space
    ) {
    }

    /// How [LivingEntityRenderer#submit] would have drawn the entity's own model: what a patch's
    /// renderables draw with when they name nothing of their own.
    public record ModelDraw(RenderType renderType, int overlayCoords, int tintedColor) {
    }
}
