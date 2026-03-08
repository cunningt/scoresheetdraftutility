package com.scoutingthestatline.ranker.config;

import com.scoutingthestatline.ranker.model.League;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "scoresheet")
public class LeagueProperties {

    private List<LeagueConfig> leagues = new ArrayList<>();

    @Data
    public static class LeagueConfig {
        private String id;
        private String name;

        public League toLeague() {
            // Derive dirLgw from id
            String dirLgw = "/FOR_WWW1/" + id;

            // Derive statsMl from id prefix
            String statsMl;
            if (id.startsWith("AL_") || id.startsWith("wP")) {
                statsMl = "AL";
            } else if (id.startsWith("NL_")) {
                statsMl = "NL";
            } else {
                statsMl = "BL";
            }

            return League.builder()
                    .id(id)
                    .name(name)
                    .dirLgw(dirLgw)
                    .statsMl(statsMl)
                    .teamN(1)
                    .build();
        }
    }

    public List<League> toLeagues() {
        return leagues.stream()
                .map(LeagueConfig::toLeague)
                .toList();
    }
}
