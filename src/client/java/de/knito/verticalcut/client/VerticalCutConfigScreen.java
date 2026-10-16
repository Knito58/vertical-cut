package de.knito.verticalcut.client;

import java.util.function.IntConsumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import de.knito.verticalcut.client.VerticalCutConfig.Corner;

/**
 * Settings screen, reachable from Mod Menu or an optional key. Changes take
 * effect immediately; they are written to disk when the screen closes.
 */
public class VerticalCutConfigScreen extends Screen {
	private static final int BUTTON_WIDTH = 200;
	private static final int ROW = 24;

	private final Screen parent;
	private final VerticalCutConfig config = VerticalCutConfig.get();

	public VerticalCutConfigScreen(Screen parent) {
		super(Component.translatable("verticalcut.config.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int x = width / 2 - BUTTON_WIDTH / 2;
		int y = 40;

		addRenderableWidget(CycleButton.onOffBuilder(config.visible)
				.create(x, y, BUTTON_WIDTH, 20, Component.translatable("verticalcut.config.visible"),
						(button, value) -> config.visible = value));
		y += ROW;

		addRenderableWidget(CycleButton.<Corner>builder(
						corner -> Component.translatable("verticalcut.config.corner." + corner.name().toLowerCase()),
						config.corner)
				.withValues(Corner.values())
				.create(x, y, BUTTON_WIDTH, 20, Component.translatable("verticalcut.config.corner"),
						(button, value) -> config.corner = value));
		y += ROW;

		addRenderableWidget(new IntSlider(x, y, "verticalcut.config.margin", 0, VerticalCutConfig.MAX_MARGIN,
				config.margin, value -> config.margin = value));
		y += ROW;

		addRenderableWidget(new IntSlider(x, y, "verticalcut.config.size", VerticalCutConfig.MIN_SIZE,
				VerticalCutConfig.MAX_SIZE, config.size, value -> config.size = value));
		y += ROW;

		addRenderableWidget(new IntSlider(x, y, "verticalcut.config.opacity", 1, VerticalCutConfig.OPACITY_LEVELS,
				config.opacity, value -> config.opacity = value));
		y += ROW;

		addRenderableWidget(CycleButton.<Integer>builder(zoom -> Component.literal(zoom + "x"), config.zoom)
				.withValues(1, 2)
				.create(x, y, BUTTON_WIDTH, 20, Component.translatable("verticalcut.config.zoom"),
						(button, value) -> config.zoom = value));
		y += ROW + 8;

		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(x, y, BUTTON_WIDTH, 20)
				.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(font, title, width / 2, 16, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		config.save();
		minecraft.gui.setScreen(parent);
	}

	/** A slider over a whole-number range that writes its value back on every change. */
	private static class IntSlider extends AbstractSliderButton {
		private final String translationKey;
		private final int min;
		private final int max;
		private final IntConsumer setter;

		IntSlider(int x, int y, String translationKey, int min, int max, int current, IntConsumer setter) {
			super(x, y, BUTTON_WIDTH, 20, Component.empty(), (double) (current - min) / (max - min));
			this.translationKey = translationKey;
			this.min = min;
			this.max = max;
			this.setter = setter;
			updateMessage();
		}

		private int intValue() {
			return min + (int) Math.round(value * (max - min));
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable(translationKey, intValue()));
		}

		@Override
		protected void applyValue() {
			setter.accept(intValue());
		}
	}
}
