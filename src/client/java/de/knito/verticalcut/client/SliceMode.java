package de.knito.verticalcut.client;

/** How the slice is colored, depending on the CRT tier. */
enum SliceMode {
	/** Vanilla map colors with bright ores. */
	COLOR,
	/** Black and white; ores, spawners and chests are bright dots. */
	MONOCHROME,
	/** Black and white without highlights: only caves, water and lava. */
	CAVES
}
