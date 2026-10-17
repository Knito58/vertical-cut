package de.knito.verticalcut.client;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import de.knito.verticalcut.CrtComponents;
import de.knito.verticalcut.CrtItem;
import de.knito.verticalcut.CrtMenu;

/**
 * The CRT's module slots above the player's inventory, with how much fuel the
 * CRT has stored and what the coal pouch holds. Drawn with plain fills in the
 * vanilla container colors, so it needs no texture. Long text (translations)
 * wraps, and the screen grows taller to fit it.
 */
public class CrtScreen extends AbstractContainerScreen<CrtMenu> {
	private static final int WIDTH = 176;
	private static final int BASE_HEIGHT = 166;
	private static final int TEXT_X = 8;
	private static final int TEXT_WIDTH = WIDTH - 2 * TEXT_X;
	private static final int TEXT_Y = CrtMenu.MODULE_Y + 22;
	private static final int LINE = 10;
	/** Lines that fit between the module slots and the inventory label without growing. */
	private static final int BASE_LINES = 2;

	private static final int PANEL = 0xFFC6C6C6;
	private static final int LIGHT = 0xFFFFFFFF;
	private static final int DARK = 0xFF555555;
	private static final int SLOT = 0xFF8B8B8B;
	private static final int SLOT_SHADOW = 0xFF373737;
	private static final int TEXT = 0xFF404040;

	public CrtScreen(CrtMenu menu, Inventory inventory, Component title) {
		this(menu, inventory, title, extraHeight());
	}

	private CrtScreen(CrtMenu menu, Inventory inventory, Component title, int extra) {
		super(menu, inventory, title, WIDTH, BASE_HEIGHT + extra);
		// The "Inventory" label already follows the height (imageHeight - 94).
		menu.moveInventoryDown(extra);
	}

	/** Room needed beyond the vanilla height, for the longest the text can get. */
	private static int extraHeight() {
		Font font = Minecraft.getInstance().font;
		String most = coals(CrtItem.MAX_FUEL);
		int stored = font.split(storedLine(most), TEXT_WIDTH).size();
		int pouch = Math.max(font.split(pouchLine(null), TEXT_WIDTH).size(),
				font.split(pouchLine(most), TEXT_WIDTH).size());
		return Math.max(0, stored + pouch - BASE_LINES) * LINE;
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
		Integer pouch = crt.get(CrtComponents.POUCH_FUEL);
		int textY = y + TEXT_Y;
		textY = drawWrapped(graphics, storedLine(coals(CrtItem.fuel(crt))), x + TEXT_X, textY);
		drawWrapped(graphics, pouchLine(pouch == null ? null : coals(pouch)), x + TEXT_X, textY);
	}

	/** Draws the text wrapped to the panel width; returns the y below it. */
	private int drawWrapped(GuiGraphicsExtractor graphics, Component text, int x, int y) {
		List<FormattedCharSequence> lines = font.split(text, TEXT_WIDTH);
		for (FormattedCharSequence line : lines) {
			graphics.text(font, line, x, y, TEXT, false);
			y += LINE;
		}
		return y;
	}

	private static Component storedLine(String coals) {
		return Component.translatable("verticalcut.crt.stored", coals);
	}

	/** @param coals the pouch's worth, or null when there is no pouch */
	private static Component pouchLine(String coals) {
		return coals == null
				? Component.translatable("verticalcut.crt.pouch.none")
				: Component.translatable("verticalcut.crt.pouch", coals);
	}

	/** Fuel units as coals with one decimal, e.g. "12.5". */
	static String coals(int units) {
		return String.format(Locale.ROOT, "%.1f", units / (double) CrtItem.FUEL_UNITS);
	}
}
