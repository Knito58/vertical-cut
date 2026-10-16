package de.knito.verticalcut;

/**
 * What each CRT tier can do. Pixels per block: 4 shows 32x32 blocks,
 * 2 shows 64x64, 1 shows 128x128 (the slice texture is always 128 pixels).
 */
public enum CrtTier {
	COPPER("copper_crt", 4, false, false);

	public final String itemName;
	/** Pixels per block when zoom can't be switched. */
	public final int pixelsPerBlock;
	public final boolean color;
	/** Whether the player may switch between 2x and 1x. */
	public final boolean switchableZoom;

	CrtTier(String itemName, int pixelsPerBlock, boolean color, boolean switchableZoom) {
		this.itemName = itemName;
		this.pixelsPerBlock = pixelsPerBlock;
		this.color = color;
		this.switchableZoom = switchableZoom;
	}
}
