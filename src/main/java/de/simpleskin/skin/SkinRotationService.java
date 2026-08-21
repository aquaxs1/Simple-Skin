package de.simpleskin.skin;

import de.simpleskin.data.SimpleSkinConfig;
import de.simpleskin.data.SkinRepository;
import de.simpleskin.data.StoredSkin;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Picks random skins: on demand from the library or the hotkey, and on a timer when the player
 * has switched automatic rotation on.
 */
public final class SkinRotationService {
    private static final int TICKS_PER_MINUTE = 20 * 60;

    private final SkinRepository repository;
    private final SimpleSkinConfig config;
    private final RandomGenerator random = RandomGenerator.getDefault();
    private int ticksSinceRotation;

    public SkinRotationService(SkinRepository repository, SimpleSkinConfig config) {
        this.repository = repository;
        this.config = config;
    }

    /**
     * Returns a saved skin other than {@code current}, or empty when the wardrobe has nothing
     * else to offer.
     */
    public Optional<StoredSkin> pick(StoredSkin current) {
        List<StoredSkin> saved = repository.savedSkins();
        if (saved.isEmpty()) {
            return Optional.empty();
        }
        List<StoredSkin> candidates = saved.stream().filter(skin -> skin != current).toList();
        if (candidates.isEmpty()) {
            // Only one saved skin, and it is already on: re-equipping it would be a no-op.
            return Optional.empty();
        }
        return Optional.of(candidates.get(random.nextInt(candidates.size())));
    }

    /**
     * Advances the rotation timer by one tick and reports the skin to switch to, if the interval
     * has elapsed. Returns empty while rotation is off or the interval has not been reached.
     */
    public Optional<StoredSkin> tick(StoredSkin current) {
        int minutes = config.rotationMinutes();
        if (minutes <= 0) {
            ticksSinceRotation = 0;
            return Optional.empty();
        }
        if (++ticksSinceRotation < minutes * TICKS_PER_MINUTE) {
            return Optional.empty();
        }
        ticksSinceRotation = 0;
        return pick(current);
    }

    /** Restarts the interval, so a manual change does not trigger a rotation moments later. */
    public void resetTimer() {
        ticksSinceRotation = 0;
    }
}
