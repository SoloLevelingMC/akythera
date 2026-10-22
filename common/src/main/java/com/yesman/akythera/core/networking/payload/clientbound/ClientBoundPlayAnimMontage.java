package com.yesman.akythera.core.networking.payload.clientbound;

import com.yesman.akythera.core.Akythera;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

/// Plays a montage on an entity, from a section, at a play rate (1: the montage's own pace).
@NullMarked
public record ClientBoundPlayAnimMontage(Identifier montageId, String montageSection, int entityId, float playRate) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientBoundPlayAnimMontage> TYPE =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Akythera.MODID, "play_anim_montage"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientBoundPlayAnimMontage> STREAM_CODEC =
        StreamCodec.composite(
            Identifier.STREAM_CODEC,
            ClientBoundPlayAnimMontage::montageId,
            ByteBufCodecs.STRING_UTF8,
            ClientBoundPlayAnimMontage::montageSection,
            ByteBufCodecs.INT,
            ClientBoundPlayAnimMontage::entityId,
            ByteBufCodecs.FLOAT,
            ClientBoundPlayAnimMontage::playRate,
            ClientBoundPlayAnimMontage::new
        );

    /// Plays at the montage's own pace.
    public ClientBoundPlayAnimMontage(final Identifier montageId, final String montageSection, final int entityId) {
        this(montageId, montageSection, entityId, 1.0F);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
