package de.knito.verticalcut.client;

import java.util.List;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import de.knito.verticalcut.CrtTier;
import de.knito.verticalcut.VerticalCutConfig;

/**
 * The CRT's personality: the hiccup when it swallows a coal, the occasional
 * flicker with an electric buzz of the crude tiers, and the old-TV
 * switch-off when the fuel runs out. Watches the fuel level once per
 * tick and runs the animations on real time so they stay smooth.
 */
final class CrtEffects {
	static final long HICCUP_NANOS = 500_000_000L;
	static final long DEATH_NANOS = 1_200_000_000L;

	private static final List<SoundEvent> GULPS = List.of(
			SoundEvents.GENERIC_DRINK.value(),
			SoundEvents.HONEY_DRINK.value(),
			SoundEvents.WITCH_DRINK,
			SoundEvents.WANDERING_TRADER_DRINK_MILK,
			SoundEvents.WANDERING_TRADER_DRINK_POTION);
	/** Played only for a moment, so they sound like a short "bzzt". */
	private static final List<SoundEvent> BUZZES = List.of(
			SoundEvents.BEE_LOOP,
			SoundEvents.RESPAWN_ANCHOR_AMBIENT);

	private final Random random = new Random();

	private int lastFuel = -1;
	private long hiccupStart = -1;
	private long deathStart = -1;
	/** Ticks until the burp(s) after a gulp; 0 = nothing pending. */
	private int burpIn;
	private int extraBurpIn;
	/** Ticks until the next flicker; -1 = not scheduled yet. */
	private int nextFlickerIn = -1;
	/** Remaining ticks of the current flicker. */
	private int flickerTicks;
	private SoundInstance buzz;
	private int buzzStopIn;

	/** Forget everything, e.g. when the CRT is put away. */
	void reset() {
		lastFuel = -1;
		hiccupStart = -1;
		deathStart = -1;
		burpIn = 0;
		extraBurpIn = 0;
		nextFlickerIn = -1;
		flickerTicks = 0;
		stopBuzz(Minecraft.getInstance());
	}

	/** Called every client tick while a CRT is held and the cut is switched on. */
	void tick(Minecraft client, int fuel) {
		if (lastFuel >= 0) {
			if (fuel > lastFuel) {
				hiccup(client);
			} else if (fuel == 0 && lastFuel > 0) {
				deathStart = System.nanoTime();
			}
		}
		lastFuel = fuel;

		if (burpIn > 0 && --burpIn == 0) {
			play(client, SoundEvents.PLAYER_BURP, 0.6f + random.nextFloat() * 0.8f, 0.5f);
		}
		if (extraBurpIn > 0 && --extraBurpIn == 0) {
			play(client, SoundEvents.PLAYER_BURP, 1.5f + random.nextFloat() * 0.4f, 0.3f);
		}
	}

	/**
	 * Called every client tick while a CRT is held and the cut is switched on.
	 * Crude tiers flicker now and then, and some flickers buzz. No new
	 * flickers start while the tank is empty.
	 */
	void tickFlicker(Minecraft client, CrtTier tier, boolean running) {
		if (buzz != null && --buzzStopIn <= 0) {
			stopBuzz(client);
		}
		if (flickerTicks > 0) {
			flickerTicks--;
		}
		if (tier.flickerMaxSeconds <= 0 || !running) {
			nextFlickerIn = -1;
			return;
		}
		if (nextFlickerIn < 0) {
			scheduleFlicker(tier);
		} else if (nextFlickerIn-- == 0) {
			flickerTicks = 3 + random.nextInt(8);
			if (random.nextFloat() < tier.buzzChance) {
				SoundEvent sound = BUZZES.get(random.nextInt(BUZZES.size()));
				stopBuzz(client);
				buzz = play(client, sound, 0.6f + random.nextFloat() * 0.9f, tier.buzzVolume);
				buzzStopIn = 4 + random.nextInt(7);
			}
			scheduleFlicker(tier);
		}
	}

	private void scheduleFlicker(CrtTier tier) {
		int spread = Math.max(1, tier.flickerMaxSeconds - tier.flickerMinSeconds);
		nextFlickerIn = (tier.flickerMinSeconds + random.nextInt(spread)) * 20;
	}

	private void stopBuzz(Minecraft client) {
		if (buzz != null) {
			client.getSoundManager().stop(buzz);
			buzz = null;
		}
	}

	boolean flickering() {
		return flickerTicks > 0;
	}

	private void hiccup(Minecraft client) {
		hiccupStart = System.nanoTime();
		deathStart = -1;
		SoundEvent gulp = GULPS.get(random.nextInt(GULPS.size()));
		play(client, gulp, 0.8f + random.nextFloat() * 0.45f, 0.7f);
		burpIn = 6 + random.nextInt(7);
		// Now and then a tiny second burp.
		extraBurpIn = random.nextFloat() < 0.15f ? burpIn + 4 + random.nextInt(4) : 0;
	}

	/** Plays a sound unless CRT sounds are switched off; returns it so it can be stopped early. */
	private static SoundInstance play(Minecraft client, SoundEvent sound, float pitch, float volume) {
		if (!VerticalCutConfig.get().sounds) {
			return null;
		}
		SoundInstance instance = SimpleSoundInstance.forUI(sound, pitch, volume);
		client.getSoundManager().play(instance);
		return instance;
	}

	/**
	 * Size factor for the hiccup "bulge": swells about 15 %, then swings
	 * slightly below normal and settles. 1 when no hiccup is running.
	 */
	float bulge() {
		if (hiccupStart < 0) {
			return 1f;
		}
		long elapsed = System.nanoTime() - hiccupStart;
		if (elapsed >= HICCUP_NANOS) {
			hiccupStart = -1;
			return 1f;
		}
		double t = (double) elapsed / HICCUP_NANOS;
		return (float) (1 + 0.15 * Math.sin(t * 2 * Math.PI) * (1 - t) * (t < 0.5 ? 1 : 0.6));
	}

	boolean dying() {
		return deathStart >= 0 && System.nanoTime() - deathStart < DEATH_NANOS;
	}

	/** 0..1 progress of the switch-off animation, or -1 when not dying. */
	float deathProgress() {
		if (!dying()) {
			return -1f;
		}
		return (float) (System.nanoTime() - deathStart) / DEATH_NANOS;
	}
}
