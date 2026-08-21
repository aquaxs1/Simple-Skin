package de.simpleskin.screen;

import net.minecraft.client.Minecraft;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Opens the operating system's file chooser through LWJGL's tinyfd, which Minecraft already
 * ships (it is what the vanilla "open screenshot folder" style dialogs use).
 */
public final class SkinFilePicker {
    private static final Logger LOGGER = LoggerFactory.getLogger("Simple Skin");

    private SkinFilePicker() {
    }

    /**
     * Opens the chooser off the render thread and hands the result back on it.
     *
     * <p>The native dialog blocks until the user answers, and running it on the thread that owns
     * the GLFW window freezes the game (and deadlocks outright on macOS), so it gets its own
     * thread. {@code accept} receives {@code null} when the dialog was cancelled or unavailable.
     */
    public static void pickPngAsync(Consumer<Path> accept) {
        Minecraft client = Minecraft.getInstance();
        Thread thread = new Thread(() -> {
            Path chosen = pickPng();
            client.execute(() -> accept.accept(chosen));
        }, "Simple Skin file dialog");
        thread.setDaemon(true);
        thread.start();
    }

    /** Returns the chosen PNG, or {@code null} if the dialog was cancelled or unavailable. */
    private static Path pickPng() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.png"));
            filters.flip();
            String chosen = TinyFileDialogs.tinyfd_openFileDialog(
                    "Import a Minecraft skin", null, filters, "PNG images (*.png)", false);
            return chosen == null || chosen.isBlank() ? null : Path.of(chosen);
        } catch (RuntimeException | UnsatisfiedLinkError exception) {
            LOGGER.warn("The native file dialog is unavailable on this system", exception);
            return null;
        }
    }
}
