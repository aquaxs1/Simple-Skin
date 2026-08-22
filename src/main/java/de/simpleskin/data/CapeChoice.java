package de.simpleskin.data;

/** What an outfit does with the player's cape when it is equipped. */
public enum CapeChoice {
    /** Leave whatever cape the profile already has. */
    KEEP("Keep cape"),
    /** Hide the cape entirely. */
    NONE("No cape"),
    /** Wear one specific cape the account owns. */
    SPECIFIC("Pick cape");

    private final String label;

    CapeChoice(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public CapeChoice next() {
        CapeChoice[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
