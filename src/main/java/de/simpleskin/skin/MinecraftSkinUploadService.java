package de.simpleskin.skin;

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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class MinecraftSkinUploadService {
    private static final URI ENDPOINT = URI.create("https://api.minecraftservices.com/minecraft/profile/skins");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public CompletableFuture<UploadResult> upload(StoredSkin skin, Path image) {
        String token = MinecraftClient.getInstance().getSession().getAccessToken();
        if (token == null || token.isBlank() || "0".equals(token)) {
            return CompletableFuture.completedFuture(new UploadResult(false, "Local preview equipped; no online session token was available."));
        }
        try {
            String boundary = "SimpleSkin" + UUID.randomUUID().toString().replace("-", "");
            byte[] body = multipart(boundary, skin.model() == de.simpleskin.data.SkinModel.SLIM ? "slim" : "classic",
                    skin.name(), Files.readAllBytes(image));
            HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("User-Agent", "Simple-Skin/1.0")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> response.statusCode() / 100 == 2
                            ? new UploadResult(true, "Skin equipped on your Minecraft profile.")
                            : new UploadResult(false, "Local preview equipped; profile upload returned HTTP " + response.statusCode() + "."))
                    .exceptionally(error -> new UploadResult(false, "Local preview equipped; profile upload failed."));
        } catch (IOException exception) {
            return CompletableFuture.completedFuture(new UploadResult(false, "Local preview equipped; the PNG could not be uploaded."));
        }
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
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public record UploadResult(boolean profileUpdated, String message) {
    }
}
