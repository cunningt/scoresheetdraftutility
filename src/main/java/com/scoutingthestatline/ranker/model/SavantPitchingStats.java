package com.scoutingthestatline.ranker.model;

public record SavantPitchingStats(
    int mlbamId,
    String name,
    int year,

    // Expected stats (percentile rankings)
    int xwOBA,
    int xBA,
    int xSLG,
    int xISO,
    int xOBP,

    // Barrel data
    int barrels,
    int barrelPct,

    // Exit velocity against
    int exitVelocity,
    int maxEV,
    int hardHitPct,

    // Plate discipline
    int kPct,
    int bbPct,
    int whiffPct,
    int chasePct,

    // Pitch characteristics
    int armStrength,
    int xERA,
    int fbVelocity,
    int fbSpin,
    int curveSpin
) {}
