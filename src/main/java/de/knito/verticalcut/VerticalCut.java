package de.knito.verticalcut;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared constants. The mod is client-only, so there is no main entrypoint;
 * all logic starts in {@code VerticalCutClient}.
 */
public final class VerticalCut {
	public static final String MOD_ID = "verticalcut";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private VerticalCut() {
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
