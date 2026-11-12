package com.example.clickmacro;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Watches the menu key and the activation key (keyboard key OR mouse button).
 *
 * HOW THE HOTBAR SLOT IS PROTECTED:
 * We do NOT register our keys as normal Minecraft keybinds. Instead we ask the
 * keyboard/mouse directly (GLFW) "is it down?". Then, at the very start of every tick
 * (BEFORE Minecraft handles input), we "swallow" the vanilla presses of any vanilla
 * keybind that uses the same key/button as ours (hotbar slot 6, swap-offhand on F,
 * pick-block on the middle mouse button, ...).
 * So the game never sees them and never switches slot. We also never call any
 * "set selected slot" method anywhere in this mod.
 */
public final class KeybindHandler {
	private static boolean menuWasDown = false;
	private static boolean activationWasDown = false;

	// Set the moment a key goes DOWN. The next tick reads them and clears them.
	private static boolean menuPressed = false;
	private static boolean activationPressed = false;

	private KeybindHandler() {}

	public static void register() {
		ClientTickEvents.START_CLIENT_TICK.register(KeybindHandler::onStartTick);
	}

	/**
	 * Checks keyboard and mouse. Called every FRAME (from ClickMacroMod) and at the start of
	 * every tick, so a super quick tap can never slip between two ticks.
	 */
	public static void pollKeys(MinecraftClient client) {
		if (client.getWindow() == null) return;
		ClickMacroConfig cfg = ClickMacroConfig.get();
		long window = client.getWindow().getHandle();

		boolean menuDown = isKeyDown(window, cfg.menuKey);
		boolean activationDown = cfg.activationIsMouse
				? isMouseDown(window, cfg.activationKey)
				: isKeyDown(window, cfg.activationKey);

		// Only react in the game world (no chat, inventory or menu open).
		boolean inWorld = client.player != null && client.world != null && client.currentScreen == null;
		if (inWorld) {
			if (menuDown && !menuWasDown) menuPressed = true;
			if (activationDown && !activationWasDown) activationPressed = true;
		}
		menuWasDown = menuDown;
		activationWasDown = activationDown;
	}

	private static void onStartTick(MinecraftClient client) {
		if (client.getWindow() == null || client.options == null) return;
		ClickMacroConfig cfg = ClickMacroConfig.get();

		// Step 1: throw away vanilla presses that share our keys (protects the slot).
		swallowVanillaPresses(client,
				InputUtil.Type.KEYSYM.createFromCode(cfg.menuKey).getTranslationKey(),
				cfg.activationKeyObject().getTranslationKey());

		// Step 2: look at the keyboard and mouse once more.
		pollKeys(client);

		// Step 3: act on what was pressed.
		boolean inWorld = client.player != null && client.world != null && client.currentScreen == null;
		if (inWorld) {
			if (menuPressed) {
				menuPressed = false;
				activationPressed = false;
				client.setScreen(new ClickMacroScreen());
			} else if (activationPressed) {
				activationPressed = false;
				ClickHandler.startBurst(); // one press = one burst of clicks
			}
		} else {
			menuPressed = false;
			activationPressed = false;
		}

		// Step 4: let the clicker do its work for this tick.
		ClickHandler.tick(client);
	}

	private static boolean isKeyDown(long window, int keyCode) {
		return keyCode > 0 && GLFW.glfwGetKey(window, keyCode) == GLFW.GLFW_PRESS;
	}

	private static boolean isMouseDown(long window, int button) {
		return button >= 0 && GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
	}

	/** Empties the "was pressed" counter of every vanilla keybind that uses one of our keys. */
	private static void swallowVanillaPresses(MinecraftClient client, String... ourKeyNames) {
		for (KeyBinding binding : client.options.allKeys) {
			String boundTo = binding.getBoundKeyTranslationKey();
			for (String ourName : ourKeyNames) {
				if (ourName.equals(boundTo)) {
					while (binding.wasPressed()) {
						// each call removes one stored press; loop ends when none are left
					}
					break;
				}
			}
		}
	}
}
