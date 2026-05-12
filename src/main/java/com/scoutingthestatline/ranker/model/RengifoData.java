package com.scoutingthestatline.ranker.model;

public record RengifoData(
    int mlbamId,
    String name,
    double value,
    double age,
    double paOrIp,
    String type  // "B" for batting, "P" for pitching
) {}
