package de.simpleskin.skin;

import de.simpleskin.data.SimpleSkinConfig;
import de.simpleskin.data.SkinRepository;
import de.simpleskin.data.StoredSkin;
import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Makes an equipped skin visible to other players.
 *
 * <p>A client-side mod cannot tell anybody else's game what to render: every other client asks
 * the server, and the server asks Mojang's session server once, when a player joins. So the only
 * route that reaches other people is
 * <ol>
 *   <li>upload the PNG to the signed-in Minecraft profile,</li>
 *   <li>wait until Mojang actually serves it (the CDN needs a moment),</li>
 *   <li>rejoin the server so it re-reads the profile and re-broadcasts it to everyone.</li>
 * </ol>
 * Steps 2 and 3 are what the original mod was missing: it uploaded and then told the player that
 * "other clients may keep Mojang's cached texture", which in practice meant the skin never
 * changed for anyone else during the session.
 */
public final class SkinVisibilityService {
    private static final Logger LOGGER = LoggerFactory.getLogger("Simple Skin");
    private static final int VERIFY_ATTEMPTS = 6;
    private static final Duration VERIFY_DELAY = Duration.ofSeconds(2);

    private final MinecraftSkinUploadService uploads;
    private final PlayerSkinDownloadService downloads;
    private final SimpleSkinConfig config;

    /** Set once the profile carries a skin that the current server connection has not seen yet. */
    private volatile boolean rejoinPending;

    public SkinVisibilityService(MinecraftSkinUploadService uploads, PlayerSkinDownloadService downloads,
            SimpleSkinConfig config) {
        this.uploads = uploads;
        this.downloads = downloads;
        this.config = config;
    }

    public boolean rejoinPending() {
        return rejoinPending;
    }

    public void clearRejoinPending() {
        rejoinPending = false;
    }

    /**
     * Pushes {@code skin} out to everyone else, reporting progress through {@code status}.
     * The callback is always invoked on the client thread.
     */
    public void publish(StoredSkin skin, Path png, Consumer<String> status) {
        MinecraftClient client = MinecraftClient.getInstance();
        SimpleSkinConfig.Visibility visibility = config.visibility();
        if (visibility == SimpleSkinConfig.Visibility.LOCAL_ONLY) {
            status.accept("Equipped for you only (visibility: " + visibility.label() + ").");
            return;
        }

        uploads.upload(skin, png).thenAccept(result -> client.execute(() -> {
            status.accept(result.message());
            if (!result.profileUpdated()) {
                return;
            }
            status.accept("Uploaded. Waiting for Mojang to serve the new skin...");
            verify(skin, png).thenAccept(served -> client.execute(() -> {
                if (!served) {
                    status.accept("Uploaded, but Mojang is still serving the old skin. Try rejoining later.");
                    return;
                }
                rejoinPending = true;
                onServed(skin, visibility, status);
            }));
        }));
    }

    private void onServed(StoredSkin skin, SimpleSkinConfig.Visibility visibility, Consumer<String> status) {
        if (!ServerReconnector.canRejoin()) {
            rejoinPending = false;
            status.accept("Your profile now shows " + skin.name() + ". Others see it next time they load you.");
            return;
        }
        if (visibility == SimpleSkinConfig.Visibility.PROFILE_AND_RECONNECT) {
            status.accept("Rejoining the server so everyone sees " + skin.name() + "...");
            rejoinNow(status);
            return;
        }
        status.accept("Profile updated. Press Rejoin so other players see " + skin.name() + ".");
    }

    /**
     * Rejoins the server so it re-reads the profile and re-broadcasts the skin to everybody.
     *
     * <p>The local override is dropped first: once the profile really carries the skin, the client
     * should render the very texture other players are about to receive, so what the player sees
     * is what everyone else sees.
     */
    public void rejoinNow(Consumer<String> status) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ServerReconnector.canRejoin()) {
            status.accept("Rejoining only works on a multiplayer server.");
            return;
        }
        if (client.player != null) {
            SkinOverrideManager.clear(client.player.getUuid());
        }
        rejoinPending = false;
        ServerReconnector.rejoin(status);
    }

    /**
     * Polls the profile until Mojang serves a skin whose bytes match the one just uploaded.
     * Mojang's CDN can lag behind the upload by a few seconds, and rejoining before it catches up
     * would hand the server the previous texture.
     */
    private CompletableFuture<Boolean> verify(StoredSkin skin, Path png) {
        String expected;
        try {
            expected = SkinRepository.sha256(Files.readAllBytes(png));
        } catch (Exception exception) {
            LOGGER.warn("Could not fingerprint the uploaded skin for verification", exception);
            return CompletableFuture.completedFuture(false);
        }
        return attempt(expected, VERIFY_ATTEMPTS);
    }

    private CompletableFuture<Boolean> attempt(String expected, int remaining) {
        if (remaining <= 0) {
            return CompletableFuture.completedFuture(false);
        }
        Executor delayed = CompletableFuture.delayedExecutor(VERIFY_DELAY.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        return CompletableFuture.supplyAsync(() -> null, delayed)
                .thenCompose(ignored -> uploads.fetchActiveSkinUrl())
                .thenCompose(this::fetchDigest)
                .thenCompose(digest -> {
                    if (digest.isPresent() && digest.get().equals(expected)) {
                        return CompletableFuture.completedFuture(true);
                    }
                    return attempt(expected, remaining - 1);
                })
                .exceptionallyCompose(error -> {
                    LOGGER.debug("Skin verification attempt failed", error);
                    return attempt(expected, remaining - 1);
                });
    }

    private CompletableFuture<Optional<String>> fetchDigest(Optional<URI> uri) {
        if (uri.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return downloads.fetch(uri.get())
                .thenApply(png -> Optional.of(SkinRepository.sha256(png)))
                .exceptionally(error -> Optional.empty());
    }
}
