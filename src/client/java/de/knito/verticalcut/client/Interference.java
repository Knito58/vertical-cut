package de.knito.verticalcut.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What disturbs the CRT's picture: lit portals (the animated portal blocks,
 * not their obsidian or end stone frames) and endermen. Only the strongest
 * source counts. Looked up once a second on the client; the server isn't
 * involved.
 */
final class Interference {
	private static final int PORTAL_RANGE = 6;
	private static final double ENDERMAN_RANGE = 8;
	private static final int SCAN_INTERVAL = 20;

	private int ticksToScan;
	private float strength;

	/** Interference strength 0..1 near the player, before any shielding. */
	float tick(Minecraft client) {
		if (--ticksToScan <= 0) {
			ticksToScan = SCAN_INTERVAL;
			strength = Math.max(portals(client), endermen(client));
		}
		return strength;
	}

	void reset() {
		ticksToScan = 0;
		strength = 0;
	}

	private static float portals(Minecraft client) {
		BlockPos center = client.player.blockPosition();
		Vec3 eyes = client.player.getEyePosition();
		double nearest = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-PORTAL_RANGE, -PORTAL_RANGE, -PORTAL_RANGE),
				center.offset(PORTAL_RANGE, PORTAL_RANGE, PORTAL_RANGE))) {
			BlockState state = client.level.getBlockState(pos);
			if (state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY)) {
				nearest = Math.min(nearest, eyes.distanceTo(Vec3.atCenterOf(pos)));
			}
		}
		return closeness(nearest, PORTAL_RANGE);
	}

	/** The nearest enderman counts; an angry one twice as much. */
	private static float endermen(Minecraft client) {
		Vec3 eyes = client.player.getEyePosition();
		Enderman nearest = null;
		double distance = Double.MAX_VALUE;
		for (Enderman enderman : client.level.getEntitiesOfClass(Enderman.class,
				new AABB(eyes, eyes).inflate(ENDERMAN_RANGE))) {
			double d = eyes.distanceTo(enderman.getEyePosition());
			if (d < distance) {
				distance = d;
				nearest = enderman;
			}
		}
		if (nearest == null) {
			return 0;
		}
		return closeness(distance, ENDERMAN_RANGE) * (nearest.isCreepy() ? 1f : 0.5f);
	}

	/** 1 right at the source, falling to 0 at the edge of the range. */
	private static float closeness(double distance, double range) {
		return (float) Math.clamp(1 - distance / range, 0, 1);
	}
}
