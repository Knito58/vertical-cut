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
	 * Black and white picture of the copper CRT: highlighted blocks are
	 * bright dots, everything else a dim grey, so the player has to read it.
	 */
	static int monochromeOf(Level level, BlockPos pos, BlockState state) {
		if (!state.isAir() && HIGHLIGHTS.containsKey(state.getBlock())) {
			return 0xFFFFFFFF;
		}
		int argb = colorOf(level, pos, state);
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;
		int grey = (r * 30 + g * 59 + b * 11) / 100 * 2 / 3;
		return (argb & 0xFF000000) | (grey << 16) | (grey << 8) | grey;
	}

	static int colorOf(Level level, BlockPos pos, BlockState state) {
		if (state.isAir()) {
			return state.is(Blocks.CAVE_AIR) ? CAVE_AIR : AIR;
		}

		Integer highlight = HIGHLIGHTS.get(state.getBlock());
		if (highlight != null) {
			return highlight;
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
