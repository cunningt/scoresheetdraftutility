package com.scoutingthestatline.ranker.model;

public record PitcherList400Data(
        int mlbamId,
        int rank,
        String name,
        int tierNum,
        String tierName,
        String hand,
        String team
) {}
