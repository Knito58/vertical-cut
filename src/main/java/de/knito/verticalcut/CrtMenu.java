package de.knito.verticalcut;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The module slots of the CRT in the player's hand, plus the player's
 * inventory. The CRT itself can't be moved while its slots are open.
 */
public class CrtMenu extends AbstractContainerMenu {
	public static final int MODULE_Y = 20;
	private static final int INVENTORY_X = 8;
	private static final int INVENTORY_Y = 84;
	private static final int HOTBAR_Y = 142;

	public final ItemStack crt;
	private final InteractionHand hand;
	private final ModuleContainer modules;
	private final int moduleSlots;
	/** Hotbar index of the CRT when it's in the main hand, else -1. */
	private final int lockedHotbarSlot;

	public CrtMenu(int containerId, Inventory inventory) {
		super(VerticalCutMod.CRT_MENU, containerId);
		Player player = inventory.player;
		InteractionHand heldHand = CrtItem.heldHand(player);
		this.hand = heldHand == null ? InteractionHand.MAIN_HAND : heldHand;
		this.crt = heldHand == null ? ItemStack.EMPTY : player.getItemInHand(hand);
		this.moduleSlots = crt.getItem() instanceof CrtItem item ? item.tier.moduleSlots : 0;
		this.modules = new ModuleContainer(crt, moduleSlots,
				player instanceof ServerPlayer serverPlayer ? serverPlayer.level() : null);
		this.lockedHotbarSlot = hand == InteractionHand.MAIN_HAND ? inventory.getSelectedSlot() : -1;

		int startX = 89 - moduleSlots * SLOT_SIZE / 2;
		for (int i = 0; i < moduleSlots; i++) {
			addSlot(new ModuleSlot(modules, i, startX + i * SLOT_SIZE, MODULE_Y));
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(inventory, 9 + row * 9 + col, INVENTORY_X + col * SLOT_SIZE, INVENTORY_Y + row * SLOT_SIZE));
			}
		}
		for (int col = 0; col < 9; col++) {
			int x = INVENTORY_X + col * SLOT_SIZE;
			addSlot(col == lockedHotbarSlot ? new LockedSlot(inventory, col, x, HOTBAR_Y) : new Slot(inventory, col, x, HOTBAR_Y));
		}
	}

	public int moduleSlots() {
		return moduleSlots;
	}

	/**
	 * Client only: moves the player's inventory down to make room for longer
	 * text above it (translations can be longer than English). Slot positions
	 * are fixed once made, so the slots are replaced by moved copies.
	 */
	public void moveInventoryDown(int dy) {
		for (int i = moduleSlots; i < slots.size(); i++) {
			Slot old = slots.get(i);
			Slot moved = old instanceof LockedSlot
					? new LockedSlot(old.container, old.getContainerSlot(), old.x, old.y + dy)
					: new Slot(old.container, old.getContainerSlot(), old.x, old.y + dy);
			moved.index = old.index;
			slots.set(i, moved);
		}
	}

	/** Re-reads the modules after the server changed them (the CRT ate from the pouch). */
	public void reload() {
		modules.reload();
		broadcastChanges();
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput input, Player player) {
		// Number keys and F would swap the CRT itself out of the hand.
		if (input == ContainerInput.SWAP
				&& (button == lockedHotbarSlot || hand == InteractionHand.OFF_HAND && button == Inventory.SLOT_OFFHAND)) {
			return;
		}
		super.clicked(slotId, button, input, player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		Slot slot = slots.get(slotIndex);
		if (!slot.hasItem() || !slot.mayPickup(player)) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack moved = stack.copy();
		if (slotIndex < moduleSlots) {
			if (!moveItemStackTo(stack, moduleSlots, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (moduleSlots == 0 || !moveItemStackTo(stack, 0, moduleSlots, false)) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return moved;
	}

	@Override
	public boolean stillValid(Player player) {
		return !crt.isEmpty() && player.getItemInHand(hand) == crt;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		modules.setChanged();
	}

	/** Accepts one module; for now that is the coal pouch, at most one per CRT. */
	private class ModuleSlot extends Slot {
		ModuleSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			if (!CrtItem.isPouch(stack)) {
				return false;
			}
			for (int i = 0; i < moduleSlots; i++) {
				if (i != getContainerSlot() && CrtItem.isPouch(modules.getItem(i))) {
					return false;
				}
			}
			return true;
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}
	}

	/** The hotbar slot holding the open CRT: can't be taken or replaced. */
	private static class LockedSlot extends Slot {
		LockedSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean mayPickup(Player player) {
			return false;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}
	}

	/** The module slots, kept on the CRT item as a data component. */
	private static class ModuleContainer implements Container {
		private final ItemStack crt;
		private final NonNullList<ItemStack> items;
		/** Null on the client, which can't work out what the pouch is worth. */
		private final ServerLevel level;

		ModuleContainer(ItemStack crt, int size, ServerLevel level) {
			this.crt = crt;
			this.items = NonNullList.withSize(size, ItemStack.EMPTY);
			this.level = level;
			reload();
		}

		void reload() {
			NonNullList<ItemStack> stored = CrtItem.modules(crt);
			for (int i = 0; i < items.size(); i++) {
				items.set(i, i < stored.size() ? stored.get(i) : ItemStack.EMPTY);
			}
		}

		@Override
		public int getContainerSize() {
			return items.size();
		}

		@Override
		public boolean isEmpty() {
			return items.stream().allMatch(ItemStack::isEmpty);
		}

		@Override
		public ItemStack getItem(int slot) {
			return items.get(slot);
		}

		@Override
		public ItemStack removeItem(int slot, int amount) {
			ItemStack removed = items.get(slot).split(amount);
			setChanged();
			return removed;
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			ItemStack removed = items.get(slot);
			items.set(slot, ItemStack.EMPTY);
			setChanged();
			return removed;
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			items.set(slot, stack);
			setChanged();
		}

		@Override
		public void setChanged() {
			if (!crt.isEmpty()) {
				CrtItem.setModules(crt, items);
				if (level != null) {
					CrtPouch.updateEstimate(level, crt);
				}
			}
		}

		@Override
		public boolean stillValid(Player player) {
			return true;
		}

		@Override
		public void clearContent() {
			for (int i = 0; i < items.size(); i++) {
				items.set(i, ItemStack.EMPTY);
			}
			setChanged();
		}
	}
}
