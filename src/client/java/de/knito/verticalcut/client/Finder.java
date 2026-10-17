package de.knito.verticalcut.client;

import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the finder bundle looks for: the ores matching its samples. A sample
 * is anything made of the ore's material, from raw ore to a single nugget.
 * Besides a fixed list of vanilla samples, the common "c" tags that most
 * mods use connect a material to its ores: an item tagged c:ingots/tin (or
 * nuggets, raw_materials, gems, dusts, ores, storage_blocks) looks for the
 * blocks tagged c:ores/tin.
 *
 * A chest as sample (any chest, modded ones in c:chests too) looks for every
 * block with an inventory: chests, barrels, hoppers, furnaces, ...
 */
record Finder(Set<Block> blocks, Set<TagKey<Block>> oreTags, boolean containers) {
	static final Finder NONE = new Finder(Set.of(), Set.of(), false);
	private static final TagKey<Item> CHESTS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "chests"));

	/** Kinds of "c" item tags that name a material, as in c:ingots/iron. */
	private static final Set<String> MATERIAL_TAGS = Set.of(
			"ingots", "nuggets", "raw_materials", "gems", "dusts", "ores", "storage_blocks", "raw_blocks");

	/** Vanilla samples, in case the common tags are missing. */
	private static final Map<Item, List<Block>> SAMPLES = new IdentityHashMap<>();

	static {
		List<Block> coal = List.of(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);
		List<Block> iron = List.of(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.RAW_IRON_BLOCK);
		List<Block> copper = List.of(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, Blocks.RAW_COPPER_BLOCK);
		List<Block> gold = List.of(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE,
				Blocks.RAW_GOLD_BLOCK);
		put(coal, Items.COAL, Items.COAL_BLOCK);
		put(iron, Items.RAW_IRON, Items.IRON_INGOT, Items.IRON_NUGGET, Items.IRON_BLOCK);
		put(copper, Items.RAW_COPPER, Items.COPPER_INGOT, Items.COPPER_NUGGET);
		put(gold, Items.RAW_GOLD, Items.GOLD_INGOT, Items.GOLD_NUGGET, Items.GOLD_BLOCK);
		put(List.of(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE), Items.REDSTONE, Items.REDSTONE_BLOCK);
		put(List.of(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE), Items.LAPIS_LAZULI, Items.LAPIS_BLOCK);
		put(List.of(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE), Items.DIAMOND, Items.DIAMOND_BLOCK);
		put(List.of(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE), Items.EMERALD, Items.EMERALD_BLOCK);
		put(List.of(Blocks.NETHER_QUARTZ_ORE), Items.QUARTZ, Items.QUARTZ_BLOCK);
		put(List.of(Blocks.ANCIENT_DEBRIS), Items.ANCIENT_DEBRIS, Items.NETHERITE_SCRAP, Items.NETHERITE_INGOT);
	}

	private static void put(List<Block> ores, Item... samples) {
		for (Item sample : samples) {
			SAMPLES.put(sample, ores);
		}
	}

	/** The ores these samples look for; anything that isn't made of an ore is ignored. */
	static Finder of(Collection<ItemStack> samples) {
		if (samples.isEmpty()) {
			return NONE;
		}
		Set<Block> blocks = new HashSet<>();
		Set<TagKey<Block>> oreTags = new HashSet<>();
		boolean containers = false;
		for (ItemStack sample : samples) {
			containers |= sample.is(Items.CHEST) || sample.is(Items.TRAPPED_CHEST) || sample.is(CHESTS);
			blocks.addAll(SAMPLES.getOrDefault(sample.getItem(), List.of()));
			BuiltInRegistries.ITEM.wrapAsHolder(sample.getItem()).tags().forEach(tag -> {
				Identifier id = tag.location();
				String[] parts = id.getPath().split("/", 2);
				if (id.getNamespace().equals("c") && parts.length == 2 && MATERIAL_TAGS.contains(parts[0])) {
					oreTags.add(TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "ores/" + parts[1])));
				}
			});
		}
		return new Finder(Set.copyOf(blocks), Set.copyOf(oreTags), containers);
	}

	boolean isEmpty() {
		return blocks.isEmpty() && oreTags.isEmpty() && !containers;
	}

	boolean matches(Level level, BlockPos pos, BlockState state) {
		if (containers && state.hasBlockEntity() && level.getBlockEntity(pos) instanceof Container) {
			return true;
		}
		if (blocks.contains(state.getBlock())) {
			return true;
		}
		for (TagKey<Block> tag : oreTags) {
			if (state.is(tag)) {
				return true;
			}
		}
		return false;
	}
}
