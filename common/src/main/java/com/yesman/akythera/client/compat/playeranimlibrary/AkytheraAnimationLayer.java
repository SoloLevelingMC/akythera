package com.yesman.akythera.client.compat.playeranimlibrary;

import com.yesman.akythera.api.asset.AnimatableObject;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.component.impl.PALPoseBinder;
import com.yesman.akythera.client.compat.playeranimlibrary.mixinaccessor.IMappedBone;
import com.yesman.akythera.client.compat.playeranimlibrary.mixinaccessor.INamedModelPart;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.layered.IAnimation;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.yesman.akythera.core.math.Transform;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/// Poses a vanilla model by the armature the [AnimatableObject] answers, read on every call.
public final class AkytheraAnimationLayer implements IAnimation {
    private final AnimatableObject animatableObject;

    // Working objects of [#get3DTransform], run for every posed part on every frame
    private final Matrix4f modelBoneTransform = new Matrix4f();
    private final Quaternionf modelRotation = new Quaternionf();

    /// Each bone's `_bend` bone in [#bendBonesArmature], or empty when it has none
    private final Map<Bone, Optional<Bone>> bendBones = new HashMap<>();
    private Armature bendBonesArmature;

    public AkytheraAnimationLayer(final AnimatableObject animatableObject) {
        this.animatableObject = animatableObject;
    }

    /// PAL poses the vanilla model while the armature holds a switched-on [PALPoseBinder].
    @Override
    public boolean isActive() {
        ArmatureComponent armatureComponent = animatableObject.getArmatureComponent();

        return armatureComponent != null && armatureComponent.getComponent(PALPoseBinder.class) != null;
    }

    @Override
    public void setupAnim(AnimationData state) {
        if (animatableObject instanceof EntityPatch<?> entityPatch) {
            entityPatch.getOriginal().setYBodyRot(entityPatch.getOriginal().getYRot());
        }
    }

    @Override
    public void get3DTransform(@NonNull PlayerAnimBone playerAnimBone) {
        ModelPart modelPart = ((IMappedBone)playerAnimBone).akythera$getModelPart();
        ArmatureComponent armatureComponent = animatableObject.getArmatureComponent();

        if (modelPart != null && armatureComponent != null) {
            Armature armature = armatureComponent.getArmature().get();
            INamedModelPart namedModelPart = ((INamedModelPart)(Object)modelPart);
            Bone bone = armature.getBoneByName(namedModelPart.akythera$getName());

            if (bone != null && armatureComponent.getBoundTransforms() != null) { // `armatureComponent.getBoundTransforms()` is null in the first frame
                Bone bendBone = bendBoneOf(armature, bone);
                toMatrix(armatureComponent.modelTransform(), modelBoneTransform).mul(armatureComponent.getBoundTransforms()[bone.getId()]);

                PlayerAnimationLibraryPlugin.mapAnimation(bone, bendBone, armatureComponent.getCachedPose(), modelBoneTransform, playerAnimBone, modelPart);
            }
        }
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