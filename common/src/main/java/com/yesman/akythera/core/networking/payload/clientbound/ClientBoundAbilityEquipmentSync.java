package com.yesman.akythera.core.networking.payload.clientbound;

import com.yesman.akythera.api.world.ability.Ability;
import com.yesman.akythera.api.world.ability.AbilitySlot;
import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.blueprint.AbilityBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/// One slot's equipment on an entity's [AbilitySystemComponent].
///
/// `held` false releases the slot. A held slot names its ability by `ability`, its blueprint key,
/// or, when that is empty, holds an ability Java built, which the client mirrors as a
/// [com.yesman.akythera.api.world.ability.MirroredAbility]. `activated` is false for a live give and
/// carries the current flag in a snapshot.
@NullMarked
public record ClientBoundAbilityEquipmentSync(
    int entityId,
    AbilitySlot slot,
    boolean held,
    Optional<ResourceKey<AbilityBlueprint>> ability,
    boolean activated
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientBoundAbilityEquipmentSync> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "ability_equipment_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientBoundAbilityEquipmentSync> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT,
            ClientBoundAbilityEquipmentSync::entityId,
            AbilitySlot.ENUM_MANAGER.getStreamCodec(),
            ClientBoundAbilityEquipmentSync::slot,
            ByteBufCodecs.BOOL,
            ClientBoundAbilityEquipmentSync::held,
            ByteBufCodecs.optional(ResourceKey.streamCodec(AkytheraRegistries.DataPack.ABILITY_KEY)),
            ClientBoundAbilityEquipmentSync::ability,
            ByteBufCodecs.BOOL,
            ClientBoundAbilityEquipmentSync::activated,
            ClientBoundAbilityEquipmentSync::new
        );

    /// `slot` now holds `ability`.
    public static ClientBoundAbilityEquipmentSync of(
        final int entityId,
        final AbilitySlot slot,
        final Ability<?> ability,
        final boolean activated
    ) {
        return new ClientBoundAbilityEquipmentSync(
            entityId, slot, true, Optional.ofNullable(ability.getBlueprintKey()), activated
        );
    }

    /// `slot` is empty.
    public static ClientBoundAbilityEquipmentSync released(final int entityId, final AbilitySlot slot) {
        return new ClientBoundAbilityEquipmentSync(entityId, slot, false, Optional.empty(), false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
