package de.knito.verticalcut.client;

import java.util.Random;
import java.util.Set;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import de.knito.verticalcut.VerticalCut;

/**
 * Computes the vertical slice in front of the player into a small texture.
 *
 * The old mod looked up every block on every frame. Here the slice is only
 * recomputed when the player moves to another block, turns noticeably, or a
 * refresh interval passes (to pick up dug or placed blocks). A crude CRT only
 * refreshes on a slow timer, so its picture lags behind. Drawing the
 * finished texture each frame is a single textured quad on the GPU.
 *
 * The computed picture is kept in {@link #base}; {@link #present} copies it
 * into the texture and applies the analog TV effects of a dying CRT.
 */
final class SliceRenderer {
	/** Texture size in pixels; the slice is always drawn into this many pixels. */
	static final int SIZE = 128;

	/** How many blocks of the slice lie behind the player (left edge), at 1 pixel per block. */
	private static final int BEHIND = 16;
	/** Fraction of the height that lies above the player's feet. */
	private static final double ABOVE_FRACTION = 0.25;
	/** Recompute at least this often (in ticks) to show changed blocks. */
	private static final int REFRESH_TICKS = 20;
	/** A laggy (crude) CRT only recomputes this often and ignores movement. */
	private static final int LAG_REFRESH_TICKS = 30;
	/** Half period of the flashing ores the finder bundle looks for. */
	private static final long FINDER_BLINK_MILLIS = 250;
	/** Half period of blinking lava in black and white pictures. */
	private static final long LAVA_BLINK_MILLIS = 400;
	/** Recompute when the view direction changed by more than this (degrees). */
	private static final float TURN_THRESHOLD = 2.0f;

	final Identifier textureId = VerticalCut.id("slice");

	private final int[] base = new int[SIZE * SIZE];
	private final Random random = new Random();

	private DynamicTexture texture;

	private BlockPos lastPos;
	private float lastYaw = Float.NaN;
	private int lastPixelsPerBlock;
	private SliceMode lastMode;
	private boolean lastDeep;
	private Set<Block> lastFinder = Set.of();
	/** Rows of blocks from the top of the shown picture down to the feet; negative = feet above it. */
	private int lastTop;
	private int ticksSinceUpdate;
	private boolean lastNoisy;
	private boolean hasLava;
	private boolean hasPortal;
	private boolean lavaBright;
	private boolean hasFinder;
	private boolean finderBright;

	boolean isReady() {
		return texture != null;
	}

	/** Y of the player's feet in the shown picture (a laggy picture shows an older one). */
	int feetY() {
		return lastPos == null ? 0 : lastPos.getY();
	}

	/** Texture pixel row at the middle of the feet block; negative when the feet are above the picture. */
	int feetPixelRow() {
		return lastTop * lastPixelsPerBlock + lastPixelsPerBlock / 2;
	}

	/** Texture pixel column where the player's block starts. */
	int feetPixelColumn() {
		return Math.max(1, BEHIND / lastPixelsPerBlock) * lastPixelsPerBlock;
	}

	/** The player (two blocks tall) doesn't fit into the picture: it shows what lies below (spyglass). */
	boolean playerAbove() {
		return feetPixelRow() < 2 * lastPixelsPerBlock;
	}

	/** Pixels per block of the shown picture. */
	int pixelsPerBlock() {
		return lastPixelsPerBlock;
	}

