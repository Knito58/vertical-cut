package de.knito.verticalcut.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Netherite CRT: "the heartbeat of what moves in the dark". Living things
 * near the cut show as dots in the picture: hostile ones red with a double
 * heartbeat pulse, neutral ones green (blue for color blind players), peaceful
 * ones yellow. A neutral mob that attacks (it turns aggressive) becomes red.
 * Other players are white stick figures. The client only knows entities
 * within its entity range, so far away the picture may stay empty.
 */
final class MobDots {
	/** Blocks to each side of the cut in which mobs show. */
	private static final double NEAR_CUT = 4.5;
	/** How far to look for entities; covers the widest picture. */
	private static final double RANGE = 130;
	private static final int HOSTILE = 0xFFFF0000;
	private static final int HOSTILE_BEAT_OFF = 0xFF800000;
	private static final int NEUTRAL = 0xFF00E000;
	private static final int NEUTRAL_COLOR_BLIND = 0xFF3080FF;
	private static final int PEACEFUL = 0xFFFFFF00;
	private static final int PLAYER = 0xFFFFFFFF;

	private enum Kind { HOSTILE, NEUTRAL, PEACEFUL, PLAYER }

	/** Along and across the cut in blocks (from the player), feet height, size. */
	private record Dot(double along, double y, double width, double height, Kind kind) {
	}

	private List<Dot> dots = List.of();

	/** Finds the living things near the cut; nothing unless the CRT sees mobs. */
	void tick(Minecraft client, boolean active, float yaw) {
		LocalPlayer player = client.player;
		if (!active || player == null || client.level == null) {
			dots = List.of();
			return;
		}
		double rad = Math.toRadians(yaw);
		double dirX = -Math.sin(rad);
		double dirZ = Math.cos(rad);
		List<Dot> found = new ArrayList<>();
		AABB box = player.getBoundingBox().inflate(RANGE);
		for (LivingEntity entity : client.level.getEntitiesOfClass(LivingEntity.class, box)) {
			if (entity == player || !entity.isAlive() || entity.isInvisible() || entity.isSpectator()) {
				continue;
			}
			double dx = entity.getX() - player.getX();
			double dz = entity.getZ() - player.getZ();
			double across = -dx * dirZ + dz * dirX;
			if (Math.abs(across) > NEAR_CUT) {
				continue;
			}
			double along = dx * dirX + dz * dirZ;
			found.add(new Dot(along, entity.getY(), entity.getBbWidth(), entity.getBbHeight(), kindOf(entity)));
		}
		dots = found;
	}

	private static Kind kindOf(LivingEntity entity) {
		if (entity instanceof Player) {
			return Kind.PLAYER;
		}
		// Attacking right now, whatever it is (an iron golem after a crime, an angry wolf, ...).
		if (entity instanceof Mob mob && mob.isAggressive()) {
			return Kind.HOSTILE;
		}
		if (entity instanceof NeutralMob) {
			return Kind.NEUTRAL;
		}
		if (entity instanceof Enemy) {
			return Kind.HOSTILE;
		}
		return Kind.PEACEFUL;
	}

	/** Draws the dots over the picture, clipped to it. */
	void draw(GuiGraphicsExtractor graphics, Minecraft client, SliceRenderer slice, int sx, int sy, int shown,
			boolean colorBlind) {
		if (dots.isEmpty()) {
			return;
		}
		float guiScale = (float) client.getWindow().getGuiScale();
		// Screen pixels per texture pixel.
		double scale = (double) shown / SliceRenderer.SIZE * guiScale;
		int ppb = slice.pixelsPerBlock();
		double feetCenterX = slice.feetPixelColumn() + ppb / 2.0;
		double feetCenterRow = slice.feetPixelRow();
		double feetCenterY = slice.feetY() + 0.5;
		int left = Math.round(sx * guiScale);
		int top = Math.round(sy * guiScale);
		int right = Math.round((sx + shown) * guiScale);
		int bottomEdge = Math.round((sy + shown) * guiScale);
		// Ba-dum ... ba-dum: two short beats, then a pause.
		long t = System.currentTimeMillis() % 1000;
		boolean beat = t < 120 || t >= 240 && t < 360;

		graphics.pose().pushMatrix();
		graphics.pose().scale(1 / guiScale, 1 / guiScale);
		for (Dot dot : dots) {
			double centerX = left + (feetCenterX + dot.along * ppb) * scale;
			int bottom = (int) Math.round(top + (feetCenterRow + (feetCenterY - dot.y) * ppb) * scale);
			if (dot.kind == Kind.PLAYER) {
				if (centerX >= left && centerX < right && bottom > top && bottom <= bottomEdge) {
					VerticalCutClient.drawFigure(graphics, guiScale, centerX, bottom, 2 * ppb * scale, PLAYER);
				}
				continue;
			}
			int w = Math.max(2, (int) Math.round(dot.width * ppb * scale));
			int h = Math.max(2, (int) Math.round(dot.height * ppb * scale));
			int x0 = Math.max(left, (int) Math.round(centerX - w / 2.0));
			int x1 = Math.min(right, (int) Math.round(centerX - w / 2.0) + w);
			int y0 = Math.max(top, bottom - h);
			int y1 = Math.min(bottomEdge, bottom);
			if (x0 >= x1 || y0 >= y1) {
				continue;
			}
			int color = switch (dot.kind) {
				case HOSTILE -> beat ? HOSTILE : HOSTILE_BEAT_OFF;
				case NEUTRAL -> colorBlind ? NEUTRAL_COLOR_BLIND : NEUTRAL;
				default -> PEACEFUL;
			};
			graphics.fill(Math.max(left, x0 - 1), Math.max(top, y0 - 1), Math.min(right, x1 + 1),
					Math.min(bottomEdge, y1 + 1), 0xFF000000);
			graphics.fill(x0, y0, x1, y1, color);
		}
		graphics.pose().popMatrix();
	}
}
