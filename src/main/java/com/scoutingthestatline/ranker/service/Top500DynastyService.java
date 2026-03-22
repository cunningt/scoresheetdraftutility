package com.scoutingthestatline.ranker.service;

import com.scoutingthestatline.ranker.model.Player;
import com.scoutingthestatline.ranker.model.Top500DynastyData;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class Top500DynastyService {

    private static final Logger log = LoggerFactory.getLogger(Top500DynastyService.class);

    private final PlayerMappingService playerMappingService;
    private final Map<Integer, Top500DynastyData> dynastyByScoressheetId = new HashMap<>();

    public Top500DynastyService(PlayerMappingService playerMappingService) {
        this.playerMappingService = playerMappingService;
    }

    @PostConstruct
    public void loadDynastyRankings() {
        // Try external file first, then classpath
        Path externalPath = Path.of("top500dynasty.txt");

        try {
            BufferedReader reader;
            if (Files.exists(externalPath)) {
                log.info("Loading Top 500 Dynasty rankings from external file: {}", externalPath);
                reader = Files.newBufferedReader(externalPath);
            } else {
                log.info("Loading Top 500 Dynasty rankings from classpath");
                ClassPathResource resource = new ClassPathResource("top500dynasty.txt");
                reader = new BufferedReader(new InputStreamReader(resource.getInputStream()));
            }

            int matched = 0;
            int unmatched = 0;

            try (reader) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;

                    String[] parts = line.split("\t");
                    if (parts.length < 5) continue;

                    try {
                        int rank = Integer.parseInt(parts[0].trim());
                        String name = parts[1].trim();
                        // parts[2] is the age/value
                        String position = parts[3].trim();
                        String team = parts[4].trim();

                        Top500DynastyData data = new Top500DynastyData(rank, name, position, team);

                        Optional<Player> player = playerMappingService.getByName(name);
                        if (player.isPresent()) {
                            dynastyByScoressheetId.put(player.get().scoresheetId(), data);
                            matched++;
                        } else {
                            log.warn("No scoresheet match for dynasty player: {} (rank {})", name, rank);
                            unmatched++;
                        }
                    } catch (NumberFormatException e) {
                        log.warn("Skipping invalid dynasty line: {}", line);
                    }
                }
            }

            log.info("Loaded Top 500 Dynasty rankings: {} matched, {} unmatched", matched, unmatched);

        } catch (IOException e) {
            log.warn("Could not load Top 500 Dynasty rankings: {}", e.getMessage());
        }
    }

    public Optional<Top500DynastyData> getByScoressheetId(int scoresheetId) {
        return Optional.ofNullable(dynastyByScoressheetId.get(scoresheetId));
    }

    public boolean hasData() {
        return !dynastyByScoressheetId.isEmpty();
    }
}