	/**
	 * @param pixelsPerBlock 1, 2 or 4
	 * @param mode coloring, see {@link SliceMode}
	 * @param decay 0 = perfect picture, 1 = almost dead (grey, noisy, jittery)
	 * @param laggy only refresh every {@link #LAG_REFRESH_TICKS}, even while moving
	 * @param yaw direction of the cut (degrees, Minecraft yaw), usually the view's
	 * @param deep show the picture one picture height lower (spyglass)
	 * @param finder blocks that flash (finder bundle); empty for none
	 */
	void tick(Minecraft client, int pixelsPerBlock, SliceMode mode, float decay, boolean laggy, float yaw,
			boolean deep, Set<Block> finder) {
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		if (player == null || level == null) {
			return;
		}

		if (texture == null) {
			texture = new DynamicTexture(() -> "Vertical Cut slice", SIZE, SIZE, true);
			client.getTextureManager().register(textureId, texture);
		}

		BlockPos pos = player.blockPosition();
		ticksSinceUpdate++;

		boolean moved = !pos.equals(lastPos);
		boolean turned = Float.isNaN(lastYaw) || Math.abs(wrapDegrees(yaw - lastYaw)) > TURN_THRESHOLD;
		boolean modeChanged = pixelsPerBlock != lastPixelsPerBlock || mode != lastMode || deep != lastDeep
				|| !finder.equals(lastFinder);
		boolean recompute = laggy
				? modeChanged || ticksSinceUpdate >= LAG_REFRESH_TICKS
				: moved || turned || modeChanged || ticksSinceUpdate >= REFRESH_TICKS;

		if (recompute) {
			lastPos = pos;
			lastYaw = yaw;
			lastPixelsPerBlock = pixelsPerBlock;
			lastMode = mode;
			lastDeep = deep;
			lastFinder = finder;
			ticksSinceUpdate = 0;
			compute(level, player, yaw, pixelsPerBlock, mode, deep, finder);
		}

		boolean bright = (System.currentTimeMillis() / LAVA_BLINK_MILLIS) % 2 == 0;
		// Portal blocks each blink in their own rhythm, so redraw every tick while one is seen.
		boolean flash = (System.currentTimeMillis() / FINDER_BLINK_MILLIS) % 2 == 0;
		boolean blink = hasLava && bright != lavaBright || hasPortal || hasFinder && flash != finderBright;
		lavaBright = bright;
		finderBright = flash;

		// A decaying picture changes every tick (noise), a healthy one only when recomputed.
		boolean noisy = decay > 0;
		if (recompute || noisy || lastNoisy || blink) {
			present(decay);
		}
		lastNoisy = noisy;
	}

	private void compute(ClientLevel level, LocalPlayer player, float yaw, int pixelsPerBlock, SliceMode mode,
			boolean deep, Set<Block> finder) {
		hasLava = false;
		hasPortal = false;
		hasFinder = false;
		int blocks = SIZE / pixelsPerBlock;
		int behind = Math.max(1, BEHIND / pixelsPerBlock);
		int above = (int) (blocks * ABOVE_FRACTION);
		int feetY = player.getBlockY();
		int minY = level.getMinY();
		int maxY = level.getMaxY();
		// The deep picture goes on where the normal one ends, but stops at the bottom of the world.
		int top = deep ? Math.min(above, Math.max(above - blocks, minY - feetY + blocks - 1)) : above;
		lastTop = top;

		// Minecraft yaw: 0 = south (+Z), 90 = west (-X).
		double rad = Math.toRadians(yaw);
		double dirX = -Math.sin(rad);
		double dirZ = Math.cos(rad);

		double startX = player.getX();
		double startZ = player.getZ();

		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		for (int col = 0; col < blocks; col++) {
			double dist = col - behind + 0.5;
			int x = (int) Math.floor(startX + dirX * dist);
			int z = (int) Math.floor(startZ + dirZ * dist);

			for (int row = 0; row < blocks; row++) {
				int y = feetY + top - row;
				int argb;
				if (y < minY || y > maxY) {
					argb = SliceColors.AIR;
				} else {
					cursor.set(x, y, z);
					BlockState state = level.getBlockState(cursor);
					argb = mode == SliceMode.COLOR
							? SliceColors.colorOf(level, cursor, state)
							: SliceColors.monochromeOf(level, cursor, state, mode == SliceMode.MONOCHROME);
					if (finder.contains(state.getBlock())) {
						argb = SliceColors.FINDER_BLINK;
						hasFinder = true;
					}
					hasLava |= argb == SliceColors.LAVA_BLINK;
					hasPortal |= isPortal(argb);
				}
				fill(col, row, pixelsPerBlock, argb);
			}
		}
	}

