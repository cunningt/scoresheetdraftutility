package com.scoutingthestatline.ranker.model;

public record BattingProjection(
    int mlbamId,
    String name,
    String team,
    String projectionSystem,

    // Counting stats
    double games,
    double plateAppearances,
    double atBats,
    double hits,
    double doubles,
    double triples,
    double homeRuns,
    double runs,
    double rbi,
    double walks,
    double strikeouts,
    double stolenBases,
    double caughtStealing,

    // Rate stats
    double avg,
    double obp,
    double slg,
    double ops,
    double wOBA,
    double wRCPlus,
    double bbPct,      // BB%
    double kPct,       // K%
    double iso,        // ISO
    double babip,      // BABIP

    // Value stats
    double war,
    double off,        // Offensive runs
    double def,        // Defensive runs (Fld)
    double ubr,        // Ultimate Base Running
    double bsr,        // Base Running Runs (total)
    double fpts,
    double spts
) {}
