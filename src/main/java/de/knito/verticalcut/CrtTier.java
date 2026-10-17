package de.knito.verticalcut;

/**
 * What each CRT tier can do. Pixels per block: 4 shows 32x32 blocks,
 * 2 shows 64x64, 1 shows 128x128 (the slice texture is always 128 pixels).
 */
public enum CrtTier {
	// Crude cave finder: no ores, a lagging picture, frequent flicker with a loud buzz.
	STONE("stone_crt", 4, new int[0], false, false, true, 10, 30, 0.5f, 0.5f, 0),
	COPPER("copper_crt", 4, new int[0], false, true, false, 60, 180, 0.4f, 0.25f, 1),
	// First color picture, sees twice as far (64x64 blocks), no flicker of its own.
	IRON("iron_crt", 2, new int[0], true, true, false, 0, 0, 0f, 0f, 2),
	// Like iron, but zooms in to see things closer: 32x32 blocks.
	GOLD("gold_crt", 2, new int[] {4, 2}, true, true, false, 0, 0, 0f, 0f, 2),
	// Like gold, but also zooms out to 128x128 blocks, and 3 module slots.
	DIAMOND("diamond_crt", 2, new int[] {4, 2, 1}, true, true, false, 0, 0, 0f, 0f, 3);

	public final String itemName;
	public final int pixelsPerBlock;
	/** Pixels per block the zoom key steps through, near to far; empty = this tier can't zoom. */
	private final int[] zoomSteps;
	public final boolean color;
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

	CrtTier(String itemName, int pixelsPerBlock, int[] zoomSteps, boolean color, boolean showsOres,
			boolean laggy, int flickerMinSeconds, int flickerMaxSeconds, float buzzChance, float buzzVolume,
			int moduleSlots) {
		this.itemName = itemName;
		this.pixelsPerBlock = pixelsPerBlock;
		this.zoomSteps = zoomSteps;
		this.color = color;
		this.showsOres = showsOres;
		this.laggy = laggy;
		this.flickerMinSeconds = flickerMinSeconds;
		this.flickerMaxSeconds = flickerMaxSeconds;
		this.buzzChance = buzzChance;
		this.buzzVolume = buzzVolume;
		this.moduleSlots = moduleSlots;
	}

	public boolean canZoom() {
		return zoomSteps.length > 0;
	}

	/** Pixels per block for the chosen zoom (saved in the settings); the default if this tier can't show it. */
	public int pixelsPerBlock(int zoom) {
		for (int step : zoomSteps) {
			if (step == zoom) {
				return zoom;
			}
		}
		return pixelsPerBlock;
	}

	/** The zoom after the current one: one step farther, after the farthest back to the nearest. */
	public int nextZoom(int zoom) {
		int current = pixelsPerBlock(zoom);
		for (int i = 0; i < zoomSteps.length; i++) {
			if (zoomSteps[i] == current) {
				return zoomSteps[(i + 1) % zoomSteps.length];
			}
		}
		return pixelsPerBlock;
	}
}
