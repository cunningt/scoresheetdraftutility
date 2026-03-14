package com.scoutingthestatline.ranker.model;

import java.util.Objects;

public class RankedPlayer {
    private int rank;
    private final Player player;
    private final BattingProjection battingProjection;
    private final PitchingProjection pitchingProjection;
    private final SavantBattingStats savantBattingStats;
    private final SavantPitchingStats savantPitchingStats;
    private final ADPData adpData;
    private final PitcherList400Data pitcherList400Data;
    private final boolean onActiveRoster;
    private final boolean drafted;
    private final String projectionSystem;

    public RankedPlayer(int rank, Player player, BattingProjection battingProjection,
                        PitchingProjection pitchingProjection, String projectionSystem) {
        this(rank, player, battingProjection, pitchingProjection, null, null, null, null, false, false, projectionSystem);
    }

    public RankedPlayer(int rank, Player player, BattingProjection battingProjection,
                        PitchingProjection pitchingProjection, SavantBattingStats savantBattingStats,
                        SavantPitchingStats savantPitchingStats, String projectionSystem) {
        this(rank, player, battingProjection, pitchingProjection, savantBattingStats, savantPitchingStats, null, null, false, false, projectionSystem);
    }

    public RankedPlayer(int rank, Player player, BattingProjection battingProjection,
                        PitchingProjection pitchingProjection, SavantBattingStats savantBattingStats,
                        SavantPitchingStats savantPitchingStats, ADPData adpData, String projectionSystem) {
        this(rank, player, battingProjection, pitchingProjection, savantBattingStats, savantPitchingStats, adpData, null, false, false, projectionSystem);
    }

    public RankedPlayer(int rank, Player player, BattingProjection battingProjection,
                        PitchingProjection pitchingProjection, SavantBattingStats savantBattingStats,
                        SavantPitchingStats savantPitchingStats, ADPData adpData, boolean onActiveRoster,
                        String projectionSystem) {
        this(rank, player, battingProjection, pitchingProjection, savantBattingStats, savantPitchingStats, adpData, null, onActiveRoster, false, projectionSystem);
    }

    public RankedPlayer(int rank, Player player, BattingProjection battingProjection,
                        PitchingProjection pitchingProjection, SavantBattingStats savantBattingStats,
                        SavantPitchingStats savantPitchingStats, ADPData adpData, PitcherList400Data pitcherList400Data,
                        boolean onActiveRoster, String projectionSystem) {
        this(rank, player, battingProjection, pitchingProjection, savantBattingStats, savantPitchingStats, adpData, pitcherList400Data, onActiveRoster, false, projectionSystem);
    }

    public RankedPlayer(int rank, Player player, BattingProjection battingProjection,
                        PitchingProjection pitchingProjection, SavantBattingStats savantBattingStats,
                        SavantPitchingStats savantPitchingStats, ADPData adpData, PitcherList400Data pitcherList400Data,
                        boolean onActiveRoster, boolean drafted, String projectionSystem) {
        this.rank = rank;
        this.player = player;
        this.battingProjection = battingProjection;
        this.pitchingProjection = pitchingProjection;
        this.savantBattingStats = savantBattingStats;
        this.savantPitchingStats = savantPitchingStats;
        this.adpData = adpData;
        this.pitcherList400Data = pitcherList400Data;
        this.onActiveRoster = onActiveRoster;
        this.drafted = drafted;
        this.projectionSystem = projectionSystem;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public Player getPlayer() {
        return player;
    }

    public BattingProjection getBattingProjection() {
        return battingProjection;
    }

    public PitchingProjection getPitchingProjection() {
        return pitchingProjection;
    }

    public String getProjectionSystem() {
        return projectionSystem;
    }

    public SavantBattingStats getSavantBattingStats() {
        return savantBattingStats;
    }

    public SavantPitchingStats getSavantPitchingStats() {
        return savantPitchingStats;
    }

    public ADPData getAdpData() {
        return adpData;
    }

    public PitcherList400Data getPitcherList400Data() {
        return pitcherList400Data;
    }

    public double getAdp() {
        return adpData != null ? adpData.adp() : Double.MAX_VALUE;
    }

    public boolean isOnActiveRoster() {
        return onActiveRoster;
    }

    public boolean isDrafted() {
        return drafted;
    }

    public double getWar() {
        if (player.isPitcher() && pitchingProjection != null) {
            return pitchingProjection.war();
        } else if (battingProjection != null) {
            return battingProjection.war();
        }
        return 0.0;
    }

    public double getFpts() {
        if (player.isPitcher() && pitchingProjection != null) {
            return pitchingProjection.fpts();
        } else if (battingProjection != null) {
            return battingProjection.fpts();
        }
        return 0.0;
    }

    public String getProjectedTeam() {
        if (player.isPitcher() && pitchingProjection != null) {
            return pitchingProjection.team();
        } else if (battingProjection != null) {
            return battingProjection.team();
        }
        return player.team();
    }

    public boolean hasProjection() {
        if ("savant".equals(projectionSystem)) {
            return (player.isPitcher() && savantPitchingStats != null) ||
                   (!player.isPitcher() && savantBattingStats != null);
        }
        if ("adp".equals(projectionSystem)) {
            return adpData != null;
        }
        if ("pitcherlist400".equals(projectionSystem)) {
            return pitcherList400Data != null;
        }
        return (player.isPitcher() && pitchingProjection != null) ||
               (!player.isPitcher() && battingProjection != null);
    }

    /**
     * Returns a ranking value for Savant projections.
     * For batters: barrel percentage (higher is better)
     * For pitchers: inverted xERA percentile (lower xERA = higher rank value)
     */
    public double getSavantRankValue() {
        if (player.isPitcher() && savantPitchingStats != null) {
            // xERA is a percentile where lower is better, so invert it
            return 100 - savantPitchingStats.xERA();
        } else if (savantBattingStats != null) {
            return savantBattingStats.barrelPct();
        }
        return 0.0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RankedPlayer that = (RankedPlayer) o;
        return rank == that.rank && Objects.equals(player, that.player);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rank, player);
    }

    @Override
    public String toString() {
        return "RankedPlayer{" +
                "rank=" + rank +
                ", player=" + player +
                ", projectionSystem='" + projectionSystem + '\'' +
                '}';
    }
}
