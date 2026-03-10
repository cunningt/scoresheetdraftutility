package com.scoutingthestatline.ranker.service;

import com.scoutingthestatline.ranker.model.ADPData;
import com.scoutingthestatline.ranker.model.BattingProjection;
import com.scoutingthestatline.ranker.model.League;
import com.scoutingthestatline.ranker.model.PitcherList400Data;
import com.scoutingthestatline.ranker.model.PitchingProjection;
import com.scoutingthestatline.ranker.model.Player;
import com.scoutingthestatline.ranker.model.RankedPlayer;
import com.scoutingthestatline.ranker.model.SavantBattingStats;
import com.scoutingthestatline.ranker.model.SavantPitchingStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RankingService {

    private static final Logger log = LoggerFactory.getLogger(RankingService.class);

    private final PlayerMappingService playerMappingService;
    private final ProjectionService projectionService;
    private final ScoresheetService scoresheetService;

    // Cache of player IDs per league
    private final Map<String, Set<Integer>> undraftedCache = new HashMap<>();
    private final Map<String, Set<Integer>> allPlayersCache = new HashMap<>();

    public RankingService(PlayerMappingService playerMappingService,
                          ProjectionService projectionService,
                          ScoresheetService scoresheetService) {
        this.playerMappingService = playerMappingService;
        this.projectionService = projectionService;
        this.scoresheetService = scoresheetService;
    }

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

    public Set<Integer> getAllPlayerIds(String leagueId, boolean refresh) {
        if (!refresh && allPlayersCache.containsKey(leagueId)) {
            return allPlayersCache.get(leagueId);
        }

        Optional<League> league = scoresheetService.getLeague(leagueId);
        if (league.isEmpty()) {
            log.warn("League not found: {}", leagueId);
            return Collections.emptySet();
        }

        Set<Integer> playerIds = scoresheetService.fetchAllPlayerIds(league.get());
        allPlayersCache.put(leagueId, playerIds);
        return playerIds;
    }

    public List<RankedPlayer> getRankedPlayers(String leagueId, String projectionSystem, boolean refresh, boolean showDrafted) {
        Set<Integer> playerIds = showDrafted
                ? getAllPlayerIds(leagueId, refresh)
                : getUndraftedPlayerIds(leagueId, refresh);

        List<RankedPlayer> rankedPlayers = new ArrayList<>();

        for (int scoresheetId : playerIds) {
            Optional<Player> playerOpt = playerMappingService.getByScoressheetId(scoresheetId);
            if (playerOpt.isEmpty()) {
                log.debug("No player mapping found for Scoresheet ID: {}", scoresheetId);
                continue;
            }

            Player player = playerOpt.get();
            int mlbamId = player.mlbamId();

            BattingProjection battingProjection = null;
            PitchingProjection pitchingProjection = null;
            SavantBattingStats savantBatting = null;
            SavantPitchingStats savantPitching = null;
            ADPData adpData = null;
            PitcherList400Data pitcherList400Data = null;

            if (player.isPitcher()) {
                if (!"savant".equals(projectionSystem) && !"adp".equals(projectionSystem) && !"pitcherlist400".equals(projectionSystem)) {
                    pitchingProjection = projectionService.getPitchingProjection(projectionSystem, mlbamId).orElse(null);
                }
                savantPitching = projectionService.getSavantPitchingStats(mlbamId).orElse(null);
                pitcherList400Data = projectionService.getPitcherList400Data(mlbamId).orElse(null);
            } else {
                if (!"savant".equals(projectionSystem) && !"adp".equals(projectionSystem) && !"pitcherlist400".equals(projectionSystem)) {
                    battingProjection = projectionService.getBattingProjection(projectionSystem, mlbamId).orElse(null);
                }
                savantBatting = projectionService.getSavantBattingStats(mlbamId).orElse(null);
            }

            // Always load ADP data if available
            adpData = projectionService.getADPData(mlbamId).orElse(null);

            // Check if player is on active roster
            boolean onActiveRoster = projectionService.isOnActiveRoster(mlbamId);

            rankedPlayers.add(new RankedPlayer(0, player, battingProjection, pitchingProjection,
                    savantBatting, savantPitching, adpData, pitcherList400Data, onActiveRoster, projectionSystem));
        }

        // Sort by appropriate metric
        if ("adp".equals(projectionSystem)) {
            // For ADP: sort by ADP ascending (lower is better)
            rankedPlayers.sort((a, b) -> Double.compare(a.getAdp(), b.getAdp()));
        } else if ("savant".equals(projectionSystem)) {
            // For Savant: sort by barrel% for batters, xERA percentile (inverted) for pitchers
            rankedPlayers.sort((a, b) -> Double.compare(b.getSavantRankValue(), a.getSavantRankValue()));
        } else if ("pitcherlist400".equals(projectionSystem)) {
            // For Pitcher List 400: sort by PL400 rank ascending
            rankedPlayers.sort((a, b) -> {
                int rankA = a.getPitcherList400Data() != null ? a.getPitcherList400Data().rank() : Integer.MAX_VALUE;
                int rankB = b.getPitcherList400Data() != null ? b.getPitcherList400Data().rank() : Integer.MAX_VALUE;
                return Integer.compare(rankA, rankB);
            });
        } else {
            // Sort by WAR descending
            rankedPlayers.sort((a, b) -> Double.compare(b.getWar(), a.getWar()));
        }

        // Assign ranks
        for (int i = 0; i < rankedPlayers.size(); i++) {
            rankedPlayers.get(i).setRank(i + 1);
        }

        return rankedPlayers;
    }

    public List<RankedPlayer> getFilteredRankedPlayers(String leagueId, String projectionSystem,
                                                        String positionFilter, boolean refresh, boolean showDrafted,
                                                        boolean activeRosterOnly) {
        List<RankedPlayer> allPlayers = getRankedPlayers(leagueId, projectionSystem, refresh, showDrafted);

        List<RankedPlayer> filtered = allPlayers.stream()
                .filter(rp -> !activeRosterOnly || rp.isOnActiveRoster())
                .filter(rp -> positionFilter == null || positionFilter.isEmpty() ||
                              positionFilter.equals("ALL") || matchesPosition(rp.getPlayer(), positionFilter))
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
            return "P".equals(player.position());
        } else if (positionFilter.equals("SR")) {
            // SR = Short Relievers
            return "SR".equals(player.position());
        } else if (positionFilter.equals("BATTER")) {
            // All non-pitchers
            return !player.isPitcher();
        } else {
            return player.position().equals(positionFilter);
        }
    }

    public List<String> getProjectionSystems() {
        return projectionService.getProjectionSystems();
    }

    public void clearCache(String leagueId) {
        undraftedCache.remove(leagueId);
        allPlayersCache.remove(leagueId);
    }
}
