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
 * Client entrypoint: key mappings and the HUD element that shows the slice.
 *
 * Only V (show/hide) is bound by default. Opacity, zoom and the settings
 * screen have keys without a default binding; players who want them can
 * bind them in Options, Controls. Everything else is in the settings
 * screen, reachable through Mod Menu.
 */
public class VerticalCutClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(VerticalCut.id("verticalcut"));

	private static final SliceRenderer SLICE = new SliceRenderer();

	private static KeyMapping toggleKey;
	private static KeyMapping opacityKey;
	private static KeyMapping zoomKey;
	private static KeyMapping settingsKey;

	@Override
	public void onInitializeClient() {
		toggleKey = register("key.verticalcut.toggle", InputConstants.KEY_V);
		opacityKey = register("key.verticalcut.opacity", InputConstants.UNKNOWN.getValue());
		zoomKey = register("key.verticalcut.zoom", InputConstants.UNKNOWN.getValue());
		settingsKey = register("key.verticalcut.settings", InputConstants.UNKNOWN.getValue());

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			VerticalCutConfig config = VerticalCutConfig.get();
			boolean changed = false;
			while (toggleKey.consumeClick()) {
				config.visible = !config.visible;
				changed = true;
			}
			while (opacityKey.consumeClick()) {
				config.opacity = config.opacity % VerticalCutConfig.OPACITY_LEVELS + 1;
				changed = true;
			}
			while (zoomKey.consumeClick()) {
				config.zoom = config.zoom == 1 ? 2 : 1;
				changed = true;
			}
			if (changed) {
				config.save();
			}
			while (settingsKey.consumeClick()) {
				client.gui.setScreen(new VerticalCutConfigScreen(null));
			}
			if (config.visible) {
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
		VerticalCutConfig config = VerticalCutConfig.get();
		if (!config.visible || client.player == null || !SLICE.isReady()) {
			return;
		}

		int size = config.size;
		int x = config.corner.right() ? graphics.guiWidth() - config.margin - size : config.margin;
		int y = config.corner.bottom() ? graphics.guiHeight() - config.margin - size : config.margin;
		int alpha = 255 * config.opacity / VerticalCutConfig.OPACITY_LEVELS;

		if (config.opacity == VerticalCutConfig.OPACITY_LEVELS) {
			graphics.fill(x, y, x + size, y + size, 0xFF203060);
		} else {
			graphics.fill(x, y, x + size, y + size, (alpha / 3) << 24);
		}

		int tex = SliceRenderer.SIZE;
		graphics.blit(RenderPipelines.GUI_TEXTURED, SLICE.textureId, x, y, 0, 0, size, size, tex, tex, tex, tex,
				(alpha << 24) | 0xFFFFFF);

		graphics.outline(x - 1, y - 1, size + 2, size + 2, 0xFFAAAAAA);
		graphics.text(client.font, "x" + config.zoom, x + 3, y + size - 11, 0xFFFFFFFF, true);
	}
}
