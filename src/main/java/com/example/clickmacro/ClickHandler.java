package com.example.clickmacro;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import java.lang.reflect.Field;

/**
 * The clicker. One press of your key = one "burst" of N clicks (N = the number in the
 * CPS box of the menu). The burst runs as fast as Minecraft can reliably handle it:
 * CLICKS_PER_TICK clicks every game tick (20 ticks per second).
 *
 * Each click is a REAL vanilla key press of your Attack / Use key, so the game handles
 * it exactly like you clicking yourself. We never touch the hotbar.
 */
public final class ClickHandler {
	/**
	 * Clicks fired every tick during a burst. 1 is the most reliable: every click gets its
	 * own tick, so Minecraft can look at what you aim at again before the next click.
	 * (Higher numbers = several clicks in the same tick; many of them get ignored.)
	 */
	private static final int CLICKS_PER_TICK = 1;

	private static int remainingClicks = 0;

	// Used to reset vanilla's "attack cooldown" (see resetAttackCooldown).
	private static boolean cooldownLookupDone = false;
	private static Field attackCooldownField = null;

	private ClickHandler() {}

	/** Called when you press the activation key: starts (or restarts) a burst. */
	public static void startBurst() {
		remainingClicks = ClickMacroConfig.get().cps;
	}

	/** Called once per tick by KeybindHandler (before the game handles its own input). */
	public static void tick(MinecraftClient client) {
		if (remainingClicks <= 0) return;

		// Stop if any screen opens (menu, chat, inventory) or we leave the world.
		if (client.player == null || client.world == null || client.currentScreen != null) {
			remainingClicks = 0;
			return;
		}

		int toFire = Math.min(CLICKS_PER_TICK, remainingClicks);
		for (int i = 0; i < toFire; i++) {
			fireClick(client);
		}
		remainingClicks -= toFire;
	}

	/** Presses the vanilla Use (right) or Attack (left) key once. */
	private static void fireClick(MinecraftClient client) {
		boolean right = ClickMacroConfig.get().rightClick;
		if (!right) {
			resetAttackCooldown(client);
		}
		KeyBinding binding = right ? client.options.useKey : client.options.attackKey;
		try {
			InputUtil.Key key = InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey());
			KeyBinding.onKeyPressed(key);
		} catch (IllegalArgumentException e) {
			// The keybind is unbound or unknown - nothing to press.
		}
	}

	/**
	 * In survival, vanilla ignores left-clicks for 10 ticks after a swing that hit nothing.
	 * That made fast left clicks "not register". We set that waiting time to 0 before each
	 * left click. It is looked up safely: if the name is ever wrong, this is simply skipped
	 * (no crash).
	 */
	private static void resetAttackCooldown(MinecraftClient client) {
		if (!cooldownLookupDone) {
			cooldownLookupDone = true;
			try {
				String runtimeName = FabricLoader.getInstance().getMappingResolver().mapFieldName(
						"named", "net.minecraft.client.MinecraftClient", "attackCooldown", "I");
				Field field = MinecraftClient.class.getDeclaredField(runtimeName);
				field.setAccessible(true);
				attackCooldownField = field;
			} catch (Exception e) {
				System.err.println("[ClickMacro] Could not find attackCooldown, skipping reset: " + e);
			}
		}
		if (attackCooldownField != null) {
			try {
				attackCooldownField.setInt(client, 0);
			} catch (Exception ignored) {
				// nothing to do
			}
		}
	}
}
