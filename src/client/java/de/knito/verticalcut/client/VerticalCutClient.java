package de.knito.verticalcut.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;

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
import de.knito.verticalcut.client.mixin.ConditionalItemModelPropertiesAccessor;

/**
 * Client entrypoint: key mappings, deciding what the cut may show (depends on
 * the held CRT and its fuel), and the HUD element that draws it.
 *
 * Only V (show/hide), K (compass lock), J (spyglass) and B (zoom) are bound by default. Opacity and the settings screen
 * have keys without a default binding.
 */
public class VerticalCutClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(VerticalCut.id("verticalcut"));

	/** Below this share of a full tank the picture starts to decay. */
	private static final float DECAY_START = 0.1f;
	/** Blocks between two marks of the Depth Gauge ruler. */
	private static final int RULER_STEP = 8;
	private static final int RULER_TICK = 4;
	private static final int RULER_MINOR_TICK = 2;
	private static final float RULER_TEXT_SCALE = 0.5f;
	/** Length of the compass needle in GUI pixels. */
	/** The view counts as aligned with a fixed cut within this many degrees; it clinks once. */
	private static final float ALIGNED_DEGREES = 1.5f;
	/** ... and stays aligned (no new clink) until it is off by more than this. */
	private static final float UNALIGNED_DEGREES = 3f;
	/** Note block steps (12 = normal pitch) the alignment clink picks from. */
	private static final int[] CHIME_NOTES = {13, 15, 18, 20, 22};
	private static final int NEEDLE_LENGTH = 6;
	/** Width at which the Shift details in tooltips wrap. */
	private static final int TOOLTIP_WIDTH = 200;
	/** Fuel units that fill the coal bar beside the frame: 64 coals. */
	private static final int COAL_BAR_FULL = 64 * CrtItem.FUEL_UNITS;

	private static final SliceRenderer SLICE = new SliceRenderer();
	private static final CrtEffects EFFECTS = new CrtEffects();
	private static final Interference INTERFERENCE = new Interference();
	private static final MobDots MOB_DOTS = new MobDots();
	/** Share of interference a lightning rod module lets through. */
	private static final float ROD_LETS_THROUGH = 0.25f;
	private static final Random RANDOM = new Random();

	private static KeyMapping toggleKey;
	private static KeyMapping opacityKey;
	private static KeyMapping settingsKey;
	private static KeyMapping lockKey;
	private static KeyMapping spyglassKey;
	private static KeyMapping zoomKey;

	/** What the HUD should draw this tick, decided in the client tick. */
	private static boolean showing;
	private static float decay;
	/** Color picture (blue solid background) or black and white (black background). */
	private static boolean colorPicture;
	/** Tank empty and nothing to refill: show a dark frame with a blinking coal. */
	private static boolean outOfCoal;
	/** Picture is decaying and there is no spare coal: small coal warning in the corner. */
	private static boolean lowOnCoal;
	/** The held CRT has a Depth Gauge module: draw the Y ruler. */
	private static boolean depthGauge;
	/** Share of the coal bar beside the frame (stored fuel plus pouch); -1 = no pouch, no bar. */
	private static float coalBar;
	/** Compass module installed: the needle shows. */
	private static boolean compass;
	/** Direction the cut is fixed to (yaw in degrees), by the lock key or a lodestone; null = follows the view. */
	private static Float cutYaw;
	/** Locked by the lock key, in the direction the player looked; null = not locked. */
	private static Float lockedYaw;
	/** The view points along the fixed cut (within {@link #ALIGNED_DEGREES}): the needle turns green. */
	private static boolean aligned;
	/** Lodestone compass without a lodestone in this dimension: the needle spins. */
	private static boolean needleSpins;
	/** The lock key was pressed an odd number of times since the last tick. */
	private static boolean lockPressed;
	/** Spyglass module: the picture shows one picture height lower. */
	private static boolean deepView;
	/** The spyglass key was pressed an odd number of times since the last tick. */
	private static boolean spyglassPressed;
	/** The zoom key was pressed an odd number of times since the last tick. */
	private static boolean zoomPressed;
	/** Last scanning state sent to the server; null = send again. */
	private static ScanStatePayload sentScanState;

	@Override
	public void onInitializeClient() {
		toggleKey = register("key.verticalcut.toggle", InputConstants.KEY_V);
		opacityKey = register("key.verticalcut.opacity", InputConstants.UNKNOWN.getValue());
		settingsKey = register("key.verticalcut.settings", InputConstants.UNKNOWN.getValue());
		// L opens the advancements in vanilla, so K.
		lockKey = register("key.verticalcut.lock", InputConstants.KEY_K);
		spyglassKey = register("key.verticalcut.spyglass", InputConstants.KEY_J);
		// B was the zoom key of the old mod.
		zoomKey = register("key.verticalcut.zoom", InputConstants.KEY_B);

		ConditionalItemModelPropertiesAccessor.verticalcut$idMapper().put(VerticalCut.id("on_track"), OnTrack.MAP_CODEC);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> sentScanState = null);
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
				details.add(Component.translatable("verticalcut.tooltip.crt.finder"));
				details.add(Component.translatable("verticalcut.tooltip.crt.lightning_rod"));
				details.add(Component.translatable("verticalcut.tooltip.crt.depth_gauge"));
				details.add(Component.translatable("verticalcut.tooltip.crt.compass", lockKey.getTranslatedKeyMessage()));
				details.add(Component.translatable("verticalcut.tooltip.crt.spyglass", spyglassKey.getTranslatedKeyMessage()));
			}
			if (crtItem.tier.canZoom()) {
				details.add(Component.translatable("verticalcut.tooltip.crt.zoom", zoomKey.getTranslatedKeyMessage()));
			}
			if (crtItem.tier != CrtTier.NETHERITE) {
				details.add(Component.translatable(prefix + "upgrade"));
			}
			addDetails(lines, details);
		} else if (stack.is(VerticalCutMod.COPPER_TUBE)) {
			addDetails(lines, List.of(Component.translatable("verticalcut.tooltip.copper_tube.use")));
		} else if (stack.is(VerticalCutMod.DEPTH_GAUGE)) {
			addDetails(lines, List.of(Component.translatable("verticalcut.tooltip.depth_gauge.use")));
		} else if (stack.is(VerticalCutMod.IRON_TUBE)) {
			addDetails(lines, List.of(Component.translatable("verticalcut.tooltip.iron_tube.use")));
		} else if (stack.is(VerticalCutMod.GOLD_TUBE)) {
			addDetails(lines, List.of(Component.translatable("verticalcut.tooltip.gold_tube.use")));
		} else if (stack.is(VerticalCutMod.DIAMOND_TUBE)) {
			addDetails(lines, List.of(Component.translatable("verticalcut.tooltip.diamond_tube.use")));
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
		depthGauge = false;
		compass = false;
		cutYaw = null;
		needleSpins = false;
		boolean lockToggled = lockPressed;
		lockPressed = false;
		boolean spyglassToggled = spyglassPressed;
		spyglassPressed = false;
		boolean zoomToggled = zoomPressed;
		zoomPressed = false;
		if (client.player == null) {
			return;
		}

		sendScanState(new ScanStatePayload(config.visible, config.crtInHand));

		ItemStack crt = CrtItem.active(client.player, config.crtInHand);
		if (crt.isEmpty() || !config.visible) {
			EFFECTS.reset();
			INTERFERENCE.reset();
			lockedYaw = null;
			deepView = false;
			return;
		}

		int fuel = CrtItem.fuel(crt);
		Integer pouch = crt.get(CrtComponents.POUCH_FUEL);
		EFFECTS.tick(client, fuel, pouch != null);
		CrtTier tier = ((CrtItem) crt.getItem()).tier;
		EFFECTS.tickFlicker(client, tier, fuel > 0);
		float interference = INTERFERENCE.tick(client);
		depthGauge = CrtItem.hasDepthGauge(CrtItem.modules(crt));
		tickCompass(client, CrtItem.compass(CrtItem.modules(crt)), lockToggled);
		tickSpyglass(client, CrtItem.hasSpyglass(CrtItem.modules(crt)), spyglassToggled);
		if (CrtItem.hasLightningRod(CrtItem.modules(crt))) {
			interference *= ROD_LETS_THROUGH;
		}
		if (tier.netherite) {
			interference = 0;
		}
		EFFECTS.tickInterference(client, interference, fuel > 0);
		colorPicture = tier.color;
		if (zoomToggled && tier.canZoom() && fuel > 0) {
			config.zoom = tier.nextZoom(config.zoom);
			config.save();
			// Like an old TV changing channels: a moment of snow and a click.
			EFFECTS.refocus();
			CrtEffects.play(client, SoundEvents.UI_BUTTON_CLICK.value(), 0.8f + RANDOM.nextFloat() * 0.3f, 0.3f);
		}
		int pixelsPerBlock = tier.pixelsPerBlock(config.zoom);

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
		// A dead CRT wakes up looking near again.
		if (outOfCoal || EFFECTS.dying()) {
			deepView = false;
		}
		if (showing) {
			SliceMode mode = tier.color ? SliceMode.COLOR : tier.showsOres ? SliceMode.MONOCHROME : SliceMode.CAVES;
			// A flicker is a short burst of the same snow a dying CRT shows.
			float pictureDecay = Math.max(decay, EFFECTS.flickerStrength());
			float yaw = cutYaw != null ? cutYaw : client.player.getYRot();
			Finder finder = tier.showsOres ? Finder.of(CrtItem.finderSamples(CrtItem.modules(crt))) : Finder.NONE;
			SLICE.tick(client, pixelsPerBlock, mode, pictureDecay, tier.laggy, yaw, deepView, finder);
			MOB_DOTS.tick(client, tier.netherite, yaw);
		}
	}

	/** Spyglass module: its key flips between the normal picture and the one below it. */
	private static void tickSpyglass(Minecraft client, boolean spyglass, boolean toggled) {
		if (!spyglass) {
			deepView = false;
			return;
		}
		if (toggled) {
			deepView = !deepView;
			CrtEffects.play(client, deepView ? SoundEvents.SPYGLASS_USE : SoundEvents.SPYGLASS_STOP_USING, 1f, 1f);
		}
	}

	/**
	 * Compass module: the lock key fixes the cut in the direction the player
	 * looks, or lets it follow the view again. A lodestone compass points the
	 * cut at its lodestone instead; without one in this dimension the needle
	 * spins and the cut follows the view. Looking along a fixed cut turns the
	 * needle green with a clink, to help walk that way.
	 */
	private static void tickCompass(Minecraft client, ItemStack stack, boolean lockToggled) {
		compass = !stack.isEmpty();
		LodestoneTracker lodestone = compass ? stack.get(DataComponents.LODESTONE_TRACKER) : null;
		if (!compass || lodestone != null) {
			lockedYaw = null;
		}
		if (!compass) {
			aligned = false;
			return;
		}
		Player player = client.player;
		if (lodestone != null) {
			Optional<GlobalPos> target = lodestone.target();
			if (target.isPresent() && target.get().dimension() == client.level.dimension()) {
				BlockPos pos = target.get().pos();
				double dx = pos.getX() + 0.5 - player.getX();
				double dz = pos.getZ() + 0.5 - player.getZ();
				cutYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			} else {
				needleSpins = true;
			}
			updateAligned(client);
			return;
		}
		if (lockToggled) {
			if (lockedYaw == null) {
				lockedYaw = player.getYRot();
				player.sendOverlayMessage(Component.translatable("verticalcut.lock.on", bearing(lockedYaw)));
			} else {
				lockedYaw = null;
				player.sendOverlayMessage(Component.translatable("verticalcut.lock.off"));
			}
		}
		cutYaw = lockedYaw;
		updateAligned(client);
	}

	/** Whether the player faces along a fixed cut; the CRT's antennas light up. */
	static boolean onTrack() {
		return aligned;
	}

	/** Clinks once when the view comes to point along the fixed cut. */
	private static void updateAligned(Minecraft client) {
		if (cutYaw == null) {
			aligned = false;
			return;
		}
		float off = Math.abs(wrapDegrees(cutYaw - client.player.getYRot()));
		if (!aligned && off <= ALIGNED_DEGREES) {
			aligned = true;
			// A random note of a pentatonic scale, so it never sounds the same twice in a row.
			int note = CHIME_NOTES[RANDOM.nextInt(CHIME_NOTES.length)];
			float pitch = (float) Math.pow(2, (note - 12) / 12.0);
			// The amethyst chime alone is too quiet; the note block chime carries it.
			CrtEffects.play(client, SoundEvents.AMETHYST_BLOCK_CHIME, pitch, 0.8f);
			CrtEffects.play(client, SoundEvents.NOTE_BLOCK_CHIME.value(), pitch, 0.35f);
		} else if (aligned && off > UNALIGNED_DEGREES) {
			aligned = false;
		}
	}

	/** Compass bearing in whole degrees: 0 = north, 90 = east (Minecraft yaw 0 looks south). */
	private static int bearing(float yaw) {
		return Math.floorMod(Math.round(yaw) + 180, 360);
	}

	private static float wrapDegrees(float degrees) {
		float d = degrees % 360f;
		if (d >= 180f) {
			d -= 360f;
		}
		if (d < -180f) {
			d += 360f;
		}
		return d;
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
		if (changed) {
			config.save();
		}
		while (lockKey.consumeClick()) {
			lockPressed = !lockPressed;
		}
		while (spyglassKey.consumeClick()) {
			spyglassPressed = !spyglassPressed;
		}
		while (zoomKey.consumeClick()) {
			zoomPressed = !zoomPressed;
		}
		while (settingsKey.consumeClick()) {
			client.gui.setScreen(new VerticalCutConfigScreen(null));
		}
	}

	private static void sendScanState(ScanStatePayload state) {
		if (state.equals(sentScanState)) {
			return;
		}
		if (ClientPlayNetworking.canSend(ScanStatePayload.TYPE)) {
			ClientPlayNetworking.send(state);
			sentScanState = state;
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
		MOB_DOTS.draw(graphics, client, SLICE, sx, sy, shown, config.colorBlind);
		int figureColor = colorPicture ? config.figureColor.argb : 0xFFFFFFFF;
		int playerLeft = SLICE.playerAbove()
				? drawPlayerAbove(graphics, client, sx, sy, shown, figureColor)
				: drawPlayer(graphics, client, sx, sy, shown, figureColor);
		if (depthGauge) {
			drawRuler(graphics, client, sx, sy, shown, playerLeft);
		}
		graphics.outline(sx - 1, sy - 1, shown + 2, shown + 2, 0xFFAAAAAA);
		if (coalBar >= 0) {
			drawCoalBar(graphics, x, y, size, config.corner.right());
		}

		if (compass) {
			drawNeedle(graphics, client, sx, sy, shown);
		}

		if (lowOnCoal && blinkOn) {
			graphics.item(new ItemStack(Items.COAL), sx + shown - 18, sy + 2);
		}
	}

	/**
	 * Depth Gauge: white marks with Y values inside the picture along its
	 * left edge, counted from the feet (labelled with their own Y) in steps
	 * of 8 blocks, so the numbers change with every step up or down. The feet
	 * mark is a line reaching the stick figure; the other marks are 8 blocks
	 * away from it and never touch it. The numbers are drawn at half size.
	 * When the marks would crowd, only every 16 blocks get one.
	 * In the deep picture (spyglass) the feet are above it: their number
	 * stays at the top of the ruler with an arrow pointing up.
	 */
	private static void drawRuler(GuiGraphicsExtractor graphics, Minecraft client, int sx, int sy, int shown,
			int lineEnd) {
		double scale = (double) shown / SliceRenderer.SIZE;
		double blockPx = SLICE.pixelsPerBlock() * scale;
		// About half size, but each font pixel a whole number of screen pixels, so it stays sharp.
		float guiScale = (float) client.getWindow().getGuiScale();
		float textScale = Math.max(1, Math.round(guiScale * RULER_TEXT_SCALE)) / guiScale;
		float textHeight = client.font.lineHeight * textScale;
		int step = blockPx * RULER_STEP >= textHeight + 2 ? RULER_STEP : 2 * RULER_STEP;
		int feetY = SLICE.feetY();
		boolean deep = SLICE.playerAbove();
		double feetPx = sy + SLICE.feetPixelRow() * scale;
		double stepPx = step * blockPx;
		int first = (int) Math.ceil((sy - feetPx) / stepPx);
		int last = (int) Math.floor((sy + shown - feetPx) / stepPx);
		// Below the feet plate at the top of a deep picture.
		float freeTop = deep ? sy + textHeight + 2 : sy;

		// Short unnumbered marks halfway between the numbered ones.
		for (int k = first - 1; k <= last; k++) {
			int y = (int) Math.round(feetPx + (k + 0.5) * stepPx);
			if (y >= sy && y < sy + shown) {
				graphics.fill(sx, y, sx + RULER_MINOR_TICK, y + 1, 0xFFFFFFFF);
			}
		}

		for (int k = first; k <= last; k++) {
			int y = (int) Math.round(feetPx + k * stepPx);
			String label = Integer.toString(feetY - k * step);
			graphics.fill(sx, y, sx + RULER_TICK, y + 1, 0xFFFFFFFF);
			int labelEnd = sx + RULER_TICK + 2 + Math.round(client.font.width(label) * textScale);
			if (k == 0 && deep) {
				// Its number stands on the plate at the top.
				continue;
			}
			if (k == 0 && labelEnd < lineEnd) {
				// From behind the number on to the stick figure.
				graphics.fill(labelEnd, y, lineEnd, y + 1, 0xFFFFFFFF);
			}
			float textY = Math.clamp(y - textHeight / 2, sy + 1, sy + shown - textHeight);
			if (textY < freeTop) {
				continue;
			}
			textY = Math.round(textY * guiScale) / guiScale;
			graphics.pose().pushMatrix();
			graphics.pose().translate(sx + RULER_TICK + 1, textY);
			graphics.pose().scale(textScale, textScale);
			if (k == 0) {
				// Black plate behind the feet number, one font pixel around the digits and their shadow.
				graphics.fill(-1, -1, client.font.width(label), client.font.lineHeight - 1, 0xFF000000);
			}
			graphics.text(client.font, label, 0, 0, 0xFFFFFFFF, true);
			graphics.pose().popMatrix();
		}
		if (deep) {
			drawFeetAbove(graphics, client, sx + RULER_TICK + 1, sy + 1, textScale, guiScale, Integer.toString(feetY));
		}
	}

	/** Up arrow in font pixels, drawn after the feet number in the deep picture. */
	private static final String[] UP_ARROW = {
			"..#..",
			".###.",
			"#.#.#",
			"..#..",
			"..#..",
			"..#..",
			"..#..",
	};

	/** The feet number on its black plate with an arrow up: the feet lie above the picture. */
	private static void drawFeetAbove(GuiGraphicsExtractor graphics, Minecraft client, float x, float y,
			float textScale, float guiScale, String label) {
		int width = client.font.width(label);
		int arrowLeft = width + 1;
		graphics.pose().pushMatrix();
		graphics.pose().translate(Math.round(x * guiScale) / guiScale, Math.round(y * guiScale) / guiScale);
		graphics.pose().scale(textScale, textScale);
		graphics.fill(-1, -1, arrowLeft + UP_ARROW[0].length() + 1, client.font.lineHeight - 1, 0xFF000000);
		graphics.text(client.font, label, 0, 0, 0xFFFFFFFF, true);
		for (int row = 0; row < UP_ARROW.length; row++) {
			for (int col = 0; col < UP_ARROW[row].length(); col++) {
				if (UP_ARROW[row].charAt(col) == '#') {
					graphics.fill(arrowLeft + col, row, arrowLeft + col + 1, row + 1, 0xFFFFFFFF);
				}
			}
		}
		graphics.pose().popMatrix();
	}

	/**
	 * Compass needle at the top of the picture: where the cut points compared
	 * to the view (straight up = ahead). Its tip is red in color pictures.
	 * While the view is aligned with a fixed cut it is bright green and twice
	 * as thick, so it also stands out for red-green color blind players.
	 * While locked, the locked bearing stands beside it.
	 */
	private static void drawNeedle(GuiGraphicsExtractor graphics, Minecraft client, int sx, int sy, int shown) {
		double angle;
		if (needleSpins) {
			angle = (System.currentTimeMillis() % 1000) / 1000.0 * 2 * Math.PI;
		} else if (cutYaw != null) {
			angle = Math.toRadians(cutYaw - client.player.getYRot());
		} else {
			angle = 0;
		}
		int px = sx + shown / 2;
		int py = sy + NEEDLE_LENGTH + 2;
		int body = aligned ? VerticalCutConfig.get().colorBlind ? 0xFF60C0FF : 0xFFA0FF60 : 0xFFFFFFFF;
		int tip = aligned ? body : colorPicture ? 0xFFFF4040 : 0xFFFFFFFF;
		for (int pass = 0; pass < 2; pass++) {
			for (int i = 0; i <= NEEDLE_LENGTH; i++) {
				int x = px + (int) Math.round(Math.sin(angle) * i);
				int y = py - (int) Math.round(Math.cos(angle) * i);
				if (pass == 0) {
					graphics.fill(x - 1, y - 1, x + (aligned ? 3 : 2), y + 2, 0xFF000000);
				} else {
					graphics.fill(x, y, x + (aligned ? 2 : 1), y + 1, i >= NEEDLE_LENGTH - 1 ? tip : body);
				}
			}
		}
		if (lockedYaw != null) {
			String label = Component.translatable("verticalcut.lock.bearing", bearing(lockedYaw)).getString();
			drawSmallText(graphics, client, label, px + NEEDLE_LENGTH + 3, py - NEEDLE_LENGTH);
		}
	}

	/** Half-size text on a black plate, snapped to whole screen pixels. */
	private static void drawSmallText(GuiGraphicsExtractor graphics, Minecraft client, String text, float x, float y) {
		float guiScale = (float) client.getWindow().getGuiScale();
		float textScale = Math.max(1, Math.round(guiScale * RULER_TEXT_SCALE)) / guiScale;
		graphics.pose().pushMatrix();
		graphics.pose().translate(Math.round(x * guiScale) / guiScale, Math.round(y * guiScale) / guiScale);
		graphics.pose().scale(textScale, textScale);
		graphics.fill(-1, -1, client.font.width(text), client.font.lineHeight - 1, 0xFF000000);
		graphics.text(client.font, text, 0, 0, 0xFFFFFFFF, true);
		graphics.pose().popMatrix();
	}

	/** The player in the picture (# = figure), drawn with a black outline. */
	static final String[] FIGURE = {
			".###.",
			".###.",
			".###.",
			"..#..",
			"#####",
			"..#..",
			"..#..",
			".#.#.",
			"#...#",
	};
	/** Below this many screen pixels per figure pixel the player is only a line. */
	private static final double MIN_FIGURE_PIXEL = 0.75;

	/**
	 * The player standing on the feet block: a stick figure about two blocks
	 * tall, each figure pixel a whole number of screen pixels so it stays
	 * sharp. When two blocks are too few pixels for a figure (zoomed far
	 * out), a line two blocks tall stands for the player. Both have a black
	 * outline of one figure (or screen) pixel.
	 *
	 * @return the GUI x of the left edge of the outline, where the ruler's feet line ends
	 */
	private static int drawPlayer(GuiGraphicsExtractor graphics, Minecraft client, int sx, int sy, int shown,
			int argb) {
		float guiScale = (float) client.getWindow().getGuiScale();
		double scale = (double) shown / SliceRenderer.SIZE * guiScale;
		int ppb = SLICE.pixelsPerBlock();
		// In screen pixels: the middle of the player's column and the bottom of the feet block.
		double center = sx * guiScale + (SLICE.feetPixelColumn() + ppb / 2.0) * scale;
		int bottom = (int) Math.round(sy * guiScale + (SLICE.feetPixelRow() - ppb / 2 + ppb) * scale);
		int left = drawFigure(graphics, guiScale, center, bottom, 2 * ppb * scale, argb);
		return (int) Math.floor(left / guiScale);
	}

	/**
	 * A stick figure (or a line when too small) in screen pixels, standing on
	 * {@code bottom}, about {@code twoBlocks} tall.
	 *
	 * @return the screen x of the left edge of its outline
	 */
	static int drawFigure(GuiGraphicsExtractor graphics, float guiScale, double center, int bottom, double twoBlocks,
			int argb) {
		double pixel = twoBlocks / FIGURE.length;

		graphics.pose().pushMatrix();
		graphics.pose().scale(1 / guiScale, 1 / guiScale);
		int left;
		if (pixel < MIN_FIGURE_PIXEL) {
			int x = (int) Math.floor(center);
			int top = bottom - Math.max(2, (int) Math.round(twoBlocks));
			graphics.fill(x - 1, top - 1, x + 2, bottom + 1, 0xFF000000);
			graphics.fill(x, top, x + 1, bottom, argb);
			left = x - 1;
		} else {
			int n = Math.max(1, (int) Math.round(pixel));
			int width = FIGURE[0].length() * n;
			int figureLeft = (int) Math.round(center - width / 2.0);
			int top = bottom - FIGURE.length * n;
			for (int pass = 0; pass < 2; pass++) {
				for (int row = 0; row < FIGURE.length; row++) {
					for (int col = 0; col < FIGURE[row].length(); col++) {
						if (FIGURE[row].charAt(col) != '#') {
							continue;
						}
						int px = figureLeft + col * n;
						int py = top + row * n;
						if (pass == 0) {
							graphics.fill(px - n, py - n, px + 2 * n, py + 2 * n, 0xFF000000);
						} else {
							graphics.fill(px, py, px + n, py + n, argb);
						}
					}
				}
			}
			left = figureLeft - n;
		}
		graphics.pose().popMatrix();
		return left;
	}

	/**
	 * In the deep picture the player stands above it: a dotted line in the
	 * figure color runs the full height at the player's column. Dots and gaps
	 * are whole screen pixels, so it stays sharp.
	 *
	 * @return the GUI x of the line's left edge
	 */
	private static int drawPlayerAbove(GuiGraphicsExtractor graphics, Minecraft client, int sx, int sy, int shown,
			int argb) {
		float guiScale = (float) client.getWindow().getGuiScale();
		double scale = (double) shown / SliceRenderer.SIZE * guiScale;
		int ppb = SLICE.pixelsPerBlock();
		int x = (int) Math.floor(sx * guiScale + (SLICE.feetPixelColumn() + ppb / 2.0) * scale);
		int dot = Math.max(1, Math.round(guiScale / 2));
		int top = Math.round(sy * guiScale);
		int bottom = Math.round((sy + shown) * guiScale);
		graphics.pose().pushMatrix();
		graphics.pose().scale(1 / guiScale, 1 / guiScale);
		for (int y = top; y < bottom; y += 2 * dot) {
			graphics.fill(x, y, x + dot, Math.min(y + dot, bottom), argb);
		}
		graphics.pose().popMatrix();
		return (int) Math.floor(x / guiScale);
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
