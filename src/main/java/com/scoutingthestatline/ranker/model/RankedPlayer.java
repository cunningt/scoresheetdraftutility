package com.scoutingthestatline.ranker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankedPlayer {
    private int rank;
    private Player player;
    private BattingProjection battingProjection;
    private PitchingProjection pitchingProjection;
    private String projectionSystem;

    public double getWar() {
        if (player.isPitcher() && pitchingProjection != null) {
            return pitchingProjection.getWar();
        } else if (battingProjection != null) {
            return battingProjection.getWar();
        }
        return 0.0;
    }

    public double getFpts() {
        if (player.isPitcher() && pitchingProjection != null) {
            return pitchingProjection.getFpts();
        } else if (battingProjection != null) {
            return battingProjection.getFpts();
        }
        return 0.0;
    }

    public String getProjectedTeam() {
        if (player.isPitcher() && pitchingProjection != null) {
            return pitchingProjection.getTeam();
        } else if (battingProjection != null) {
            return battingProjection.getTeam();
        }
        return player.getTeam();
    }

    public boolean hasProjection() {
        return (player.isPitcher() && pitchingProjection != null) ||
               (!player.isPitcher() && battingProjection != null);
    }
}
