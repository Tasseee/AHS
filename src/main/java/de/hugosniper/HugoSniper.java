package de.hugosniper;

import de.hugosniper.auction.SniperEngine;
import de.hugosniper.command.AhsCommand;
import de.hugosniper.gui.DashboardScreen;
import de.hugosniper.hud.HudRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Einstiegspunkt (nur Client). Registriert Befehl, Tick-Handler und HUD. */
public final class HugoSniper implements ClientModInitializer {
    public static final String MOD_ID = "hugo_sniper";
    public static final Logger LOGGER = LoggerFactory.getLogger("HugoSniper");

    @Override
    public void onInitializeClient() {
        SniperEngine engine = SniperEngine.get();
        engine.init(FabricLoader.getInstance().getConfigDir().resolve("hugo_sniper.json"));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> AhsCommand.register(dispatcher));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            engine.onClientTick(client);
            // Dashboard erst oeffnen, wenn der Chat zu ist (sonst wuerde er den neuen Bildschirm ueberschreiben)
            if (client.screen == null && AhsCommand.consumeOpenRequest()) {
                client.setScreen(new DashboardScreen());
            }
        });

        HudRenderCallback.EVENT.register((graphics, tickCounter) -> HudRenderer.render(graphics));

        LOGGER.info("HugoSniper geladen. /ahs oeffnet das Dashboard.");
    }
}
