package it.ratlab.ratworker.config;

/**
 * A preset only supplies defaults. Every preset-aware key in ratworker-server.toml defaults to
 * "PRESET" (enum keys) or -1 (numeric keys); any explicit value wins over the preset.
 */
public enum Preset {
    /** Baseline of the mod: needs on, tasks learned from tomes, perma death, 3 workers. */
    CUSTOM(UnlockMode.TOMES, true, true, false, true, false, 3, 6, 64),
    /** Easy singleplayer: everything unlocked, no needs, big limits. */
    SANDBOX(UnlockMode.FREE, false, false, false, false, false, 10, 16, 200),
    /** Plain survival: needs on, tomes, moderate limits. */
    SURVIVAL(UnlockMode.TOMES, true, true, false, true, false, 3, 6, 60),
    /** Story packs (RatLab style): tomes, needs on, strict limits, recruiting gated. */
    THEMED(UnlockMode.TOMES, true, true, false, true, true, 3, 4, 40);

    public final UnlockMode unlockMode;
    public final boolean hunger;
    public final boolean thirst;
    public final boolean needsKill;
    public final boolean permaDeath;
    public final boolean requireStageToRecruit;
    public final int maxWorkersPerPlayer;
    public final int maxWorkersPerChunk;
    public final int maxActiveWorkersPerServer;

    Preset(UnlockMode unlockMode, boolean hunger, boolean thirst, boolean needsKill, boolean permaDeath,
           boolean requireStageToRecruit, int maxWorkersPerPlayer, int maxWorkersPerChunk, int maxActiveWorkersPerServer) {
        this.unlockMode = unlockMode;
        this.hunger = hunger;
        this.thirst = thirst;
        this.needsKill = needsKill;
        this.permaDeath = permaDeath;
        this.requireStageToRecruit = requireStageToRecruit;
        this.maxWorkersPerPlayer = maxWorkersPerPlayer;
        this.maxWorkersPerChunk = maxWorkersPerChunk;
        this.maxActiveWorkersPerServer = maxActiveWorkersPerServer;
    }

    public enum UnlockMode { PRESET, TOMES, STAGES, OPS_ONLY, FREE, NONE }

    public enum TriState {
        PRESET, TRUE, FALSE;

        public boolean or(boolean presetValue) {
            return this == PRESET ? presetValue : this == TRUE;
        }
    }

    public enum TeamSharing { OWNER, TEAM, EVERYONE }

    public enum NameMode { POOL, CUSTOM, PLAYER_CHOSEN }

    public enum ThreatBehaviour { FLEE, DEFEND, PASSIVE }
}
