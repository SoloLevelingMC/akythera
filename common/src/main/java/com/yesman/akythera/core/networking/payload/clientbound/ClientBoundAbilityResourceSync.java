package com.yesman.akythera.core.networking.payload.clientbound;

import com.yesman.akythera.api.world.ability.AbilityResource;
import com.yesman.akythera.api.world.ability.AbilitySystemComponent;
import com.yesman.akythera.core.Akythera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/// Where one [AbilityResource] of the receiving player stands on the server: its value and its max,
/// for the HUD. Sent only to the owner, by its [AbilitySystemComponent], for a resource that declares
/// a [AbilityResource#syncThreshold].
///
/// The resource travels as its registry id, which the handler resolves: a client missing the entry
/// drops the packet rather than the connection.
@NullMarked
public record ClientBoundAbilityResourceSync(
    int entityId,
    Identifier resource,
    float value,
    float max
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientBoundAbilityResourceSync> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "ability_resource_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientBoundAbilityResourceSync> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT,
            ClientBoundAbilityResourceSync::entityId,
            Identifier.STREAM_CODEC,
            ClientBoundAbilityResourceSync::resource,
            ByteBufCodecs.FLOAT,
            ClientBoundAbilityResourceSync::value,
            ByteBufCodecs.FLOAT,
            ClientBoundAbilityResourceSync::max,
            ClientBoundAbilityResourceSync::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
