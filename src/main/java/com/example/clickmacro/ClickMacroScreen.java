package com.example.clickmacro;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * The settings menu with 3 equally sized boxes:
 *   Box 1 (top left)      CLICK = Right/Left
 *   Box 2 (top right)     CPS = n   with "-" and "+"
 *   Box 3 (center bottom) KEYBINDS = F   (or Middle Button, ...)
 *
 * The boxes are drawn by us (dark, rounded-looking). On top of each one sits a fully
 * transparent vanilla button that just receives the mouse click.
 */
public class ClickMacroScreen extends Screen {
	private static final int BOX_W = 150;
	private static final int BOX_H = 56;
	private static final int MINI_W = 44;
	private static final int MINI_H = 16;

	private static final int COLOR_BORDER = 0xFF5A5A5A;
	private static final int COLOR_BOX = 0xF0202020;
	private static final int COLOR_BOX_HOVER = 0xF0383838;
	private static final int COLOR_TEXT = 0xFFFFFFFF;
	private static final int COLOR_WARNING = 0xFFFF5555;

	private int leftX, rightX, topY, bottomX, bottomY;

	private boolean waitingForKey = false;
	private final boolean[] mouseWasDown = new boolean[GLFW.GLFW_MOUSE_BUTTON_LAST + 1];
	private String warning = "";
	private long warningUntilMillis = 0L;

	public ClickMacroScreen() {
		super(Text.literal("ClickMacro"));
	}

	@Override
	protected void init() {
		// Box positions (like your blueprint).
		leftX = width / 4 - BOX_W / 2;
		rightX = (width * 3) / 4 - BOX_W / 2;
		topY = Math.max(30, height / 5);
		bottomX = width / 2 - BOX_W / 2;
		bottomY = height / 2 + 20;

		ClickMacroConfig cfg = ClickMacroConfig.get();

		// Box 1: toggle Right <-> Left
		addInvisibleButton(leftX, topY, BOX_W, BOX_H, b -> {
			cfg.rightClick = !cfg.rightClick;
			ClickMacroConfig.save();
		});

		// Box 2: "-" and "+"
		addInvisibleButton(minusX(), miniY(), MINI_W, MINI_H, b -> {
			cfg.cps = Math.max(ClickMacroConfig.MIN_CPS, cfg.cps - 1);
			ClickMacroConfig.save();
		});
		addInvisibleButton(plusX(), miniY(), MINI_W, MINI_H, b -> {
			cfg.cps = Math.min(ClickMacroConfig.MAX_CPS, cfg.cps + 1);
			ClickMacroConfig.save();
		});

		// Box 3: wait for the next key / mouse button press
		addInvisibleButton(bottomX, bottomY, BOX_W, BOX_H, b -> startWaiting());
	}

	private int minusX() { return rightX + 12; }
	private int plusX() { return rightX + BOX_W - 12 - MINI_W; }
	private int miniY() { return topY + BOX_H - MINI_H - 8; }

	/** A button you cannot see; it only exists to catch mouse clicks. */
	private void addInvisibleButton(int x, int y, int w, int h, ButtonWidget.PressAction action) {
		ButtonWidget button = ButtonWidget.builder(Text.empty(), action)
				.dimensions(x, y, w, h)
				.build();
		button.setAlpha(0.0f);
		addDrawableChild(button);
	}

	// ---------------------------------------------------------------- key capture

	private void startWaiting() {
		waitingForKey = true;
		// Remember which mouse buttons are already held, so only a NEW press counts.
		if (client != null && client.getWindow() != null) {
			long window = client.getWindow().getHandle();
			for (int b = 0; b < mouseWasDown.length; b++) {
				mouseWasDown[b] = GLFW.glfwGetMouseButton(window, b) == GLFW.GLFW_PRESS;
			}
		}
	}

