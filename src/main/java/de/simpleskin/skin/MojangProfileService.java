package de.simpleskin.skin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Looks a player up by name through Mojang's public API, so a skin can be taken from anybody, not
 * only from the players currently on the server.
 *
 * <p>NameMC is deliberately not used: it publishes no API and its terms forbid scraping. Mojang's
 * own endpoints return exactly the same data — the profile UUID and its texture property.
 */
public final class MojangProfileService {
    private static final URI NAME_ENDPOINT = URI.create("https://api.mojang.com/users/profiles/minecraft/");
    private static final URI PROFILE_ENDPOINT =
            URI.create("https://sessionserver.mojang.com/session/minecraft/profile/");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Resolves a player name to a profile carrying its texture property. */
    public CompletableFuture<GameProfile> lookup(String name) {
        String trimmed = name == null ? "" : name.strip();
        if (!isValidName(trimmed)) {
            return CompletableFuture.failedFuture(
                    new IOException("\"" + trimmed + "\" is not a valid Minecraft name"));
        }
        return get(NAME_ENDPOINT.resolve(NAME_ENDPOINT.getPath()
                        + URLEncoder.encode(trimmed, StandardCharsets.UTF_8)), trimmed)
                .thenApply(body -> parseId(body, trimmed))
                .thenCompose(id -> get(PROFILE_ENDPOINT.resolve(PROFILE_ENDPOINT.getPath()
                                + id.toString().replace("-", "")), trimmed)
                        .thenApply(body -> parseProfile(body, id, trimmed)));
    }

    private CompletableFuture<String> get(URI uri, String name) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("User-Agent", "Simple-Skin/2.0")
                .GET()
                .build();
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    int status = response.statusCode();
                    if (status == 204 || status == 404) {
                        throw new IllegalStateException("No Minecraft account is called \"" + name + "\"");
                    }
                    if (status == 429) {
                        throw new IllegalStateException("Mojang is rate limiting lookups; try again shortly");
                    }
                    if (status / 100 != 2) {
                        throw new IllegalStateException("Mojang returned HTTP " + status);
                    }
                    return response.body();
                });
    }

    static UUID parseId(String body, String name) {
        JsonObject root = asObject(body, name);
        JsonElement id = root.get("id");
        if (id == null || !id.isJsonPrimitive()) {
            throw new IllegalStateException("Mojang sent no profile id for \"" + name + "\"");
        }
        return toUuid(id.getAsString(), name);
    }

    static GameProfile parseProfile(String body, UUID id, String name) {
        JsonObject root = asObject(body, name);
        String resolvedName = root.has("name") ? root.get("name").getAsString() : name;
        GameProfile profile = new GameProfile(id, resolvedName);
        JsonElement properties = root.get("properties");
        if (properties != null && properties.isJsonArray()) {
            for (JsonElement element : properties.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject property = element.getAsJsonObject();
                if (property.has("name") && "textures".equals(property.get("name").getAsString())
                        && property.has("value")) {
                    String signature = property.has("signature") ? property.get("signature").getAsString() : null;
                    profile.properties().put("textures",
                            new Property("textures", property.get("value").getAsString(), signature));
                }
            }
        }
        if (profile.properties().get("textures").isEmpty()) {
            throw new IllegalStateException("\"" + resolvedName + "\" has no skin on their profile");
        }
        return profile;
    }

    private static JsonObject asObject(String body, String name) {
        JsonElement parsed = JsonParser.parseString(body);
        if (!parsed.isJsonObject()) {
            throw new IllegalStateException("Mojang sent an unreadable answer for \"" + name + "\"");
        }
        return parsed.getAsJsonObject();
    }

    /** Turns Mojang's dash-less profile id into a UUID. */
    public static UUID toUuid(String raw, String name) {
        String hex = raw.replace("-", "");
        if (hex.length() != 32) {
            throw new IllegalStateException("Mojang sent a malformed profile id for \"" + name + "\"");
        }
        return UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16)
                + "-" + hex.substring(16, 20) + "-" + hex.substring(20));
    }

    /** Mirrors Mojang's own rule: 3-16 characters of letters, digits and underscore. */
    public static boolean isValidName(String name) {
        if (name.length() < 3 || name.length() > 16) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (int index = 0; index < lower.length(); index++) {
            char character = lower.charAt(index);
            boolean allowed = (character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')
                    || character == '_';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }
}
