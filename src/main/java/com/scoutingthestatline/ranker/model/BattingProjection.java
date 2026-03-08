package com.scoutingthestatline.ranker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BattingProjection {
    private int mlbamId;
    private String name;
    private String team;
    private String projectionSystem;

    // Counting stats
    private double games;
    private double plateAppearances;
    private double atBats;
    private double hits;
    private double doubles;
    private double triples;
    private double homeRuns;
    private double runs;
    private double rbi;
    private double walks;
    private double strikeouts;
    private double stolenBases;
    private double caughtStealing;

    // Rate stats
    private double avg;
    private double obp;
    private double slg;
    private double ops;
    private double wOBA;
    private double wRCPlus;
    private double bbPct;      // BB%
    private double kPct;       // K%
    private double iso;        // ISO
    private double babip;      // BABIP

    // Value stats
    private double war;
    private double off;        // Offensive runs
    private double def;        // Defensive runs (Fld)
    private double fpts;
    private double spts;
}
