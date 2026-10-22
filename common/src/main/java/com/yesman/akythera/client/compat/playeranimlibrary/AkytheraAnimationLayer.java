package com.yesman.akythera.client.compat.playeranimlibrary;

import com.yesman.akythera.api.asset.AnimatableObject;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.component.impl.PALPoseBinder;
import com.yesman.akythera.client.compat.playeranimlibrary.mixinaccessor.IMappedBone;
import com.yesman.akythera.client.compat.playeranimlibrary.mixinaccessor.INamedModelPart;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.yesman.akythera.core.math.Transform;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/// Poses a vanilla model by the armature the [AnimatableObject] answers, read on every call, as its
/// [PALPoseBinder] says: weight and mask against the pose under this layer, body yaw, bends and carried
/// parts, corrections.
public final class AkytheraAnimationLayer implements IAnimation {
    /// The pose of the bone being posed as it was before this layer, see [#vanillaPose]. Render thread only.
    private static final PlayerAnimBone VANILLA_POSE = new PlayerAnimBone("vanilla");
    private static @Nullable PlayerAnimBone vanillaPoseOf;

    private final AnimatableObject animatableObject;

    // Working objects of [#get3DTransform], run for every posed part on every frame
    private final Matrix4f modelBoneTransform = new Matrix4f();
    private final Matrix4f armatureTransform = new Matrix4f();
    private final Quaternionf modelRotation = new Quaternionf();

    /// Each bone's `_bend` bone in [#bendBonesArmature], or empty when it has none
    private final Map<Bone, Optional<Bone>> bendBones = new HashMap<>();
    private Armature bendBonesArmature;

    /// The binder's weight, read once per frame in [#setupAnim]
    private float frameWeight;
    /// The transform carrying each bent bone's followers this frame, by bent bone; null when not folded
    private final Map<String, Optional<Matrix4f>> frameCarries = new HashMap<>();

    public AkytheraAnimationLayer(final AnimatableObject animatableObject) {
        this.animatableObject = animatableObject;
    }

    /// The pose `bone` had before this layer posed it (the vanilla animation and the layers under this
    /// one), for a layer above this one that blends with it; null unless this layer posed `bone` last.
    /// The result is shared and overwritten by the next bone: copy it to keep it. Render thread only.
    public static @Nullable PlayerAnimBone vanillaPose(final PlayerAnimBone bone) {
        return vanillaPoseOf == bone ? VANILLA_POSE : null;
    }

    /// PAL poses the vanilla model while the armature holds a switched-on [PALPoseBinder] whose weight
    /// isn't 0.
    @Override
    public boolean isActive() {
        PALPoseBinder binder = binder();

        return binder != null && binder.weight(animatableObject.getArmatureComponent()) > 0.0F;
    }

    @Override
    public void setupAnim(AnimationData state) {
        PALPoseBinder binder = binder();
        ArmatureComponent armatureComponent = animatableObject.getArmatureComponent();

        frameWeight = binder == null ? 0.0F : binder.weight(armatureComponent);
        frameCarries.clear();

        if (binder == null) return;

        binder.bendCarries().keySet().forEach(bentBone ->
            frameCarries.put(bentBone, Optional.ofNullable(PALPoseBinder.bendCarryTransform(armatureComponent, bentBone)))
        );

        if (animatableObject instanceof EntityPatch<?> entityPatch) {
            Entity entity = entityPatch.getOriginal();
            float vanillaYaw = entity instanceof LivingEntity living ? living.yBodyRot : entity.getYRot();
            float yaw = switch (binder.bodyYaw()) {
                case NONE -> vanillaYaw;
                case LOOK -> entity.getYRot();
                case SUPPLIED -> binder.bodyYawSource() == null
                    ? entity.getYRot()
                    : binder.bodyYawSource().bodyYaw(entity, vanillaYaw, state.getPartialTick());
            };

            if (yaw != vanillaYaw) {
                entity.setYBodyRot(frameWeight >= 1.0F ? yaw : Mth.rotLerp(frameWeight, vanillaYaw, yaw));
            }
        }
    }

