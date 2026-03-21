package com.scoutingthestatline.ranker.service;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;
import com.scoutingthestatline.ranker.model.ADPData;
import com.scoutingthestatline.ranker.model.BattingProjection;
import com.scoutingthestatline.ranker.model.PitcherList400Data;
import com.scoutingthestatline.ranker.model.PitchingProjection;
import com.scoutingthestatline.ranker.model.SavantBattingStats;
import com.scoutingthestatline.ranker.model.SavantPitchingStats;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class ProjectionService {

    private static final Logger log = LoggerFactory.getLogger(ProjectionService.class);

    private static final List<String> PROJECTION_SYSTEMS = List.of("oopsy", "oopsy-peak", "steamer", "zips", "spring", "savant", "adp", "pitcherlist400");

    // Map: projection system -> mlbamId -> projection
    private final Map<String, Map<Integer, BattingProjection>> battingProjections = new HashMap<>();
    private final Map<String, Map<Integer, PitchingProjection>> pitchingProjections = new HashMap<>();

    // Savant stats (keyed by mlbamId)
    private final Map<Integer, SavantBattingStats> savantBattingStats = new HashMap<>();
    private final Map<Integer, SavantPitchingStats> savantPitchingStats = new HashMap<>();

    // ADP data (keyed by mlbamId)
    private final Map<Integer, ADPData> adpData = new HashMap<>();

    // Pitcher List 400 data (keyed by mlbamId)
    private final Map<Integer, PitcherList400Data> pitcherList400Data = new HashMap<>();

    // NFBC ID to MLB ID mapping
    private final Map<Integer, Integer> nfbcToMlbId = new HashMap<>();

    // Active roster players (MLB ID -> section/category)
    private final Map<Integer, String> activeRosterPlayers = new HashMap<>();

    @PostConstruct
    public void loadProjections() throws IOException, CsvException {
        // Load NFBC to MLB ID mapping first (needed for ADP)
        loadNfbcMapping();

        // Load active roster data
        loadActiveRosterData();

        for (String system : PROJECTION_SYSTEMS) {
            if ("savant".equals(system)) {
                loadSavantBattingStats();
                loadSavantPitchingStats();
            } else if ("adp".equals(system)) {
                loadADPData();
            } else if ("pitcherlist400".equals(system)) {
                loadPitcherList400Data();
            } else if ("spring".equals(system)) {
                loadSpringTrainingBatting();
                loadSpringTrainingPitching();
            } else {
                loadBattingProjections(system);
                loadPitchingProjections(system);
            }
        }
    }

    private String getProjectionFilename(String system, String type) {
        if ("oopsy-peak".equals(system)) {
            return "oopsy-peakprojections-" + type + ".csv";
        }
        return system + "-" + type + "-projections.csv";
    }

    private void loadBattingProjections(String system) throws IOException, CsvException {
        String filename = getProjectionFilename(system, "batting");
        Resource resource = new ClassPathResource(filename);

        if (!resource.exists()) {
            log.warn("Batting projections file not found: {}", filename);
            return;
        }

        log.info("Loading batting projections from classpath: {}", filename);
        Map<Integer, BattingProjection> projections = new HashMap<>();

        try (Reader fileReader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             CSVReader reader = new CSVReader(fileReader)) {
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

                    BattingProjection proj = new BattingProjection(
                            mlbamId,
                            getColumn(row, colIndex, "Name"),
                            getColumn(row, colIndex, "Team"),
                            system,
                            parseDoubleSafe(getColumn(row, colIndex, "G")),
                            parseDoubleSafe(getColumn(row, colIndex, "PA")),
                            parseDoubleSafe(getColumn(row, colIndex, "AB")),
                            parseDoubleSafe(getColumn(row, colIndex, "H")),
                            parseDoubleSafe(getColumn(row, colIndex, "2B")),
                            parseDoubleSafe(getColumn(row, colIndex, "3B")),
                            parseDoubleSafe(getColumn(row, colIndex, "HR")),
                            parseDoubleSafe(getColumn(row, colIndex, "R")),
                            parseDoubleSafe(getColumn(row, colIndex, "RBI")),
                            parseDoubleSafe(getColumn(row, colIndex, "BB")),
                            parseDoubleSafe(getColumn(row, colIndex, "SO")),
                            parseDoubleSafe(getColumn(row, colIndex, "SB")),
                            parseDoubleSafe(getColumn(row, colIndex, "CS")),
                            parseDoubleSafe(getColumn(row, colIndex, "AVG")),
                            parseDoubleSafe(getColumn(row, colIndex, "OBP")),
                            parseDoubleSafe(getColumn(row, colIndex, "SLG")),
                            parseDoubleSafe(getColumn(row, colIndex, "OPS")),
                            parseDoubleSafe(getColumn(row, colIndex, "wOBA")),
                            parseDoubleSafe(getColumn(row, colIndex, "wRC+")),
                            parseDoubleSafe(getColumn(row, colIndex, "BB%")),
                            parseDoubleSafe(getColumn(row, colIndex, "K%")),
                            parseDoubleSafe(getColumn(row, colIndex, "ISO")),
                            parseDoubleSafe(getColumn(row, colIndex, "BABIP")),
                            parseDoubleSafe(getColumn(row, colIndex, "WAR")),
                            parseDoubleSafe(getColumn(row, colIndex, "Off")),
                            parseDoubleSafe(getColumn(row, colIndex, "Fld")),
                            parseDoubleSafe(getColumn(row, colIndex, "UBR")),
                            parseDoubleSafe(getColumn(row, colIndex, "BsR")),
                            parseDoubleSafe(getColumn(row, colIndex, "FPTS")),
                            parseDoubleSafe(getColumn(row, colIndex, "SPTS"))
                    );

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
        String filename = getProjectionFilename(system, "pitching");
        Resource resource = new ClassPathResource(filename);

        if (!resource.exists()) {
            log.warn("Pitching projections file not found: {}", filename);
            return;
        }

        log.info("Loading pitching projections from classpath: {}", filename);
        Map<Integer, PitchingProjection> projections = new HashMap<>();

        try (Reader fileReader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             CSVReader reader = new CSVReader(fileReader)) {
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

                    PitchingProjection proj = new PitchingProjection(
                            mlbamId,
                            getColumn(row, colIndex, "Name"),
                            getColumn(row, colIndex, "Team"),
                            system,
                            parseDoubleSafe(getColumn(row, colIndex, "W")),
                            parseDoubleSafe(getColumn(row, colIndex, "L")),
                            parseDoubleSafe(getColumn(row, colIndex, "QS")),
                            parseDoubleSafe(getColumn(row, colIndex, "G")),
                            parseDoubleSafe(getColumn(row, colIndex, "GS")),
                            parseDoubleSafe(getColumn(row, colIndex, "SV")),
                            parseDoubleSafe(getColumn(row, colIndex, "HLD")),
                            parseDoubleSafe(getColumn(row, colIndex, "IP")),
                            parseDoubleSafe(getColumn(row, colIndex, "H")),
                            parseDoubleSafe(getColumn(row, colIndex, "R")),
                            parseDoubleSafe(getColumn(row, colIndex, "ER")),
                            parseDoubleSafe(getColumn(row, colIndex, "HR")),
                            parseDoubleSafe(getColumn(row, colIndex, "BB")),
                            parseDoubleSafe(getColumn(row, colIndex, "SO")),
                            parseDoubleSafe(getColumn(row, colIndex, "ERA")),
                            parseDoubleSafe(getColumn(row, colIndex, "WHIP")),
                            parseDoubleSafe(getColumn(row, colIndex, "K/9")),
                            parseDoubleSafe(getColumn(row, colIndex, "BB/9")),
                            parseDoubleSafe(getColumn(row, colIndex, "K/BB")),
                            parseDoubleSafe(getColumn(row, colIndex, "K-BB%")),
                            parseDoubleSafe(getColumn(row, colIndex, "FIP")),
                            parseDoubleSafe(getColumn(row, colIndex, "WAR")),
                            parseDoubleSafe(getColumn(row, colIndex, "FPTS")),
                            parseDoubleSafe(getColumn(row, colIndex, "SPTS")),
                            parseDoubleSafe(getColumn(row, colIndex, "ADP"))
                    );

                    projections.put(mlbamId, proj);
                } catch (Exception e) {
                    log.warn("Error parsing pitching row {}: {}", i, e.getMessage());
                }
            }
        }

        pitchingProjections.put(system, projections);
        log.info("Loaded {} {} pitching projections", projections.size(), system);
    }

    private void loadSpringTrainingBatting() throws IOException, CsvException {
        String filename = "spring-training-batting.csv";
        Resource resource = new ClassPathResource(filename);

        if (!resource.exists()) {
            log.warn("Spring training batting file not found: {}", filename);
            return;
        }

        log.info("Loading spring training batting stats from classpath: {}", filename);
        Map<Integer, BattingProjection> projections = new HashMap<>();

        try (Reader fileReader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             CSVReader reader = new CSVReader(fileReader)) {
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

                    double pa = parseDoubleSafe(getColumn(row, colIndex, "PA"));
                    double ab = parseDoubleSafe(getColumn(row, colIndex, "AB"));
                    double h = parseDoubleSafe(getColumn(row, colIndex, "H"));
                    double bb = parseDoubleSafe(getColumn(row, colIndex, "BB"));
                    double so = parseDoubleSafe(getColumn(row, colIndex, "SO"));
                    double avg = parseDoubleSafe(getColumn(row, colIndex, "AVG"));

                    // Calculate OBP: (H + BB + HBP) / (AB + BB + HBP + SF)
                    double hbp = parseDoubleSafe(getColumn(row, colIndex, "HBP"));
                    double sf = parseDoubleSafe(getColumn(row, colIndex, "SF"));
                    double obp = (ab + bb + hbp + sf) > 0 ? (h + bb + hbp) / (ab + bb + hbp + sf) : 0;

                    // Calculate SLG
                    double singles = parseDoubleSafe(getColumn(row, colIndex, "1B"));
                    double doubles = parseDoubleSafe(getColumn(row, colIndex, "2B"));
                    double triples = parseDoubleSafe(getColumn(row, colIndex, "3B"));
                    double hr = parseDoubleSafe(getColumn(row, colIndex, "HR"));
                    double slg = ab > 0 ? (singles + 2*doubles + 3*triples + 4*hr) / ab : 0;
                    double iso = slg - avg;

                    // Calculate rate stats
                    double bbPct = pa > 0 ? bb / pa : 0;
                    double kPct = pa > 0 ? so / pa : 0;

                    BattingProjection proj = new BattingProjection(
                            mlbamId,
                            getColumn(row, colIndex, "Name"),
                            getColumn(row, colIndex, "Team"),
                            "spring",
                            parseDoubleSafe(getColumn(row, colIndex, "G")),
                            pa,
                            ab,
                            h,
                            doubles,
                            triples,
                            hr,
                            parseDoubleSafe(getColumn(row, colIndex, "R")),
                            parseDoubleSafe(getColumn(row, colIndex, "RBI")),
                            bb,
                            so,
                            parseDoubleSafe(getColumn(row, colIndex, "SB")),
                            parseDoubleSafe(getColumn(row, colIndex, "CS")),
                            avg,
                            obp,
                            slg,
                            obp + slg, // OPS
                            0, // wOBA - not available
                            0, // wRC+ - not available
                            bbPct,
                            kPct,
                            iso,
                            0, // BABIP - not available
                            0, // WAR - not available
                            0, // Off - not available
                            0, // Fld - not available
                            0, // UBR - not available
                            0, // BsR - not available
                            0, // FPTS
                            0  // SPTS
                    );

                    projections.put(mlbamId, proj);
                } catch (Exception e) {
                    log.warn("Error parsing spring training batting row {}: {}", i, e.getMessage());
                }
            }
        }

        battingProjections.put("spring", projections);
        log.info("Loaded {} spring training batting stats", projections.size());
    }

    private void loadSpringTrainingPitching() throws IOException, CsvException {
        String filename = "spring-training-pitching.csv";
        Resource resource = new ClassPathResource(filename);

        if (!resource.exists()) {
            log.warn("Spring training pitching file not found: {}", filename);
            return;
        }

        log.info("Loading spring training pitching stats from classpath: {}", filename);
        Map<Integer, PitchingProjection> projections = new HashMap<>();

        try (Reader fileReader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             CSVReader reader = new CSVReader(fileReader)) {
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

                    double ip = parseDoubleSafe(getColumn(row, colIndex, "IP"));
                    double bb = parseDoubleSafe(getColumn(row, colIndex, "BB"));
                    double so = parseDoubleSafe(getColumn(row, colIndex, "SO"));
                    double h = parseDoubleSafe(getColumn(row, colIndex, "H"));
                    double er = parseDoubleSafe(getColumn(row, colIndex, "ER"));
                    double hr = parseDoubleSafe(getColumn(row, colIndex, "HR"));

                    // Calculate rate stats
                    double era = parseDoubleSafe(getColumn(row, colIndex, "ERA"));
                    double whip = ip > 0 ? (bb + h) / ip : 0;
                    double k9 = ip > 0 ? (so * 9) / ip : 0;
                    double bb9 = ip > 0 ? (bb * 9) / ip : 0;
                    double kbb = bb > 0 ? so / bb : so;

                    // Calculate K-BB% (using TBF if available)
                    double tbf = parseDoubleSafe(getColumn(row, colIndex, "TBF"));
                    double kbbPct = tbf > 0 ? (so - bb) / tbf : 0;

                    // Calculate FIP: ((13*HR + 3*BB - 2*K) / IP) + constant (use 3.2)
                    double fip = ip > 0 ? ((13*hr + 3*bb - 2*so) / ip) + 3.2 : 0;

                    PitchingProjection proj = new PitchingProjection(
                            mlbamId,
                            getColumn(row, colIndex, "Name"),
                            getColumn(row, colIndex, "Team"),
                            "spring",
                            parseDoubleSafe(getColumn(row, colIndex, "W")),
                            parseDoubleSafe(getColumn(row, colIndex, "L")),
                            0, // QS - not available
                            parseDoubleSafe(getColumn(row, colIndex, "G")),
                            parseDoubleSafe(getColumn(row, colIndex, "GS")),
                            parseDoubleSafe(getColumn(row, colIndex, "SV")),
                            parseDoubleSafe(getColumn(row, colIndex, "HLD")),
                            ip,
                            h,
                            parseDoubleSafe(getColumn(row, colIndex, "R")),
                            er,
                            hr,
                            bb,
                            so,
                            era,
                            whip,
                            k9,
                            bb9,
                            kbb,
                            kbbPct,
                            fip,
                            0, // WAR - not available
                            0, // FPTS
                            0, // SPTS
                            0  // ADP
                    );

                    projections.put(mlbamId, proj);
                } catch (Exception e) {
                    log.warn("Error parsing spring training pitching row {}: {}", i, e.getMessage());
                }
            }
        }

        pitchingProjections.put("spring", projections);
        log.info("Loaded {} spring training pitching stats", projections.size());
    }

    private void loadSavantBattingStats() throws IOException, CsvException {
        String filename = "savant-batting.csv";
        Resource resource = new ClassPathResource(filename);

        if (!resource.exists()) {
            log.warn("Savant batting stats file not found: {}", filename);
            return;
        }

        log.info("Loading Savant batting stats from classpath: {}", filename);

        try (Reader fileReader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             CSVReader reader = new CSVReader(fileReader)) {
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
                    int mlbamId = parseIntSafe(getColumn(row, colIndex, "player_id"));
                    if (mlbamId == 0) continue;

                    String name = getColumn(row, colIndex, "last_name, first_name");
                    // Convert "Last, First" to "First Last"
                    if (name.contains(",")) {
                        String[] parts = name.split(",", 2);
                        name = parts[1].trim() + " " + parts[0].trim();
                    }

                    SavantBattingStats stats = new SavantBattingStats(
                            mlbamId,
                            name,
                            parseIntSafe(getColumn(row, colIndex, "attempts")),
                            parseDoubleSafe(getColumn(row, colIndex, "avg_hit_angle")),
                            parseDoubleSafe(getColumn(row, colIndex, "anglesweetspotpercent")),
                            parseDoubleSafe(getColumn(row, colIndex, "max_hit_speed")),
                            parseDoubleSafe(getColumn(row, colIndex, "avg_hit_speed")),
                            parseDoubleSafe(getColumn(row, colIndex, "ev50")),
                            parseDoubleSafe(getColumn(row, colIndex, "fbld")),
                            parseDoubleSafe(getColumn(row, colIndex, "gb")),
                            parseDoubleSafe(getColumn(row, colIndex, "max_distance")),
                            parseDoubleSafe(getColumn(row, colIndex, "avg_distance")),
                            parseDoubleSafe(getColumn(row, colIndex, "avg_hr_distance")),
                            parseIntSafe(getColumn(row, colIndex, "ev95plus")),
                            parseDoubleSafe(getColumn(row, colIndex, "ev95percent")),
                            parseIntSafe(getColumn(row, colIndex, "barrels")),
                            parseDoubleSafe(getColumn(row, colIndex, "brl_percent")),
                            parseDoubleSafe(getColumn(row, colIndex, "brl_pa"))
                    );

                    savantBattingStats.put(mlbamId, stats);
                } catch (Exception e) {
                    log.warn("Error parsing Savant batting row {}: {}", i, e.getMessage());
                }
            }
        }

        log.info("Loaded {} Savant batting stats", savantBattingStats.size());
    }

    private void loadSavantPitchingStats() throws IOException, CsvException {
        String filename = "savant-pitching.csv";
        Resource resource = new ClassPathResource(filename);

        if (!resource.exists()) {
            log.warn("Savant pitching stats file not found: {}", filename);
            return;
        }

        log.info("Loading Savant pitching stats from classpath: {}", filename);

        try (Reader fileReader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             CSVReader reader = new CSVReader(fileReader)) {
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
                    int mlbamId = parseIntSafe(getColumn(row, colIndex, "player_id"));
                    if (mlbamId == 0) continue;

                    SavantPitchingStats stats = new SavantPitchingStats(
                            mlbamId,
                            getColumn(row, colIndex, "player_name"),
                            parseIntSafe(getColumn(row, colIndex, "year")),
                            parseIntSafe(getColumn(row, colIndex, "xwoba")),
                            parseIntSafe(getColumn(row, colIndex, "xba")),
                            parseIntSafe(getColumn(row, colIndex, "xslg")),
                            parseIntSafe(getColumn(row, colIndex, "xiso")),
                            parseIntSafe(getColumn(row, colIndex, "xobp")),
                            parseIntSafe(getColumn(row, colIndex, "brl")),
                            parseIntSafe(getColumn(row, colIndex, "brl_percent")),
                            parseIntSafe(getColumn(row, colIndex, "exit_velocity")),
                            parseIntSafe(getColumn(row, colIndex, "max_ev")),
                            parseIntSafe(getColumn(row, colIndex, "hard_hit_percent")),
                            parseIntSafe(getColumn(row, colIndex, "k_percent")),
                            parseIntSafe(getColumn(row, colIndex, "bb_percent")),
                            parseIntSafe(getColumn(row, colIndex, "whiff_percent")),
                            parseIntSafe(getColumn(row, colIndex, "chase_percent")),
                            parseIntSafe(getColumn(row, colIndex, "arm_strength")),
                            parseIntSafe(getColumn(row, colIndex, "xera")),
                            parseIntSafe(getColumn(row, colIndex, "fb_velocity")),
                            parseIntSafe(getColumn(row, colIndex, "fb_spin")),
                            parseIntSafe(getColumn(row, colIndex, "curve_spin"))
                    );

                    savantPitchingStats.put(mlbamId, stats);
                } catch (Exception e) {
                    log.warn("Error parsing Savant pitching row {}: {}", i, e.getMessage());
                }
            }
        }

        log.info("Loaded {} Savant pitching stats", savantPitchingStats.size());
    }

    private void loadNfbcMapping() throws IOException, CsvException {
        String filename = "sfbbplayeridmap.csv";
        Resource resource = new ClassPathResource(filename);

        if (!resource.exists()) {
            log.warn("NFBC player ID mapping file not found: {}", filename);
            return;
        }

        log.info("Loading NFBC to MLB ID mapping from classpath: {}", filename);

        try (Reader fileReader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             CSVReader reader = new CSVReader(fileReader)) {
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
                    int nfbcId = parseIntSafe(getColumn(row, colIndex, "NFBCID"));
                    int mlbId = parseIntSafe(getColumn(row, colIndex, "MLBID"));

                    if (nfbcId > 0 && mlbId > 0) {
                        nfbcToMlbId.put(nfbcId, mlbId);
                    }
                } catch (Exception e) {
                    // Skip invalid rows silently
                }
            }
        }

        log.info("Loaded {} NFBC to MLB ID mappings", nfbcToMlbId.size());
    }

    private void loadADPData() throws IOException, CsvException {
        String filename = "ADP.tsv";
        Resource resource = new ClassPathResource(filename);

        if (!resource.exists()) {
            log.warn("ADP file not found: {}", filename);
            return;
        }

        log.info("Loading ADP data from classpath: {}", filename);

        CSVParser tsvParser = new CSVParserBuilder().withSeparator('\t').build();
        try (Reader fileReader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             CSVReader reader = new CSVReaderBuilder(fileReader).withCSVParser(tsvParser).build()) {
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
                    int rank = parseIntSafe(getColumn(row, colIndex, "Rank"));
                    int nfbcId = parseIntSafe(getColumn(row, colIndex, "Player ID"));
                    String name = getColumn(row, colIndex, "Player");
                    String team = getColumn(row, colIndex, "Team");
                    String positions = getColumn(row, colIndex, "Position(s)");
                    double adp = parseDoubleSafe(getColumn(row, colIndex, "ADP"));
                    int minPick = parseIntSafe(getColumn(row, colIndex, "Min Pick"));
                    int maxPick = parseIntSafe(getColumn(row, colIndex, "Max Pick"));

                    // Map NFBC ID to MLB ID
                    Integer mlbId = nfbcToMlbId.get(nfbcId);
                    if (mlbId == null || mlbId == 0) {
                        log.debug("No MLB ID mapping for NFBC ID {} ({})", nfbcId, name);
                        continue;
                    }

                    ADPData adpEntry = new ADPData(
                            mlbId,
                            nfbcId,
                            rank,
                            adp,
                            name,
                            team,
                            positions,
                            minPick,
                            maxPick
                    );

                    adpData.put(mlbId, adpEntry);
                } catch (Exception e) {
                    log.warn("Error parsing ADP row {}: {}", i, e.getMessage());
                }
            }
        }

        log.info("Loaded {} ADP entries", adpData.size());
    }

    private void loadActiveRosterData() throws IOException, CsvException {
        String filename = "rosterresource.csv";
        Resource resource = new ClassPathResource(filename);

        if (!resource.exists()) {
            log.warn("Roster resource file not found: {}", filename);
            return;
        }

        log.info("Loading active roster data from classpath: {}", filename);

        try (Reader fileReader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             CSVReader reader = new CSVReader(fileReader)) {
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
                    int mlbId = parseIntSafe(getColumn(row, colIndex, "mlb_id"));
                    String section = getColumn(row, colIndex, "section");
                    if (mlbId > 0 && !section.isEmpty()) {
                        activeRosterPlayers.put(mlbId, section);
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        log.info("Loaded {} active roster players", activeRosterPlayers.size());
    }

    private void loadPitcherList400Data() throws IOException, CsvException {
        String filename = "pitcherlist400.csv";
        Resource resource = new ClassPathResource(filename);

        if (!resource.exists()) {
            log.warn("Pitcher List 400 file not found: {}", filename);
            return;
        }

        log.info("Loading Pitcher List 400 data from classpath: {}", filename);

        try (Reader fileReader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
             CSVReader reader = new CSVReader(fileReader)) {
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
                    int mlbId = parseIntSafe(getColumn(row, colIndex, "mlbid"));
                    if (mlbId == 0) continue;

                    PitcherList400Data data = new PitcherList400Data(
                            mlbId,
                            parseIntSafe(getColumn(row, colIndex, "rank")),
                            getColumn(row, colIndex, "name"),
                            parseIntSafe(getColumn(row, colIndex, "tier_num")),
                            getColumn(row, colIndex, "tier_name"),
                            getColumn(row, colIndex, "hand"),
                            getColumn(row, colIndex, "team")
                    );

                    pitcherList400Data.put(mlbId, data);
                } catch (Exception e) {
                    log.warn("Error parsing Pitcher List 400 row {}: {}", i, e.getMessage());
                }
            }
        }

        log.info("Loaded {} Pitcher List 400 entries", pitcherList400Data.size());
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

    public Optional<SavantBattingStats> getSavantBattingStats(int mlbamId) {
        return Optional.ofNullable(savantBattingStats.get(mlbamId));
    }

    public Optional<SavantPitchingStats> getSavantPitchingStats(int mlbamId) {
        return Optional.ofNullable(savantPitchingStats.get(mlbamId));
    }

    public Optional<ADPData> getADPData(int mlbamId) {
        return Optional.ofNullable(adpData.get(mlbamId));
    }

    public boolean isOnActiveRoster(int mlbamId) {
        return activeRosterPlayers.containsKey(mlbamId);
    }

    public Optional<String> getRosterResourceCategory(int mlbamId) {
        return Optional.ofNullable(activeRosterPlayers.get(mlbamId));
    }

    public Optional<PitcherList400Data> getPitcherList400Data(int mlbamId) {
        return Optional.ofNullable(pitcherList400Data.get(mlbamId));
    }
}
