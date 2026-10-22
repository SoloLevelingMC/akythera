package com.yesman.akythera.api.animation.impl;

import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.api.asset.SingletonAccessor;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.asset.loader.ArmatureLoader;
import com.yesman.akythera.core.math.Transform;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/// Conversions between an armature pose and the cuboid models of vanilla ([ModelPart] trees), for
/// armatures that animate such a model through `ModelPartPoseBinder`.
///
/// ## Spaces
///
/// An armature lives in meters, Y up. A model part lives in model pixels (1/16 of a block), Y down,
/// X mirrored: the renderer draws models through a `scale(-1, -1, 1)`. Mapping one to the other is a
/// half turn about Z plus a factor of 16, so a location `(x, y, z)` in meters is the offset
/// `(-16x, -16y, 16z)` in pixels, and a rotation keeps its Z part and turns its X and Y parts over.
///
/// ## Offsets
///
/// A bone's transform in a [Pose] is read as an **offset added to the part's rest pose**, the way
/// vanilla keyframe animations and Blockbench treat them: the part's rotation angles (radians, applied
/// Z then Y then X like a `ModelPart`) are added to its rest angles, its offset (pixels) to its rest
/// position, and its scale multiplies the rest scale. A clip authored in those units turns into
/// armature keyframes through [#fromPartOffset], and back through [#toPartRotation] and
/// [#toPartOffset].
///
/// Rotations go through quaternions, so an offset angle is recovered within one turn (X and Z in
/// (-180°, 180°], Y in [-90°, 90°], or an equivalent triple), and keyframes interpolate by the
/// shortest arc. Sample a clip densely enough (a keyframe every few hundredths of a second) when it
/// turns a part by more than half a turn between two keys.
///
/// [ModelPart]: net.minecraft.client.model.geom.ModelPart
public final class ModelPartSpace {
    /// Model pixels per meter.
    public static final float PIXELS_PER_METER = 16.0F;

    /// The armature transform of a part offset: angles in radians, translation in model pixels.
    public static Transform fromPartOffset(
        final float xRot, final float yRot, final float zRot,
        final float x, final float y, final float z
    ) {
        Quaterniond rotation = new Quaterniond().rotationZYX(zRot, yRot, xRot);
        // Half turn about Z: X and Y parts flip.
        rotation.x = -rotation.x;
        rotation.y = -rotation.y;

        return Transform.of(
            new Vec3(-x / PIXELS_PER_METER, -y / PIXELS_PER_METER, z / PIXELS_PER_METER),
            rotation,
            new Vec3(1.0D, 1.0D, 1.0D)
        );
    }

    /// [#fromPartOffset] with angles in degrees, as most authoring tools write them.
    public static Transform fromPartOffsetDegrees(
        final float xDeg, final float yDeg, final float zDeg,
        final float x, final float y, final float z
    ) {
        return fromPartOffset(
            (float)Math.toRadians(xDeg), (float)Math.toRadians(yDeg), (float)Math.toRadians(zDeg), x, y, z
        );
    }

    /// Writes into `dest` the part angles (radians, X Y Z) that `rotation` stands for.
    public static Vector3f toPartRotation(final Quaterniond rotation, final Vector3f dest) {
        Quaterniond partRotation = new Quaterniond(-rotation.x, -rotation.y, rotation.z, rotation.w);
        Vector3d angles = partRotation.getEulerAnglesZYX(new Vector3d());

        return dest.set((float)angles.x, (float)angles.y, (float)angles.z);
    }

    /// Writes into `dest` the part offset (model pixels) that `location` stands for.
    public static Vector3f toPartOffset(final Vec3 location, final Vector3f dest) {
        return dest.set(
            (float)(-location.x * PIXELS_PER_METER),
            (float)(-location.y * PIXELS_PER_METER),
            (float)(location.z * PIXELS_PER_METER)
        );
    }

    /// A flat armature for a part-animated model: the root bone [ArmatureLoader#ROOT_BONE], standing for
    /// the model's root part, and one bone per name under it, all at rest on the origin.
    ///
    /// Only bone names matter to `ModelPartPoseBinder`, so the same names give the same armature on
    /// both sides, a dedicated server included. Bone model transforms of such an armature don't follow
    /// the model's geometry: don't place colliders or sockets on it.
    ///
    /// @param boneNames part names; [ArmatureLoader#ROOT_BONE] among them is the root itself
    public static Armature flatArmature(final Collection<String> boneNames) {
        Map<String, Bone> bones = new HashMap<>();
        Bone root = new Bone(
            ArmatureLoader.ROOT_BONE, 0, new Matrix4f(), new Vector3f(0.0F, ArmatureLoader.GENERATED_ROOT_TAIL_HEIGHT, 0.0F)
        );
        bones.put(root.getName(), root);

        for (String name : boneNames) {
            if (bones.containsKey(name)) continue;

            Bone bone = new Bone(name, bones.size(), new Matrix4f(), new Vector3f(0.0F, 0.1F, 0.0F));
            root.addChildBones(bone);
            bones.put(name, bone);
        }

        root.initOriginTransform(new Matrix4f());

        return new Armature(root, bones.size(), bones);
    }

    /// [#flatArmature] behind an accessor, for an [com.yesman.akythera.api.component.impl.ArmatureComponent].
    /// Not resolvable by id: never write it into synced data or codecs.
    public static AssetAccessor<Armature> flatArmatureAccessor(final Identifier id, final Collection<String> boneNames) {
        return new SingletonAccessor<>(id, flatArmature(boneNames), AssetAccessor.Type.ARMATURE);
    }

    private ModelPartSpace() {}
}
