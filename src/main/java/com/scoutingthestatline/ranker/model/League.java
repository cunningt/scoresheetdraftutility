package com.scoutingthestatline.ranker.model;

public record League(
    String id,
    String name,
    String dirLgw,
    String statsMl,
    int teamN
) {
    public String getUndraftedPlayersUrl() {
        return String.format(
            "https://scoresheet.com/htm-lib/lg_players_frames.htm?dir_lgw=%s;stats_ml=%s;ranking_url=ranking.htm;team_n=%d",
            dirLgw, statsMl, teamN
        );
    }
}
