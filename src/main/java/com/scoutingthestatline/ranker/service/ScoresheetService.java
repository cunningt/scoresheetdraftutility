package com.scoutingthestatline.ranker.service;

import com.scoutingthestatline.ranker.config.LeagueProperties;
import com.scoutingthestatline.ranker.model.League;
import org.apache.commons.codec.digest.DigestUtils;
import org.htmlunit.BrowserVersion;
import org.htmlunit.WebClient;
import org.htmlunit.html.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${scoresheet.login.firstname:}")
    private String firstName;

    @Value("${scoresheet.login.lastname:}")
    private String lastName;

    @Value("${scoresheet.login.password:}")
    private String password;

    private WebClient webClient;
    private boolean loggedIn = false;

    public ScoresheetService(LeagueProperties leagueProperties) {
        this.leagueProperties = leagueProperties;
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

    private synchronized void ensureLoggedIn() throws IOException {
        if (loggedIn) {
            return;
        }

        log.info("Logging in to Scoresheet...");
        WebClient client = getWebClient();

        // Navigate to login page
        HtmlPage loginPage = client.getPage("https://scoresheet.com/htm-lib/login.htm");
        client.waitForBackgroundJavaScript(3000);

        // Find the form by ID
        HtmlForm form = (HtmlForm) loginPage.getElementById("main-form");

        if (form == null) {
            form = loginPage.getForms().stream().findFirst().orElse(null);
        }

        if (form == null) {
            log.error("Could not find login form");
            return;
        }

        // Fill in the form fields by name
        HtmlInput firstNameInput = form.getInputByName("first_name");
        HtmlInput lastNameInput = form.getInputByName("last_name");

        // Password field is form.elements[2] - it's a password input without a name
        // We need to find it by type
        HtmlInput passwordInput = null;
        for (HtmlElement el : form.getElementsByTagName("input")) {
            if (el instanceof HtmlPasswordInput) {
                passwordInput = (HtmlPasswordInput) el;
                break;
            }
        }

        if (passwordInput == null) {
            log.error("Could not find password input");
            return;
        }

        firstNameInput.setValue(firstName);
        lastNameInput.setValue(lastName);
        passwordInput.setValue(password);

        // Now we need to manually set the hidden 'p' field with MD5 hash
        // because HtmlUnit's JS might not execute the form's onsubmit properly
        HtmlInput hiddenPassword = form.getInputByName("p");
        String hashedPassword = DigestUtils.md5Hex(password + "M3");
        hiddenPassword.setValue(hashedPassword);

        // Find and click submit button
        HtmlInput submitButton = form.getInputByValue("Login");
        HtmlPage resultPage = submitButton.click();
        client.waitForBackgroundJavaScript(5000);

        // Check if login was successful by looking at the URL or page content
        String resultUrl = resultPage.getUrl().toString();
        log.info("Result URL after login: {}", resultUrl);

        loggedIn = true;
        log.info("Logged in successfully");
    }

    public Set<Integer> fetchUndraftedPlayerIds(League league) {
        try {
            ensureLoggedIn();
            WebClient client = getWebClient();

            String url = league.getUndraftedPlayersUrl();
            log.info("Navigating to: {}", url);

            HtmlPage page = client.getPage(url);
            client.waitForBackgroundJavaScript(8000);

            Set<Integer> playerIds = new HashSet<>();
            Set<Integer> draftedIds = new HashSet<>();
            Set<Integer> rankingListIds = new HashSet<>();

            // Get all frames on the page
            List<FrameWindow> frames = page.getFrames();
            log.info("Found {} frames on page", frames.size());

            for (FrameWindow frame : frames) {
                try {
                    HtmlPage framePage = (HtmlPage) frame.getEnclosedPage();
                    String frameHtml = framePage.getBody().asXml();

                    // First, find players in the "Ranking list:" section - these should NOT be treated as drafted
                    extractRankingListPlayerIds(frameHtml, rankingListIds);

                    // Pattern for drafted players (with class="faint") - but exclude ranking list players
                    extractDraftedPlayerIds(frameHtml, draftedIds, rankingListIds);

                    // Pattern for all player links
                    Pattern playerPattern = Pattern.compile(
                            "<a[^>]*href=\"#p(\\d+)\"[^>]*id=\"p\\d+\"[^>]*target=\"Rank\"[^>]*>");
                    Matcher playerMatcher = playerPattern.matcher(frameHtml);
                    while (playerMatcher.find()) {
                        try {
                            int id = Integer.parseInt(playerMatcher.group(1));
                            if (!draftedIds.contains(id)) {
                                playerIds.add(id);
                            }
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }

                } catch (Exception e) {
                    log.debug("Error processing frame: {}", e.getMessage());
                }
            }

            // Also check the main page body
            String mainHtml = page.getBody().asXml();

            // First, find players in the "Ranking list:" section
            extractRankingListPlayerIds(mainHtml, rankingListIds);

            // Then find drafted players, excluding ranking list
            extractDraftedPlayerIds(mainHtml, draftedIds, rankingListIds);

            Pattern playerPattern = Pattern.compile(
                    "<a[^>]*href=\"#p(\\d+)\"[^>]*id=\"p\\d+\"[^>]*target=\"Rank\"[^>]*>");
            Matcher playerMatcher = playerPattern.matcher(mainHtml);
            while (playerMatcher.find()) {
                try {
                    int id = Integer.parseInt(playerMatcher.group(1));
                    if (!draftedIds.contains(id)) {
                        playerIds.add(id);
                    }
                } catch (NumberFormatException e) {
                    // ignore
                }
            }

            // Add ranking list players to undrafted set
            playerIds.addAll(rankingListIds);
            log.info("Found {} undrafted player IDs for league {} (excluded {} drafted, {} from ranking list)",
                    playerIds.size(), league.id(), draftedIds.size(), rankingListIds.size());
            return playerIds;

        } catch (Exception e) {
            log.error("Error fetching undrafted players: {}", e.getMessage(), e);
            return Collections.emptySet();
        }
    }

    public Set<Integer> fetchAllPlayerIds(League league) {
        try {
            ensureLoggedIn();
            WebClient client = getWebClient();

            String url = league.getUndraftedPlayersUrl();
            log.info("Fetching all players from: {}", url);

            HtmlPage page = client.getPage(url);
            client.waitForBackgroundJavaScript(8000);

            Set<Integer> allPlayerIds = new HashSet<>();
            Set<Integer> rankingListIds = new HashSet<>();

            // Get all frames on the page
            List<FrameWindow> frames = page.getFrames();

            for (FrameWindow frame : frames) {
                try {
                    HtmlPage framePage = (HtmlPage) frame.getEnclosedPage();
                    String frameHtml = framePage.getBody().asXml();

                    // Extract ranking list players
                    extractRankingListPlayerIds(frameHtml, rankingListIds);

                    // Pattern for all player links (both drafted and undrafted)
                    Pattern playerPattern = Pattern.compile(
                            "<a[^>]*href=\"#p(\\d+)\"[^>]*id=\"p\\d+\"[^>]*target=\"Rank\"[^>]*>");
                    Matcher playerMatcher = playerPattern.matcher(frameHtml);
                    while (playerMatcher.find()) {
                        try {
                            int id = Integer.parseInt(playerMatcher.group(1));
                            allPlayerIds.add(id);
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }

                } catch (Exception e) {
                    log.debug("Error processing frame: {}", e.getMessage());
                }
            }

            // Also check the main page body
            String mainHtml = page.getBody().asXml();
            extractRankingListPlayerIds(mainHtml, rankingListIds);

            Pattern playerPattern = Pattern.compile(
                    "<a[^>]*href=\"#p(\\d+)\"[^>]*id=\"p\\d+\"[^>]*target=\"Rank\"[^>]*>");
            Matcher playerMatcher = playerPattern.matcher(mainHtml);
            while (playerMatcher.find()) {
                try {
                    int id = Integer.parseInt(playerMatcher.group(1));
                    allPlayerIds.add(id);
                } catch (NumberFormatException e) {
                    // ignore
                }
            }

            // Add ranking list players
            allPlayerIds.addAll(rankingListIds);

            log.info("Found {} total player IDs for league {}", allPlayerIds.size(), league.id());
            return allPlayerIds;

        } catch (Exception e) {
            log.error("Error fetching all players: {}", e.getMessage(), e);
            return Collections.emptySet();
        }
    }

    /**
     * Extract player IDs from the "Ranking list:" section.
     * These players may have faint styling but should be treated as undrafted.
     */
    private void extractRankingListPlayerIds(String html, Set<Integer> rankingListIds) {
        // Find the "Ranking list:" section
        int rankingListStart = html.toLowerCase().indexOf("ranking list:");
        if (rankingListStart == -1) {
            return;
        }

        // Look for the end of the ranking list section
        String afterRankingList = html.substring(rankingListStart);

        // Find where the ranking list section likely ends
        int endIndex = afterRankingList.length();

        // Check for position group headers (e.g., "C:", "1B:", "P:", etc.)
        Pattern positionHeaderPattern = Pattern.compile("(?:<br[^>]*>|<p>|<div>|\\n)\\s*(?:C|1B|2B|3B|SS|OF|DH|P|SR)\\s*:", Pattern.CASE_INSENSITIVE);
        Matcher positionMatcher = positionHeaderPattern.matcher(afterRankingList);
        if (positionMatcher.find()) {
            endIndex = Math.min(endIndex, positionMatcher.start());
        }

        // Also check for other structural breaks
        String[] sectionBreaks = {"<hr", "<table", "Available players:"};
        for (String breakPattern : sectionBreaks) {
            int breakIdx = afterRankingList.toLowerCase().indexOf(breakPattern.toLowerCase());
            if (breakIdx > 0) {
                endIndex = Math.min(endIndex, breakIdx);
            }
        }

        String rankingSection = afterRankingList.substring(0, endIndex);

        // The ranking list is in a textarea with format: "166  P   Cody Ponce"
        // Extract the textarea content first
        Pattern textareaPattern = Pattern.compile("<textarea[^>]*name=\"ranks\"[^>]*>(.*?)</textarea>", Pattern.DOTALL);
        Matcher textareaMatcher = textareaPattern.matcher(rankingSection);

        if (textareaMatcher.find()) {
            String textareaContent = textareaMatcher.group(1);

            // Parse lines - format is: ID  POS  Name (e.g., "166  P   Cody Ponce")
            Pattern linePattern = Pattern.compile("^\\s*(\\d+)\\s+", Pattern.MULTILINE);
            Matcher lineMatcher = linePattern.matcher(textareaContent);
            while (lineMatcher.find()) {
                try {
                    int id = Integer.parseInt(lineMatcher.group(1));
                    rankingListIds.add(id);
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
        }

        // Also check for anchor link patterns as fallback
        Pattern playerIdPattern = Pattern.compile("href=\"#p(\\d+)\"");
        Matcher playerMatcher = playerIdPattern.matcher(rankingSection);
        while (playerMatcher.find()) {
            try {
                int id = Integer.parseInt(playerMatcher.group(1));
                rankingListIds.add(id);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
    }

    /**
     * Extract drafted player IDs (those with faint class), excluding ranking list players.
     */
    private void extractDraftedPlayerIds(String html, Set<Integer> draftedIds, Set<Integer> rankingListIds) {
        Pattern draftedPattern = Pattern.compile(
                "<a[^>]*href=\"#p(\\d+)\"[^>]*class=\"faint\"[^>]*>");
        Matcher draftedMatcher = draftedPattern.matcher(html);
        while (draftedMatcher.find()) {
            try {
                int id = Integer.parseInt(draftedMatcher.group(1));
                // Only mark as drafted if NOT in the ranking list
                if (!rankingListIds.contains(id)) {
                    draftedIds.add(id);
                }
            } catch (NumberFormatException e) {
                // ignore
            }
        }
    }

    @PreDestroy
    public void closeBrowser() {
        if (webClient != null) {
            try {
                webClient.close();
                webClient = null;
                loggedIn = false;
            } catch (Exception e) {
                log.warn("Error closing browser: {}", e.getMessage());
            }
        }
    }
}
