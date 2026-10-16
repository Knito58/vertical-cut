package de.knito.verticalcut.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Adds a "Configure" button for Vertical Cut in Mod Menu. Only loaded when
 * Mod Menu is installed; the mod works without it.
 */
public class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return VerticalCutConfigScreen::new;
	}
}
