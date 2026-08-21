package de.simpleskin.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SkinRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger("Simple Skin");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int HISTORY_LIMIT = 40;
    /** Skins are 64x64 PNGs; anything past this is not a skin and is refused before it is buffered. */
    public static final int MAX_PNG_BYTES = 512 * 1024;

    private final Path root;
    private final Path images;
    private final Path metadata;
    private Library library = new Library();

    public SkinRepository() {
        this.root = FabricLoader.getInstance().getConfigDir().resolve("simple-skin");
        this.images = root.resolve("skins");
        this.metadata = root.resolve("skins.json");
    }

    public synchronized void load() {
        try {
            Files.createDirectories(images);
            boolean hadMetadata = Files.exists(metadata);
            if (hadMetadata) {
                Library loaded = GSON.fromJson(Files.readString(metadata), Library.class);
                library = loaded == null ? new Library() : loaded;
            }
            normalize(hadMetadata);
        } catch (Exception exception) {
            library = new Library();
            LOGGER.error("Simple Skin could not load its library; starting with an empty one", exception);
            if (Files.exists(metadata)) {
                try {
                    Files.move(metadata, metadata.resolveSibling("skins.json.broken-" + System.currentTimeMillis()),
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException moveError) {
                    LOGGER.warn("Could not preserve the damaged Simple Skin metadata file", moveError);
                }
            }
        }
    }

    /**
     * Stores a skin PNG. Identical images are stored once: re-adding the same bytes returns the
     * existing entry instead of filling the library with duplicates every time a skin is stolen.
     */
    public synchronized StoredSkin add(byte[] png, String name, String sourcePlayer, SkinModel model, boolean saved)
            throws IOException {
        validatePng(png);
        String digest = sha256(png);
        Optional<StoredSkin> existing = library.skins.stream()
                .filter(skin -> digest.equals(skin.digest()))
                .findFirst();
        if (existing.isPresent()) {
            StoredSkin skin = existing.get();
            if (saved && !skin.saved()) {
                skin.setSaved(true);
            }
            // The stored model is left alone: it may have been switched to slim by hand, and
            // re-stealing the same image must not silently undo that.
            save();
            return skin;
        }

        String fileName = java.util.UUID.randomUUID() + ".png";
        Files.createDirectories(images);
        Files.write(images.resolve(fileName), png);
        StoredSkin skin = new StoredSkin(cleanName(name), fileName, sourcePlayer, model, saved);
        skin.setDigest(digest);
        library.skins.add(skin);
        save();
        return skin;
    }

    /** Imports a PNG that the player picked from disk. */
    public synchronized StoredSkin importFile(Path source, boolean saved) throws IOException {
        if (Files.size(source) > MAX_PNG_BYTES) {
            throw new IOException("That file is too large to be a Minecraft skin");
        }
        String name = source.getFileName().toString();
        if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
            name = name.substring(0, name.length() - 4);
        }
        return add(Files.readAllBytes(source), name, null, SkinModel.WIDE, saved);
    }

    public synchronized Optional<StoredSkin> find(String id) {
        return library.skins.stream().filter(skin -> skin.id().equals(id)).findFirst();
    }

    public synchronized List<StoredSkin> savedSkins() {
        return library.skins.stream()
                .filter(StoredSkin::saved)
                .sorted(Comparator.comparingLong(StoredSkin::createdAt).reversed())
                .toList();
    }

    public synchronized List<StoredSkin> history() {
        List<StoredSkin> result = new ArrayList<>();
        for (String id : library.history) {
            find(id).ifPresent(result::add);
        }
        return result;
    }

    public synchronized List<StoredSkin> all() {
        return List.copyOf(library.skins);
    }

    /** Returns the skin that owns {@code keyCode}, ignoring {@code exclude}. */
    public synchronized Optional<StoredSkin> findByKeyCode(int keyCode, StoredSkin exclude) {
        if (keyCode < 0) {
            return Optional.empty();
        }
        return library.skins.stream()
                .filter(skin -> skin != exclude && skin.keyCode() == keyCode)
                .findFirst();
    }

    /** Assigns a hotkey, releasing it from whichever skin held it before. */
    public synchronized void assignKeyCode(StoredSkin skin, int keyCode) {
        if (keyCode >= 0) {
            findByKeyCode(keyCode, skin).ifPresent(other -> other.setKeyCode(-1));
        }
        skin.setKeyCode(keyCode);
        saveQuietly();
    }

    public synchronized void markEquipped(StoredSkin skin) {
        skin.setLastEquippedAt(System.currentTimeMillis());
        library.history.remove(skin.id());
        library.history.add(0, skin.id());
        if (library.history.size() > HISTORY_LIMIT) {
            library.history.subList(HISTORY_LIMIT, library.history.size()).clear();
        }
        saveQuietly();
    }

    public synchronized void update(StoredSkin skin) {
        skin.setName(cleanName(skin.name()));
        saveQuietly();
    }

    /** Removes a skin from the library and deletes its PNG. */
    public synchronized void delete(StoredSkin skin) {
        Path image = imagePath(skin);
        library.skins.removeIf(candidate -> candidate.id().equals(skin.id()));
        library.history.remove(skin.id());
        try {
            Files.deleteIfExists(image);
        } catch (IOException exception) {
            LOGGER.warn("Could not delete the skin PNG {}", image, exception);
        }
        saveQuietly();
    }

    public synchronized Path imagePath(StoredSkin skin) {
        return images.resolve(skin.imageFile());
    }

    public synchronized Path export(StoredSkin skin) throws IOException {
        Path exportDir = FabricLoader.getInstance().getGameDir().resolve("simple-skin").resolve("exports");
        Files.createDirectories(exportDir);
        String safeName = cleanName(skin.name()).replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safeName.isBlank()) {
            safeName = "skin";
        }
        Path target = uniqueTarget(exportDir, safeName + ".png");
        Files.copy(imagePath(skin), target);
        return target;
    }

    private void save() throws IOException {
        Files.createDirectories(root);
        Path temporary = metadata.resolveSibling("skins.json.tmp");
        Files.writeString(temporary, GSON.toJson(library));
        try {
            Files.move(temporary, metadata, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            Files.move(temporary, metadata, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Persists on a best-effort basis. A failing disk must not take the game down from a click
     * handler or a client tick, so the error is logged and the in-memory library stays usable.
     */
    private void saveQuietly() {
        try {
            save();
        } catch (IOException exception) {
            LOGGER.error("Simple Skin could not save its library", exception);
        }
    }

    /**
     * @param pruneOrphans only when {@code skins.json} was actually read. Without that guard a
     *     missing or freshly reset metadata file would make every PNG on disk look unreferenced
     *     and delete the player's whole collection.
     */
    private void normalize(boolean pruneOrphans) {
        if (library.skins == null) {
            library.skins = new ArrayList<>();
        }
        if (library.history == null) {
            library.history = new ArrayList<>();
        }
        library.skins.removeIf(skin -> skin == null || skin.id() == null || skin.imageFile() == null
                || !Files.exists(imagePath(skin)));

        Set<String> referenced = new HashSet<>();
        for (StoredSkin skin : library.skins) {
            referenced.add(skin.imageFile());
            if (skin.digest() == null) {
                try {
                    skin.setDigest(sha256(Files.readAllBytes(imagePath(skin))));
                } catch (IOException exception) {
                    LOGGER.warn("Could not fingerprint the stored skin {}", skin.name(), exception);
                }
            }
        }
        if (pruneOrphans) {
            deleteOrphanImages(referenced);
        }

        Set<String> valid = new LinkedHashSet<>();
        for (String id : library.history) {
            if (find(id).isPresent()) {
                valid.add(id);
            }
        }
        library.history = new ArrayList<>(valid);

        Set<Integer> usedKeys = new HashSet<>();
        for (StoredSkin skin : library.skins) {
            if (skin.keyCode() >= 0 && !usedKeys.add(skin.keyCode())) {
                skin.setKeyCode(-1);
            }
        }
    }

    /** Deletes PNGs that no library entry points at any more. */
    private void deleteOrphanImages(Set<String> referenced) {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(images, "*.png")) {
            for (Path entry : entries) {
                if (!referenced.contains(entry.getFileName().toString())) {
                    Files.deleteIfExists(entry);
                }
            }
        } catch (IOException | UncheckedIOException exception) {
            LOGGER.warn("Could not clean up unused Simple Skin images", exception);
        }
    }

    private static String cleanName(String name) {
        String cleaned = name == null ? "Skin" : name.strip();
        if (cleaned.isEmpty()) {
            return "Skin";
        }
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", exception);
        }
    }

    public static void validatePng(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 24 || bytes[0] != (byte) 0x89 || bytes[1] != 0x50
                || bytes[2] != 0x4E || bytes[3] != 0x47) {
            throw new IOException("The downloaded skin is not a PNG image");
        }
        if (bytes.length > MAX_PNG_BYTES) {
            throw new IOException("The downloaded skin is too large to be a Minecraft skin");
        }
        String header = new String(bytes, 12, 4, StandardCharsets.US_ASCII);
        if (!"IHDR".equals(header)) {
            throw new IOException("The downloaded skin has no PNG header chunk");
        }
        int width = readInt(bytes, 16);
        int height = readInt(bytes, 20);
        if (width != 64 || (height != 64 && height != 32)) {
            throw new IOException("Minecraft skins must be 64x64 or legacy 64x32 PNG files");
        }
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
    }

    private static Path uniqueTarget(Path directory, String fileName) {
        Path candidate = directory.resolve(fileName);
        String base = fileName.substring(0, fileName.length() - 4);
        int suffix = 2;
        while (Files.exists(candidate)) {
            candidate = directory.resolve(base + "-" + suffix++ + ".png");
        }
        return candidate;
    }

    private static final class Library {
        private List<StoredSkin> skins = new ArrayList<>();
        private List<String> history = new ArrayList<>();
    }
}
