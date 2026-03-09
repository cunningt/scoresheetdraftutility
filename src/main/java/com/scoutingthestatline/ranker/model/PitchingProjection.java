package com.scoutingthestatline.ranker.model;

public record PitchingProjection(
    int mlbamId,
    String name,
    String team,
    String projectionSystem,

    // Counting stats
    double wins,
    double losses,
    double qualityStarts,
    double games,
    double gamesStarted,
    double saves,
    double holds,
    double inningsPitched,
    double hits,
    double runs,
    double earnedRuns,
    double homeRuns,
    double walks,
    double strikeouts,

    // Rate stats
    double era,
    double whip,
    double k9,
    double bb9,
    double kPerBB,
    double kMinusBbPct,
    double fip,

    // Value stats
    double war,
    double fpts,
    double spts,
    double adp
) {}
