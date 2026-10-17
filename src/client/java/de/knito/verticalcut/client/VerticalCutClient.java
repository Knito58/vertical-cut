package de.knito.verticalcut.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import de.knito.verticalcut.CrtComponents;
import de.knito.verticalcut.CrtItem;
import de.knito.verticalcut.CrtTier;
import de.knito.verticalcut.ScanStatePayload;
import de.knito.verticalcut.VerticalCut;
import de.knito.verticalcut.VerticalCutConfig;
import de.knito.verticalcut.VerticalCutMod;

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
	/** Width at which the Shift details in tooltips wrap. */
	private static final int TOOLTIP_WIDTH = 200;
	/** Fuel units that fill the coal bar beside the frame: 64 coals. */
	private static final int COAL_BAR_FULL = 64 * CrtItem.FUEL_UNITS;

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
	/** Color picture (blue solid background) or black and white (black background). */
	private static boolean colorPicture;
	/** Tank empty and nothing to refill: show a dark frame with a blinking coal. */
	private static boolean outOfCoal;
	/** Picture is decaying and there is no spare coal: small coal warning in the corner. */
	private static boolean lowOnCoal;
	/** Share of the coal bar beside the frame (stored fuel plus pouch); -1 = no pouch, no bar. */
	private static float coalBar;
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
		MenuScreens.register(VerticalCutMod.CRT_MENU, CrtScreen::new);
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> addTooltip(stack, lines));
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, VerticalCut.id("slice"), VerticalCutClient::extract);
	}

	/**
	 * Fuel lines on every CRT, and with Shift held a short explanation of the
	 * item itself. It never tells about the next tiers; those are riddles.
	 */
	private static void addTooltip(ItemStack stack, List<Component> lines) {
		if (stack.getItem() instanceof CrtItem crtItem) {
			lines.add(Component.translatable("verticalcut.crt.stored", CrtScreen.coals(CrtItem.fuel(stack)))
					.withStyle(ChatFormatting.GRAY));
			Integer pouch = stack.get(CrtComponents.POUCH_FUEL);
			if (pouch != null) {
				lines.add(Component.translatable("verticalcut.crt.pouch", CrtScreen.coals(pouch))
						.withStyle(ChatFormatting.GRAY));
			}
			List<Component> details = new ArrayList<>();
			String prefix = "verticalcut.tooltip." + crtItem.tier.itemName + ".";
			details.add(Component.translatable(prefix + "shows"));
			details.add(Component.translatable(prefix + "picture"));
			details.add(Component.translatable("verticalcut.tooltip.crt.coal", VerticalCutConfig.get().secondsPerCoal));
			details.add(Component.translatable("verticalcut.tooltip.crt.key", toggleKey.getTranslatedKeyMessage()));
			if (crtItem.tier.moduleSlots > 0) {
				details.add(Component.translatable("verticalcut.tooltip.crt.slots", crtItem.tier.moduleSlots));
				details.add(Component.translatable("verticalcut.tooltip.crt.pouch"));
			}
			if (crtItem.tier == CrtTier.STONE) {
				details.add(Component.translatable("verticalcut.tooltip.stone_crt.upgrade"));
			}
			addDetails(lines, details);
		} else if (stack.is(VerticalCutMod.COPPER_TUBE)) {
			addDetails(lines, List.of(Component.translatable("verticalcut.tooltip.copper_tube.use")));
		}
	}

	/** Adds the details while Shift is held, else a hint to hold it. Long lines wrap. */
	private static void addDetails(List<Component> lines, List<Component> details) {
		Minecraft client = Minecraft.getInstance();
		if (!client.hasShiftDown()) {
			lines.add(Component.translatable("verticalcut.tooltip.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		for (Component detail : details) {
			for (FormattedText part : client.font.getSplitter().splitLines(detail, TOOLTIP_WIDTH, Style.EMPTY)) {
				lines.add(Component.literal(part.getString()).withStyle(ChatFormatting.GRAY));
			}
		}
	}

	private static KeyMapping register(String name, int key) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, key, CATEGORY));
	}

	private static void tick(Minecraft client) {
		VerticalCutConfig config = VerticalCutConfig.get();
		handleKeys(client, config);

		showing = false;
		decay = 0;
		colorPicture = true;
		outOfCoal = false;
		lowOnCoal = false;
		coalBar = -1;
		if (client.player == null) {
			return;
		}

		sendScanState(config.visible && config.requireDevice);

		if (!config.requireDevice) {
			EFFECTS.reset();
			showing = config.visible;
			if (showing) {
				SLICE.tick(client, config.zoom, SliceMode.COLOR, 0, false);
			}
			return;
		}

		ItemStack crt = CrtItem.held(client.player);
		if (crt.isEmpty() || !config.visible) {
			EFFECTS.reset();
			return;
		}

		int fuel = CrtItem.fuel(crt);
		Integer pouch = crt.get(CrtComponents.POUCH_FUEL);
		EFFECTS.tick(client, fuel, pouch != null);
		CrtTier tier = ((CrtItem) crt.getItem()).tier;
		EFFECTS.tickFlicker(client, tier, fuel > 0);
		colorPicture = tier.color;

		float share = (float) fuel / CrtItem.FUEL_UNITS;
		decay = fuel == 0 ? 1f : Math.max(0f, 1f - share / DECAY_START);
		// A pouch with fuel left means this isn't the real end yet: no snow.
		if (fuel > 0 && pouch != null && pouch > 0) {
			decay = 0;
		}
		showing = fuel > 0 || EFFECTS.dying();
		// With a pouch the CRT only eats from the pouch, without one only inventory coal.
		boolean spare = pouch != null ? pouch > 0 : hasCoal(client.player);
		boolean noSpare = !client.player.getAbilities().instabuild && !spare;
		if (pouch != null) {
			coalBar = Math.min(1f, (float) (fuel + pouch) / COAL_BAR_FULL);
		}
		outOfCoal = fuel == 0 && !EFFECTS.dying() && noSpare;
		lowOnCoal = decay > 0 && noSpare;
		if (showing) {
			int pixelsPerBlock = tier.switchableZoom ? config.zoom : tier.pixelsPerBlock;
			SliceMode mode = tier.color ? SliceMode.COLOR : tier.showsOres ? SliceMode.MONOCHROME : SliceMode.CAVES;
			// A flicker is a short burst of the same snow a dying CRT shows.
			float pictureDecay = Math.max(decay, EFFECTS.flickerStrength());
			SLICE.tick(client, pixelsPerBlock, mode, pictureDecay, tier.laggy);
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

		// The frame stays put (it only bulges when the CRT eats); all disturbance
		// happens inside the picture: snow and sliding rows come from the slice
		// texture, and a dying picture also dims now and then.
		int pictureAlpha = alpha;
		if (decay > 0 && RANDOM.nextFloat() < decay) {
			pictureAlpha = (int) (alpha * (0.55f + RANDOM.nextFloat() * 0.45f));
		}

		int shown = Math.round(size * EFFECTS.bulge());
		int sx = cx - shown / 2;
		int sy = cy - shown / 2;

		if (config.opacity == VerticalCutConfig.OPACITY_LEVELS) {
			graphics.fill(sx, sy, sx + shown, sy + shown, colorPicture ? 0xFF203060 : 0xFF000000);
		} else {
			graphics.fill(sx, sy, sx + shown, sy + shown, (alpha / 3) << 24);
		}

		int tex = SliceRenderer.SIZE;
		graphics.blit(RenderPipelines.GUI_TEXTURED, SLICE.textureId, sx, sy, 0, 0, shown, shown, tex, tex, tex, tex,
				(pictureAlpha << 24) | 0xFFFFFF);
		graphics.outline(sx - 1, sy - 1, shown + 2, shown + 2, 0xFFAAAAAA);
		if (coalBar >= 0) {
			drawCoalBar(graphics, x, y, size, config.corner.right());
		}

		if (lowOnCoal && blinkOn) {
			graphics.item(new ItemStack(Items.COAL), sx + shown - 18, sy + 2);
		}
	}

	/**
	 * Thin bar on the inner side of the frame (towards the middle of the
	 * screen), filling from the bottom: stored fuel plus the pouch. It stays
	 * with the frame and doesn't bulge.
	 */
	private static void drawCoalBar(GuiGraphicsExtractor graphics, int x, int y, int size, boolean rightCorner) {
		int bx = rightCorner ? x - 5 : x + size + 2;
		graphics.fill(bx, y - 1, bx + 3, y + size + 1, 0xC0101010);
		int filled = Math.round(size * coalBar);
		graphics.fill(bx, y + size - filled, bx + 3, y + size, 0xFFE0A030);
	}

	/**
	 * Old tube TV switching off: the frame stays, only the picture inside
	 * collapses into a bright horizontal line, the line shrinks to a dot, the
	 * dot fades away. The frame matches the out-of-coal screen that follows.
	 */
	private static void drawDeath(GuiGraphicsExtractor graphics, int cx, int cy, int size, float t) {
		int x = cx - size / 2;
		int y = cy - size / 2;
		graphics.fill(x, y, x + size, y + size, 0xC0101010);
		graphics.outline(x - 1, y - 1, size + 2, size + 2, 0xFF666666);

		if (t < 0.35f) {
			float p = t / 0.35f;
			int h = Math.max(2, Math.round(size * (1 - p * p)));
			int glow = (int) (255 * p);
			int tex = SliceRenderer.SIZE;
			graphics.blit(RenderPipelines.GUI_TEXTURED, SLICE.textureId, x, cy - h / 2, 0, 0, size, h,
					tex, tex, tex, tex, 0xFFFFFFFF);
			graphics.fill(x, cy - h / 2, x + size, cy - h / 2 + h, (glow << 24) | 0xFFFFFF);
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
