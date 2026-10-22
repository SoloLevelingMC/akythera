package com.yesman.akythera.client.api.component.impl;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.api.component.impl.ArmatureComponent;

import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.compat.playeranimlibrary.AkytheraAnimationLayer;
import com.yesman.akythera.compat.CompatibleMod;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akythera.core.asset.armature.TransformSocket;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.platform.ModPlatformProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/// Binds the entity's own vanilla model to the pose of the [ArmatureComponent] it is attached to,
/// through Player Animation Library.
///
/// Draws nothing itself: [LivingEntityRenderer] keeps submitting the model, and
/// [AkytheraAnimationLayer] poses it while this binder is on. Whether that model is drawn at all is
/// [EntityPatch#isBlockingVanillaRender] and the renderables standing in for it.
///
/// ## Weight and blending
///
/// Each part of the model goes from the pose Player Animation Library hands to the layer (the vanilla
/// animation, and the layers under this one) to the armature's pose, by a weight: the binder's
/// [Weighting] (always 1, or the montages' fade in and fade out) times the part's own weight from
/// [PartWeights] (a mask: upper body against vanilla legs, for instance). Position, scale and bend
/// are blended linearly, each rotation angle by its shortest way. At weight 0 the layer is inactive
/// and the model keeps its vanilla animation. A layer above this one reads the pose the part had
/// before this layer by [AkytheraAnimationLayer#vanillaPose].
///
/// ## Body yaw
///
/// [BodyYaw] says which way the body faces while the layer poses it: its vanilla yaw ([BodyYaw#NONE]),
/// the look direction ([BodyYaw#LOOK], the default), or a yaw a [BodyYawSource] gives
/// ([BodyYaw#SUPPLIED]). Below full weight the yaw is blended from the vanilla one.
///
/// ## Bends (BendableCuboids)
///
/// A part bends by the X rotation of the armature's bone named after it with a `_bend` suffix. A bone
/// Player Animation Library asks for with no model part of its own (the `right_arm` and `left_arm`
/// bones the held item reads its elbow bend from) gets the bend of that `_bend` bone too, so the item
/// follows the forearm.
///
/// [#bendCarries] makes parts follow the bend of a sibling: BendableCuboids folds the upper half of the
/// player's torso, but the head and arms are siblings of the torso in the vanilla model, not its
/// children. With `{"body": ["head", "right_arm", "left_arm"]}` ([#PLAYER_TORSO_BEND]) they turn with
/// the upper half of the torso about its fold, as Player Animation Library's own animations do.
///
/// ## Corrections
///
/// A [PartCorrection] may move a part in the armature's model space, left of its posed transform:
/// to set the upper body of a clip on the hips of the vanilla walk, for instance.
///
/// Weights, bones and parts are named after the model parts (`head`, `body`, `right_arm`…), which are
/// the armature's bone names.
///
/// Requires Player Animation Library: [#instantiate] throws without it.
///
/// Simultaneously a component and a blueprint: [#instantiate] copies the authored binder per
/// armature, the way [TransformSocket] copies itself, so each armature owns its own switch and
/// settings. In an entity patch data pack: `{"weighting": "always", "body_yaw": "look",
/// "bend_carries": {"body": ["head", "right_arm", "left_arm"]}}`, every field optional; the
/// [PartWeights], [PartCorrection] and [BodyYawSource] are set in code.
public final class PALPoseBinder
    implements UniqueComponent, ComponentBlueprint<PALPoseBinder, ArmatureComponent>
{
    public static final MapCodec<PALPoseBinder> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            ModelPartPoseBinder.Weighting.CODEC.optionalFieldOf("weighting", ModelPartPoseBinder.Weighting.ALWAYS).forGetter(PALPoseBinder::weighting),
            BodyYaw.CODEC.optionalFieldOf("body_yaw", BodyYaw.LOOK).forGetter(PALPoseBinder::bodyYaw),
            Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).optionalFieldOf("bend_carries", Map.of())
                .forGetter(binder -> binder.bendCarries.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())))),
            ENABLED_CODEC.forGetter(PALPoseBinder::enabled)
        ).apply(instance, (weighting, bodyYaw, carries, enabled) -> {
            PALPoseBinder binder = new PALPoseBinder(enabled);
            binder.setWeighting(weighting);
            binder.setBodyYaw(bodyYaw);
            carries.forEach((bone, parts) -> binder.setBendCarries(bone, Set.copyOf(parts)));
            return binder;
        })
    );

    /// The parts that follow the fold of the player's torso: head and arms ([#setBendCarries]).
    public static final Map<String, Set<String>> PLAYER_TORSO_BEND = Map.of("body", Set.of("head", "right_arm", "left_arm"));

    /// Below this angle (radians) BendableCuboids doesn't fold a part, so nothing follows it.
    public static final double MIN_BEND_ANGLE = 1.0E-4;

    /// Which way the body faces while the layer poses the model.
    public enum BodyYaw implements StringRepresentable {
        /// The vanilla body yaw: the body follows the walk.
        NONE,
        /// The look direction (the entity's yaw).
        LOOK,
        /// The yaw [#setBodyYawSource] gives. Without a source, as [#LOOK].
        SUPPLIED;

        public static final Codec<BodyYaw> CODEC = StringRepresentable.fromEnum(BodyYaw::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /// Gives the body yaw of [BodyYaw#SUPPLIED], once per frame.
    @FunctionalInterface
    public interface BodyYawSource {
        /// @param entity       the posed entity
        /// @param vanillaYaw   its body yaw before the layer, in degrees
        /// @param partialTick  the frame's partial tick
        /// @return the body yaw to pose with, in degrees
        float bodyYaw(Entity entity, float vanillaYaw, float partialTick);
    }

    /// The share of the armature's pose each part takes, from 0 (vanilla) to 1. Asked for every posed part,
    /// every frame; multiplied by the binder's weight.
    @FunctionalInterface
    public interface PartWeights {
        /// Every part takes the full pose.
        PartWeights FULL = part -> 1.0F;

        float weight(String partName);

        /// The weight of each named part, `others` for the rest.
        static PartWeights of(final Map<String, Float> weights, final float others) {
            Map<String, Float> copy = Map.copyOf(weights);
            return part -> copy.getOrDefault(part, others);
        }
    }

    /// Moves a posed part in the armature's model space: the matrix is applied left of the part's transform
    /// (the armature's own transform and [#bendCarries] included). Asked for every posed part, every frame.
    @FunctionalInterface
    public interface PartCorrection {
        /// @return the correction of the part, or null for none
        @Nullable Matrix4fc correction(String partName);
    }

    private boolean enabled;
    private ModelPartPoseBinder.Weighting weighting = ModelPartPoseBinder.Weighting.ALWAYS;
    private BodyYaw bodyYaw = BodyYaw.LOOK;
    private @Nullable BodyYawSource bodyYawSource;
    private PartWeights partWeights = PartWeights.FULL;
    private @Nullable PartCorrection partCorrection;
    /// Bent bone name to the parts that follow its fold
    private final Map<String, Set<String>> bendCarries = new HashMap<>();

    /// Switched on, [ModelPartPoseBinder.Weighting#ALWAYS], [BodyYaw#LOOK], no mask, no correction, no carried part.
    public PALPoseBinder() {
        this(true);
    }

    public PALPoseBinder(final boolean enabled) {
        this.enabled = enabled;
    }

    // ***************************************************************
    // Settings
    // ***************************************************************

    public ModelPartPoseBinder.Weighting weighting() {
        return weighting;
    }

    /// Where the layer's weight comes from: [ModelPartPoseBinder.Weighting#ALWAYS] (1 while the binder is on,
    /// the default) or [ModelPartPoseBinder.Weighting#MONTAGE] (the montages' fade in and fade out, so the model
    /// goes back to its vanilla animation as a montage fades out).
    public PALPoseBinder setWeighting(final ModelPartPoseBinder.Weighting weighting) {
        this.weighting = weighting;
        return this;
    }

    public BodyYaw bodyYaw() {
        return bodyYaw;
    }

    public PALPoseBinder setBodyYaw(final BodyYaw bodyYaw) {
        this.bodyYaw = bodyYaw;
        return this;
    }

    public @Nullable BodyYawSource bodyYawSource() {
        return bodyYawSource;
    }

    /// Sets the source of the body yaw and switches to [BodyYaw#SUPPLIED]; null goes back to [BodyYaw#LOOK].
    public PALPoseBinder setBodyYawSource(final @Nullable BodyYawSource bodyYawSource) {
        this.bodyYawSource = bodyYawSource;
        this.bodyYaw = bodyYawSource == null ? BodyYaw.LOOK : BodyYaw.SUPPLIED;
        return this;
    }

    public PartWeights partWeights() {
        return partWeights;
    }

    /// Sets the share of the pose each part takes; null for [PartWeights#FULL].
    public PALPoseBinder setPartWeights(final @Nullable PartWeights partWeights) {
        this.partWeights = partWeights == null ? PartWeights.FULL : partWeights;
        return this;
    }

    public @Nullable PartCorrection partCorrection() {
        return partCorrection;
    }

    public PALPoseBinder setPartCorrection(final @Nullable PartCorrection partCorrection) {
        this.partCorrection = partCorrection;
        return this;
    }

    /// The parts that follow the fold of each bent bone, by bent bone name. Unmodifiable.
    public Map<String, Set<String>> bendCarries() {
        return Map.copyOf(bendCarries);
    }

    /// Makes `parts` follow the fold of `bone` (the BendableCuboids bend its `_bend` bone gives it); an empty
    /// set stops it. See [#PLAYER_TORSO_BEND].
    public PALPoseBinder setBendCarries(final String bone, final Set<String> parts) {
        if (parts.isEmpty()) {
            bendCarries.remove(bone);
        } else {
            bendCarries.put(bone, Set.copyOf(parts));
        }

        return this;
    }

    /// The bent bone whose fold `partName` follows, or null.
    public @Nullable String carrierOf(final String partName) {
        for (Map.Entry<String, Set<String>> entry : bendCarries.entrySet()) {
            if (entry.getValue().contains(partName)) return entry.getKey();
        }

        return null;
    }

    /// The layer's weight on `armatureComponent` this frame: 0 while switched off, else by [#weighting].
    public float weight(final ArmatureComponent armatureComponent) {
        if (!enabled) return 0.0F;

        return weighting == ModelPartPoseBinder.Weighting.ALWAYS
            ? 1.0F
            : Math.clamp(armatureComponent.getAnimator().getMontageWeight(), 0.0F, 1.0F);
    }

    // ***************************************************************
    // Bends
    // ***************************************************************

    /// The bend angle (radians) BendableCuboids draws for the bone `bendBone` of `pose`: the X angle of its
    /// rotation decomposed in Z, Y, X order. 0 when the pose has no such bone.
    public static double bendAngle(final @Nullable Pose pose, final String bendBone) {
        if (pose == null || !pose.hasTransform(bendBone)) return 0.0;

        Quaterniond rot = pose.get(bendBone).rot();
        return rot.getEulerAnglesZYX(new Vector3d()).x();
    }

    /// The transform that carries a part along with the fold of `bentBone` in the armature's current pose,
    /// in the space of [ArmatureComponent#getBoundTransforms] (multiply it left of a part's bound transform):
    /// `bound(bentBone) × T(pivot) × Rx(angle) × T(−pivot) × bound(bentBone)⁻¹`, the pivot being the rest
    /// position of its `_bend` bone. Null when the bone isn't folded (below [#MIN_BEND_ANGLE]) or the pose
    /// isn't there yet.
    ///
    /// Also what code following the drawn model needs, a weapon trail for instance.
    public static @Nullable Matrix4f bendCarryTransform(final ArmatureComponent armatureComponent, final String bentBone) {
        Matrix4f[] bound = armatureComponent.getBoundTransforms();

        if (bound == null || armatureComponent.getArmature() == null) return null;

        double angle = bendAngle(armatureComponent.getCachedPose(), bentBone + "_bend");

        if (Math.abs(angle) < MIN_BEND_ANGLE) return null;

        Armature armature = armatureComponent.getArmature().get();
        Bone bone = armature.getBoneByName(bentBone);
        Bone bend = armature.getBoneByName(bentBone + "_bend");

        if (bone == null || bend == null || bone.getId() >= bound.length) return null;

        Vector3f pivot = bend.getLocalTransform().getTranslation(new Vector3f());
        Matrix4f boneTransform = bound[bone.getId()];

        return new Matrix4f(boneTransform)
            .translate(0.0F, pivot.y, pivot.z)
            .rotateX((float)angle)
            .translate(0.0F, -pivot.y, -pivot.z)
            .mul(new Matrix4f(boneTransform).invertAffine());
    }

    // ***************************************************************
    // PatchComponent
    // ***************************************************************

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    // ***************************************************************
    // ComponentBlueprint
    // ***************************************************************

    @Override
    public PALPoseBinder instantiate(final ArmatureComponent blueprintOwner) {
        if (!palLoaded()) {
            throw new IllegalStateException("PAL Pose Binder requires PAL to be installed, but it doesn't.");
        }

        PALPoseBinder copy = new PALPoseBinder(enabled);
        copy.weighting = weighting;
        copy.bodyYaw = bodyYaw;
        copy.bodyYawSource = bodyYawSource;
        copy.partWeights = partWeights;
        copy.partCorrection = partCorrection;
        copy.bendCarries.putAll(bendCarries);

        return copy;
    }

    /// Built only on a client that has Player Animation Library. Elsewhere the binder is skipped, and
    /// a lookup by name answers null.
    @Override
    public boolean buildsFor(final boolean logicalClient) {
        return logicalClient && palLoaded();
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public MapCodec<? extends ComponentBlueprint<PALPoseBinder, ArmatureComponent>> mapCodec() {
        return CODEC;
    }

    // ***************************************************************
    // Helpers
    // ***************************************************************

    /// Whether Player Animation Library is installed.
    private static boolean palLoaded() {
        return ModPlatformProvider.get().isModLoaded(CompatibleMod.PLAYER_ANIMATION_LIBRARY.getModId());
    }
}
