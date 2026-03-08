package com.scoutingthestatline.ranker.service;

import com.scoutingthestatline.ranker.config.LeagueProperties;
import com.scoutingthestatline.ranker.model.League;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.htmlunit.BrowserVersion;
import org.htmlunit.WebClient;
import org.htmlunit.html.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScoresheetService {

    private final LeagueProperties leagueProperties;

    @Value("${scoresheet.login.firstname:}")
    private String firstName;

    @Value("${scoresheet.login.lastname:}")
    private String lastName;

    @Value("${scoresheet.login.password:}")
    private String password;

    private WebClient webClient;
    private boolean loggedIn = false;

    public List<League> getLeagues() {
        return leagueProperties.toLeagues();
    }

    public Optional<League> getLeague(String leagueId) {
        return getLeagues().stream()
                .filter(l -> l.getId().equals(leagueId))
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

            // Get all frames on the page
            List<FrameWindow> frames = page.getFrames();
            log.info("Found {} frames on page", frames.size());

            for (FrameWindow frame : frames) {
                try {
                    HtmlPage framePage = (HtmlPage) frame.getEnclosedPage();
                    String frameHtml = framePage.getBody().asXml();

                    // Pattern for drafted players (with class="faint")
                    Pattern draftedPattern = Pattern.compile(
                            "<a[^>]*href=\"#p(\\d+)\"[^>]*class=\"faint\"[^>]*>");
                    Matcher draftedMatcher = draftedPattern.matcher(frameHtml);
                    while (draftedMatcher.find()) {
                        try {
                            int id = Integer.parseInt(draftedMatcher.group(1));
                            draftedIds.add(id);
                        } catch (NumberFormatException e) {
                            // ignore
                        }
                    }

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

            Pattern draftedPattern = Pattern.compile(
                    "<a[^>]*href=\"#p(\\d+)\"[^>]*class=\"faint\"[^>]*>");
            Matcher draftedMatcher = draftedPattern.matcher(mainHtml);
            while (draftedMatcher.find()) {
                try {
                    int id = Integer.parseInt(draftedMatcher.group(1));
                    draftedIds.add(id);
                } catch (NumberFormatException e) {
                    // ignore
                }
            }

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

            log.info("Found {} undrafted player IDs for league {} (excluded {} drafted)",
                    playerIds.size(), league.getId(), draftedIds.size());
            return playerIds;

        } catch (Exception e) {
            log.error("Error fetching undrafted players: {}", e.getMessage(), e);
            return Collections.emptySet();
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
