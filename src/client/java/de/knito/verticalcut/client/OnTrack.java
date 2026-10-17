package de.knito.verticalcut.client;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Item model condition "verticalcut:on_track": true while the player faces
 * along a fixed cut (compass module), so the CRT's antennas light up.
 */
public record OnTrack() implements ConditionalItemModelProperty {
	public static final MapCodec<OnTrack> MAP_CODEC = MapCodec.unit(new OnTrack());

	@Override
	public boolean get(ItemStack stack, ClientLevel level, LivingEntity entity, int seed, ItemDisplayContext context) {
		return entity != null && entity == Minecraft.getInstance().player && VerticalCutClient.onTrack();
	}

	@Override
	public MapCodec<OnTrack> type() {
		return MAP_CODEC;
	}
}
