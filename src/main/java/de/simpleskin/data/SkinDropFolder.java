package de.simpleskin.data;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Imports PNGs dropped into {@code config/simple-skin/import} without the player opening a file
 * dialog: put a skin in the folder and it turns up in the library.
 */
public final class SkinDropFolder {
    private static final Logger LOGGER = LoggerFactory.getLogger("Simple Skin");
    /** Scans run on a worker thread; this is how often one is started, in client ticks. */
    private static final int SCAN_INTERVAL_TICKS = 60;

    private final SkinRepository repository;
    private final Path folder;
    private final AtomicBoolean scanning = new AtomicBoolean();
    /** Size seen on the previous scan, so a file still being copied is left alone. */
    private final Map<String, Long> pendingSizes = new HashMap<>();
    private int timer;

    public SkinDropFolder(SkinRepository repository) {
        this.repository = repository;
        this.folder = FabricLoader.getInstance().getConfigDir().resolve("simple-skin").resolve("import");
    }

    public Path folder() {
        return folder;
    }

    /** Creates the folder so it is discoverable before anything has been dropped into it. */
    public void prepare() {
        try {
            Files.createDirectories(folder);
        } catch (IOException exception) {
            LOGGER.warn("Could not create the Simple Skin import folder", exception);
        }
    }

    /**
     * Called once per client tick. Reports each imported skin through {@code report}, which is
     * invoked on the thread that runs {@code onImported}.
     */
    public void tick(Consumer<List<StoredSkin>> onImported) {
        if (++timer < SCAN_INTERVAL_TICKS) {
            return;
        }
        timer = 0;
        if (!scanning.compareAndSet(false, true)) {
            return;
        }
        Thread worker = new Thread(() -> {
            List<StoredSkin> imported = List.of();
            try {
                imported = scan();
            } catch (RuntimeException exception) {
                LOGGER.warn("Simple Skin could not scan its import folder", exception);
            } finally {
                scanning.set(false);
            }
            if (!imported.isEmpty()) {
                onImported.accept(imported);
            }
        }, "Simple Skin drop folder");
        worker.setDaemon(true);
        worker.start();
    }

    private List<StoredSkin> scan() {
        List<StoredSkin> imported = new ArrayList<>();
        if (!Files.isDirectory(folder)) {
            return imported;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder)) {
            for (Path entry : entries) {
                if (!Files.isRegularFile(entry)
                        || !entry.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
                    continue;
                }
                importOne(entry).ifPresent(imported::add);
            }
        } catch (IOException exception) {
            LOGGER.warn("Simple Skin could not read its import folder", exception);
        }
        return imported;
    }

    private java.util.Optional<StoredSkin> importOne(Path entry) {
        String key = entry.getFileName().toString();
        try {
            long size = Files.size(entry);
            // A file that is still being copied grows between scans; wait until it settles.
            Long previous = pendingSizes.put(key, size);
            if (previous == null || previous != size) {
                return java.util.Optional.empty();
            }
            pendingSizes.remove(key);
            StoredSkin skin = repository.importFile(entry, true);
            Files.deleteIfExists(entry);
            return java.util.Optional.of(skin);
        } catch (IOException exception) {
            pendingSizes.remove(key);
            // A rejected file is renamed so it is not retried on every scan for the rest of the run.
            LOGGER.warn("Simple Skin rejected the dropped file {}: {}", key, exception.getMessage());
            try {
                Files.move(entry, entry.resolveSibling(key + ".rejected"));
            } catch (IOException moveError) {
                LOGGER.warn("Could not set aside the rejected file {}", key, moveError);
            }
            return java.util.Optional.empty();
        }
    }
}
