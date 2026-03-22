package com.scoutingthestatline.ranker.model;

public record Top500DynastyData(
    int rank,
    String name,
    String position,
    String team
) {}
