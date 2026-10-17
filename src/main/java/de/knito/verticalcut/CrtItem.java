package de.knito.verticalcut;

import java.util.List;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

/**
 * The CRT ("Coal-powered Rock Tomograph").
 *
 * Fuel is one number, the stored burning value in fuel units
 * ({@link #FUEL_UNITS} per coal, up to {@link #MAX_FUEL}). The vanilla
 * durability bar doubles as a gauge for the coal that is burning right now:
 * damage 0 is a fresh coal, damage {@link #EMPTY} is nothing left. The damage
 * never reaches the maximum, so the item can't break. A new CRT starts empty.
 *
 * Sneak + right-click opens the module slots, if the tier has any.
 */
public class CrtItem extends Item {
	public static final int FUEL_UNITS = 240;
	public static final int MAX_FUEL = 256 * FUEL_UNITS;
	public static final int EMPTY = FUEL_UNITS;
	public static final int MAX_DAMAGE = FUEL_UNITS + 1;

	public final CrtTier tier;

	public CrtItem(CrtTier tier, Properties properties) {
		super(properties);
		this.tier = tier;
	}

	public static int fuel(ItemStack stack) {
		Integer stored = stack.get(CrtComponents.FUEL);
		if (stored != null) {
			return stored;
		}
		// CRTs from before the fuel store only had the gauge.
		return Math.max(0, EMPTY - stack.getDamageValue());
	}

	public static void setFuel(ItemStack stack, int units) {
		int fuel = Math.clamp(units, 0, MAX_FUEL);
		stack.set(CrtComponents.FUEL, fuel);
		int burning = fuel == 0 ? 0 : (fuel - 1) % FUEL_UNITS + 1;
		stack.setDamageValue(EMPTY - burning);
	}

	/** The modules in the CRT's slots; empty stacks for free slots. */
	public static NonNullList<ItemStack> modules(ItemStack crt) {
		int size = crt.getItem() instanceof CrtItem item ? item.tier.moduleSlots : 0;
		NonNullList<ItemStack> modules = NonNullList.withSize(size, ItemStack.EMPTY);
		crt.getOrDefault(CrtComponents.MODULES, ItemContainerContents.EMPTY).copyInto(modules);
		return modules;
	}

	public static void setModules(ItemStack crt, List<ItemStack> modules) {
		crt.set(CrtComponents.MODULES, ItemContainerContents.fromItems(modules));
	}

	/** Index of the coal pouch (any bundle) among the modules, or -1. */
	public static int pouchIndex(List<ItemStack> modules) {
		for (int i = 0; i < modules.size(); i++) {
			if (isPouch(modules.get(i))) {
				return i;
			}
		}
		return -1;
	}

	/** Whether a lightning rod (any kind, weathered and waxed ones too) is among the modules. */
	public static boolean hasLightningRod(List<ItemStack> modules) {
		return modules.stream().anyMatch(CrtItem::isLightningRod);
	}

	public static boolean isLightningRod(ItemStack stack) {
		return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("lightning_rod");
	}

	public static boolean hasDepthGauge(List<ItemStack> modules) {
		return modules.stream().anyMatch(stack -> stack.is(VerticalCutMod.DEPTH_GAUGE));
	}

	/** The compass module (plain or lodestone), or {@link ItemStack#EMPTY}. */
	public static ItemStack compass(List<ItemStack> modules) {
		return modules.stream().filter(stack -> stack.is(Items.COMPASS)).findFirst().orElse(ItemStack.EMPTY);
	}

	/** Whether the item works as a module, and which kind; null if it doesn't. */
	public static String moduleKind(ItemStack stack) {
		if (isPouch(stack)) {
			return "pouch";
		}
		if (isLightningRod(stack)) {
			return "lightning_rod";
		}
		if (stack.is(VerticalCutMod.DEPTH_GAUGE)) {
			return "depth_gauge";
		}
		if (stack.is(Items.COMPASS)) {
			return "compass";
		}
		return null;
	}

	/** Anything that works like a bundle, modded bundles included. */
	public static boolean isPouch(ItemStack stack) {
		return !stack.isEmpty() && stack.has(DataComponents.BUNDLE_CONTENTS);
	}

	/** The CRT the player holds, main hand first (the order Minecraft tries hands in), or {@link ItemStack#EMPTY}. */
	public static ItemStack held(Player player) {
		InteractionHand hand = heldHand(player);
		return hand == null ? ItemStack.EMPTY : player.getItemInHand(hand);
	}

	/**
	 * The CRT that scans: the held one, else (unless it must be in hand) the
	 * first one in the inventory; {@link ItemStack#EMPTY} if there is none.
	 */
	public static ItemStack active(Player player, boolean inHand) {
		ItemStack held = held(player);
		if (!held.isEmpty() || inHand) {
			return held;
		}
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).getItem() instanceof CrtItem) {
				return inventory.getItem(i);
			}
		}
		return ItemStack.EMPTY;
	}

	public static InteractionHand heldHand(Player player) {
		if (player.getMainHandItem().getItem() instanceof CrtItem) {
			return InteractionHand.MAIN_HAND;
		}
		if (player.getOffhandItem().getItem() instanceof CrtItem) {
			return InteractionHand.OFF_HAND;
		}
		return null;
	}

	/**
	 * The fuel changes every few ticks; without this the hand would bob as if
	 * a new item had been picked up each time.
	 */
	@Override
	public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
		return oldStack.getItem() != newStack.getItem();
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			if (tier.moduleSlots == 0) {
				serverPlayer.sendSystemMessage(Component.translatable("verticalcut.crt.no_slots"), true);
			} else {
				serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, p) -> new CrtMenu(id, inventory),
						Component.translatable("verticalcut.container.modules")));
			}
		}
		return InteractionResult.SUCCESS;
	}
}
