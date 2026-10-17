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
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;
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
	static {
		CrtComponents.init();
	}

	public static final CrtItem STONE_CRT = registerCrt(CrtTier.STONE);
	public static final CrtItem COPPER_CRT = registerCrt(CrtTier.COPPER);
	/** Upgrades a Stone CRT to a Copper CRT on the smithing table. */
	public static final Item COPPER_TUBE = registerItem("copper_tube");
	/** Module: a Y ruler inside the picture. */
	public static final Item DEPTH_GAUGE = registerItem("depth_gauge");
	public static final CrtItem IRON_CRT = registerCrt(CrtTier.IRON);
	/** Upgrades a Copper CRT to an Iron CRT on the smithing table. */
	public static final Item IRON_TUBE = registerItem("iron_tube");
	public static final CrtItem GOLD_CRT = registerCrt(CrtTier.GOLD);
	/** Upgrades an Iron CRT to a Gold CRT on the smithing table. */
	public static final Item GOLD_TUBE = registerItem("gold_tube");
	public static final CrtItem DIAMOND_CRT = registerCrt(CrtTier.DIAMOND);
	/** Upgrades a Gold CRT to a Diamond CRT on the smithing table. */
	public static final Item DIAMOND_TUBE = registerItem("diamond_tube");
	public static final MenuType<CrtMenu> CRT_MENU = Registry.register(BuiltInRegistries.MENU,
			VerticalCut.id("crt_modules"), new MenuType<>(CrtMenu::new, FeatureFlagSet.of()));

	/** Players whose client currently shows the cut. */
	private static final Set<UUID> SCANNING = new HashSet<>();
	/** Scanning players whose CRT may be anywhere in the inventory, not only in hand. */
	private static final Set<UUID> ANYWHERE = new HashSet<>();

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
			if (payload.inHand()) {
				ANYWHERE.remove(id);
			} else {
				ANYWHERE.add(id);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			SCANNING.remove(handler.player.getUUID());
			ANYWHERE.remove(handler.player.getUUID());
		});

		ServerTickEvents.END_SERVER_TICK.register(VerticalCutMod::tick);

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(tab -> {
					tab.accept(STONE_CRT);
					tab.accept(COPPER_CRT);
					tab.accept(COPPER_TUBE);
					tab.accept(IRON_CRT);
					tab.accept(IRON_TUBE);
					tab.accept(GOLD_CRT);
					tab.accept(GOLD_TUBE);
					tab.accept(DIAMOND_CRT);
					tab.accept(DIAMOND_TUBE);
					tab.accept(DEPTH_GAUGE);
				});

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

	private static Item registerItem(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, VerticalCut.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
	}

	/**
	 * Burns one fuel unit for every scanning player at a pace that burns one
	 * coal in the configured seconds per coal. When the store runs dry the
	 * CRT eats: from the coal pouch if it has one, else coal from the inventory.
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
			ItemStack crt = CrtItem.active(player, !ANYWHERE.contains(player.getUUID()));
			if (crt.isEmpty()) {
				continue;
			}
			// Refuel in the same step the tank runs dry, so the client sees
			// "1 -> full" (hiccup) with coal, and "1 -> 0" (switch-off) only without.
			int fuel = Math.max(0, CrtItem.fuel(crt) - 1);
			if (fuel == 0) {
				fuel = eat(player, crt);
			}
			CrtItem.setFuel(crt, fuel);
			CrtPouch.updateEstimate(player.level(), crt);
			if (player.containerMenu instanceof CrtMenu menu && menu.crt == crt) {
				menu.reload();
			}
		}
	}

	/** Fuel units gained by eating; creative players get a coal for free. */
	private static int eat(ServerPlayer player, ItemStack crt) {
		if (player.getAbilities().instabuild) {
			return CrtItem.FUEL_UNITS;
		}
		if (CrtItem.pouchIndex(CrtItem.modules(crt)) >= 0) {
			return CrtPouch.eat(player, crt);
		}
		return eatCoal(player) ? CrtItem.FUEL_UNITS : 0;
	}

	/** Takes one coal or charcoal from the inventory. */
	private static boolean eatCoal(ServerPlayer player) {
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
