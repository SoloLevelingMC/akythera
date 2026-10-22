package com.yesman.akythera.client.compat.playeranimlibrary;

import com.yesman.akythera.api.world.patch.AkytheraPatches;
import com.yesman.akythera.client.compat.IClientModPlugin;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranim.util.RenderUtil;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class PlayerAnimationLibraryPlugin implements IClientModPlugin {
    // Working objects of [#mapAnimation], run for every posed part on every frame. Render thread only.
    private static final Matrix4f TRANSFORM = new Matrix4f();
    private static final Quaternionf ROTATION = new Quaternionf();
    private static final Vector3f EULER_ZYX = new Vector3f();
    private static final Vector3f SCALE = new Vector3f();
    private static final Vector3d BEND_EULER_ZYX = new Vector3d();

    /// Initial pose of a model part as a matrix, by pose. Models share a handful of poses, so this stays small.
    private static final Map<PartPose, Matrix4fc> INITIAL_POSE_MATRICES = new HashMap<>();

    /// Poses `modelPart` by `bone`. Render thread only.
    ///
    /// @param modelBoneTransform the bone's forward-bound transform in the patch's model space, the armature's own
    ///                           transform applied
    public static void mapAnimation(
        @NonNull final Bone bone,
        @Nullable final Bone bendBone,
        @NonNull final Pose pose,
        @NonNull final Matrix4f modelBoneTransform,
        @NonNull PlayerAnimBone palBone,
        @NonNull ModelPart modelPart
    ) {
        PartPose initPose = modelPart.getInitialPose();
        // Calculates an initial pose of a cube
        Matrix4fc initPoseTransform = INITIAL_POSE_MATRICES.computeIfAbsent(initPose, PlayerAnimationLibraryPlugin::initialPoseMatrix);

        Matrix4f transform = TRANSFORM;
        // Revert Mesh space coord -> Minecraft model space coord
        transform.scaling(-16.0F, -16.0F, 16.0F);
        transform.translate(0.0F, -1.501F, 0.0F);

        // Apply animation in mesh space
        transform.mul(modelBoneTransform);
        transform.mul(bone.getInverseTransformToOrigin());

        // Minecraft model space coord -> Mesh space coord (where the center of each leg becomes [0, 0, 0])
        // Was specified in LivingEntityRenderer#submit (v26.1, hardcoded)
        transform.translate(0.0F, 1.501F, 0.0F);
        transform.scale(-1.0F / 16.0F, -1.0F / 16.0F, 1.0F / 16.0F);

        // Move the cube to initial pose
        transform.mul(initPoseTransform);

        float translationX = transform.m30();
        float translationY = transform.m31();
        float translationZ = transform.m32();
        Vector3f eulerZXY = transform.getUnnormalizedRotation(ROTATION).getEulerAnglesZYX(EULER_ZYX);
        Vector3f scale = transform.getScale(SCALE);

        /* mapping the animation into vanilla model part*/
        /*
        modelPart.x = translationX;
        modelPart.y = translationY;
        modelPart.z = translationZ;
        modelPart.xRot = eulerZXY.x();
        modelPart.yRot = eulerZXY.y();
        modelPart.zRot = eulerZXY.z();
        modelPart.xScale = scale.x();
        modelPart.yScale = scale.y();
        modelPart.zScale = scale.z();
        */

        palBone.position.x = translationX - initPose.x();
        palBone.position.y = -translationY + initPose.y();
        palBone.position.z = translationZ - initPose.z();
        palBone.rotation.x = eulerZXY.x();
        palBone.rotation.y = eulerZXY.y() - initPose.yRot();
        palBone.rotation.z = eulerZXY.z();
        palBone.scale.x = scale.x();
        palBone.scale.y = scale.y();
        palBone.scale.z = scale.z();

        RenderUtil.translatePartToBone(modelPart, palBone, initPose);

        // Apply bends from bend bone
        if (bendBone != null && pose.hasTransform(bendBone.getName())) {
            Vector3d rotations = pose.get(bendBone.getName()).rot().getEulerAnglesZYX(BEND_EULER_ZYX);
            palBone.bend = (float)-rotations.x();
        }
    }

    /// The matrix a model part applies for `initPose`, as [ModelPart#translateAndRotate] does: translation, rotation
    /// in Z, Y, X order (in radians), then scale.
    private static Matrix4fc initialPoseMatrix(final PartPose initPose) {
        return new Matrix4f()
            .translation(initPose.x(), initPose.y(), initPose.z())
            .rotateZYX(initPose.zRot(), initPose.yRot(), initPose.xRot())
            .scale(initPose.xScale(), initPose.yScale(), initPose.zScale());
    }

    private static final Identifier LOCOMOTION_LAYER = Identifier.fromNamespaceAndPath(Akythera.MODID, "animation_layer");

    @Override
    public void onInitializeClient() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LOCOMOTION_LAYER, 1000, player -> {
            EntityPatch<?> entityPatch = AkytheraPatches.getEntityPatch(player);

            if (entityPatch != null && entityPatch.getArmatureComponent() != null) {
                return new AkytheraAnimationLayer(entityPatch);
            }

            return null;
        });
    }
}
