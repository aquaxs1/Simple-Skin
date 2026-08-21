package de.simpleskin.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Persisted client settings, kept next to the skin library in {@code config/simple-skin}. */
public final class SimpleSkinConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("Simple Skin");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private Values values = new Values();

    public SimpleSkinConfig() {
        this.file = FabricLoader.getInstance().getConfigDir().resolve("simple-skin").resolve("config.json");
    }

    public synchronized void load() {
        try {
            if (Files.exists(file)) {
                Values loaded = GSON.fromJson(Files.readString(file), Values.class);
                if (loaded != null) {
                    values = loaded;
                }
            }
            if (values.visibility == null) {
                values.visibility = Visibility.PROFILE;
            }
        } catch (Exception exception) {
            values = new Values();
            LOGGER.error("Simple Skin could not read its config; using defaults", exception);
        }
    }

    public synchronized Visibility visibility() {
        return values.visibility == null ? Visibility.PROFILE : values.visibility;
    }

    public synchronized void setVisibility(Visibility visibility) {
        values.visibility = visibility;
        save();
    }

    /** Whether a hotkey press should also push the skin to the Mojang profile. */
    public synchronized boolean uploadOnHotkey() {
        return values.uploadOnHotkey;
    }

    public synchronized void setUploadOnHotkey(boolean uploadOnHotkey) {
        values.uploadOnHotkey = uploadOnHotkey;
        save();
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling("config.json.tmp");
            Files.writeString(temporary, GSON.toJson(values));
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            LOGGER.error("Simple Skin could not save its config", exception);
        }
    }

    /** How far a skin change should travel. */
    public enum Visibility {
        /** Only this client renders the new skin. Nothing leaves the machine. */
        LOCAL_ONLY("Only me", "Changes the skin on this client only."),
        /** Push to the Mojang profile; other players pick it up when they next see you fresh. */
        PROFILE("Upload to profile", "Uploads to your Minecraft profile so others get it on reconnect."),
        /** Push to the profile and rejoin the server so everyone sees it right away. */
        PROFILE_AND_RECONNECT("Upload + rejoin", "Uploads, then rejoins the server so others see it now.");

        private final String label;
        private final String description;

        Visibility(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String label() {
            return label;
        }

        public String description() {
            return description;
        }

        public Visibility next() {
            Visibility[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    private static final class Values {
        private Visibility visibility = Visibility.PROFILE;
        private boolean uploadOnHotkey = true;
    }
}
