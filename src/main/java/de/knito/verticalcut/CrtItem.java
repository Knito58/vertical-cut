package de.knito.verticalcut;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The CRT ("Coal-powered Rock Tomograph"). Its fuel is stored as item
 * damage, so the vanilla durability bar doubles as a fuel gauge:
 * damage 0 is a full tank, damage {@link #EMPTY} is empty. The damage never
 * reaches the maximum, so the item can't break. A new CRT starts empty.
 *
 * One coal fills {@link #FUEL_UNITS} units; how fast they burn depends on
 * the configured seconds per coal.
 */
public class CrtItem extends Item {
	public static final int FUEL_UNITS = 240;
	public static final int EMPTY = FUEL_UNITS;
	public static final int MAX_DAMAGE = FUEL_UNITS + 1;

	public final CrtTier tier;

	public CrtItem(CrtTier tier, Properties properties) {
		super(properties);
		this.tier = tier;
	}

	public static int fuel(ItemStack stack) {
		return Math.max(0, EMPTY - stack.getDamageValue());
	}

	public static void setFuel(ItemStack stack, int units) {
		stack.setDamageValue(EMPTY - Math.clamp(units, 0, FUEL_UNITS));
	}

	/** The CRT the player holds, offhand first, or {@link ItemStack#EMPTY}. */
	public static ItemStack held(Player player) {
		if (player.getOffhandItem().getItem() instanceof CrtItem) {
			return player.getOffhandItem();
		}
		if (player.getMainHandItem().getItem() instanceof CrtItem) {
			return player.getMainHandItem();
		}
		return ItemStack.EMPTY;
	}
}
