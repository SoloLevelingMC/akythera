package com.yesman.akythera.client.api.lifecycle;

import com.mojang.blaze3d.systems.DeviceInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yesman.akythera.api.lifecycle.ComponentsFactory;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.util.ClientOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.jspecify.annotations.Nullable;

/// *Physical client*(commonly refers Minecraft launcher) side components factory.
@ClientOnly
public final class ClientComponentsFactory extends ComponentsFactory {
    /// Returns client component factory by force casting
    public static ClientComponentsFactory getImpl() {
        return (ClientComponentsFactory)ComponentsFactory.getImpl();
    }

    @Override
    public ResourceManager getResourceManager() {
        return Minecraft.getInstance().getResourceManager();
    }

    @Override
    public @Nullable PlayerSkin getPlayerSkin(final Player player) {
        if (player instanceof AbstractClientPlayer abstractClientPlayer) return abstractClientPlayer.getSkin();

        return null;
    }

    // *********************************************************
    //  Compute Shader Part
    // *********************************************************
    /// Logs the graphics backend Minecraft picked.
    ///
    /// SoloLevelingMC fork: the compute shader path (GPU skinning, cloth simulation, `TrailRenderer`
    /// ribbons) is removed together with its shader sources, which are not redistributable (see
    /// README_FORK.md). Skeletal meshes are always skinned on the CPU ([SkeletalMesh#drawPosed]).
    public void inspectDeviceSpec() {
        DeviceInfo deviceInfo = RenderSystem.getDevice().getDeviceInfo();

        Akythera.LOGGER.info(
            "[Akythera] Graphics backend: {}; compute shaders are not part of this build, meshes are skinned on the CPU",
            deviceInfo.backendName()
        );
    }

    /// Always false: this build has no compute shader path, see [#inspectDeviceSpec].
    public boolean supportComputeShader() {
        return false;
    }

    /// Always false: this build never simulates cloth, see [#inspectDeviceSpec].
    public boolean supportClothSimulation() {
        return false;
    }

    // *********************************************************
    //  Equipment Part
    // *********************************************************
    private ArmorModelResolver armorModelResolver = (_, _, model) -> model;

    /// Sets how the model an item replaces its wearer's armor model with is read.
    public void setArmorModelResolver(final ArmorModelResolver armorModelResolver) {
        this.armorModelResolver = armorModelResolver;
    }

    /// The model `stack` is worn as, given `model`, the one its wearer's renderer wears that slot with.
    public Model<?> getArmorModel(final ItemStack stack, final EquipmentClientInfo.LayerType layerType, final Model<?> model) {
        return armorModelResolver.resolve(stack, layerType, model);
    }

    private ArmorTextureResolver armorTextureResolver = (_, _, _, texture) -> texture;

    /// Sets how the texture an item replaces one of its equipment layers' with is read.
    public void setArmorTextureResolver(final ArmorTextureResolver armorTextureResolver) {
        this.armorTextureResolver = armorTextureResolver;
    }

    /// The texture `layer` of `stack` is drawn with, given `texture`, the one its equipment asset names.
    public Identifier getArmorTexture(
        final ItemStack stack, final EquipmentClientInfo.LayerType layerType, final EquipmentClientInfo.Layer layer, final Identifier texture
    ) {
        return armorTextureResolver.resolve(stack, layerType, layer, texture);
    }

    private ArmorTintResolver armorTintResolver = (stack, layer, _) -> {
        int dyeColor = DyedItemColor.getOrDefault(stack, 0);

        return layer.dyeable().isEmpty()
            ? -1
            : dyeColor != 0 ? dyeColor : layer.dyeable().get().colorWhenUndyed().map(ARGB::opaque).orElse(0);
    };

    /// Sets how the color an item tints one of its equipment layers with is read.
    public void setArmorTintResolver(final ArmorTintResolver armorTintResolver) {
        this.armorTintResolver = armorTintResolver;
    }

    /// The ARGB color the `layerIndex`-th equipment layer of `stack` is tinted with, 0 for a layer that is not drawn.
    public int getArmorTint(final ItemStack stack, final EquipmentClientInfo.Layer layer, final int layerIndex) {
        return armorTintResolver.resolve(stack, layer, layerIndex);
    }

    private ArmorSubmitter armorSubmitter = (_, _, _, _, _, _, _) -> false;

    /// Sets how an item that draws its own armor is drawn.
    public void setArmorSubmitter(final ArmorSubmitter armorSubmitter) {
        this.armorSubmitter = armorSubmitter;
    }

    /// Draws `stack` through the renderer its item registered for itself, in the space of `parentModel`. False
    /// when the item registered none.
    public boolean submitArmor(
        final PoseStack poseStack,
        final SubmitNodeCollector submitNodeCollector,
        final ItemStack stack,
        final LivingEntityRenderState state,
        final EquipmentSlot slot,
        final int lightCoords,
        final Model<?> parentModel
    ) {
        return armorSubmitter.submit(poseStack, submitNodeCollector, stack, state, slot, lightCoords, parentModel);
    }

    /// Reads the model an item is worn as through the mod loader's own item hook.
    @FunctionalInterface
    public interface ArmorModelResolver {
        /// The model `stack` is worn as, `model` itself for an item that replaces nothing.
        Model<?> resolve(ItemStack stack, EquipmentClientInfo.LayerType layerType, Model<?> model);
    }

    /// Reads the texture of an equipment layer through the mod loader's own item hook.
    @FunctionalInterface
    public interface ArmorTextureResolver {
        /// The texture `layer` is drawn with, `texture` itself for an item that replaces nothing.
        Identifier resolve(ItemStack stack, EquipmentClientInfo.LayerType layerType, EquipmentClientInfo.Layer layer, Identifier texture);
    }

    /// Reads the tint of an equipment layer through the mod loader's own item hook.
    @FunctionalInterface
    public interface ArmorTintResolver {
        /// The ARGB color the `layerIndex`-th layer is tinted with, 0 for a layer that is not drawn.
        int resolve(ItemStack stack, EquipmentClientInfo.Layer layer, int layerIndex);
    }

    /// Draws an item through the armor renderer the mod loader keeps for it.
    @FunctionalInterface
    public interface ArmorSubmitter {
        /// Whether `stack` was drawn.
        boolean submit(
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            ItemStack stack,
            LivingEntityRenderState state,
            EquipmentSlot slot,
            int lightCoords,
            Model<?> parentModel
        );
    }
}
