package com.scoutingthestatline.ranker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class League {
    private String id;
    private String name;
    private String dirLgw;
    private String statsMl;
    private int teamN;

    public String getUndraftedPlayersUrl() {
        return String.format(
            "https://scoresheet.com/htm-lib/lg_players_frames.htm?dir_lgw=%s;stats_ml=%s;ranking_url=ranking.htm;team_n=%d",
            dirLgw, statsMl, teamN
        );
    }
}