    @Override
    @SuppressWarnings("removal") // PlayerAnimBone.bend: what BendableCuboids reads
    public void get3DTransform(@NonNull PlayerAnimBone playerAnimBone) {
        VANILLA_POSE.copyOtherBone(playerAnimBone);
        VANILLA_POSE.bend = playerAnimBone.bend;
        vanillaPoseOf = playerAnimBone;

        PALPoseBinder binder = binder();
        ArmatureComponent armatureComponent = animatableObject.getArmatureComponent();

        if (binder == null || armatureComponent == null || frameWeight <= 0.0F) return;

        ModelPart modelPart = ((IMappedBone)playerAnimBone).akythera$getModelPart();
        Armature armature = armatureComponent.getArmature().get();

        if (modelPart == null) {
            // A bone with no part of its own, like the arm bones the held item reads its elbow bend from
            // (BendableCuboids): the bend of the armature's `_bend` bone of that name.
            String bendName = playerAnimBone.getName() + "_bend";
            Pose pose = armatureComponent.getCachedPose();

            if (pose != null && pose.hasTransform(bendName)) {
                float weight = frameWeight * partWeight(binder, playerAnimBone.getName());
                float bend = (float)-PALPoseBinder.bendAngle(pose, bendName);
                playerAnimBone.bend = weight >= 1.0F ? bend : Mth.lerp(weight, playerAnimBone.bend, bend);
            }

            return;
        }

        String partName = ((INamedModelPart)(Object)modelPart).akythera$getName();
        Bone bone = armature.getBoneByName(partName);

        // `armatureComponent.getBoundTransforms()` is null in the first frame
        if (bone == null || armatureComponent.getBoundTransforms() == null || bone.getId() >= armatureComponent.getBoundTransforms().length) {
            return;
        }

        float weight = frameWeight * partWeight(binder, partName);

        if (weight <= 0.0F) return;

        Bone bendBone = bendBoneOf(armature, bone);
        Matrix4fc correction = binder.partCorrection() == null ? null : binder.partCorrection().correction(partName);
        String carrier = binder.carrierOf(partName);
        Matrix4f carry = carrier == null ? null : frameCarries.getOrDefault(carrier, Optional.empty()).orElse(null);

        if (correction != null) {
            modelBoneTransform.set(correction).mul(toMatrix(armatureComponent.modelTransform(), armatureTransform));
        } else {
            toMatrix(armatureComponent.modelTransform(), modelBoneTransform);
        }

        if (carry != null) modelBoneTransform.mul(carry);

        modelBoneTransform.mul(armatureComponent.getBoundTransforms()[bone.getId()]);

        PlayerAnimationLibraryPlugin.mapAnimation(bone, bendBone, armatureComponent.getCachedPose(), modelBoneTransform, playerAnimBone, modelPart);

        if (weight < 1.0F) blend(playerAnimBone, VANILLA_POSE, weight);
    }

    /// Blends `bone` (the armature's pose) back towards `from` (the pose under the layer): position, scale and
    /// bend linearly, each rotation angle by its shortest way.
    @SuppressWarnings("removal")
    private static void blend(final PlayerAnimBone bone, final PlayerAnimBone from, final float weight) {
        from.position.lerp(bone.position, weight, bone.position);
        from.scale.lerp(bone.scale, weight, bone.scale);
        bone.rotation.set(
            lerpAngle(from.rotation.x, bone.rotation.x, weight),
            lerpAngle(from.rotation.y, bone.rotation.y, weight),
            lerpAngle(from.rotation.z, bone.rotation.z, weight)
        );
        bone.bend = Mth.lerp(weight, from.bend, bone.bend);
    }

    /// From `from` to `to` (radians) by `weight`, the shortest way round
    private static float lerpAngle(final float from, final float to, final float weight) {
        return (float)(from + Math.IEEEremainder(to - from, 2.0 * Math.PI) * weight);
    }

    private static float partWeight(final PALPoseBinder binder, final String partName) {
        return Math.clamp(binder.partWeights().weight(partName), 0.0F, 1.0F);
    }

    private @Nullable PALPoseBinder binder() {
        ArmatureComponent armatureComponent = animatableObject.getArmatureComponent();

        return armatureComponent == null ? null : armatureComponent.getComponent(PALPoseBinder.class);
    }

    /// The bone named after `bone` with a `_bend` suffix, looked up once per armature
    private Bone bendBoneOf(final Armature armature, final Bone bone) {
        if (armature != bendBonesArmature) {
            bendBones.clear();
            bendBonesArmature = armature;
        }

        return bendBones.computeIfAbsent(bone, b -> Optional.ofNullable(armature.getBoneByName(b.getName() + "_bend"))).orElse(null);
    }

    /// [Transform#toMatrix] written into `dest`
    private Matrix4f toMatrix(final Transform transform, final Matrix4f dest) {
        modelRotation.set((float)transform.rot().x(), (float)transform.rot().y(), (float)transform.rot().z(), (float)transform.rot().w());

        return dest
            .translation((float)transform.loc().x(), (float)transform.loc().y(), (float)transform.loc().z())
            .rotate(modelRotation)
            .scale((float)transform.sca().x(), (float)transform.sca().y(), (float)transform.sca().z());
    }
}
