package de.simpleskin.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
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
            if (Files.exists(metadata)) {
                Library loaded = GSON.fromJson(Files.readString(metadata), Library.class);
                library = loaded == null ? new Library() : loaded;
            }
            normalize();
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

    public synchronized StoredSkin add(byte[] png, String name, String sourcePlayer, SkinModel model, boolean saved)
            throws IOException {
        validatePng(png);
        String fileName = java.util.UUID.randomUUID() + ".png";
        Files.createDirectories(images);
        Files.write(images.resolve(fileName), png);
        StoredSkin skin = new StoredSkin(cleanName(name), fileName, sourcePlayer, model, saved);
        library.skins.add(skin);
        save();
        return skin;
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

    public synchronized void markEquipped(StoredSkin skin) {
        skin.setLastEquippedAt(System.currentTimeMillis());
        library.history.remove(skin.id());
        library.history.add(0, skin.id());
        if (library.history.size() > HISTORY_LIMIT) {
            library.history.subList(HISTORY_LIMIT, library.history.size()).clear();
        }
        saveUnchecked();
    }

    public synchronized void update(StoredSkin skin) {
        skin.setName(cleanName(skin.name()));
        saveUnchecked();
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
        Files.copy(imagePath(skin), target, StandardCopyOption.COPY_ATTRIBUTES);
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

    private void saveUnchecked() {
        try {
            save();
        } catch (IOException exception) {
            throw new IllegalStateException("Simple Skin could not save its library", exception);
        }
    }

    private void normalize() {
        if (library.skins == null) {
            library.skins = new ArrayList<>();
        }
        if (library.history == null) {
            library.history = new ArrayList<>();
        }
        library.skins.removeIf(skin -> skin == null || skin.id() == null || !Files.exists(imagePath(skin)));
        Set<String> valid = new LinkedHashSet<>();
        for (String id : library.history) {
            if (find(id).isPresent()) {
                valid.add(id);
            }
        }
        library.history = new ArrayList<>(valid);
    }

    private static String cleanName(String name) {
        String cleaned = name == null ? "Skin" : name.strip();
        if (cleaned.isEmpty()) {
            return "Skin";
        }
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }

    private static void validatePng(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 24 || bytes[0] != (byte) 0x89 || bytes[1] != 0x50
                || bytes[2] != 0x4E || bytes[3] != 0x47) {
            throw new IOException("The downloaded skin is not a PNG image");
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
