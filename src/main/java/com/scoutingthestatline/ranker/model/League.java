package com.scoutingthestatline.ranker.model;

public record League(
    String id,
    String name,
    String dirLgw,
    String statsMl,
    int numTeams
) {
    public String getUndraftedPlayersUrl() {
        return String.format(
            "https://scoresheet.com/htm-lib/lg_players_frames.htm?dir_lgw=%s;stats_ml=%s;ranking_url=ranking.htm;team_n=1",
            dirLgw, statsMl
        );
    }

    public String getDynamicTeamUrl(int teamN) {
        return String.format(
            "https://www.scoresheet.com/htm-lib/lg_players_frames.htm?dir_lgw=%s;dynamic;team_n=%d",
            dirLgw, teamN
        );
    }
}
