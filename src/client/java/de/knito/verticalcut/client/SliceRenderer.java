package de.knito.verticalcut.client;

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
 */
final class SliceRenderer {
	/** Texture size in pixels; the slice is always drawn into this many pixels. */
	static final int SIZE = 128;

	/** How many blocks of the slice lie behind the player (left edge). */
	private static final int BEHIND = 8;
	/** Fraction of the height that lies above the player's feet. */
	private static final double ABOVE_FRACTION = 0.25;
	/** Recompute at least this often (in ticks) to show changed blocks. */
	private static final int REFRESH_TICKS = 20;
	/** Recompute when the view direction changed by more than this (degrees). */
	private static final float TURN_THRESHOLD = 2.0f;

	private static final int MARKER_COLOR = 0xFFFFFFFF;

	final Identifier textureId = VerticalCut.id("slice");

	private DynamicTexture texture;
	private int zoom = 1;

	private BlockPos lastPos;
	private float lastYaw = Float.NaN;
	private int lastZoom;
	private int ticksSinceUpdate;

	int zoom() {
		return zoom;
	}

	/** Cycles zoom between 1 (one pixel per block) and 2 (two pixels per block). */
	void cycleZoom() {
		zoom = zoom == 1 ? 2 : 1;
	}

	boolean isReady() {
		return texture != null;
	}

	void tick(Minecraft client) {
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
		if (!moved && !turned && zoom == lastZoom && ticksSinceUpdate < REFRESH_TICKS) {
			return;
		}

		lastPos = pos;
		lastYaw = yaw;
		lastZoom = zoom;
		ticksSinceUpdate = 0;

		compute(level, player, yaw);
		texture.upload();
	}

	private void compute(ClientLevel level, LocalPlayer player, float yaw) {
		NativeImage image = texture.getPixels();
		if (image == null) {
			return;
		}

		int blocks = SIZE / zoom;
		int behind = BEHIND / zoom;
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
				int color;
				if (y < minY || y > maxY) {
					color = SliceColors.AIR;
				} else {
					cursor.set(x, y, z);
					color = SliceColors.colorOf(level, cursor, level.getBlockState(cursor));
				}
				fill(image, col, row, color);
			}
		}

		// Mark the player's feet and head.
		fill(image, behind, above, MARKER_COLOR);
		fill(image, behind, above - 1, MARKER_COLOR);
	}

	/** Writes one block as a zoom x zoom square of pixels. */
	private void fill(NativeImage image, int col, int row, int color) {
		int px = col * zoom;
		int py = row * zoom;
		for (int dy = 0; dy < zoom; dy++) {
			for (int dx = 0; dx < zoom; dx++) {
				image.setPixel(px + dx, py + dy, color);
			}
		}
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
