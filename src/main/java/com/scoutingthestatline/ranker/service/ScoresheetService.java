package com.scoutingthestatline.ranker.service;

import com.scoutingthestatline.ranker.config.LeagueProperties;
import com.scoutingthestatline.ranker.model.League;
import org.htmlunit.BrowserVersion;
import org.htmlunit.WebClient;
import org.htmlunit.html.FrameWindow;
import org.htmlunit.html.HtmlPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ScoresheetService {

    private static final Logger log = LoggerFactory.getLogger(ScoresheetService.class);

    private final LeagueProperties leagueProperties;
    private final PlayerMappingService playerMappingService;
    private final HttpClient httpClient;

    @Value("${google.oauth.client-id:}")
    private String googleClientId;

    @Value("${google.oauth.client-secret:}")
    private String googleClientSecret;

    @Value("${google.oauth.refresh-token:}")
    private String googleRefreshToken;

    private String cachedAccessToken;
    private long accessTokenExpiry;

    private WebClient webClient;

    public ScoresheetService(LeagueProperties leagueProperties, PlayerMappingService playerMappingService) {
        this.leagueProperties = leagueProperties;
        this.playerMappingService = playerMappingService;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
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
     * Fetch drafted player IDs from all team dynamic pages or from a Google Sheet.
     * These pages are public and don't require login.
     *
     * For AL/NL leagues, the dynamic page shows AL IDs but we need to map them
     * to the league-specific ID ranges:
     * - AL batters: 0-999, NL batters: 1000-1999 (offset of 1000)
     * - AL pitchers: 4000-4999, NL pitchers: 5000-5999 (offset of 1000)
     */
    public Set<Integer> fetchDraftedPlayerIds(League league) {
        // If league has a Google Sheet for draft tracking, use that instead
        if (league.hasDraftSheet()) {
            return fetchDraftedPlayerIdsFromSheet(league);
        }

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
     * Fetch drafted player IDs from a Google Sheet using OAuth.
     * Looks for a column named "SSID" in the header row.
     */
    private Set<Integer> fetchDraftedPlayerIdsFromSheet(League league) {
        Set<Integer> draftedIds = new HashSet<>();

        // Check if OAuth is configured
        if (googleRefreshToken == null || googleRefreshToken.isEmpty()) {
            log.error("Google OAuth refresh token not configured - cannot fetch from Google Sheet");
            return draftedIds;
        }

        try {
            // Get access token
            String accessToken = getGoogleAccessToken();
            if (accessToken == null) {
                log.error("Failed to get Google access token");
                return draftedIds;
            }

            // Fetch sheet data using Google Sheets API
            String spreadsheetId = league.draftSheetId();
            String sheetGid = league.draftSheetGid();

            // Use sheet name from config, or look up by gid
            String sheetName = league.draftSheetName();
            if (sheetName == null || sheetName.isEmpty()) {
                sheetName = getSheetNameFromGid(accessToken, spreadsheetId, sheetGid);
                if (sheetName == null) {
                    sheetName = "Sheet1"; // Default fallback
                }
            }

            // Get column name from config
            String idColumnName = league.draftSheetIdColumn();
            if (idColumnName == null || idColumnName.isEmpty()) {
                idColumnName = "SSID"; // Default
            }

            // Try Sheets API first, fall back to CSV export for Excel files
            String apiUrl = String.format(
                "https://sheets.googleapis.com/v4/spreadsheets/%s/values/%s",
                spreadsheetId, URLEncoder.encode(sheetName, StandardCharsets.UTF_8)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                draftedIds = parseSheetValues(response.body(), idColumnName);
            } else if (response.statusCode() == 400 && response.body().contains("not supported for this document")) {
                // Excel file - try CSV export instead
                draftedIds = fetchDraftedIdsViaCsvExport(accessToken, spreadsheetId, sheetGid, idColumnName);
            } else {
                log.error("Google Sheets API error: {} - {}", response.statusCode(), response.body());
                return draftedIds;
            }
            log.info("Fetched {} drafted player IDs from Google Sheet for league {}", draftedIds.size(), league.id());

        } catch (Exception e) {
            log.error("Error fetching drafted players from Google Sheet: {}", e.getMessage(), e);
        }

        return draftedIds;
    }

    /**
     * Get a Google access token using the refresh token.
     */
    private String getGoogleAccessToken() {
        // Check if we have a valid cached token
        if (cachedAccessToken != null && System.currentTimeMillis() < accessTokenExpiry) {
            return cachedAccessToken;
        }

        try {
            String tokenUrl = "https://oauth2.googleapis.com/token";
            String body = String.format(
                "client_id=%s&client_secret=%s&refresh_token=%s&grant_type=refresh_token",
                URLEncoder.encode(googleClientId, StandardCharsets.UTF_8),
                URLEncoder.encode(googleClientSecret, StandardCharsets.UTF_8),
                URLEncoder.encode(googleRefreshToken, StandardCharsets.UTF_8)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Failed to refresh access token: {} - {}", response.statusCode(), response.body());
                return null;
            }

            // Parse JSON response to get access_token
            String json = response.body();
            String accessToken = extractJsonString(json, "access_token");
            int expiresIn = extractJsonInt(json, "expires_in", 3600);

            // Cache the token with some buffer time
            cachedAccessToken = accessToken;
            accessTokenExpiry = System.currentTimeMillis() + (expiresIn - 60) * 1000L;

            return accessToken;

        } catch (Exception e) {
            log.error("Error refreshing Google access token: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get sheet name from gid using the spreadsheets API.
     */
    private String getSheetNameFromGid(String accessToken, String spreadsheetId, String gid) {
        if (gid == null || gid.isEmpty()) {
            return null;
        }

        try {
            String apiUrl = String.format(
                "https://sheets.googleapis.com/v4/spreadsheets/%s?fields=sheets.properties",
                spreadsheetId
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Failed to get sheet metadata: {}", response.statusCode());
                return null;
            }

            // Parse JSON to find sheet with matching gid
            String json = response.body();
            int targetGid = Integer.parseInt(gid);

            // Simple JSON parsing for sheets array
            int sheetsStart = json.indexOf("\"sheets\"");
            if (sheetsStart == -1) return null;

            // Find each sheetId and title pair
            int searchPos = sheetsStart;
            while (true) {
                int sheetIdPos = json.indexOf("\"sheetId\"", searchPos);
                if (sheetIdPos == -1) break;

                int colonPos = json.indexOf(":", sheetIdPos);
                int commaPos = json.indexOf(",", colonPos);
                if (commaPos == -1) commaPos = json.indexOf("}", colonPos);

                String sheetIdStr = json.substring(colonPos + 1, commaPos).trim();
                int sheetId = Integer.parseInt(sheetIdStr);

                if (sheetId == targetGid) {
                    // Find the title for this sheet
                    int titlePos = json.lastIndexOf("\"title\"", sheetIdPos);
                    if (titlePos == -1) titlePos = json.indexOf("\"title\"", sheetIdPos);
                    if (titlePos != -1) {
                        int titleColonPos = json.indexOf(":", titlePos);
                        int titleQuoteStart = json.indexOf("\"", titleColonPos + 1);
                        int titleQuoteEnd = json.indexOf("\"", titleQuoteStart + 1);
                        return json.substring(titleQuoteStart + 1, titleQuoteEnd);
                    }
                }

                searchPos = commaPos + 1;
            }

        } catch (Exception e) {
            log.warn("Error getting sheet name from gid: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Fetch drafted player IDs via CSV export (works for Excel files in Google Drive).
     */
    private Set<Integer> fetchDraftedIdsViaCsvExport(String accessToken, String spreadsheetId,
                                                      String sheetGid, String idColumnName) {
        Set<Integer> ids = new HashSet<>();

        try {
            // Build CSV export URL
            String gid = (sheetGid != null && !sheetGid.isEmpty()) ? sheetGid : "0";
            String csvUrl = String.format(
                "https://docs.google.com/spreadsheets/d/%s/export?format=csv&gid=%s",
                spreadsheetId, gid
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(csvUrl))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("CSV export error: {} - {}", response.statusCode(),
                          response.body().substring(0, Math.min(500, response.body().length())));
                return ids;
            }

            // Parse CSV
            String[] lines = response.body().split("\n");
            if (lines.length == 0) {
                return ids;
            }

            // Parse header to find SSID column
            String[] header = parseCsvLine(lines[0]);
            int ssidColumnIndex = -1;
            String targetColumn = idColumnName.toUpperCase();
            for (int i = 0; i < header.length; i++) {
                String h = header[i].trim().toUpperCase();
                if (targetColumn.equals(h) || "SSID".equals(h) || "SS_ID".equals(h) ||
                    "SCORESHEET_ID".equals(h) || "SS ID".equals(h) || "SSBB".equals(h)) {
                    ssidColumnIndex = i;
                    break;
                }
            }

            if (ssidColumnIndex == -1) {
                ssidColumnIndex = 0;
            }

            // Parse data rows
            for (int i = 1; i < lines.length; i++) {
                String[] row = parseCsvLine(lines[i]);
                if (row.length > ssidColumnIndex) {
                    String cellValue = row[ssidColumnIndex].trim();
                    try {
                        int id = Integer.parseInt(cellValue);
                        if (id > 0) {
                            ids.add(id);
                        }
                    } catch (NumberFormatException e) {
                        // Skip non-numeric values
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error fetching CSV export: {}", e.getMessage(), e);
        }

        return ids;
    }

    /**
     * Parse a CSV line, handling quoted values.
     */
    private String[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                values.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        values.add(current.toString().trim());

        return values.toArray(new String[0]);
    }

    /**
     * Parse the Google Sheets API values response to extract player ID column.
     */
    private Set<Integer> parseSheetValues(String json, String idColumnName) {
        Set<Integer> ids = new HashSet<>();

        try {
            // Find the values array
            int valuesStart = json.indexOf("\"values\"");
            if (valuesStart == -1) {
                return ids;
            }

            int arrayStart = json.indexOf("[", valuesStart);
            int arrayEnd = findMatchingBracket(json, arrayStart);
            String valuesArray = json.substring(arrayStart, arrayEnd + 1);

            // Parse rows
            List<List<String>> rows = parseJsonArray(valuesArray);
            if (rows.isEmpty()) {
                return ids;
            }

            // Find ID column in header - check for configured name and common alternatives
            List<String> header = rows.get(0);
            int ssidColumnIndex = -1;
            String targetColumn = idColumnName.toUpperCase();
            for (int i = 0; i < header.size(); i++) {
                // Strip quotes and whitespace from header value
                String h = header.get(i).trim();
                if (h.startsWith("\"") && h.endsWith("\"")) {
                    h = h.substring(1, h.length() - 1);
                }
                h = h.trim().toUpperCase();
                if (targetColumn.equals(h) || "SSID".equals(h) || "SS_ID".equals(h) ||
                    "SCORESHEET_ID".equals(h) || "SS ID".equals(h) || "SSBB".equals(h)) {
                    ssidColumnIndex = i;
                    break;
                }
            }

            if (ssidColumnIndex == -1) {
                ssidColumnIndex = 0;
            }

            // Parse data rows
            for (int i = 1; i < rows.size(); i++) {
                List<String> row = rows.get(i);
                if (row.size() > ssidColumnIndex) {
                    String cellValue = row.get(ssidColumnIndex).trim();
                    try {
                        int id = Integer.parseInt(cellValue);
                        if (id > 0) {
                            ids.add(id);
                        }
                    } catch (NumberFormatException e) {
                        // Skip non-numeric values
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error parsing sheet values: {}", e.getMessage(), e);
        }

        return ids;
    }

    /**
     * Simple JSON array parser for Google Sheets values response.
     */
    private List<List<String>> parseJsonArray(String json) {
        List<List<String>> rows = new ArrayList<>();

        int pos = 1; // Skip opening [
        while (pos < json.length()) {
            // Find next row array
            int rowStart = json.indexOf("[", pos);
            if (rowStart == -1) break;

            int rowEnd = findMatchingBracket(json, rowStart);
            String rowJson = json.substring(rowStart + 1, rowEnd);

            // Parse row values
            List<String> row = new ArrayList<>();
            int valuePos = 0;
            while (valuePos < rowJson.length()) {
                // Skip whitespace (including newlines) and commas
                while (valuePos < rowJson.length()) {
                    char c = rowJson.charAt(valuePos);
                    if (c == ',' || c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                        valuePos++;
                    } else {
                        break;
                    }
                }
                if (valuePos >= rowJson.length()) break;

                if (rowJson.charAt(valuePos) == '"') {
                    // Quoted string
                    int endQuote = valuePos + 1;
                    while (endQuote < rowJson.length()) {
                        if (rowJson.charAt(endQuote) == '"' && rowJson.charAt(endQuote - 1) != '\\') {
                            break;
                        }
                        endQuote++;
                    }
                    if (endQuote >= rowJson.length()) break;
                    row.add(rowJson.substring(valuePos + 1, endQuote));
                    valuePos = endQuote + 1;
                } else {
                    // Unquoted value (number, null, etc.)
                    int endValue = valuePos;
                    while (endValue < rowJson.length() && rowJson.charAt(endValue) != ',' && rowJson.charAt(endValue) != ']') {
                        endValue++;
                    }
                    row.add(rowJson.substring(valuePos, endValue).trim());
                    valuePos = endValue;
                }
            }

            rows.add(row);
            pos = rowEnd + 1;
        }

        return rows;
    }

    private int findMatchingBracket(String json, int start) {
        int depth = 1;
        int pos = start + 1;
        boolean inString = false;

        while (pos < json.length() && depth > 0) {
            char c = json.charAt(pos);
            if (c == '"' && (pos == 0 || json.charAt(pos - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == '[') depth++;
                else if (c == ']') depth--;
            }
            pos++;
        }

        return pos - 1;
    }

    private String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int keyPos = json.indexOf(pattern);
        if (keyPos == -1) return null;

        int colonPos = json.indexOf(":", keyPos);
        int quoteStart = json.indexOf("\"", colonPos);
        int quoteEnd = json.indexOf("\"", quoteStart + 1);
        return json.substring(quoteStart + 1, quoteEnd);
    }

    private int extractJsonInt(String json, String key, int defaultValue) {
        String pattern = "\"" + key + "\"";
        int keyPos = json.indexOf(pattern);
        if (keyPos == -1) return defaultValue;

        int colonPos = json.indexOf(":", keyPos);
        int start = colonPos + 1;
        while (start < json.length() && !Character.isDigit(json.charAt(start))) start++;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;

        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
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
