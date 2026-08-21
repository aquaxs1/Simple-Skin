package de.simpleskin.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.simpleskin.data.SkinModel;
import de.simpleskin.data.SkinRepository;
import de.simpleskin.data.StoredSkin;
import net.minecraft.client.MinecraftClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Talks to the Minecraft services API, which is what actually makes a skin visible to other
 * players: the game server reads the skin from Mojang's session server when a client joins, so
 * only a skin that sits on the authenticated profile can ever reach anybody else.
 */
public final class MinecraftSkinUploadService {
    private static final URI SKINS_ENDPOINT = URI.create("https://api.minecraftservices.com/minecraft/profile/skins");
    private static final URI PROFILE_ENDPOINT = URI.create("https://api.minecraftservices.com/minecraft/profile");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    /** Guards against the hotkey path firing a stack of uploads and tripping Mojang's rate limit. */
    private final AtomicBoolean uploading = new AtomicBoolean();
    private volatile String profileDigest;

    /** Digest of the skin currently believed to be on the profile, or {@code null} if unknown. */
    public Optional<String> profileDigest() {
        return Optional.ofNullable(profileDigest);
    }

    public void forgetProfileDigest() {
        profileDigest = null;
    }

    public CompletableFuture<UploadResult> upload(StoredSkin skin, Path image) {
        String token = accessToken();
        if (token == null) {
            return CompletableFuture.completedFuture(UploadResult.failed(
                    "Equipped locally. No online session, so other players keep your old skin."));
        }
        byte[] png;
        try {
            png = Files.readAllBytes(image);
        } catch (IOException exception) {
            return CompletableFuture.completedFuture(UploadResult.failed(
                    "Equipped locally. The skin PNG could not be read for upload."));
        }
        String digest = SkinRepository.sha256(png);
        if (digest.equals(profileDigest)) {
            return CompletableFuture.completedFuture(new UploadResult(true, digest,
                    "Already active on your Minecraft profile."));
        }
        if (!uploading.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(UploadResult.failed(
                    "Equipped locally. Another skin upload is still running."));
        }

        HttpRequest request;
        try {
            String boundary = "SimpleSkin" + UUID.randomUUID().toString().replace("-", "");
            byte[] body = multipart(boundary, skin.model() == SkinModel.SLIM ? "slim" : "classic",
                    skin.name(), png);
            request = HttpRequest.newBuilder(SKINS_ENDPOINT)
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Simple-Skin/1.1")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
        } catch (IOException exception) {
            uploading.set(false);
            return CompletableFuture.completedFuture(UploadResult.failed(
                    "Equipped locally. The skin PNG could not be prepared for upload."));
        }

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, error) -> {
                    uploading.set(false);
                    if (error != null) {
                        return UploadResult.failed("Equipped locally. Mojang could not be reached.");
                    }
                    int status = response.statusCode();
                    if (status / 100 == 2) {
                        profileDigest = digest;
                        return new UploadResult(true, digest, "Uploaded to your Minecraft profile.");
                    }
                    return UploadResult.failed("Equipped locally. " + describe(status));
                });
    }

    /** Reads the skin URL Mojang currently serves for this account. */
    public CompletableFuture<Optional<URI>> fetchActiveSkinUrl() {
        String token = accessToken();
        if (token == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        HttpRequest request = HttpRequest.newBuilder(PROFILE_ENDPOINT)
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .header("User-Agent", "Simple-Skin/1.1")
                .GET()
                .build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, error) -> {
                    if (error != null || response.statusCode() / 100 != 2) {
                        return Optional.empty();
                    }
                    return parseActiveSkinUrl(response.body());
                });
    }

    static Optional<URI> parseActiveSkinUrl(String json) {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                return Optional.empty();
            }
            JsonElement skins = parsed.getAsJsonObject().get("skins");
            if (skins == null || !skins.isJsonArray()) {
                return Optional.empty();
            }
            JsonArray array = skins.getAsJsonArray();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject entry = element.getAsJsonObject();
                boolean active = entry.has("state") && "ACTIVE".equalsIgnoreCase(entry.get("state").getAsString());
                if (active && entry.has("url")) {
                    return Optional.of(URI.create(entry.get("url").getAsString()));
                }
            }
            return Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static String accessToken() {
        MinecraftClient client = MinecraftClient.getInstance();
        String token = client.getSession() == null ? null : client.getSession().getAccessToken();
        if (token == null || token.isBlank() || "0".equals(token) || "FabricMC".equals(token)) {
            return null;
        }
        return token;
    }

    private static String describe(int status) {
        return switch (status) {
            case 401 -> "Your Minecraft session expired; restart the game and sign in again.";
            case 403 -> "Mojang refused the upload for this account.";
            case 429 -> "Mojang is rate limiting skin changes; wait a moment and try again.";
            default -> "Profile upload returned HTTP " + status + ".";
        };
    }

    private static byte[] multipart(String boundary, String variant, String fileName, byte[] png) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        write(output, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\n" + variant + "\r\n");
        write(output, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + sanitize(fileName) + ".png\"\r\nContent-Type: image/png\r\n\r\n");
        output.write(png);
        write(output, "\r\n--" + boundary + "--\r\n");
        return output.toByteArray();
    }

    private static void write(ByteArrayOutputStream output, String text) throws IOException {
        output.write(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String sanitize(String value) {
        String cleaned = value.replaceAll("[^a-zA-Z0-9._-]", "_");
        return cleaned.isBlank() ? "skin" : cleaned;
    }

    public record UploadResult(boolean profileUpdated, String digest, String message) {
        static UploadResult failed(String message) {
            return new UploadResult(false, null, message);
        }
    }
}
