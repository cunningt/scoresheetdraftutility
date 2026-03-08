package com.scoutingthestatline.ranker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PitchingProjection {
    private int mlbamId;
    private String name;
    private String team;
    private String projectionSystem;

    // Counting stats
    private double wins;
    private double losses;
    private double qualityStarts;
    private double games;
    private double gamesStarted;
    private double saves;
    private double holds;
    private double inningsPitched;
    private double hits;
    private double runs;
    private double earnedRuns;
    private double homeRuns;
    private double walks;
    private double strikeouts;

    // Rate stats
    private double era;
    private double whip;
    private double k9;
    private double bb9;
    private double kPerBB;
    private double kMinusBbPct;
    private double fip;

    // Value stats
    private double war;
    private double fpts;
    private double spts;
    private double adp;
}
