package de.knito.verticalcut.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import de.knito.verticalcut.VerticalCut;

/**
 * Step 1 skeleton: draws a placeholder box in the top-left corner and lets
 * the V key toggle it. The real terrain slice replaces the box in step 2.
 */
public class VerticalCutClient implements ClientModInitializer {
	private static final int BOX_X = 4;
	private static final int BOX_Y = 4;
	private static final int BOX_WIDTH = 128;
	private static final int BOX_HEIGHT = 64;

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(VerticalCut.id("verticalcut"));

	private static KeyMapping toggleKey;
	private static boolean visible = true;

	@Override
	public void onInitializeClient() {
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.verticalcut.toggle",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_V,
				CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.consumeClick()) {
				visible = !visible;
			}
		});

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, VerticalCut.id("slice"), VerticalCutClient::extract);

		VerticalCut.LOGGER.info("Vertical Cut loaded");
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		if (!visible || client.player == null) {
			return;
		}

		// Semi-transparent dark background with a light border.
		graphics.fill(BOX_X, BOX_Y, BOX_X + BOX_WIDTH, BOX_Y + BOX_HEIGHT, 0x80000000);
		graphics.fill(BOX_X, BOX_Y, BOX_X + BOX_WIDTH, BOX_Y + 1, 0xFFAAAAAA);
		graphics.fill(BOX_X, BOX_Y + BOX_HEIGHT - 1, BOX_X + BOX_WIDTH, BOX_Y + BOX_HEIGHT, 0xFFAAAAAA);
		graphics.fill(BOX_X, BOX_Y, BOX_X + 1, BOX_Y + BOX_HEIGHT, 0xFFAAAAAA);
		graphics.fill(BOX_X + BOX_WIDTH - 1, BOX_Y, BOX_X + BOX_WIDTH, BOX_Y + BOX_HEIGHT, 0xFFAAAAAA);

		graphics.text(client.font, "Vertical Cut", BOX_X + 6, BOX_Y + 6, 0xFFFFFFFF, true);
		graphics.text(client.font, "Y: " + client.player.getBlockY(), BOX_X + 6, BOX_Y + 18, 0xFFCCCCCC, true);
	}
}
