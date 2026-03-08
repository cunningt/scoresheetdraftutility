package com.scoutingthestatline.ranker.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import com.scoutingthestatline.ranker.model.BattingProjection;
import com.scoutingthestatline.ranker.model.PitchingProjection;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@Service
public class ProjectionService {

    @Value("${scoresheet.projections.dir:.}")
    private String projectionsDir;

    private static final List<String> PROJECTION_SYSTEMS = List.of("oopsy", "steamer", "zips");

    // Map: projection system -> mlbamId -> projection
    private final Map<String, Map<Integer, BattingProjection>> battingProjections = new HashMap<>();
    private final Map<String, Map<Integer, PitchingProjection>> pitchingProjections = new HashMap<>();

    @PostConstruct
    public void loadProjections() throws IOException, CsvException {
        for (String system : PROJECTION_SYSTEMS) {
            loadBattingProjections(system);
            loadPitchingProjections(system);
        }
    }

    private void loadBattingProjections(String system) throws IOException, CsvException {
        String filename = system + "-batting-projections.csv";
        Path filePath = Path.of(projectionsDir, filename);

        if (!filePath.toFile().exists()) {
            log.warn("Batting projections file not found: {}", filePath);
            return;
        }

        log.info("Loading batting projections from {}", filePath);
        Map<Integer, BattingProjection> projections = new HashMap<>();

        try (CSVReader reader = new CSVReader(new FileReader(filePath.toFile()))) {
            List<String[]> rows = reader.readAll();
            if (rows.isEmpty()) return;

            String[] header = rows.get(0);
            Map<String, Integer> colIndex = new HashMap<>();
            for (int i = 0; i < header.length; i++) {
                colIndex.put(header[i].replace("\uFEFF", "").trim(), i);
            }

            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                try {
                    int mlbamId = parseIntSafe(getColumn(row, colIndex, "MLBAMID"));
                    if (mlbamId == 0) continue;

                    BattingProjection proj = BattingProjection.builder()
                            .mlbamId(mlbamId)
                            .name(getColumn(row, colIndex, "Name"))
                            .team(getColumn(row, colIndex, "Team"))
                            .projectionSystem(system)
                            .games(parseDoubleSafe(getColumn(row, colIndex, "G")))
                            .plateAppearances(parseDoubleSafe(getColumn(row, colIndex, "PA")))
                            .atBats(parseDoubleSafe(getColumn(row, colIndex, "AB")))
                            .hits(parseDoubleSafe(getColumn(row, colIndex, "H")))
                            .doubles(parseDoubleSafe(getColumn(row, colIndex, "2B")))
                            .triples(parseDoubleSafe(getColumn(row, colIndex, "3B")))
                            .homeRuns(parseDoubleSafe(getColumn(row, colIndex, "HR")))
                            .runs(parseDoubleSafe(getColumn(row, colIndex, "R")))
                            .rbi(parseDoubleSafe(getColumn(row, colIndex, "RBI")))
                            .walks(parseDoubleSafe(getColumn(row, colIndex, "BB")))
                            .strikeouts(parseDoubleSafe(getColumn(row, colIndex, "SO")))
                            .stolenBases(parseDoubleSafe(getColumn(row, colIndex, "SB")))
                            .caughtStealing(parseDoubleSafe(getColumn(row, colIndex, "CS")))
                            .avg(parseDoubleSafe(getColumn(row, colIndex, "AVG")))
                            .obp(parseDoubleSafe(getColumn(row, colIndex, "OBP")))
                            .slg(parseDoubleSafe(getColumn(row, colIndex, "SLG")))
                            .ops(parseDoubleSafe(getColumn(row, colIndex, "OPS")))
                            .wOBA(parseDoubleSafe(getColumn(row, colIndex, "wOBA")))
                            .wRCPlus(parseDoubleSafe(getColumn(row, colIndex, "wRC+")))
                            .bbPct(parseDoubleSafe(getColumn(row, colIndex, "BB%")))
                            .kPct(parseDoubleSafe(getColumn(row, colIndex, "K%")))
                            .iso(parseDoubleSafe(getColumn(row, colIndex, "ISO")))
                            .babip(parseDoubleSafe(getColumn(row, colIndex, "BABIP")))
                            .war(parseDoubleSafe(getColumn(row, colIndex, "WAR")))
                            .off(parseDoubleSafe(getColumn(row, colIndex, "Off")))
                            .def(parseDoubleSafe(getColumn(row, colIndex, "Fld")))
                            .fpts(parseDoubleSafe(getColumn(row, colIndex, "FPTS")))
                            .spts(parseDoubleSafe(getColumn(row, colIndex, "SPTS")))
                            .build();

                    projections.put(mlbamId, proj);
                } catch (Exception e) {
                    log.warn("Error parsing batting row {}: {}", i, e.getMessage());
                }
            }
        }

