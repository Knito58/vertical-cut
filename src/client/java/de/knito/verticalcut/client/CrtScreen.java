package de.knito.verticalcut.client;

import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import de.knito.verticalcut.CrtComponents;
import de.knito.verticalcut.CrtItem;
import de.knito.verticalcut.CrtMenu;

/**
 * The CRT's module slots above the player's inventory, with how much fuel the
 * CRT has stored and what the coal pouch holds. Drawn with plain fills in the
 * vanilla container colors, so it needs no texture.
 */
public class CrtScreen extends AbstractContainerScreen<CrtMenu> {
	private static final int PANEL = 0xFFC6C6C6;
	private static final int LIGHT = 0xFFFFFFFF;
	private static final int DARK = 0xFF555555;
	private static final int SLOT = 0xFF8B8B8B;
	private static final int SLOT_SHADOW = 0xFF373737;
	private static final int TEXT = 0xFF404040;

	public CrtScreen(CrtMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractBackground(graphics, mouseX, mouseY, delta);
		int x = leftPos;
		int y = topPos;
		graphics.fill(x, y, x + imageWidth, y + imageHeight, DARK);
		graphics.fill(x, y, x + imageWidth - 1, y + imageHeight - 1, LIGHT);
		graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, PANEL);
		for (Slot slot : menu.slots) {
			int sx = x + slot.x - 1;
			int sy = y + slot.y - 1;
			graphics.fill(sx, sy, sx + 18, sy + 18, LIGHT);
			graphics.fill(sx, sy, sx + 17, sy + 17, SLOT_SHADOW);
			graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, SLOT);
		}

		// The client's copy of the CRT is replaced whenever the server syncs it.
		ItemStack crt = CrtItem.held(minecraft.player);
		int textY = y + CrtMenu.MODULE_Y + 22;
		graphics.text(font, Component.translatable("verticalcut.crt.stored", coals(CrtItem.fuel(crt))),
				x + 8, textY, TEXT, false);
		Integer pouch = crt.get(CrtComponents.POUCH_FUEL);
		Component pouchLine = pouch == null
				? Component.translatable("verticalcut.crt.pouch.none")
				: Component.translatable("verticalcut.crt.pouch", coals(pouch));
		graphics.text(font, pouchLine, x + 8, textY + 11, TEXT, false);
	}

	/** Fuel units as coals with one decimal, e.g. "12.5". */
	static String coals(int units) {
		return String.format(Locale.ROOT, "%.1f", units / (double) CrtItem.FUEL_UNITS);
	}
}
