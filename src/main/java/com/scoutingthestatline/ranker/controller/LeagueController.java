package com.scoutingthestatline.ranker.controller;

import com.scoutingthestatline.ranker.model.League;
import com.scoutingthestatline.ranker.model.RankedPlayer;
import com.scoutingthestatline.ranker.service.RankingService;
import com.scoutingthestatline.ranker.service.ScoresheetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class LeagueController {

    private final ScoresheetService scoresheetService;
    private final RankingService rankingService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("leagues", scoresheetService.getLeagues());
        return "home";
    }

    @GetMapping("/league/{leagueId}")
    public String viewLeague(
            @PathVariable String leagueId,
            @RequestParam(defaultValue = "steamer") String projection,
            @RequestParam(defaultValue = "BATTER") String position,
            @RequestParam(defaultValue = "false") boolean refresh,
            Model model) {

        log.info("Viewing league {} with projection {} and position filter {}", leagueId, projection, position);

        League league = scoresheetService.getLeague(leagueId).orElse(null);
        if (league == null) {
            return "redirect:/";
        }

        List<RankedPlayer> rankedPlayers = rankingService.getFilteredRankedPlayers(
                leagueId, projection, position, refresh);

        model.addAttribute("league", league);
        model.addAttribute("leagues", scoresheetService.getLeagues());
        model.addAttribute("rankedPlayers", rankedPlayers);
        model.addAttribute("projectionSystems", rankingService.getProjectionSystems());
        model.addAttribute("selectedProjection", projection);
        model.addAttribute("selectedPosition", position);
        model.addAttribute("positions", List.of("BATTER", "Pitcher", "P", "SR", "C", "1B", "2B", "3B", "SS", "OF", "DH"));

        return "league";
    }

    @GetMapping("/league/{leagueId}/refresh")
    public String refreshLeague(@PathVariable String leagueId,
                                @RequestParam(defaultValue = "steamer") String projection) {
        rankingService.clearCache(leagueId);
        return "redirect:/league/" + leagueId + "?projection=" + projection + "&refresh=true";
    }
}
