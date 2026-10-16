package de.knito.verticalcut.client;

import java.util.Random;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import de.knito.verticalcut.VerticalCut;

/**
 * Computes the vertical slice in front of the player into a small texture.
 *
 * The old mod looked up every block on every frame. Here the slice is only
 * recomputed when the player moves to another block, turns noticeably, or a
 * refresh interval passes (to pick up dug or placed blocks). Drawing the
 * finished texture each frame is a single textured quad on the GPU.
 *
 * The computed picture is kept in {@link #base}; {@link #present} copies it
 * into the texture and applies the analog TV effects of a dying CRT.
 */
final class SliceRenderer {
	/** Texture size in pixels; the slice is always drawn into this many pixels. */
	static final int SIZE = 128;

	/** How many blocks of the slice lie behind the player (left edge), at 1 pixel per block. */
	private static final int BEHIND = 8;
	/** Fraction of the height that lies above the player's feet. */
	private static final double ABOVE_FRACTION = 0.25;
	/** Recompute at least this often (in ticks) to show changed blocks. */
	private static final int REFRESH_TICKS = 20;
	/** Recompute when the view direction changed by more than this (degrees). */
	private static final float TURN_THRESHOLD = 2.0f;

	private static final int MARKER_COLOR = 0xFFFFFFFF;

	final Identifier textureId = VerticalCut.id("slice");

	private final int[] base = new int[SIZE * SIZE];
	private final Random random = new Random();

	private DynamicTexture texture;

	private BlockPos lastPos;
	private float lastYaw = Float.NaN;
	private int lastPixelsPerBlock;
	private boolean lastColor;
	private int ticksSinceUpdate;
	private boolean lastNoisy;

	boolean isReady() {
		return texture != null;
	}

	/**
	 * @param pixelsPerBlock 1, 2 or 4
	 * @param color false draws black and white with ores as bright dots
	 * @param decay 0 = perfect picture, 1 = almost dead (grey, noisy, jittery)
	 */
	void tick(Minecraft client, int pixelsPerBlock, boolean color, float decay) {
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
		float yaw = player.getYRot();
		ticksSinceUpdate++;

		boolean moved = !pos.equals(lastPos);
		boolean turned = Float.isNaN(lastYaw) || Math.abs(wrapDegrees(yaw - lastYaw)) > TURN_THRESHOLD;
		boolean modeChanged = pixelsPerBlock != lastPixelsPerBlock || color != lastColor;
		boolean recompute = moved || turned || modeChanged || ticksSinceUpdate >= REFRESH_TICKS;

		if (recompute) {
			lastPos = pos;
			lastYaw = yaw;
			lastPixelsPerBlock = pixelsPerBlock;
			lastColor = color;
			ticksSinceUpdate = 0;
			compute(level, player, yaw, pixelsPerBlock, color);
		}

		// A decaying picture changes every tick (noise), a healthy one only when recomputed.
		boolean noisy = decay > 0;
		if (recompute || noisy || lastNoisy) {
			present(decay);
		}
		lastNoisy = noisy;
	}

	private void compute(ClientLevel level, LocalPlayer player, float yaw, int pixelsPerBlock, boolean color) {
		int blocks = SIZE / pixelsPerBlock;
		int behind = Math.max(1, BEHIND / pixelsPerBlock);
		int above = (int) (blocks * ABOVE_FRACTION);

		// Minecraft yaw: 0 = south (+Z), 90 = west (-X).
		double rad = Math.toRadians(yaw);
		double dirX = -Math.sin(rad);
		double dirZ = Math.cos(rad);

		double startX = player.getX();
		double startZ = player.getZ();
		int feetY = player.getBlockY();
		int minY = level.getMinY();
		int maxY = level.getMaxY();

		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		for (int col = 0; col < blocks; col++) {
			double dist = col - behind + 0.5;
			int x = (int) Math.floor(startX + dirX * dist);
			int z = (int) Math.floor(startZ + dirZ * dist);

			for (int row = 0; row < blocks; row++) {
				int y = feetY + above - row;
				int argb;
				if (y < minY || y > maxY) {
					argb = SliceColors.AIR;
				} else {
					cursor.set(x, y, z);
					argb = color
							? SliceColors.colorOf(level, cursor, level.getBlockState(cursor))
							: SliceColors.monochromeOf(level, cursor, level.getBlockState(cursor));
				}
				fill(col, row, pixelsPerBlock, argb);
			}
		}

		// Mark the player's feet and head.
		fill(behind, above, pixelsPerBlock, MARKER_COLOR);
		fill(behind, above - 1, pixelsPerBlock, MARKER_COLOR);
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

		for (int y = 0; y < SIZE; y++) {
			// Lost horizontal sync: some rows slide sideways.
			int shift = 0;
			if (decay > 0 && random.nextFloat() < decay * 0.15f) {
				shift = random.nextInt(9) - 4;
			}
			for (int x = 0; x < SIZE; x++) {
				int sx = Math.floorMod(x + shift, SIZE);
				int argb = base[y * SIZE + sx];
				if (decay > 0) {
					argb = decayPixel(argb, decay);
				}
				image.setPixel(x, y, argb);
			}
		}
		texture.upload();
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
