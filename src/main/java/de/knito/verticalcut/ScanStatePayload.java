package de.knito.verticalcut;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: whether the player currently shows the cut, and whether
 * the CRT must be in hand (else any CRT in the inventory works). The server
 * only burns fuel while the cut shows, so hiding it with V saves coal.
 */
public record ScanStatePayload(boolean scanning, boolean inHand) implements CustomPacketPayload {
	public static final Type<ScanStatePayload> TYPE = new Type<>(VerticalCut.id("scan_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ScanStatePayload> CODEC =
			StreamCodec.composite(ByteBufCodecs.BOOL, ScanStatePayload::scanning, ByteBufCodecs.BOOL,
					ScanStatePayload::inHand, ScanStatePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
