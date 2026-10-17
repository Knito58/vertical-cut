package de.knito.verticalcut;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.component.ItemContainerContents;

/** Data stored on a CRT item. All of it is synced to the client. */
public final class CrtComponents {
	/**
	 * Stored burning value in fuel units ({@link CrtItem#FUEL_UNITS} per coal),
	 * from 0 up to {@link CrtItem#MAX_FUEL}.
	 */
	public static final DataComponentType<Integer> FUEL = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			VerticalCut.id("fuel"),
			DataComponentType.<Integer>builder()
					.persistent(ExtraCodecs.NON_NEGATIVE_INT)
					.networkSynchronized(ByteBufCodecs.VAR_INT)
					.build());

	/**
	 * What the coal pouch holds, in fuel units. Worked out by the server,
	 * which knows the burn times; only there while a pouch is installed.
	 */
	public static final DataComponentType<Integer> POUCH_FUEL = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			VerticalCut.id("pouch_fuel"),
			DataComponentType.<Integer>builder()
					.persistent(ExtraCodecs.NON_NEGATIVE_INT)
					.networkSynchronized(ByteBufCodecs.VAR_INT)
					.build());

	/** The modules put into the CRT's module slots. */
	public static final DataComponentType<ItemContainerContents> MODULES = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			VerticalCut.id("modules"),
			DataComponentType.<ItemContainerContents>builder()
					.persistent(ItemContainerContents.CODEC)
					.networkSynchronized(ItemContainerContents.STREAM_CODEC)
					.build());

	private CrtComponents() {
	}

	/** Forces class loading, so the components are registered before the items. */
	static void init() {
	}
}
