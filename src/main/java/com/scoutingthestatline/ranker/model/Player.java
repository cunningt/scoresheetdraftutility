package com.scoutingthestatline.ranker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Player {
    private int scoresheetId;
    private int mlbamId;
    private String firstName;
    private String lastName;
    private String team;
    private String position;
    private String handedness;
    private int age;

    // Positional ranges (null/0 means can't play that position)
    private Double range1B;
    private Double range2B;
    private Double range3B;
    private Double rangeSS;
    private Double rangeOF;

    // Split adjustments vs RHP (BA, OBP, SLG)
    private Integer baVsR;
    private Integer obpVsR;
    private Integer slgVsR;

    // Split adjustments vs LHP (BA, OBP, SLG)
    private Integer baVsL;
    private Integer obpVsL;
    private Integer slgVsL;

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean isPitcher() {
        return "P".equals(position) || "SR".equals(position);
    }

    public boolean isCatcher() {
        return "C".equals(position);
    }

    /**
     * Returns a formatted string of all positions the player can play with their ranges.
     * E.g., "2B (4.25), SS (4.78)"
     */
    public String getPositionsWithRanges() {
        if (isPitcher() || isCatcher()) {
            return position;
        }

        StringBuilder sb = new StringBuilder();
        if (range1B != null && range1B > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("1B (").append(String.format("%.2f", range1B)).append(")");
        }
        if (range2B != null && range2B > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("2B (").append(String.format("%.2f", range2B)).append(")");
        }
        if (range3B != null && range3B > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("3B (").append(String.format("%.2f", range3B)).append(")");
        }
        if (rangeSS != null && rangeSS > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("SS (").append(String.format("%.2f", rangeSS)).append(")");
        }
        if (rangeOF != null && rangeOF > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("OF (").append(String.format("%.2f", rangeOF)).append(")");
        }

        return sb.length() > 0 ? sb.toString() : position;
    }

    /**
     * Returns list of secondary positions (positions other than primary).
     */
    public String getSecondaryPositions() {
        if (isPitcher() || isCatcher()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        if (range1B != null && range1B > 0 && !"1B".equals(position)) {
            if (sb.length() > 0) sb.append("/");
            sb.append("1B");
        }
        if (range2B != null && range2B > 0 && !"2B".equals(position)) {
            if (sb.length() > 0) sb.append("/");
            sb.append("2B");
        }
        if (range3B != null && range3B > 0 && !"3B".equals(position)) {
            if (sb.length() > 0) sb.append("/");
            sb.append("3B");
        }
        if (rangeSS != null && rangeSS > 0 && !"SS".equals(position)) {
            if (sb.length() > 0) sb.append("/");
            sb.append("SS");
        }
        if (rangeOF != null && rangeOF > 0 && !"OF".equals(position)) {
            if (sb.length() > 0) sb.append("/");
            sb.append("OF");
        }

        return sb.toString();
    }

    /**
     * Format splits vs RHP as "BA/OBP/SLG" with +/- signs
     */
    public String getSplitsVsR() {
        if (baVsR == null && obpVsR == null && slgVsR == null) return "-";
        return formatSplit(baVsR) + "/" + formatSplit(obpVsR) + "/" + formatSplit(slgVsR);
    }

    /**
     * Format splits vs LHP as "BA/OBP/SLG" with +/- signs
     */
    public String getSplitsVsL() {
        if (baVsL == null && obpVsL == null && slgVsL == null) return "-";
        return formatSplit(baVsL) + "/" + formatSplit(obpVsL) + "/" + formatSplit(slgVsL);
    }

    private String formatSplit(Integer val) {
        if (val == null) return "0";
        if (val > 0) return "+" + val;
        return String.valueOf(val);
    }
}
