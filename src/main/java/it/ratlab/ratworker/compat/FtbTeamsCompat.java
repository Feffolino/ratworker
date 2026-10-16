package it.ratlab.ratworker.compat;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;

import java.util.UUID;

/** Loaded only when FTB Teams is present. */
final class FtbTeamsCompat {
    private FtbTeamsCompat() {}

    static boolean sameTeam(UUID a, UUID b) {
        var api = FTBTeamsAPI.api();
        return api.isManagerLoaded() && api.getManager().arePlayersInSameTeam(a, b);
    }
}
