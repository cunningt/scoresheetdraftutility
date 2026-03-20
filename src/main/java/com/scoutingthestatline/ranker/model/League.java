package com.scoutingthestatline.ranker.model;

public record League(
    String id,
    String name,
    String dirLgw,
    String statsMl,
    int numTeams,
    String draftSheetId,  // Google Sheet ID for draft tracking (null to use dynamic page)
    String draftSheetGid,  // Google Sheet tab/gid (null defaults to 0)
    String draftSheetName,  // Google Sheet tab name (alternative to gid)
    String draftSheetIdColumn  // Column name for scoresheet ID (null defaults to SSID)
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

    public boolean hasDraftSheet() {
        return draftSheetId != null && !draftSheetId.isEmpty();
    }

    public String getDraftSheetCsvUrl() {
        if (draftSheetId == null) return null;
        String gid = (draftSheetGid != null && !draftSheetGid.isEmpty()) ? draftSheetGid : "0";
        return String.format(
            "https://docs.google.com/spreadsheets/d/%s/export?format=csv&gid=%s",
            draftSheetId, gid
        );
    }
}