        battingProjections.put(system, projections);
        log.info("Loaded {} {} batting projections", projections.size(), system);
    }

    private void loadPitchingProjections(String system) throws IOException, CsvException {
        String filename = system + "-pitching-projections.csv";
        Path filePath = Path.of(projectionsDir, filename);

        if (!filePath.toFile().exists()) {
            log.warn("Pitching projections file not found: {}", filePath);
            return;
        }

        log.info("Loading pitching projections from {}", filePath);
        Map<Integer, PitchingProjection> projections = new HashMap<>();

        try (CSVReader reader = new CSVReader(new FileReader(filePath.toFile()))) {
            List<String[]> rows = reader.readAll();
            if (rows.isEmpty()) return;

            String[] header = rows.get(0);
            Map<String, Integer> colIndex = new HashMap<>();
            for (int i = 0; i < header.length; i++) {
                colIndex.put(header[i].replace("\uFEFF", "").trim(), i);
            }

            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                try {
                    int mlbamId = parseIntSafe(getColumn(row, colIndex, "MLBAMID"));
                    if (mlbamId == 0) continue;

                    PitchingProjection proj = PitchingProjection.builder()
                            .mlbamId(mlbamId)
                            .name(getColumn(row, colIndex, "Name"))
                            .team(getColumn(row, colIndex, "Team"))
                            .projectionSystem(system)
                            .wins(parseDoubleSafe(getColumn(row, colIndex, "W")))
                            .losses(parseDoubleSafe(getColumn(row, colIndex, "L")))
                            .qualityStarts(parseDoubleSafe(getColumn(row, colIndex, "QS")))
                            .games(parseDoubleSafe(getColumn(row, colIndex, "G")))
                            .gamesStarted(parseDoubleSafe(getColumn(row, colIndex, "GS")))
                            .saves(parseDoubleSafe(getColumn(row, colIndex, "SV")))
                            .holds(parseDoubleSafe(getColumn(row, colIndex, "HLD")))
                            .inningsPitched(parseDoubleSafe(getColumn(row, colIndex, "IP")))
                            .hits(parseDoubleSafe(getColumn(row, colIndex, "H")))
                            .runs(parseDoubleSafe(getColumn(row, colIndex, "R")))
                            .earnedRuns(parseDoubleSafe(getColumn(row, colIndex, "ER")))
                            .homeRuns(parseDoubleSafe(getColumn(row, colIndex, "HR")))
                            .walks(parseDoubleSafe(getColumn(row, colIndex, "BB")))
                            .strikeouts(parseDoubleSafe(getColumn(row, colIndex, "SO")))
                            .era(parseDoubleSafe(getColumn(row, colIndex, "ERA")))
                            .whip(parseDoubleSafe(getColumn(row, colIndex, "WHIP")))
                            .k9(parseDoubleSafe(getColumn(row, colIndex, "K/9")))
                            .bb9(parseDoubleSafe(getColumn(row, colIndex, "BB/9")))
                            .kPerBB(parseDoubleSafe(getColumn(row, colIndex, "K/BB")))
                            .kMinusBbPct(parseDoubleSafe(getColumn(row, colIndex, "K-BB%")))
                            .fip(parseDoubleSafe(getColumn(row, colIndex, "FIP")))
                            .war(parseDoubleSafe(getColumn(row, colIndex, "WAR")))
                            .fpts(parseDoubleSafe(getColumn(row, colIndex, "FPTS")))
                            .spts(parseDoubleSafe(getColumn(row, colIndex, "SPTS")))
                            .adp(parseDoubleSafe(getColumn(row, colIndex, "ADP")))
                            .build();

                    projections.put(mlbamId, proj);
                } catch (Exception e) {
                    log.warn("Error parsing pitching row {}: {}", i, e.getMessage());
                }
            }
        }

        pitchingProjections.put(system, projections);
        log.info("Loaded {} {} pitching projections", projections.size(), system);
    }

    private String getColumn(String[] row, Map<String, Integer> colIndex, String columnName) {
        Integer idx = colIndex.get(columnName);
        if (idx == null || idx >= row.length) return "";
        return row[idx].trim();
    }

    private double parseDoubleSafe(String value) {
        if (value == null || value.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private int parseIntSafe(String value) {
        if (value == null || value.isEmpty()) return 0;
        try {
            return (int) Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public List<String> getProjectionSystems() {
        return PROJECTION_SYSTEMS;
    }

    public Optional<BattingProjection> getBattingProjection(String system, int mlbamId) {
        Map<Integer, BattingProjection> projections = battingProjections.get(system);
        if (projections == null) return Optional.empty();
        return Optional.ofNullable(projections.get(mlbamId));
    }

    public Optional<PitchingProjection> getPitchingProjection(String system, int mlbamId) {
        Map<Integer, PitchingProjection> projections = pitchingProjections.get(system);
        if (projections == null) return Optional.empty();
        return Optional.ofNullable(projections.get(mlbamId));
    }
}
