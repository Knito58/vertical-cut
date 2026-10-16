package de.knito.verticalcut;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Common entrypoint (runs on client and server): registers the CRT items and
 * burns their fuel. Fuel logic lives on the server, so it works the same in
 * single player and multiplayer.
 */
public class VerticalCutMod implements ModInitializer {
	public static final CrtItem COPPER_CRT = registerCrt(CrtTier.COPPER);

	/** Players whose client currently shows the cut. */
	private static final Set<UUID> SCANNING = new HashSet<>();

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.serverboundPlay().register(ScanStatePayload.TYPE, ScanStatePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ScanStatePayload.TYPE, (payload, context) -> {
			UUID id = context.player().getUUID();
			if (payload.scanning()) {
				SCANNING.add(id);
			} else {
				SCANNING.remove(id);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SCANNING.remove(handler.player.getUUID()));

		ServerTickEvents.END_SERVER_TICK.register(VerticalCutMod::tick);

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(tab -> tab.accept(COPPER_CRT));

		VerticalCut.LOGGER.info("Vertical Cut loaded");
	}

	private static CrtItem registerCrt(CrtTier tier) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, VerticalCut.id(tier.itemName));
		Item.Properties properties = new Item.Properties()
				.setId(key)
				.stacksTo(1)
				.durability(CrtItem.MAX_DAMAGE)
				.component(DataComponents.DAMAGE, CrtItem.EMPTY);
		CrtItem item = new CrtItem(tier, properties);
		Registry.register(BuiltInRegistries.ITEM, key, item);
		return item;
	}

	/**
	 * Burns one fuel unit for every scanning player at a pace that empties a
	 * full tank in the configured seconds per coal, and refuels from coal.
	 */
	private static void tick(MinecraftServer server) {
		// Spread FUEL_UNITS evenly over the configured time. A coal lasts at
		// least 60 s = 1200 ticks, so this burns at most one unit per tick.
		long ticksPerCoal = VerticalCutConfig.get().secondsPerCoal * 20L;
		long now = server.getTickCount();
		if (now * CrtItem.FUEL_UNITS / ticksPerCoal == (now - 1) * CrtItem.FUEL_UNITS / ticksPerCoal) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!SCANNING.contains(player.getUUID())) {
				continue;
			}
			ItemStack crt = CrtItem.held(player);
			if (crt.isEmpty()) {
				continue;
			}
			// Refuel in the same step the tank runs dry, so the client sees
			// "1 -> full" (hiccup) with coal, and "1 -> 0" (switch-off) only without.
			int fuel = Math.max(0, CrtItem.fuel(crt) - 1);
			if (fuel == 0 && eatCoal(player)) {
				fuel = CrtItem.FUEL_UNITS;
			}
			CrtItem.setFuel(crt, fuel);
		}
	}

	/** Takes one coal or charcoal from the inventory. Creative players get it for free. */
	private static boolean eatCoal(ServerPlayer player) {
		if (player.getAbilities().instabuild) {
			return true;
		}
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(ItemTags.COALS)) {
				stack.shrink(1);
				return true;
			}
		}
		return false;
	}
}
