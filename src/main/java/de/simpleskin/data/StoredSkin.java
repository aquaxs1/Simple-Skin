package de.simpleskin.data;

import java.util.UUID;

public final class StoredSkin {
    private String id;
    private String name;
    private String imageFile;
    private String sourcePlayer;
    private String digest;
    private SkinModel model;
    private CapeChoice capeChoice = CapeChoice.KEEP;
    private String capeId;
    private boolean saved;
    private long createdAt;
    private long lastEquippedAt;
    private int keyCode = -1;

    public StoredSkin() {
    }

    public StoredSkin(String name, String imageFile, String sourcePlayer, SkinModel model, boolean saved) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.imageFile = imageFile;
        this.sourcePlayer = sourcePlayer;
        this.model = model;
        this.saved = saved;
        this.createdAt = System.currentTimeMillis();
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String imageFile() {
        return imageFile;
    }

    public String sourcePlayer() {
        return sourcePlayer;
    }

    /** SHA-256 of the stored PNG, used to keep the library free of duplicates. */
    public String digest() {
        return digest;
    }

    public void setDigest(String digest) {
        this.digest = digest;
    }

    /**
     * What this outfit does with the cape. Mojang only lets an account pick among the capes it
     * already owns, so a cape can be kept, hidden, or set to one the player owns — never uploaded.
     */
    public CapeChoice capeChoice() {
        return capeChoice == null ? CapeChoice.KEEP : capeChoice;
    }

    public void setCapeChoice(CapeChoice capeChoice) {
        this.capeChoice = capeChoice;
    }

    /** The owned cape id this outfit selects, when {@link #capeChoice()} is {@code SPECIFIC}. */
    public String capeId() {
        return capeId;
    }

    public void setCapeId(String capeId) {
        this.capeId = capeId;
    }

    public SkinModel model() {
        return model == null ? SkinModel.WIDE : model;
    }

    public void setModel(SkinModel model) {
        this.model = model;
    }

    public boolean saved() {
        return saved;
    }

    public void setSaved(boolean saved) {
        this.saved = saved;
    }

    public long createdAt() {
        return createdAt;
    }

    public long lastEquippedAt() {
        return lastEquippedAt;
    }

    public void setLastEquippedAt(long lastEquippedAt) {
        this.lastEquippedAt = lastEquippedAt;
    }

    public int keyCode() {
        return keyCode;
    }

    public void setKeyCode(int keyCode) {
        this.keyCode = keyCode;
    }
}