	/** While waiting, watch the middle and side mouse buttons (left/right stay for clicking boxes). */
	private void pollMouseButtons() {
		if (client == null || client.getWindow() == null) return;
		long window = client.getWindow().getHandle();
		for (int b = GLFW.GLFW_MOUSE_BUTTON_MIDDLE; b <= GLFW.GLFW_MOUSE_BUTTON_LAST; b++) {
			boolean down = GLFW.glfwGetMouseButton(window, b) == GLFW.GLFW_PRESS;
			if (down && !mouseWasDown[b] && waitingForKey) {
				waitingForKey = false;
				applyNewActivationKey(b, true);
			}
			mouseWasDown[b] = down;
		}
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (waitingForKey) {
			int key = input.key();
			if (key == GLFW.GLFW_KEY_UNKNOWN) {
				return true; // ignore keys GLFW cannot name, keep waiting
			}
			waitingForKey = false;
			if (key != GLFW.GLFW_KEY_ESCAPE) { // ESC = cancel
				applyNewActivationKey(key, false);
			}
			return true; // we used the key, so the game must not use it (no ESC-close, no slot change)
		}
		return super.keyPressed(input);
	}

	private void applyNewActivationKey(int code, boolean isMouse) {
		ClickMacroConfig cfg = ClickMacroConfig.get();
		if (!isMouse && code == cfg.menuKey) {
			showWarning("That key opens this menu. Pick a different key.");
			return; // keep the old key
		}
		cfg.activationKey = code;
		cfg.activationIsMouse = isMouse;
		ClickMacroConfig.save();
	}

	private void showWarning(String message) {
		warning = message;
		warningUntilMillis = System.currentTimeMillis() + 3000L;
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta); // background + (invisible) buttons

		if (waitingForKey) {
			pollMouseButtons();
		}

		ClickMacroConfig cfg = ClickMacroConfig.get();

		drawCentered(context, Text.literal("ClickMacro"), width / 2, 10);

		// Box 1
		drawBox(context, leftX, topY, BOX_W, BOX_H, mouseX, mouseY);
		drawCentered(context,
				Text.literal("CLICK = " + (cfg.rightClick ? "Right" : "Left")),
				leftX + BOX_W / 2, topY + (BOX_H - 8) / 2);

		// Box 2
		drawBox(context, rightX, topY, BOX_W, BOX_H, -1, -1); // no hover on the big part
		drawCentered(context, Text.literal("CPS = " + cfg.cps), rightX + BOX_W / 2, topY + 10);
		drawBox(context, minusX(), miniY(), MINI_W, MINI_H, mouseX, mouseY);
		drawCentered(context, Text.literal("-"), minusX() + MINI_W / 2, miniY() + 4);
		drawBox(context, plusX(), miniY(), MINI_W, MINI_H, mouseX, mouseY);
		drawCentered(context, Text.literal("+"), plusX() + MINI_W / 2, miniY() + 4);

		// Box 3
		drawBox(context, bottomX, bottomY, BOX_W, BOX_H, mouseX, mouseY);
		String keyText = waitingForKey ? "Press any key..." : "KEYBINDS = " + cfg.activationName();
		drawCentered(context, Text.literal(keyText), bottomX + BOX_W / 2, bottomY + (BOX_H - 8) / 2);

		// Small hints / warning at the bottom
		if (waitingForKey) {
			drawCentered(context, Text.literal("Key or middle/side mouse button. ESC = cancel"),
					width / 2, bottomY + BOX_H + 8);
		}
		if (System.currentTimeMillis() < warningUntilMillis) {
			context.drawTextWithShadow(textRenderer, Text.literal(warning),
					(width - textRenderer.getWidth(warning)) / 2, bottomY + BOX_H + 22, COLOR_WARNING);
		}
	}

	/** Rounded-looking box: a border-colored shape with a dark shape on top. */
	private void drawBox(DrawContext context, int x, int y, int w, int h, int mouseX, int mouseY) {
		boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
		drawRounded(context, x - 1, y - 1, w + 2, h + 2, COLOR_BORDER);
		drawRounded(context, x, y, w, h, hover ? COLOR_BOX_HOVER : COLOR_BOX);
	}

	/** Three overlapping rectangles = a rectangle with its corners cut off. */
	private void drawRounded(DrawContext context, int x, int y, int w, int h, int color) {
		context.fill(x + 2, y, x + w - 2, y + h, color);
		context.fill(x, y + 2, x + w, y + h - 2, color);
		context.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
	}

	private void drawCentered(DrawContext context, Text text, int centerX, int y) {
		context.drawTextWithShadow(textRenderer, text, centerX - textRenderer.getWidth(text) / 2, y, COLOR_TEXT);
	}
}
