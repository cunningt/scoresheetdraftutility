package com.scoutingthestatline.ranker.config;

import com.scoutingthestatline.ranker.model.League;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "scoresheet")
public class LeagueProperties {

    private List<LeagueConfig> leagues = new ArrayList<>();

    public List<LeagueConfig> getLeagues() {
        return leagues;
    }

    public void setLeagues(List<LeagueConfig> leagues) {
        this.leagues = leagues;
    }

    public static class LeagueConfig {
        private String id;
        private String name;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

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

            return new League(id, name, dirLgw, statsMl, 1);
        }
    }

    public List<League> toLeagues() {
        return leagues.stream()
                .map(LeagueConfig::toLeague)
                .toList();
    }
}
