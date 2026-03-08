package com.scoutingthestatline.ranker.service;

import com.scoutingthestatline.ranker.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {

    private final PlayerMappingService playerMappingService;
    private final ProjectionService projectionService;
    private final ScoresheetService scoresheetService;

    // Cache of undrafted player IDs per league
    private final Map<String, Set<Integer>> undraftedCache = new HashMap<>();

    public Set<Integer> getUndraftedPlayerIds(String leagueId, boolean refresh) {
        if (!refresh && undraftedCache.containsKey(leagueId)) {
            return undraftedCache.get(leagueId);
        }

        Optional<League> league = scoresheetService.getLeague(leagueId);
        if (league.isEmpty()) {
            log.warn("League not found: {}", leagueId);
            return Collections.emptySet();
        }

        Set<Integer> playerIds = scoresheetService.fetchUndraftedPlayerIds(league.get());
        undraftedCache.put(leagueId, playerIds);
        return playerIds;
    }

    public List<RankedPlayer> getRankedPlayers(String leagueId, String projectionSystem, boolean refresh) {
        Set<Integer> undraftedIds = getUndraftedPlayerIds(leagueId, refresh);

        List<RankedPlayer> rankedPlayers = new ArrayList<>();

        for (int scoresheetId : undraftedIds) {
            Optional<Player> playerOpt = playerMappingService.getByScoressheetId(scoresheetId);
            if (playerOpt.isEmpty()) {
                log.debug("No player mapping found for Scoresheet ID: {}", scoresheetId);
                continue;
            }

            Player player = playerOpt.get();
            int mlbamId = player.getMlbamId();

            RankedPlayer.RankedPlayerBuilder builder = RankedPlayer.builder()
                    .player(player)
                    .projectionSystem(projectionSystem);

            if (player.isPitcher()) {
                Optional<PitchingProjection> pitchingProj = projectionService.getPitchingProjection(projectionSystem, mlbamId);
                pitchingProj.ifPresent(builder::pitchingProjection);
            } else {
                Optional<BattingProjection> battingProj = projectionService.getBattingProjection(projectionSystem, mlbamId);
                battingProj.ifPresent(builder::battingProjection);
            }

            rankedPlayers.add(builder.build());
        }

        // Sort by WAR descending
        rankedPlayers.sort((a, b) -> Double.compare(b.getWar(), a.getWar()));

        // Assign ranks
        for (int i = 0; i < rankedPlayers.size(); i++) {
            rankedPlayers.get(i).setRank(i + 1);
        }

        return rankedPlayers;
    }

    public List<RankedPlayer> getFilteredRankedPlayers(String leagueId, String projectionSystem,
                                                        String positionFilter, boolean refresh) {
        List<RankedPlayer> allPlayers = getRankedPlayers(leagueId, projectionSystem, refresh);

        if (positionFilter == null || positionFilter.isEmpty() || positionFilter.equals("ALL")) {
            return allPlayers;
        }

        List<RankedPlayer> filtered = allPlayers.stream()
                .filter(rp -> matchesPosition(rp.getPlayer(), positionFilter))
                .collect(Collectors.toList());

        // Re-rank filtered list
        for (int i = 0; i < filtered.size(); i++) {
            filtered.get(i).setRank(i + 1);
        }

        return filtered;
    }

    private boolean matchesPosition(Player player, String positionFilter) {
        if (positionFilter.equals("Pitcher")) {
            // Pitcher = All pitchers (P and SR)
            return player.isPitcher();
        } else if (positionFilter.equals("P")) {
            // P = Starting pitchers only (not SR)
            return "P".equals(player.getPosition());
        } else if (positionFilter.equals("SR")) {
            // SR = Short Relievers
            return "SR".equals(player.getPosition());
        } else if (positionFilter.equals("BATTER")) {
            // All non-pitchers
            return !player.isPitcher();
        } else {
            return player.getPosition().equals(positionFilter);
        }
    }

    public List<String> getProjectionSystems() {
        return projectionService.getProjectionSystems();
    }

    public void clearCache(String leagueId) {
        undraftedCache.remove(leagueId);
    }
}
