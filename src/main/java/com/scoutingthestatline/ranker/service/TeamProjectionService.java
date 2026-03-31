package com.scoutingthestatline.ranker.service;

import com.scoutingthestatline.ranker.model.BattingProjection;
import com.scoutingthestatline.ranker.model.League;
import com.scoutingthestatline.ranker.model.PitchingProjection;
import com.scoutingthestatline.ranker.model.Player;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TeamProjectionService {

    private static final Logger log = LoggerFactory.getLogger(TeamProjectionService.class);
    private static final double REPLACEMENT_LEVEL_WINS = 48.0;

    private final PlayerMappingService playerMappingService;
    private final ProjectionService projectionService;
    private final ScoresheetService scoresheetService;

    // Map of normalized last name -> list of players (for matching)
    private final Map<String, List<Player>> playersByLastName = new HashMap<>();

    public TeamProjectionService(PlayerMappingService playerMappingService,
                                  ProjectionService projectionService,
                                  ScoresheetService scoresheetService) {
        this.playerMappingService = playerMappingService;
        this.projectionService = projectionService;
        this.scoresheetService = scoresheetService;
    }

    @PostConstruct
    public void init() {
        // Build last name index for matching
        for (Integer id : playerMappingService.getAllScoressheetIds()) {
            Player player = playerMappingService.getByScoressheetId(id).orElse(null);
            if (player != null) {
                String lastName = normalizeLastName(player.lastName());
                playersByLastName.computeIfAbsent(lastName, k -> new ArrayList<>()).add(player);
            }
        }
        log.info("Built last name index with {} unique last names", playersByLastName.size());
    }

    public record TeamProjection(
        String teamName,
        String owner,
        double totalWar,
        double battingWar,
        double pitchingWar,
        int projectedWins,
        int matchedPlayers,
        int unmatchedPlayers,
        List<String> unmatchedNames,
        List<String> topPlayers
    ) {}

    /**
     * Project all teams for a league by fetching rosters dynamically.
     */
    public List<TeamProjection> projectLeague(League league, Map<Integer, String> teamOwners) {
        // Fetch team rosters from dynamic page
        Map<Integer, Set<Integer>> teamRosters = scoresheetService.fetchAllTeamRosters(league);

        List<TeamProjection> projections = new ArrayList<>();

        for (Map.Entry<Integer, Set<Integer>> entry : teamRosters.entrySet()) {
            int teamNum = entry.getKey();
            Set<Integer> playerIds = entry.getValue();

            double battingWar = 0.0;
            double pitchingWar = 0.0;
            int matched = 0;
            int unmatched = 0;
            List<String> unmatchedNames = new ArrayList<>();
            List<PlayerWarPair> playerWarPairs = new ArrayList<>();

            for (int scoresheetId : playerIds) {
                Player player = playerMappingService.getByScoressheetId(scoresheetId).orElse(null);
                if (player != null) {
                    int mlbamId = playerMappingService.getMlbamId(scoresheetId);
                    if (mlbamId > 0) {
                        if (player.isPitcher()) {
                            PitchingProjection proj = projectionService.getPitchingProjection("oopsy", mlbamId).orElse(null);
                            if (proj != null) {
                                pitchingWar += proj.war();
                                playerWarPairs.add(new PlayerWarPair(player.lastName(), proj.war()));
                                matched++;
                            } else {
                                unmatchedNames.add(player.lastName() + " (no proj)");
                                unmatched++;
                            }
                        } else {
                            BattingProjection proj = projectionService.getBattingProjection("oopsy", mlbamId).orElse(null);
                            if (proj != null) {
                                battingWar += proj.war();
                                playerWarPairs.add(new PlayerWarPair(player.lastName(), proj.war()));
                                matched++;
                            } else {
                                unmatchedNames.add(player.lastName() + " (no proj)");
                                unmatched++;
                            }
                        }
                    } else {
                        unmatchedNames.add(player.lastName() + " (no mlbam)");
                        unmatched++;
                    }
                } else {
                    unmatchedNames.add("ID:" + scoresheetId);
                    unmatched++;
                }
            }

            double totalWar = battingWar + pitchingWar;
            int projectedWins = (int) Math.round(REPLACEMENT_LEVEL_WINS + totalWar);

            // Get top 5 players by WAR
            playerWarPairs.sort((a, b) -> Double.compare(b.war, a.war));
            List<String> topPlayers = playerWarPairs.stream()
                .limit(5)
                .map(p -> p.name + " (" + String.format("%.1f", p.war) + ")")
                .collect(Collectors.toList());

            String teamName = "Team #" + teamNum;
            String owner = teamOwners.getOrDefault(teamNum, "Team " + teamNum);

            // Log top players for debugging
            log.info("Team {} ({}): Top players: {}", teamNum, owner, topPlayers);

            projections.add(new TeamProjection(
                teamName, owner, totalWar, battingWar, pitchingWar,
                projectedWins, matched, unmatched, unmatchedNames, topPlayers
            ));
        }

        // Sort by projected wins descending
        projections.sort((a, b) -> Integer.compare(b.projectedWins(), a.projectedWins()));
        return projections;
    }

    private record PlayerWarPair(String name, double war) {}

    public List<TeamProjection> projectAllTeams(Map<String, List<String>> teamRosters) {
        List<TeamProjection> projections = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : teamRosters.entrySet()) {
            String teamKey = entry.getKey();
            List<String> playerNames = entry.getValue();

            double battingWar = 0.0;
            double pitchingWar = 0.0;
            int matched = 0;
            int unmatched = 0;
            List<String> unmatchedNames = new ArrayList<>();

            for (String name : playerNames) {
                Player player = findPlayer(name);
                if (player != null) {
                    int mlbamId = playerMappingService.getMlbamId(player.scoresheetId());
                    if (mlbamId > 0) {
                        // Get OOPSY projection
                        if (player.isPitcher()) {
                            PitchingProjection proj = projectionService.getPitchingProjection("oopsy", mlbamId).orElse(null);
                            if (proj != null) {
                                pitchingWar += proj.war();
                                matched++;
                            } else {
                                unmatchedNames.add(name + " (no proj)");
                                unmatched++;
                            }
                        } else {
                            BattingProjection proj = projectionService.getBattingProjection("oopsy", mlbamId).orElse(null);
                            if (proj != null) {
                                battingWar += proj.war();
                                matched++;
                            } else {
                                unmatchedNames.add(name + " (no proj)");
                                unmatched++;
                            }
                        }
                    } else {
                        unmatchedNames.add(name + " (no mlbam)");
                        unmatched++;
                    }
                } else {
                    unmatchedNames.add(name);
                    unmatched++;
                }
            }

            double totalWar = battingWar + pitchingWar;
            int projectedWins = (int) Math.round(REPLACEMENT_LEVEL_WINS + totalWar);

            // Parse team name and owner from key like "Team #1 (Kenny Chng)"
            String owner = teamKey;
            String teamName = teamKey;
            if (teamKey.contains("(") && teamKey.contains(")")) {
                int start = teamKey.indexOf("(");
                int end = teamKey.indexOf(")");
                owner = teamKey.substring(start + 1, end);
                teamName = teamKey.substring(0, start).trim();
            }

            projections.add(new TeamProjection(
                teamName, owner, totalWar, battingWar, pitchingWar,
                projectedWins, matched, unmatched, unmatchedNames, List.of()
            ));
        }

        // Sort by projected wins descending
        projections.sort((a, b) -> Integer.compare(b.projectedWins(), a.projectedWins()));
        return projections;
    }

    private Player findPlayer(String name) {
        String normalized = normalizeLastName(name);

        // Try exact last name match
        List<Player> candidates = playersByLastName.get(normalized);
        if (candidates != null && candidates.size() == 1) {
            return candidates.get(0);
        }

        // If multiple candidates or none found, try full name match
        if (candidates != null && candidates.size() > 1) {
            // Return first match - could be improved with first name matching
            return candidates.get(0);
        }

        // Try removing suffixes like Jr, II, etc.
        String withoutSuffix = normalized.replaceAll("(jr|sr|ii|iii|iv)$", "").trim();
        if (!withoutSuffix.equals(normalized)) {
            candidates = playersByLastName.get(withoutSuffix);
            if (candidates != null && !candidates.isEmpty()) {
                return candidates.get(0);
            }
        }

        return null;
    }

    private String normalizeLastName(String name) {
        return name.toLowerCase()
            .replace("'", "")
            .replace("-", "")
            .replace(".", "")
            .replaceAll("\\s+", "")
            .trim();
    }
}
