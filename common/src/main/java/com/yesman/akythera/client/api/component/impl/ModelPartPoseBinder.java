package com.yesman.akythera.client.api.component.impl;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.animation.impl.ModelPartSpace;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.core.animation.keyframe.Pose;
import com.yesman.akythera.core.asset.loader.ArmatureLoader;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import net.minecraft.util.StringRepresentable;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// Binds the entity's own cuboid model (the `ModelPart`s of its vanilla or modded `EntityModel`) to the
/// pose of the [ArmatureComponent] it is attached to. No Player Animation Library, no mesh: the parts of
/// the model the renderer already draws are moved.
///
/// ## Bones and parts
///
/// Each bone of the pose drives the model part of the same name, found anywhere in the model's tree.
/// The bone [ArmatureLoader#ROOT_BONE] drives the model's root part. [#partNames] renames: bone name to
/// part name, for a bone whose part is named otherwise. A bone with no part in the model is skipped,
/// and so is a part no bone of the current pose animates: it keeps what the model's own `setupAnim`
/// gave it. [ModelPartSpace#flatArmature] builds an armature from the part names alone.
///
/// ## Values
///
/// A bone's transform is an offset from the part's rest pose, in the units and axes
/// [ModelPartSpace] describes: rotation angles added to the part's rest angles, translation in model
/// pixels added to its rest position, scale multiplying its rest scale. That is the vanilla keyframe
/// animation and Blockbench convention.
///
/// ## Blending
///
/// - [Blend#OVERRIDE] (default): the part goes to its rest pose plus the offset, blended from what the
///   model's own animation set by the [Weighting].
/// - [Blend#ADD]: the offset, scaled by the weight, is added on top of the model's own animation.
///
/// With [Weighting#MONTAGE] (default), the weight follows the montages' fade in and fade out
/// ([com.yesman.akythera.api.animation.animator.Animator#getMontageWeight]): the model plays its own
/// animation while no montage plays. With [Weighting#ALWAYS] the armature drives every part its pose
/// holds, all the time.
///
/// ## Rendering
///
/// Only for entities drawn by a `LivingEntityRenderer`. The parts are moved right after every
/// `setupAnim` of a model drawn with the entity's render state, the entity's own model and the models
/// of its layers alike, so a layer that copies the body's geometry follows it. Unlike a skeletal
/// mesh, the renderer keeps placing the model itself (body rotation, scale, death tilt).
///
/// ## Registering
///
/// In code, under the [ArmatureComponent] of the patch, on the client:
///
/// ```java
/// BuiltInEventHooks.Entity.CONSTRUCTED.registerEvent(event -> {
///     if (event.isCopied() || !event.getEntityPatch().isLogicalClient()
///         || event.getEntityPatch().getOriginal().getType() != MY_CREATURE) return;
///
///     EntityPatch<?> patch = event.getEntityPatch();
///     event.addComponent("armature", new ArmatureComponent(
///         patch, MY_CREATURE_ARMATURE, // ModelPartSpace.flatArmatureAccessor(id, boneNames)
///         ComponentsFactory.getImpl().getAnimator(patch, MyAnimatorScript::new), false,
///         Transform.IDENTITY, TransformInheritPolicy.LOC_ROT_SCA,
///         Map.of("model_parts", new ModelPartPoseBinder())));
/// });
/// ```
///
/// In an entity patch data pack, as the component `akythera:model_part_pose_binder` of the armature:
/// `{"parts": {"bone": "part"}, "blend": "override", "weighting": "montage"}`, every field optional.
///
/// Simultaneously a component and a blueprint, like [PALPoseBinder]. This class names no client-only
/// type: a dedicated server reads its codec.
public final class ModelPartPoseBinder
    implements UniqueComponent, ComponentBlueprint<ModelPartPoseBinder, ArmatureComponent>
{
    public static final MapCodec<ModelPartPoseBinder> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("parts", Map.of()).forGetter(ModelPartPoseBinder::partNames),
            Blend.CODEC.optionalFieldOf("blend", Blend.OVERRIDE).forGetter(ModelPartPoseBinder::blend),
            Weighting.CODEC.optionalFieldOf("weighting", Weighting.MONTAGE).forGetter(ModelPartPoseBinder::weighting),
            ENABLED_CODEC.forGetter(ModelPartPoseBinder::enabled)
        ).apply(instance, ModelPartPoseBinder::new)
    );

    /// How a bone's offset meets the pose the model's own animation gave the part.
    public enum Blend implements StringRepresentable {
        /// Rest pose plus offset, blended from the model's own pose by the weight.
        OVERRIDE,
        /// Offset scaled by the weight, added to the model's own pose.
        ADD;

        public static final Codec<Blend> CODEC = StringRepresentable.fromEnum(Blend::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /// Where the weight of [Blend] comes from.
    public enum Weighting implements StringRepresentable {
        /// The montages' weight: 0 while none plays, fading in and out with them.
        MONTAGE,
        /// Always 1.
        ALWAYS;

        public static final Codec<Weighting> CODEC = StringRepresentable.fromEnum(Weighting::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /// The offset of one part, in model units: angles in radians, translation in model pixels.
    public record PartOffset(
        String part,
        float xRot, float yRot, float zRot,
        float x, float y, float z,
        float xScale, float yScale, float zScale
    ) {}

    /// The offsets one frame of the armature puts on the model, read off the armature while the render
    /// state is extracted and applied to the model when it is set up.
    public record Snapshot(List<PartOffset> offsets, float weight, Blend blend) {}

    private final Map<String, String> partNames;
    private final Blend blend;
    private final Weighting weighting;
    private boolean enabled;

    /// Bones drive the parts of the same name, [Blend#OVERRIDE], [Weighting#MONTAGE].
    public ModelPartPoseBinder() {
        this(Map.of(), Blend.OVERRIDE, Weighting.MONTAGE, true);
    }

    /// @param partNames bone name to part name, for the bones whose part is named otherwise
    public ModelPartPoseBinder(final Map<String, String> partNames, final Blend blend, final Weighting weighting, final boolean enabled) {
        this.partNames = Map.copyOf(partNames);
        this.blend = blend;
        this.weighting = weighting;
        this.enabled = enabled;
    }

    public Map<String, String> partNames() {
        return partNames;
    }

    public Blend blend() {
        return blend;
    }

    public Weighting weighting() {
        return weighting;
    }

    /// The part `boneName` drives. [ArmatureLoader#ROOT_BONE] stands for the model's root part.
    public String partOf(final String boneName) {
        return partNames.getOrDefault(boneName, boneName);
    }

    /// The offsets the armature's last pose puts on the model, or null when it puts none: binder
    /// switched off, no pose yet, or weight 0.
    public @Nullable Snapshot snapshot(final ArmatureComponent armatureComponent) {
        if (!enabled) return null;

        float weight = weighting == Weighting.ALWAYS ? 1.0F : Math.clamp(armatureComponent.getAnimator().getMontageWeight(), 0.0F, 1.0F);
        Pose pose = armatureComponent.getCachedPose();

        if (weight <= 0.0F || pose == null) return null;

        List<PartOffset> offsets = new ArrayList<>();
        Vector3f rotation = new Vector3f();
        Vector3f translation = new Vector3f();

        pose.forEachTransforms((boneName, transform) -> {
            ModelPartSpace.toPartRotation(transform.rot(), rotation);
            ModelPartSpace.toPartOffset(transform.loc(), translation);
            offsets.add(new PartOffset(
                partOf(boneName),
                rotation.x, rotation.y, rotation.z,
                translation.x, translation.y, translation.z,
                (float)transform.sca().x, (float)transform.sca().y, (float)transform.sca().z
            ));
        });

        return offsets.isEmpty() ? null : new Snapshot(offsets, weight, blend);
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
    public ModelPartPoseBinder instantiate(final ArmatureComponent blueprintOwner) {
        return new ModelPartPoseBinder(partNames, blend, weighting, enabled);
    }

    /// Only a client draws the model.
    @Override
    public boolean buildsFor(final boolean logicalClient) {
        return logicalClient;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public MapCodec<? extends ComponentBlueprint<ModelPartPoseBinder, ArmatureComponent>> mapCodec() {
        return CODEC;
    }
}
