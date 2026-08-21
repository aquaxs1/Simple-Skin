package de.simpleskin.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import de.simpleskin.data.SkinModel;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

public final class PlayerSkinDownloadService {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public CompletableFuture<DownloadedSkin> download(GameProfile profile) {
        try {
            ResolvedTexture texture = resolve(profile);
            HttpRequest request = HttpRequest.newBuilder(texture.uri())
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Simple-Skin/1.0")
                    .GET()
                    .build();
            return http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                    .thenApply(response -> {
                        if (response.statusCode() / 100 != 2) {
                            throw new IllegalStateException("Skin download returned HTTP " + response.statusCode());
                        }
                        return new DownloadedSkin(response.body(), profile.name(), texture.model());
                    });
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private static ResolvedTexture resolve(GameProfile profile) throws IOException {
        Property property = profile.properties().get("textures").stream().findFirst()
                .orElseThrow(() -> new IOException("No downloadable skin was sent for " + profile.name()));
        String decoded = new String(Base64.getDecoder().decode(property.value()), java.nio.charset.StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(decoded).getAsJsonObject();
        JsonObject skin = root.getAsJsonObject("textures").getAsJsonObject("SKIN");
        if (skin == null || !skin.has("url")) {
            throw new IOException("No skin texture is available for " + profile.name());
        }
        SkinModel model = SkinModel.WIDE;
        if (skin.has("metadata") && skin.getAsJsonObject("metadata").has("model")) {
            model = SkinModel.fromMetadata(skin.getAsJsonObject("metadata").get("model").getAsString());
        }
        URI uri = URI.create(skin.get("url").getAsString());
        if (!"textures.minecraft.net".equalsIgnoreCase(uri.getHost())
                || (!("http".equalsIgnoreCase(uri.getScheme())) && !("https".equalsIgnoreCase(uri.getScheme())))) {
            throw new IOException("Refusing a skin URL outside textures.minecraft.net");
        }
        if ("http".equalsIgnoreCase(uri.getScheme())) {
            uri = URI.create("https://textures.minecraft.net" + uri.getRawPath());
        }
        return new ResolvedTexture(uri, model);
    }

    public record DownloadedSkin(byte[] png, String playerName, SkinModel model) {
    }

    private record ResolvedTexture(URI uri, SkinModel model) {
    }
}
