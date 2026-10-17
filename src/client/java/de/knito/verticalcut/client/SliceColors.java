package de.knito.verticalcut.client;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * Picks the pixel color for one block in the slice. Interesting blocks
 * (ores, spawners, chests) get fixed bright colors, because their map color
 * is just plain stone. Everything else uses the vanilla map color.
 * Colors are ARGB.
 */
final class SliceColors {
	static final int AIR = 0x00000000;
	static final int CAVE_AIR = 0x30000000;
	/**
	 * Placeholder for lava in black and white pictures. Never shown as is:
	 * {@link SliceRenderer} replaces it with a blinking bright or dark grey.
	 */
	static final int LAVA_BLINK = 0x01FF4400;
	static final int LAVA_BRIGHT = 0xFFFFFFFF;
	static final int LAVA_DARK = 0xFF505050;
	/**
	 * Marker (alpha 2) for lit portal blocks in black and white pictures; the
	 * low 24 bits are a per-block random value. {@link SliceRenderer} lets
	 * each such block blink black and white in its own rhythm.
	 */
	static final int PORTAL_MARK = 0x02000000;
	/** The same for color pictures: nether portals blink purple, end portals teal. */
	static final int NETHER_PORTAL_MARK = 0x03000000;
	static final int END_PORTAL_MARK = 0x04000000;
	/**
	 * Marker (alpha 5) for ores the finder bundle looks for; the low 24 bits
	 * are the block's own color. {@link SliceRenderer} lets them all flash
	 * together: white, own color, black, own color.
	 */
	static final int FINDER_MARK = 0x05000000;
	/** Water in black and white pictures: a mid grey, between stone and the bright ores. */
	static final int WATER = 0xFF8C8C8C;

	private static final Map<Block, Integer> HIGHLIGHTS = new IdentityHashMap<>();

	static {
		put(0xFF000000, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);
		put(0xFFD8AF93, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.RAW_IRON_BLOCK);
		put(0xFFE77C56, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, Blocks.RAW_COPPER_BLOCK);
		put(0xFFFCEE4B, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE, Blocks.RAW_GOLD_BLOCK);
		put(0xFFFF0000, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE);
		put(0xFF1E4FD8, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE);
		put(0xFF00FFFF, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);
		put(0xFF00D93A, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE);
		put(0xFFEDE6DC, Blocks.NETHER_QUARTZ_ORE);
		put(0xFF8B4A33, Blocks.ANCIENT_DEBRIS);
		put(0xFFFF00FF, Blocks.SPAWNER, Blocks.TRIAL_SPAWNER);
		put(0xFFFF8800, Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL);
		put(0xFFFFFF80, Blocks.TORCH, Blocks.WALL_TORCH);
	}

	private SliceColors() {
	}

	private static void put(int color, Block... blocks) {
		for (Block block : blocks) {
			HIGHLIGHTS.put(block, color);
		}
	}

	/**
	 * Black and white picture: highlighted blocks are bright dots (only when
	 * {@code showOres}), water is mid grey, lava blinks, everything else is a
	 * dim grey, so the player has to read it.
	 */
	static int monochromeOf(Level level, BlockPos pos, BlockState state, boolean showOres) {
		if (state.is(Blocks.LAVA)) {
			return LAVA_BLINK;
		}
		if (state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY)) {
			return PORTAL_MARK | portalSeed(pos);
		}
		// Water, and anything waterlogged or growing under water.
		if (!state.getFluidState().isEmpty()) {
			return WATER;
		}
		if (showOres && !state.isAir() && HIGHLIGHTS.containsKey(state.getBlock())) {
			return 0xFFFFFFFF;
		}
		int argb = plainColorOf(level, pos, state);
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;
		int grey = (r * 30 + g * 59 + b * 11) / 100 * 2 / 3;
		return (argb & 0xFF000000) | (grey << 16) | (grey << 8) | grey;
	}

	static int colorOf(Level level, BlockPos pos, BlockState state) {
		if (state.is(Blocks.NETHER_PORTAL)) {
			return NETHER_PORTAL_MARK | portalSeed(pos);
		}
		if (state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY)) {
			return END_PORTAL_MARK | portalSeed(pos);
		}
		if (!state.isAir()) {
			Integer highlight = HIGHLIGHTS.get(state.getBlock());
			if (highlight != null) {
				return highlight;
			}
		}
		return plainColorOf(level, pos, state);
	}

	/** A fixed random 24-bit value per block, so each portal block keeps its own rhythm. */
	private static int portalSeed(BlockPos pos) {
		return (int) (pos.asLong() * 0x9E3779B97F4A7C15L >>> 40);
	}

	/** Map color without highlights. */
	private static int plainColorOf(Level level, BlockPos pos, BlockState state) {
		if (state.isAir()) {
			return state.is(Blocks.CAVE_AIR) ? CAVE_AIR : AIR;
		}

		MapColor mapColor = state.getMapColor(level, pos);
		if (mapColor == MapColor.NONE) {
			return AIR;
		}
		// Darken ordinary blocks a bit so the highlights stand out.
		int rgb = mapColor.col;
		int r = ((rgb >> 16) & 0xFF) * 3 / 4;
		int g = ((rgb >> 8) & 0xFF) * 3 / 4;
		int b = (rgb & 0xFF) * 3 / 4;
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}
}