	/** Writes one block as a square of pixels into the base picture. */
	private void fill(int col, int row, int pixelsPerBlock, int argb) {
		int px = col * pixelsPerBlock;
		int py = row * pixelsPerBlock;
		for (int dy = 0; dy < pixelsPerBlock; dy++) {
			for (int dx = 0; dx < pixelsPerBlock; dx++) {
				base[(py + dy) * SIZE + px + dx] = argb;
			}
		}
	}

	/** Copies the base picture into the texture, adding TV decay effects. */
	private void present(float decay) {
		NativeImage image = texture.getPixels();
		if (image == null) {
			return;
		}
		long now = System.currentTimeMillis();

		for (int y = 0; y < SIZE; y++) {
			// Lost horizontal sync: some rows slide sideways.
			int shift = 0;
			if (decay > 0 && random.nextFloat() < decay * 0.15f) {
				shift = random.nextInt(9) - 4;
			}
			for (int x = 0; x < SIZE; x++) {
				int sx = Math.floorMod(x + shift, SIZE);
				int argb = base[y * SIZE + sx];
				if (argb == SliceColors.LAVA_BLINK) {
					argb = lavaBright ? SliceColors.LAVA_BRIGHT : SliceColors.LAVA_DARK;
				} else if (argb == SliceColors.FINDER_BLINK) {
					// White and black: stands out by brightness, also for color blind players.
					argb = finderBright ? 0xFFFFFFFF : 0xFF000000;
				} else if (isPortal(argb)) {
					argb = portalPixel(argb, now);
				}
				if (decay > 0) {
					argb = decayPixel(argb, decay);
				}
				image.setPixel(x, y, argb);
			}
		}
		texture.upload();
	}

	private static boolean isPortal(int argb) {
		int mark = argb >>> 24;
		return mark >= SliceColors.PORTAL_MARK >>> 24 && mark <= SliceColors.END_PORTAL_MARK >>> 24;
	}

	/**
	 * A lit portal block: switches between a bright and a dark color every
	 * 150 to 650 ms, the speed and phase picked once per block from its random
	 * value. Black and white pictures blink white, color pictures purple
	 * (nether) or teal (end).
	 */
	private static int portalPixel(int marked, long now) {
		int seed = marked & 0xFFFFFF;
		long halfPeriod = 150 + seed % 500;
		long phase = (seed >> 9) % 1000;
		boolean on = ((now + phase) / halfPeriod) % 2 == 0;
		int mark = marked & 0xFF000000;
		if (mark == SliceColors.NETHER_PORTAL_MARK) {
			return on ? 0xFFB040FF : 0xFF300050;
		}
		if (mark == SliceColors.END_PORTAL_MARK) {
			return on ? 0xFF40E0C0 : 0xFF002018;
		}
		return on ? 0xFFFFFFFF : 0xFF000000;
	}

	/** Drains color and mixes in snow; at full decay the picture is mostly noise. */
	private int decayPixel(int argb, float decay) {
		int a = argb >>> 24;
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;
		int grey = (r * 30 + g * 59 + b * 11) / 100;
		r += (int) ((grey - r) * decay);
		g += (int) ((grey - g) * decay);
		b += (int) ((grey - b) * decay);

		if (random.nextFloat() < decay * 0.6f) {
			int snow = random.nextInt(256);
			float mix = decay * 0.8f;
			r += (int) ((snow - r) * mix);
			g += (int) ((snow - g) * mix);
			b += (int) ((snow - b) * mix);
			a = Math.max(a, (int) (200 * decay));
		}
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static float wrapDegrees(float degrees) {
		float d = degrees % 360.0f;
		if (d >= 180.0f) {
			d -= 360.0f;
		}
		if (d < -180.0f) {
			d += 360.0f;
		}
		return d;
	}
}
