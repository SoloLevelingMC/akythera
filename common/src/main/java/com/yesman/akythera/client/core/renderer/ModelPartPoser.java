package com.yesman.akythera.client.core.renderer;

import com.yesman.akythera.client.api.component.impl.ModelPartPoseBinder;
import com.yesman.akythera.core.asset.loader.ArmatureLoader;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

/// Moves the parts of a model by a [ModelPartPoseBinder.Snapshot], right after the model's own
/// `setupAnim`.
public final class ModelPartPoser {
    /// Part lookup by model, built on first use. Models live as long as their renderer.
    private static final Map<Model<?>, Function<String, ModelPart>> PART_LOOKUPS = new WeakHashMap<>();

    public static void apply(final Model<?> model, final ModelPartPoseBinder.Snapshot snapshot) {
        Function<String, ModelPart> lookup = PART_LOOKUPS.computeIfAbsent(model, m -> m.root().createPartLookup());
        float weight = snapshot.weight();

        for (ModelPartPoseBinder.PartOffset offset : snapshot.offsets()) {
            ModelPart part = partOf(model, lookup, offset.part());

            if (part == null) continue;

            switch (snapshot.blend()) {
                case OVERRIDE -> override(part, offset, weight);
                case ADD -> add(part, offset, weight);
            }
        }
    }

    /// [ArmatureLoader#ROOT_BONE] is the model's root, even when a child part bears the same name.
    private static @Nullable ModelPart partOf(final Model<?> model, final Function<String, ModelPart> lookup, final String name) {
        return ArmatureLoader.ROOT_BONE.equals(name) ? model.root() : lookup.apply(name);
    }

    private static void override(final ModelPart part, final ModelPartPoseBinder.PartOffset offset, final float weight) {
        PartPose rest = part.getInitialPose();

        part.xRot = Mth.lerp(weight, part.xRot, rest.xRot() + offset.xRot());
        part.yRot = Mth.lerp(weight, part.yRot, rest.yRot() + offset.yRot());
        part.zRot = Mth.lerp(weight, part.zRot, rest.zRot() + offset.zRot());
        part.x = Mth.lerp(weight, part.x, rest.x() + offset.x());
        part.y = Mth.lerp(weight, part.y, rest.y() + offset.y());
        part.z = Mth.lerp(weight, part.z, rest.z() + offset.z());
        part.xScale = Mth.lerp(weight, part.xScale, rest.xScale() * offset.xScale());
        part.yScale = Mth.lerp(weight, part.yScale, rest.yScale() * offset.yScale());
        part.zScale = Mth.lerp(weight, part.zScale, rest.zScale() * offset.zScale());
    }

    private static void add(final ModelPart part, final ModelPartPoseBinder.PartOffset offset, final float weight) {
        part.xRot += offset.xRot() * weight;
        part.yRot += offset.yRot() * weight;
        part.zRot += offset.zRot() * weight;
        part.x += offset.x() * weight;
        part.y += offset.y() * weight;
        part.z += offset.z() * weight;
        part.xScale *= Mth.lerp(weight, 1.0F, offset.xScale());
        part.yScale *= Mth.lerp(weight, 1.0F, offset.yScale());
        part.zScale *= Mth.lerp(weight, 1.0F, offset.zScale());
    }

    private ModelPartPoser() {}
}
