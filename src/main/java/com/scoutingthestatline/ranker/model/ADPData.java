package com.scoutingthestatline.ranker.model;

public record ADPData(
    int mlbamId,
    int nfbcId,
    int rank,
    double adp,
    String name,
    String team,
    String positions,
    int minPick,
    int maxPick
) {}
