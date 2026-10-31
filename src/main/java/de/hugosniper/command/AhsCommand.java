package de.hugosniper.command;

import com.mojang.brigadier.CommandDispatcher;
import de.hugosniper.auction.SniperEngine;
import de.hugosniper.util.TimeFmt;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * Client-Befehl /ahs. Der Server-Befehl /ah wird NICHT beruehrt (anderer Literal-Name).
 * Der Bildschirm wird nicht direkt im Befehl geoeffnet (der Chat schliesst sich danach und wuerde ihn ueberschreiben),
 * sondern per Flag im naechsten Client-Tick.
 */
public final class AhsCommand {
    private AhsCommand() {}

    private static volatile boolean openRequested;

    public static boolean consumeOpenRequest() {
        boolean r = openRequested;
        openRequested = false;
        return r;
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> d) {
        SniperEngine eng = SniperEngine.get();
        d.register(ClientCommandManager.literal("ahs")
                .executes(ctx -> { openRequested = true; return 1; })
                .then(ClientCommandManager.literal("start").executes(ctx -> {
                    eng.start();
                    ctx.getSource().sendFeedback(Component.literal("HugoSniper: Suche gestartet. Oeffne /ah."));
                    return 1;
                }))
                .then(ClientCommandManager.literal("pause").executes(ctx -> {
                    eng.pause("per Befehl");
                    ctx.getSource().sendFeedback(Component.literal("HugoSniper: pausiert."));
                    return 1;
                }))
                .then(ClientCommandManager.literal("scan").executes(ctx -> {
                    eng.requestSingleScan();
                    ctx.getSource().sendFeedback(Component.literal("HugoSniper: Einzelscan angefordert. Oeffne /ah."));
                    return 1;
                }))
                .then(ClientCommandManager.literal("hud").executes(ctx -> {
                    eng.config.hudEnabled = !eng.config.hudEnabled;
                    eng.saveConfig();
                    ctx.getSource().sendFeedback(Component.literal("HugoSniper: HUD " + (eng.config.hudEnabled ? "an" : "aus")));
                    return 1;
                }))
                .then(ClientCommandManager.literal("status").executes(ctx -> {
                    ctx.getSource().sendFeedback(Component.literal("HugoSniper: " + eng.mode() + " - " + eng.modeDetail()
                            + " | Treffer: " + eng.lastFoundCount() + " | Letzter Scan: " + TimeFmt.hms(eng.lastScanEpoch())));
                    return 1;
                })));
    }
}
