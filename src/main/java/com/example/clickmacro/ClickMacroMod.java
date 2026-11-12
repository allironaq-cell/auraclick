package com.example.clickmacro;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/**
 * Main class. Fabric calls onInitializeClient() once when the game starts.
 * The mod shows NOTHING on screen.
 */
public class ClickMacroMod implements ClientModInitializer {
	public static final String MOD_ID = "clickmacro";

	@Override
	public void onInitializeClient() {
		// 1. Load saved settings (or defaults if the file is missing/broken).
		ClickMacroConfig.load();

		// 2. Start watching our keys + running the clicker every tick.
		KeybindHandler.register();

		// 3. This draws NOTHING. It only checks the keyboard on every frame (hundreds of
		//    times per second) so even a very quick tap of your key is never missed.
		HudElementRegistry.addLast(Identifier.of(MOD_ID, "key_watcher"),
				(context, tickCounter) -> KeybindHandler.pollKeys(MinecraftClient.getInstance()));
	}
}
