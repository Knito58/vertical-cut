package de.knito.verticalcut;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;

/**
 * The coal pouch: any bundle in a module slot. The CRT eats whatever burns in
 * a furnace from it, worth as much as it would burn there compared to coal.
 * Server side only, because burn times come from the server's data.
 */
public final class CrtPouch {
	/** Furnace burn time of one coal. */
	private static final int COAL_TICKS = 1600;

	private CrtPouch() {
	}

	/** Fuel units one item is worth, 0 if it doesn't burn. */
	public static int value(ServerLevel level, ItemStack stack) {
		if (!stack.has(DataComponents.COOKING_FUEL)) {
			return 0;
		}
		LootContext context = new LootContext.Builder(new LootParams.Builder(level).create(LootContextParamSets.EMPTY))
				.create(Optional.empty());
		try {
			int ticks = ResolvableInt.getFromItem(stack, DataComponents.COOKING_FUEL, CookingFuel::burnTime, context, 0);
			return (int) ((long) Math.max(0, ticks) * CrtItem.FUEL_UNITS / COAL_TICKS);
		} catch (RuntimeException e) {
			// A modded fuel that needs more context than we have just doesn't burn here.
			return 0;
		}
	}

	/**
	 * Eats from the pouch until at least one coal's worth is gained, in one
	 * gulp. An item worth more than the store holds stays in the pouch.
	 * Leftovers like the empty lava bucket go back into the pouch, else into
	 * the inventory, else onto the ground.
	 *
	 * @return the fuel units gained, 0 if there is no pouch or nothing in it burns
	 */
	public static int eat(ServerPlayer player, ItemStack crt) {
		NonNullList<ItemStack> modules = CrtItem.modules(crt);
		int index = CrtItem.pouchIndex(modules);
		if (index < 0) {
			return 0;
		}
		ItemStack pouch = modules.get(index);
		BundleContents contents = pouch.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
		List<ItemStack> items = new ArrayList<>(contents.itemCopies().toList());
		ServerLevel level = player.level();

		int gained = 0;
		List<ItemStack> leftovers = new ArrayList<>();
		for (ItemStack stack : items) {
			int value = value(level, stack);
			while (value > 0 && !stack.isEmpty() && gained < CrtItem.FUEL_UNITS && gained + value <= CrtItem.MAX_FUEL) {
				gained += value;
				ItemStackTemplate remainder = stack.getItem().getCraftingRemainder();
				stack.shrink(1);
				if (remainder != null) {
					leftovers.add(remainder.create());
				}
			}
			if (gained >= CrtItem.FUEL_UNITS) {
				break;
			}
		}
		if (gained == 0) {
			return 0;
		}

		BundleContents.Mutable mutable = contents.copyWithContents(items.stream().filter(s -> !s.isEmpty())).asMutable();
		for (ItemStack leftover : leftovers) {
			mutable.tryInsert(leftover);
			if (!leftover.isEmpty()) {
				player.getInventory().placeItemBackInInventory(leftover);
			}
		}
		pouch.set(DataComponents.BUNDLE_CONTENTS, mutable.toImmutable());
		CrtItem.setModules(crt, modules);
		return gained;
	}

	/**
	 * Remembers on the CRT what the pouch is worth, so the client can show it
	 * (it can't work out burn times itself). Removed when there is no pouch.
	 */
	public static void updateEstimate(ServerLevel level, ItemStack crt) {
		NonNullList<ItemStack> modules = CrtItem.modules(crt);
		int index = CrtItem.pouchIndex(modules);
		Integer estimate = null;
		if (index >= 0) {
			long total = modules.get(index).getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY)
					.itemCopies()
					.mapToLong(stack -> (long) value(level, stack) * stack.getCount())
					.sum();
			estimate = (int) Math.min(Integer.MAX_VALUE, total);
		}
		if (!Objects.equals(estimate, crt.get(CrtComponents.POUCH_FUEL))) {
			crt.set(CrtComponents.POUCH_FUEL, estimate);
		}
	}
}
