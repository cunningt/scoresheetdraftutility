package com.scoutingthestatline.ranker.model;

public record SavantBattingStats(
    int mlbamId,
    String name,

    // Batted ball data
    int attempts,
    double avgHitAngle,
    double sweetSpotPct,
    double maxExitVelocity,
    double avgExitVelocity,
    double ev50,             // 50th percentile EV
    double fbldPct,          // Fly ball / line drive %
    double gbPct,            // Ground ball %

    // Distance
    double maxDistance,
    double avgDistance,
    double avgHrDistance,

    // Hard hit / barrels
    int ev95Plus,            // Balls hit 95+ mph
    double ev95Percent,
    int barrels,
    double barrelPct,
    double barrelPerPA
) {}
