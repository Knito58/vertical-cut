package de.knito.verticalcut;

/**
 * What each CRT tier can do. Pixels per block: 4 shows 32x32 blocks,
 * 2 shows 64x64, 1 shows 128x128 (the slice texture is always 128 pixels).
 */
public enum CrtTier {
	// Crude cave finder: no ores, a lagging picture, frequent flicker with a loud buzz.
	STONE("stone_crt", 4, false, false, false, true, 10, 30, 0.5f, 0.5f, 0),
	COPPER("copper_crt", 4, false, false, true, false, 60, 180, 0.4f, 0.25f, 1),
	// First color picture, sees twice as far (64x64 blocks), no flicker of its own.
	IRON("iron_crt", 2, true, false, true, false, 0, 0, 0f, 0f, 2);

	public final String itemName;
	/** Pixels per block when zoom can't be switched. */
	public final int pixelsPerBlock;
	public final boolean color;
	/** Whether the player may switch between 2x and 1x. */
	public final boolean switchableZoom;
	/** Whether ores, spawners and chests stand out. Without, only caves, water and lava show. */
	public final boolean showsOres;
	/** Picture only refreshes now and then, not when the player moves or turns. */
	public final boolean laggy;
	/** Random pause between flickers in seconds; 0 = never flickers. */
	public final int flickerMinSeconds;
	public final int flickerMaxSeconds;
	/** Chance that a flicker buzzes, and how loud. */
	public final float buzzChance;
	public final float buzzVolume;
	/** How many modules (coal pouch, ...) fit into this CRT. */
	public final int moduleSlots;

	CrtTier(String itemName, int pixelsPerBlock, boolean color, boolean switchableZoom, boolean showsOres,
			boolean laggy, int flickerMinSeconds, int flickerMaxSeconds, float buzzChance, float buzzVolume,
			int moduleSlots) {
		this.itemName = itemName;
		this.pixelsPerBlock = pixelsPerBlock;
		this.color = color;
		this.switchableZoom = switchableZoom;
		this.showsOres = showsOres;
		this.laggy = laggy;
		this.flickerMinSeconds = flickerMinSeconds;
		this.flickerMaxSeconds = flickerMaxSeconds;
		this.buzzChance = buzzChance;
		this.buzzVolume = buzzVolume;
		this.moduleSlots = moduleSlots;
	}
}
