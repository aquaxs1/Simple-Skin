package de.simpleskin.skin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

import java.util.function.Consumer;

/**
 * Rejoins the current server.
 *
 * <p>A game server reads a player's skin from Mojang exactly once, during login. Rejoining is
 * therefore the only client-side way to get a freshly uploaded skin in front of the other players
 * who are already online, and it is the same code path the multiplayer list uses to connect.
 */
public final class ServerReconnector {
    private ServerReconnector() {
    }

    /** True when there is a real server connection that can be re-established. */
    public static boolean canRejoin() {
        Minecraft client = Minecraft.getInstance();
        return client.getCurrentServer() != null
                && !client.isLocalServer()
                && !(client.gui.screen() instanceof ConnectScreen);
    }

    public static void rejoin(Consumer<String> status) {
        Minecraft client = Minecraft.getInstance();
        // startConnecting disconnects internally, so the entry has to be captured first.
        ServerData server = client.getCurrentServer();
        if (server == null || client.isLocalServer()) {
            status.accept("Rejoining only works on a multiplayer server.");
            return;
        }
        if (client.gui.screen() instanceof ConnectScreen) {
            return;
        }
        ServerAddress address = ServerAddress.parseString(server.ip);
        Screen parent = new JoinMultiplayerScreen(new TitleScreen());
        ConnectScreen.startConnecting(parent, client, address, server, false, null);
    }
}
