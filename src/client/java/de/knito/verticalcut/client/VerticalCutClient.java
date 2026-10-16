package de.knito.verticalcut.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import de.knito.verticalcut.VerticalCut;

/**
 * Client entrypoint: key mappings and the HUD element that shows the slice
 * in the top-left corner.
 *
 * Keys (changeable in Options, Controls): V shows or hides the cut,
 * X cycles through 7 opacity levels, B switches zoom between 1 and 2.
 */
public class VerticalCutClient implements ClientModInitializer {
	private static final int BOX_X = 4;
	private static final int BOX_Y = 4;
	private static final int OPACITY_LEVELS = 7;

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(VerticalCut.id("verticalcut"));

	private static final SliceRenderer SLICE = new SliceRenderer();

	private static KeyMapping toggleKey;
	private static KeyMapping opacityKey;
	private static KeyMapping zoomKey;

	private static boolean visible = true;
	/** 1..OPACITY_LEVELS; the highest level also draws a solid background. */
	private static int opacity = OPACITY_LEVELS - 1;

	@Override
	public void onInitializeClient() {
		toggleKey = register("key.verticalcut.toggle", InputConstants.KEY_V);
		opacityKey = register("key.verticalcut.opacity", InputConstants.KEY_X);
		zoomKey = register("key.verticalcut.zoom", InputConstants.KEY_B);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.consumeClick()) {
				visible = !visible;
			}
			while (opacityKey.consumeClick()) {
				opacity = opacity % OPACITY_LEVELS + 1;
			}
			while (zoomKey.consumeClick()) {
				SLICE.cycleZoom();
			}
			if (visible) {
				SLICE.tick(client);
			}
		});

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, VerticalCut.id("slice"), VerticalCutClient::extract);

		VerticalCut.LOGGER.info("Vertical Cut loaded");
	}

	private static KeyMapping register(String name, int key) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, key, CATEGORY));
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		if (!visible || client.player == null || !SLICE.isReady()) {
			return;
		}

		int size = SliceRenderer.SIZE;
		int alpha = 255 * opacity / OPACITY_LEVELS;

		if (opacity == OPACITY_LEVELS) {
			graphics.fill(BOX_X, BOX_Y, BOX_X + size, BOX_Y + size, 0xFF203060);
		} else {
			graphics.fill(BOX_X, BOX_Y, BOX_X + size, BOX_Y + size, (alpha / 3) << 24);
		}

		graphics.blit(RenderPipelines.GUI_TEXTURED, SLICE.textureId, BOX_X, BOX_Y, 0, 0, size, size, size, size,
				(alpha << 24) | 0xFFFFFF);

		graphics.outline(BOX_X - 1, BOX_Y - 1, size + 2, size + 2, 0xFFAAAAAA);
		graphics.text(client.font, "x" + SLICE.zoom(), BOX_X + 3, BOX_Y + size - 11, 0xFFFFFFFF, true);
	}
}
