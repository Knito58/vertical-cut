package de.knito.verticalcut;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the module key was pressed, open the active CRT's module screen. */
public record OpenModulesPayload() implements CustomPacketPayload {
	public static final OpenModulesPayload INSTANCE = new OpenModulesPayload();
	public static final Type<OpenModulesPayload> TYPE = new Type<>(VerticalCut.id("open_modules"));
	public static final StreamCodec<RegistryFriendlyByteBuf, OpenModulesPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
