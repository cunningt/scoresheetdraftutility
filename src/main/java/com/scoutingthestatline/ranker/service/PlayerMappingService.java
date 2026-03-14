package com.scoutingthestatline.ranker.service;

import com.scoutingthestatline.ranker.model.Player;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class PlayerMappingService {

    private static final Logger log = LoggerFactory.getLogger(PlayerMappingService.class);

    @Value("${scoresheet.players.url:https://www.scoresheet.com/FOR_WWW/BL_Players_2026.tsv}")
    private String playersUrl;

    private final Map<Integer, Player> playersByScoresheet = new HashMap<>();
    private final Map<Integer, Player> playersByMlbam = new HashMap<>();

    @PostConstruct
    public void loadPlayers() throws IOException {
        log.info("Downloading player mappings from {}", playersUrl);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(playersUrl))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("Failed to download player file: HTTP " + response.statusCode());
            }

            try (BufferedReader reader = new BufferedReader(new java.io.StringReader(response.body()))) {
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue; // skip header
                }

                String[] parts = line.split("\t");
                if (parts.length < 9) continue;

                try {
                    int scoresheetId = Integer.parseInt(parts[0].trim());
                    int mlbamId = Integer.parseInt(parts[1].trim());
                    String position = parts[3].trim();
                    String handedness = parts[4].trim();
                    int age = Integer.parseInt(parts[5].trim());
                    String team = parts[6].trim();
                    String firstName = parts[7].trim();
                    String lastName = parts[8].trim();

                    // Parse positional ranges (columns 9-13: 1B, 2B, 3B, SS, OF)
                    Double range1B = parseDoubleSafe(parts.length > 9 ? parts[9] : "");
                    Double range2B = parseDoubleSafe(parts.length > 10 ? parts[10] : "");
                    Double range3B = parseDoubleSafe(parts.length > 11 ? parts[11] : "");
                    Double rangeSS = parseDoubleSafe(parts.length > 12 ? parts[12] : "");
                    Double rangeOF = parseDoubleSafe(parts.length > 13 ? parts[13] : "");

                    // Parse split adjustments (columns 18-23: BAvR, OBvR, SLvR, BAvL, OBvL, SLvL)
                    Integer baVsR = parseIntSafe(parts.length > 18 ? parts[18] : "");
                    Integer obpVsR = parseIntSafe(parts.length > 19 ? parts[19] : "");
                    Integer slgVsR = parseIntSafe(parts.length > 20 ? parts[20] : "");
                    Integer baVsL = parseIntSafe(parts.length > 21 ? parts[21] : "");
                    Integer obpVsL = parseIntSafe(parts.length > 22 ? parts[22] : "");
                    Integer slgVsL = parseIntSafe(parts.length > 23 ? parts[23] : "");

                    Player player = new Player(
                            scoresheetId,
                            mlbamId,
                            firstName,
                            lastName,
                            team,
                            position,
                            handedness,
                            age,
                            range1B,
                            range2B,
                            range3B,
                            rangeSS,
                            rangeOF,
                            baVsR,
                            obpVsR,
                            slgVsR,
                            baVsL,
                            obpVsL,
                            slgVsL
                    );

                    playersByScoresheet.put(scoresheetId, player);
                    playersByMlbam.put(mlbamId, player);
                } catch (NumberFormatException e) {
                    log.warn("Skipping invalid line: {}", line);
                }
            }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }

        log.info("Loaded {} players", playersByScoresheet.size());
    }

    public Optional<Player> getByScoressheetId(int scoresheetId) {
        return Optional.ofNullable(playersByScoresheet.get(scoresheetId));
    }

    public Optional<Player> getByMlbamId(int mlbamId) {
        return Optional.ofNullable(playersByMlbam.get(mlbamId));
    }

    public int getMlbamId(int scoresheetId) {
        Player player = playersByScoresheet.get(scoresheetId);
        return player != null ? player.mlbamId() : 0;
    }

    public Set<Integer> getAllScoressheetIds() {
        return new HashSet<>(playersByScoresheet.keySet());
    }

    private Double parseDoubleSafe(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseIntSafe(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
