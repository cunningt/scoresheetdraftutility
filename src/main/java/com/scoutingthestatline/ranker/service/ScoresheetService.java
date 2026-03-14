package com.scoutingthestatline.ranker.service;

import com.scoutingthestatline.ranker.config.LeagueProperties;
import com.scoutingthestatline.ranker.model.League;
import org.htmlunit.BrowserVersion;
import org.htmlunit.WebClient;
import org.htmlunit.html.FrameWindow;
import org.htmlunit.html.HtmlPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ScoresheetService {

    private static final Logger log = LoggerFactory.getLogger(ScoresheetService.class);

    private final LeagueProperties leagueProperties;
    private final PlayerMappingService playerMappingService;

    private WebClient webClient;

    public ScoresheetService(LeagueProperties leagueProperties, PlayerMappingService playerMappingService) {
        this.leagueProperties = leagueProperties;
        this.playerMappingService = playerMappingService;
    }

    public List<League> getLeagues() {
        return leagueProperties.toLeagues();
    }

    public Optional<League> getLeague(String leagueId) {
        return getLeagues().stream()
                .filter(l -> l.id().equals(leagueId))
                .findFirst();
    }

    private synchronized WebClient getWebClient() {
        if (webClient == null) {
            webClient = new WebClient(BrowserVersion.CHROME);
            webClient.getOptions().setJavaScriptEnabled(true);
            webClient.getOptions().setCssEnabled(false);
            webClient.getOptions().setThrowExceptionOnScriptError(false);
            webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
            webClient.getOptions().setPrintContentOnFailingStatusCode(false);
            webClient.getOptions().setDownloadImages(false);
            webClient.getOptions().setTimeout(30000);
            webClient.waitForBackgroundJavaScript(10000);
        }
        return webClient;
    }

    /**
     * Fetch undrafted player IDs by taking all players from the scoresheet player list
     * and subtracting the drafted players from the dynamic team pages.
     * This approach uses public pages and doesn't require login.
     */
    public Set<Integer> fetchUndraftedPlayerIds(League league) {
        try {
            // Get all player IDs from the scoresheet player list, filtered by league type
            Set<Integer> allPlayerIds = filterPlayerIdsByLeague(
                    playerMappingService.getAllScoressheetIds(), league);
            log.info("Total players from scoresheet list for {}: {}", league.id(), allPlayerIds.size());

            // Fetch drafted player IDs from all team pages
            Set<Integer> draftedIds = fetchDraftedPlayerIds(league);
            log.info("Drafted players from dynamic team pages: {}", draftedIds.size());

            // Undrafted = all players - drafted players
            Set<Integer> undraftedIds = new HashSet<>(allPlayerIds);
            undraftedIds.removeAll(draftedIds);

            log.info("Found {} undrafted player IDs for league {} (total: {}, drafted: {})",
                    undraftedIds.size(), league.id(), allPlayerIds.size(), draftedIds.size());
            return undraftedIds;

        } catch (Exception e) {
            log.error("Error fetching undrafted players: {}", e.getMessage(), e);
            return Collections.emptySet();
        }
    }

    /**
     * Fetch drafted player IDs from all team dynamic pages.
     * These pages are public and don't require login.
     *
     * For AL/NL leagues, the dynamic page shows AL IDs but we need to map them
     * to the league-specific ID ranges:
     * - AL batters: 0-999, NL batters: 1000-1999 (offset of 1000)
     * - AL pitchers: 4000-4999, NL pitchers: 5000-5999 (offset of 1000)
     */
    public Set<Integer> fetchDraftedPlayerIds(League league) {
        Set<Integer> draftedIds = new HashSet<>();
        WebClient client = getWebClient();

        // Only need to fetch one team page since all teams are shown on each page
        try {
            Set<Integer> rawDraftedIds = fetchTeamPlayerIds(client, league, 1);

            // For AL/NL leagues, add both the raw ID and the mapped ID
            // This handles cases where the dynamic page shows AL IDs but we need NL IDs
            String leagueId = league.id();
            String leagueName = league.name();
            boolean isAL = leagueName.startsWith("AL") || leagueId.contains("_AL") || leagueId.startsWith("AL_");
            boolean isNL = leagueName.startsWith("NL") || leagueId.contains("_NL") || leagueId.startsWith("NL_");

            for (int id : rawDraftedIds) {
                draftedIds.add(id);

                if (isNL) {
                    // For NL leagues, also add the NL-mapped ID
                    // AL batter (0-999) -> NL batter (1000-1999)
                    // AL pitcher (4000-4999) -> NL pitcher (5000-5999)
                    if (id > 0 && id < 1000) {
                        draftedIds.add(id + 1000);
                    } else if (id > 4000 && id < 5000) {
                        draftedIds.add(id + 1000);
                    }
                } else if (isAL) {
                    // For AL leagues, also add the AL-mapped ID
                    // NL batter (1000-1999) -> AL batter (0-999)
                    // NL pitcher (5000-5999) -> AL pitcher (4000-4999)
                    if (id > 1000 && id < 2000) {
                        draftedIds.add(id - 1000);
                    } else if (id > 5000 && id < 6000) {
                        draftedIds.add(id - 1000);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error fetching drafted players: {}", e.getMessage());
        }

        return draftedIds;
    }

    /**
     * Fetch player IDs for a specific team from the dynamic team page.
     */
    private Set<Integer> fetchTeamPlayerIds(WebClient client, League league, int teamN) throws IOException {
        String url = league.getDynamicTeamUrl(teamN);
        log.debug("Fetching from: {}", url);

        HtmlPage page = client.getPage(url);
        client.waitForBackgroundJavaScript(8000);

        Set<Integer> playerIds = new HashSet<>();

        // Get all frames on the page
        List<FrameWindow> frames = page.getFrames();
        for (FrameWindow frame : frames) {
            try {
                HtmlPage framePage = (HtmlPage) frame.getEnclosedPage();
                String frameHtml = framePage.getBody().asXml();
                extractPlayerIdsFromHtml(frameHtml, playerIds);
            } catch (Exception e) {
                log.trace("Error processing frame: {}", e.getMessage());
            }
        }

        // Also check the main page body
        String mainHtml = page.getBody().asXml();
        extractPlayerIdsFromHtml(mainHtml, playerIds);

        return playerIds;
    }

    /**
     * Extract player IDs from HTML content using various patterns.
     */
    private void extractPlayerIdsFromHtml(String html, Set<Integer> playerIds) {
        // Pattern for dynamic team page: span id="t{teamNum}p{playerId}"
        // e.g., id="t8p627" for team 8, player 627 (Aaron Judge)
        Pattern teamPlayerPattern = Pattern.compile("id=\"t\\d+p(\\d+)\"");
        Matcher teamPlayerMatcher = teamPlayerPattern.matcher(html);
        while (teamPlayerMatcher.find()) {
            try {
                playerIds.add(Integer.parseInt(teamPlayerMatcher.group(1)));
            } catch (NumberFormatException e) {
                // ignore
            }
        }

        // Pattern for player links: href="#p123"
        Pattern hrefPattern = Pattern.compile("href=\"#p(\\d+)\"");
        Matcher hrefMatcher = hrefPattern.matcher(html);
        while (hrefMatcher.find()) {
            try {
                playerIds.add(Integer.parseInt(hrefMatcher.group(1)));
            } catch (NumberFormatException e) {
                // ignore
            }
        }

        // Pattern for anchor id: id="p123"
        Pattern idPattern = Pattern.compile("id=\"p(\\d+)\"");
        Matcher idMatcher = idPattern.matcher(html);
        while (idMatcher.find()) {
            try {
                playerIds.add(Integer.parseInt(idMatcher.group(1)));
            } catch (NumberFormatException e) {
                // ignore
            }
        }
    }

    /**
     * Fetch all player IDs from the scoresheet player list, filtered by league type.
     */
    public Set<Integer> fetchAllPlayerIds(League league) {
        Set<Integer> allPlayerIds = filterPlayerIdsByLeague(
                playerMappingService.getAllScoressheetIds(), league);
        log.info("Found {} total player IDs from scoresheet player list for {}", allPlayerIds.size(), league.id());
        return allPlayerIds;
    }

    /**
     * Filter player IDs based on league type (AL/NL).
     * AL leagues: 0 < id < 1000 or 4000 < id < 5000
     * NL leagues: 1000 < id < 2000 or 5000 < id < 6000
     * Other leagues (BL, etc.): no filtering
     */
    private Set<Integer> filterPlayerIdsByLeague(Set<Integer> playerIds, League league) {
        String leagueId = league.id();
        String leagueName = league.name();

        boolean isAL = leagueName.startsWith("AL") || leagueId.contains("_AL") || leagueId.startsWith("AL_");
        boolean isNL = leagueName.startsWith("NL") || leagueId.contains("_NL") || leagueId.startsWith("NL_");

        if (isAL) {
            // AL leagues: 0 < id < 1000 or 4000 < id < 5000
            return playerIds.stream()
                    .filter(id -> (id > 0 && id < 1000) || (id > 4000 && id < 5000))
                    .collect(java.util.stream.Collectors.toSet());
        } else if (isNL) {
            // NL leagues: 1000 < id < 2000 or 5000 < id < 6000
            return playerIds.stream()
                    .filter(id -> (id > 1000 && id < 2000) || (id > 5000 && id < 6000))
                    .collect(java.util.stream.Collectors.toSet());
        }

        // No filtering for other league types (BL, etc.)
        return playerIds;
    }

    @PreDestroy
    public void closeBrowser() {
        if (webClient != null) {
            try {
                webClient.close();
                webClient = null;
            } catch (Exception e) {
                log.warn("Error closing browser: {}", e.getMessage());
            }
        }
    }
}
