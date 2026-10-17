package de.knito.verticalcut;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Settings, stored as config/verticalcut.json. Values read from the
 * file are clamped, so a hand-edited file can't break the game.
 */
public final class VerticalCutConfig {
	public enum Corner {
		TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

		public boolean right() {
			return this == TOP_RIGHT || this == BOTTOM_RIGHT;
		}

		public boolean bottom() {
			return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
		}
	}

	/** Color of the stick figure in color pictures; black and white pictures always draw it white. */
	public enum FigureColor {
		YELLOW(0xFFFFDC28), RED(0xFFFF5050), ORANGE(0xFFFF9020), GREEN(0xFF50E050), CYAN(0xFF50E0FF),
		MAGENTA(0xFFFF60FF), WHITE(0xFFFFFFFF);

		public final int argb;

		FigureColor(int argb) {
			this.argb = argb;
		}
	}

	public static final int OPACITY_LEVELS = 7;
	public static final int MIN_SIZE = 64;
	public static final int MAX_SIZE = 256;
	public static final int MAX_MARGIN = 64;
	public static final int MIN_SECONDS_PER_COAL = 60;
	public static final int MAX_SECONDS_PER_COAL = 600;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("verticalcut.json");

	private static VerticalCutConfig instance;

	public boolean visible = true;
	/** On: the CRT must be held. Off: any CRT in the inventory scans (same fuel and modules rules). */
	public boolean crtInHand = true;
	public Corner corner = Corner.TOP_LEFT;
	/** Distance from the screen edges, in GUI pixels. */
	public int margin = 4;
	/** Width and height of the cut on screen, in GUI pixels. */
	public int size = 128;
	/** 1..OPACITY_LEVELS; the highest level draws a solid blue background. */
	public int opacity = OPACITY_LEVELS - 1;
	/**
	 * How long one coal lasts while scanning. Read by the server, so in
	 * multiplayer the server's config file decides.
	 */
	public int secondsPerCoal = 240;
	/** Gulp and burp when the CRT swallows a coal. Client only. */
	public boolean sounds = true;
	public FigureColor figureColor = FigureColor.YELLOW;
	/** Zoom chosen with the zoom key, in pixels per block; CRTs that can't show it use their own. */
	public int zoom = 2;
	/** Colors for red-green color blind players: blue instead of green. */
	public boolean colorBlind = false;

	public static VerticalCutConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static VerticalCutConfig load() {
		VerticalCutConfig config = null;
		if (Files.exists(FILE)) {
			try (Reader reader = Files.newBufferedReader(FILE)) {
				config = GSON.fromJson(reader, VerticalCutConfig.class);
			} catch (IOException | RuntimeException e) {
				VerticalCut.LOGGER.warn("Could not read {}, using defaults", FILE, e);
			}
		}
		if (config == null) {
			config = new VerticalCutConfig();
		}
		config.clamp();
		return config;
	}

	public void save() {
		clamp();
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(FILE)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			VerticalCut.LOGGER.warn("Could not write {}", FILE, e);
		}
	}

	private void clamp() {
		if (corner == null) {
			corner = Corner.TOP_LEFT;
		}
		if (figureColor == null) {
			figureColor = FigureColor.YELLOW;
		}
		margin = Math.clamp(margin, 0, MAX_MARGIN);
		size = Math.clamp(size, MIN_SIZE, MAX_SIZE);
		opacity = Math.clamp(opacity, 1, OPACITY_LEVELS);
		secondsPerCoal = Math.clamp(secondsPerCoal, MIN_SECONDS_PER_COAL, MAX_SECONDS_PER_COAL);
	}
}
