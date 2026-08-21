package de.simpleskin.skin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import de.simpleskin.data.SkinModel;
import de.simpleskin.data.SkinRepository;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

public final class PlayerSkinDownloadService {
    private static final String TEXTURE_HOST = "textures.minecraft.net";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public CompletableFuture<DownloadedSkin> download(GameProfile profile) {
        try {
            ResolvedTexture texture = resolve(profile);
            return fetch(texture.uri()).thenApply(png -> new DownloadedSkin(png, profile.name(), texture.model()));
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    /** Downloads a skin PNG from Mojang's texture CDN, refusing anything that is not a skin. */
    public CompletableFuture<byte[]> fetch(URI uri) {
        try {
            URI safe = requireTextureUri(uri);
            HttpRequest request = HttpRequest.newBuilder(safe)
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Simple-Skin/1.1")
                    .GET()
                    .build();
            return http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                    .thenApply(response -> {
                        if (response.statusCode() / 100 != 2) {
                            throw new IllegalStateException("Skin download returned HTTP " + response.statusCode());
                        }
                        byte[] body = response.body();
                        if (body.length > SkinRepository.MAX_PNG_BYTES) {
                            throw new IllegalStateException("Skin download was too large to be a skin");
                        }
                        return body;
                    });
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private static ResolvedTexture resolve(GameProfile profile) throws IOException {
        Property property = profile.properties().get("textures").stream().findFirst()
                .orElseThrow(() -> new IOException("No downloadable skin was sent for " + profile.name()));
        String decoded = new String(Base64.getDecoder().decode(property.value()), StandardCharsets.UTF_8);
        JsonElement parsed = JsonParser.parseString(decoded);
        if (!parsed.isJsonObject()) {
            throw new IOException("The skin data for " + profile.name() + " could not be read");
        }
        JsonObject textures = parsed.getAsJsonObject().getAsJsonObject("textures");
        if (textures == null) {
            throw new IOException("No skin texture is available for " + profile.name());
        }
        JsonObject skin = textures.getAsJsonObject("SKIN");
        if (skin == null || !skin.has("url")) {
            throw new IOException("No skin texture is available for " + profile.name());
        }
        SkinModel model = SkinModel.WIDE;
        JsonObject metadata = skin.getAsJsonObject("metadata");
        if (metadata != null && metadata.has("model")) {
            model = SkinModel.fromMetadata(metadata.get("model").getAsString());
        }
        return new ResolvedTexture(requireTextureUri(URI.create(skin.get("url").getAsString())), model);
    }

    /**
     * Pins skin downloads to Mojang's texture host over HTTPS. A texture property is attacker
     * controlled on a hostile server, so an arbitrary URL here would be a request forgery.
     */
    private static URI requireTextureUri(URI uri) throws IOException {
        String scheme = uri.getScheme();
        if (!TEXTURE_HOST.equalsIgnoreCase(uri.getHost())
                || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IOException("Refusing a skin URL outside " + TEXTURE_HOST);
        }
        if ("http".equalsIgnoreCase(scheme)) {
            return URI.create("https://" + TEXTURE_HOST + uri.getRawPath());
        }
        return uri;
    }

    public record DownloadedSkin(byte[] png, String playerName, SkinModel model) {
    }

    private record ResolvedTexture(URI uri, SkinModel model) {
    }
}
