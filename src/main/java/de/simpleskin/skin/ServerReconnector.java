package de.simpleskin.skin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

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
        MinecraftClient client = MinecraftClient.getInstance();
        return client.getCurrentServerEntry() != null
                && !client.isInSingleplayer()
                && !(client.currentScreen instanceof ConnectScreen);
    }

    public static void rejoin(Consumer<String> status) {
        MinecraftClient client = MinecraftClient.getInstance();
        // ConnectScreen.connect disconnects internally, so the entry has to be captured first.
        ServerInfo info = client.getCurrentServerEntry();
        if (info == null || client.isInSingleplayer()) {
            status.accept("Rejoining only works on a multiplayer server.");
            return;
        }
        if (client.currentScreen instanceof ConnectScreen) {
            return;
        }
        ServerAddress address = ServerAddress.parse(info.address);
        Screen parent = new MultiplayerScreen(new TitleScreen());
        ConnectScreen.connect(parent, client, address, info, false, null);
    }
}
