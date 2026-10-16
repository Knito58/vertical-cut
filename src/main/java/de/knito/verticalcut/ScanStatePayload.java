package de.knito.verticalcut;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: whether the player currently shows the cut. The server
 * only burns fuel while it does, so hiding the cut with V saves coal.
 */
public record ScanStatePayload(boolean scanning) implements CustomPacketPayload {
	public static final Type<ScanStatePayload> TYPE = new Type<>(VerticalCut.id("scan_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ScanStatePayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.BOOL, ScanStatePayload::scanning, ScanStatePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
