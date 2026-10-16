package de.knito.verticalcut.client;

import java.util.Random;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import de.knito.verticalcut.CrtItem;
import de.knito.verticalcut.CrtTier;
import de.knito.verticalcut.ScanStatePayload;
import de.knito.verticalcut.VerticalCut;

/**
 * Client entrypoint: key mappings, deciding what the cut may show (depends on
 * the held CRT and its fuel), and the HUD element that draws it.
 *
 * Only V (show/hide) is bound by default. Opacity, zoom and the settings
 * screen have keys without a default binding.
 */
public class VerticalCutClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(VerticalCut.id("verticalcut"));

	/** Below this share of a full tank the picture starts to decay. */
	private static final float DECAY_START = 0.1f;

	private static final SliceRenderer SLICE = new SliceRenderer();
	private static final CrtEffects EFFECTS = new CrtEffects();
	private static final Random RANDOM = new Random();

	private static KeyMapping toggleKey;
	private static KeyMapping opacityKey;
	private static KeyMapping zoomKey;
	private static KeyMapping settingsKey;

	/** What the HUD should draw this tick, decided in the client tick. */
	private static boolean showing;
	private static float decay;
	/** Tank empty and nothing to refill: show a dark frame with a blinking coal. */
	private static boolean outOfCoal;
	/** Picture is decaying and there is no spare coal: small coal warning in the corner. */
	private static boolean lowOnCoal;
	/** Last scanning state sent to the server; null = send again. */
	private static Boolean sentScanning;

	@Override
	public void onInitializeClient() {
		toggleKey = register("key.verticalcut.toggle", InputConstants.KEY_V);
		opacityKey = register("key.verticalcut.opacity", InputConstants.UNKNOWN.getValue());
		zoomKey = register("key.verticalcut.zoom", InputConstants.UNKNOWN.getValue());
		settingsKey = register("key.verticalcut.settings", InputConstants.UNKNOWN.getValue());

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> sentScanning = null);
		ClientTickEvents.END_CLIENT_TICK.register(VerticalCutClient::tick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, VerticalCut.id("slice"), VerticalCutClient::extract);
	}

	private static KeyMapping register(String name, int key) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, key, CATEGORY));
	}

	private static void tick(Minecraft client) {
		VerticalCutConfig config = VerticalCutConfig.get();
		handleKeys(client, config);

		showing = false;
		decay = 0;
		outOfCoal = false;
		lowOnCoal = false;
		if (client.player == null) {
			return;
		}

		sendScanState(config.visible && config.requireDevice);

		if (!config.requireDevice) {
			EFFECTS.reset();
			showing = config.visible;
			if (showing) {
				SLICE.tick(client, config.zoom, true, 0);
			}
			return;
		}

		ItemStack crt = CrtItem.held(client.player);
		if (crt.isEmpty() || !config.visible) {
			EFFECTS.reset();
			return;
		}

		int fuel = CrtItem.fuel(crt);
		EFFECTS.tick(client, fuel);
		CrtTier tier = ((CrtItem) crt.getItem()).tier;

		float share = (float) fuel / CrtItem.FUEL_SECONDS;
		decay = fuel == 0 ? 1f : Math.max(0f, 1f - share / DECAY_START);
		showing = fuel > 0 || EFFECTS.dying();
		boolean noSpare = !client.player.getAbilities().instabuild && !hasCoal(client.player);
		outOfCoal = fuel == 0 && !EFFECTS.dying() && noSpare;
		lowOnCoal = decay > 0 && noSpare;
		if (showing) {
			int pixelsPerBlock = tier.switchableZoom ? config.zoom : tier.pixelsPerBlock;
			SLICE.tick(client, pixelsPerBlock, tier.color, decay);
		}
	}

	private static boolean hasCoal(Player player) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(ItemTags.COALS)) {
				return true;
			}
		}
		return false;
	}

	private static void handleKeys(Minecraft client, VerticalCutConfig config) {
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
	}

	private static void sendScanState(boolean scanning) {
		if (sentScanning != null && sentScanning == scanning) {
			return;
		}
		if (ClientPlayNetworking.canSend(ScanStatePayload.TYPE)) {
			ClientPlayNetworking.send(new ScanStatePayload(scanning));
			sentScanning = scanning;
		}
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || !(showing && SLICE.isReady() || outOfCoal)) {
			return;
		}

		VerticalCutConfig config = VerticalCutConfig.get();
		int size = config.size;
		int x = config.corner.right() ? graphics.guiWidth() - config.margin - size : config.margin;
		int y = config.corner.bottom() ? graphics.guiHeight() - config.margin - size : config.margin;
		int cx = x + size / 2;
		int cy = y + size / 2;
		boolean blinkOn = (System.currentTimeMillis() / 500) % 2 == 0;

		if (outOfCoal) {
			// Dead screen with a blinking "feed me" coal in the middle.
			graphics.fill(x, y, x + size, y + size, 0xC0101010);
			graphics.outline(x - 1, y - 1, size + 2, size + 2, 0xFF666666);
			if (blinkOn) {
				graphics.item(new ItemStack(Items.COAL), cx - 8, cy - 8);
			}
			return;
		}
		int alpha = 255 * config.opacity / VerticalCutConfig.OPACITY_LEVELS;

		float death = EFFECTS.deathProgress();
		if (death >= 0) {
			drawDeath(graphics, cx, cy, size, death);
			return;
		}

		// Flicker and wobble of a picture that is running out of fuel.
		int jitterX = 0;
		if (decay > 0 && RANDOM.nextFloat() < decay) {
			alpha = (int) (alpha * (0.55f + RANDOM.nextFloat() * 0.45f));
			jitterX = RANDOM.nextInt(5) - 2;
		}

		int shown = Math.round(size * EFFECTS.bulge());
		int sx = cx - shown / 2 + jitterX;
		int sy = cy - shown / 2;

		if (config.opacity == VerticalCutConfig.OPACITY_LEVELS) {
			graphics.fill(sx, sy, sx + shown, sy + shown, 0xFF203060);
		} else {
			graphics.fill(sx, sy, sx + shown, sy + shown, (alpha / 3) << 24);
		}

		int tex = SliceRenderer.SIZE;
		graphics.blit(RenderPipelines.GUI_TEXTURED, SLICE.textureId, sx, sy, 0, 0, shown, shown, tex, tex, tex, tex,
				(alpha << 24) | 0xFFFFFF);
		graphics.outline(sx - 1, sy - 1, shown + 2, shown + 2, 0xFFAAAAAA);

		if (lowOnCoal && blinkOn) {
			graphics.item(new ItemStack(Items.COAL), sx + shown - 18, sy + 2);
		}
	}

	/**
	 * Old tube TV switching off: the picture collapses into a bright
	 * horizontal line, the line shrinks to a dot, the dot fades away.
	 */
	private static void drawDeath(GuiGraphicsExtractor graphics, int cx, int cy, int size, float t) {
		if (t < 0.35f) {
			float p = t / 0.35f;
			int h = Math.max(2, Math.round(size * (1 - p * p)));
			int glow = (int) (255 * p);
			int tex = SliceRenderer.SIZE;
			graphics.blit(RenderPipelines.GUI_TEXTURED, SLICE.textureId, cx - size / 2, cy - h / 2, 0, 0, size, h,
					tex, tex, tex, tex, 0xFFFFFFFF);
			graphics.fill(cx - size / 2, cy - h / 2, cx + size / 2, cy - h / 2 + h, (glow << 24) | 0xFFFFFF);
		} else if (t < 0.6f) {
			float p = (t - 0.35f) / 0.25f;
			int w = Math.max(2, Math.round(size * (1 - p * p)));
			graphics.fill(cx - w / 2, cy - 1, cx - w / 2 + w, cy + 1, 0xFFFFFFFF);
		} else {
			float p = (t - 0.6f) / 0.4f;
			int a = (int) (255 * (1 - p));
			graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, (a << 24) | 0xFFFFFF);
		}
	}
}
