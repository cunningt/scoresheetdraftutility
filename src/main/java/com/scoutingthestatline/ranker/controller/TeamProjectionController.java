package com.scoutingthestatline.ranker.controller;

import com.scoutingthestatline.ranker.model.League;
import com.scoutingthestatline.ranker.service.ScoresheetService;
import com.scoutingthestatline.ranker.service.TeamProjectionService;
import com.scoutingthestatline.ranker.service.TeamProjectionService.TeamProjection;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.*;

@Controller
public class TeamProjectionController {

    private final TeamProjectionService teamProjectionService;
    private final ScoresheetService scoresheetService;

    public TeamProjectionController(TeamProjectionService teamProjectionService,
                                     ScoresheetService scoresheetService) {
        this.teamProjectionService = teamProjectionService;
        this.scoresheetService = scoresheetService;
    }

    @GetMapping("/projections/team-wins")
    public String teamWins(Model model) {
        // BL Final Table rosters (from scoresheet.com)
        Map<String, List<String>> rosters = new LinkedHashMap<>();

        rosters.put("Team #1 (Kenny Chng)", Arrays.asList(
            "Peralta", "Brown", "Boyd", "Rodon", "Bremner", "Scherzer", "Elder", "Rodriguez", "Smith-Shawver", "Smith", "Milner", "Detmers", "Martin", "Lee", "Williams", "Faucher", "Raley", "Jansen", "Higashioka", "Murphy", "Altuve", "Cronenworth", "Chapman", "Story", "Bichette", "Soler", "Arozarena", "Hays", "Tatis", "O'Hearn", "Varsho", "Pederson"
        ));

        rosters.put("Team #2 (Grant Syverson)", Arrays.asList(
            "Cease", "Boyle", "Weathers", "McClanahan", "Drohan", "Holmes", "Vasquez", "Fitts", "Henderson", "Buehler", "Rogers", "Vesia", "Megill", "Alvarado", "Kelly", "Amaya", "Quero", "Vaughn", "Acuna", "Muncy", "Schwarber", "Adames", "Carpenter", "Buxton", "Vierling"
        ));

        rosters.put("Team #3 (John R. Mayne)", Arrays.asList(
            "Skubal", "Gausman", "Gray", "Castillo", "Suarez", "Matthews", "Lopez", "Senga", "Morgan", "Klein", "Duran", "Hurter", "Uribe", "Fairbanks", "Contreras", "Diaz", "Alonso", "Rafaela", "Ramirez", "Nimmo", "Abreu", "Thomas"
        ));

        rosters.put("Team #4 (Tom Cunningham)", Arrays.asList(
            "Gallen", "Bello", "Burke", "Nelson", "Jump", "Weiss", "Scott", "Griffin", "Soto", "Nance", "Miller", "Ruiz", "Mack", "Hoskins", "Schmitt", "Estrada", "Paredes", "Adams", "Abrams", "Arias", "Lopez", "Pena", "Walker", "Mitchell", "Robles", "George", "Davis", "Stowers"
        ));

        rosters.put("Team #5 (Paul Miramonti)", Arrays.asList(
            "Brown", "Bowlan", "Mize", "Cecconi", "Lauer", "Hancock", "Irvin", "Casparius", "Ashcraft", "Wrobleski", "Vest", "Leasure", "Holton", "Jax", "Dingler", "Wells", "Burger", "Keith", "Baty", "Gorman", "Sosa", "Westburg", "Henderson", "Smith", "Witt", "Meadows", "Thomas", "Dominguez"
        ));

        rosters.put("Team #6 (Max Fawcett)", Arrays.asList(
            "Crochet", "Pepiot", "Leiter", "Cantillo", "Harrison", "Rodriguez", "Ponce", "Gibson", "Birdsong", "Meyer", "Ryan", "Ferrer", "Ashcraft", "Hoffman", "Zerpa", "Diaz", "Harvey", "Rice", "Torkelson", "Black", "Hamilton", "Julien", "Albies", "Rocchio", "Ortiz", "Grisham", "DeLauter", "Harris", "Hernandez"
        ));

        rosters.put("Team #7 (Ben Murphy/Ian Leff)", Arrays.asList(
            "Kremer", "Springs", "Bubic", "WoodsRichardson", "Ragans", "Valdez", "Woodruff", "Susana", "Bednar", "Jansen", "Moran", "Dominguez", "Torres", "Urias", "Moncada", "Lindor", "Ward", "Stanton", "Sheets", "Benintendi", "Jones", "Matos"
        ));

        rosters.put("Team #8 (Gareth Bartholomeusz)", Arrays.asList(
            "Gilbert", "Severino", "Flaherty", "Arnold", "Kelly", "Musgrove", "White", "Santana", "Harris", "Naylor", "Bell", "Riley", "DeLaCruz", "Lee", "Lewis", "Garcia", "O'Neill", "Carter", "Rooker", "Butler", "Soderstrom"
        ));

        rosters.put("Team #9 (Michael Vogel)", Arrays.asList(
            "Rogers", "Soriano", "deGrom", "Baz", "Bassitt", "Pallante", "Brazoban", "Chapman", "Scott", "Baker", "Herrera", "Rodriguez", "Devers", "Guerrero", "Goldschmidt", "Turang", "Rosario", "Peraza", "Seager", "Mullins", "Meyers", "Fraley", "Frelick", "Wallner", "Alvarez", "Nootbaar", "Ozuna"
        ));

        rosters.put("Team #10 (Dan Troy)", Arrays.asList(
            "Messick", "Smith", "King", "Civale", "Lodolo", "Sasaki", "Painter", "Little", "Cruz", "France", "Arroyo", "Lawlar", "Duran", "Pena", "Greene", "Montgomery", "Chourio"
        ));

        rosters.put("Team #11 (Carl Brownson)", Arrays.asList(
            "Anderson", "Pfaadt", "Cabrera", "May", "Holmes", "Schwellenbach", "Sheehan", "Ohtani", "Lowder", "Doyle", "Doval", "Sands", "Munoz", "Suarez", "Narvaez", "Holliday", "Stott", "Castro", "Edman", "Arenado", "Tovar", "Hernandez", "Langford", "Crawford", "Bleday", "Morel"
        ));

        rosters.put("Team #12 (Jesse Roche)", Arrays.asList(
            "Cavalli", "Ashby", "Kikuchi", "Warren", "Lugo", "Miller", "Montgomery", "Helsley", "Strahm", "Winn", "Smith", "Bailey", "Walker", "Estrada", "Crooks", "Ford", "Swanson", "Hall", "Duran", "Laureano", "Bader", "Suzuki", "Ohtani"
        ));

        rosters.put("Team #13 (D.J. Short)", Arrays.asList(
            "Latz", "Klassen", "Sloan", "Perez", "Tong", "Sproat", "Gasser", "Peralta", "Puk", "Murakami", "Holliday", "Melton", "Jenkins", "Crews", "Alcantara"
        ));

        rosters.put("Team #14 (Michael Zink)", Arrays.asList(
            "Woo", "Ober", "Lopez", "Morales", "Oviedo", "Wheeler", "Houser", "King", "Speier", "Jensen", "Salas", "McLain", "Garcia", "Bregman", "Mauricio", "Correa", "Sanchez"
        ));

        rosters.put("Team #15 (Ari Houser)", Arrays.asList(
            "Schmidt", "Lopez", "Rocker", "Jobe", "Bieber", "Schultz", "Greene", "Dollander", "Waldrep", "O'Hoppe", "Stephenson", "Olson", "Lux", "Neto", "Montgomery", "Pages", "Lee", "Beck", "Davidson"
        ));

        rosters.put("Team #16 (Eric Moyer)", Arrays.asList(
            "Abbott", "Eovaldi", "Bradley", "Cameron", "Burrows", "Keller", "Mahle", "Beeks", "Mears", "Banks", "Manzardo", "Marte", "Bohm", "Betts", "Baez", "Scott", "Myers", "Acuna", "Veen"
        ));

        rosters.put("Team #17 (Erwin Hunke)", Arrays.asList(
            "Sanchez", "Eflin", "Seymour", "Rea", "Priester", "Roupp", "McGreevy", "Manaea", "Garrett", "Canning", "Varland", "Brash", "Iglesias", "Kerkering", "Caratini", "Realmuto", "Freeman", "Hoerner", "McNeil", "Garcia", "Reynolds", "Springer", "Isbel"
        ));

        rosters.put("Team #18 (Matt Houser)", Arrays.asList(
            "Webb", "Melton", "Peterson", "Barco", "Williamson", "Jeffers", "Moreno", "Schanuel", "Polanco", "Donovan", "Gonzalez", "Muncy", "Caminero", "Cruz", "Jones", "Garcia", "Scott"
        ));

        rosters.put("Team #19 (Ben Zalman)", Arrays.asList(
            "Hall", "Gore", "Matz", "Bradish", "Kay", "Taillon", "Burnes", "Jones", "Romero", "Erceg", "Keller", "Pagan", "Alvarez", "Mayo", "Vientos", "Volpe", "Walls", "Soto", "Rojas", "Crow-Armstrong", "Merrill", "Cowser", "Marte"
        ));

        rosters.put("Team #20 (Nate Stephens)", Arrays.asList(
            "Liberatore", "Skenes", "Wacha", "Martin", "Perkins", "Hart", "Snelling", "Rodriguez", "Fisher", "Phillips", "Morejon", "Raleigh", "Smith", "Martinez", "Arroyo", "Rengifo", "Suarez", "Judge", "Canzone", "Carroll"
        ));

        rosters.put("Team #21 (Kraig Smith)", Arrays.asList(
            "Rasmussen", "Bradford", "Tiedemann", "Verlander", "Ray", "Imanaga", "Sale", "Strider", "Assad", "Littell", "Slaten", "Kittredge", "Hader", "Yates", "Adam", "Maton", "Campusano", "Contreras", "Chisholm", "Lowe", "Sosa", "Kemp", "Trout", "Yelich", "Robert", "Marsh", "Yoshida"
        ));

        rosters.put("Team #22 (Scott White)", Arrays.asList(
            "Yamamoto", "Bibee", "Smith", "Berrios", "Kirby", "Nola", "Murphy", "Armstrong", "Fermin", "Langeliers", "Aranda", "India", "Machado", "Marte", "Kiner-Falefa", "Williams", "Kim", "Kwan", "Happ", "Profar"
        ));

        rosters.put("Team #23 (Kevin Cremin)", Arrays.asList(
            "Fried", "Ryan", "Gil", "McCullers", "Javier", "Arrighetti", "Cole", "Martinez", "Singer", "Steele", "Lopez", "Ginkel", "Hicks", "Torrens", "Steer", "Casas", "Ortiz", "Gimenez", "Marte", "Norby", "Winn", "Carlson", "Gonzalez", "Adell", "Benson"
        ));

        rosters.put("Team #24 (Bret Sayre)", Arrays.asList(
            "Williams", "Luzardo", "Alcantara", "Horton", "Glasnow", "Soroka", "Snell", "Garcia", "Finnegan", "Minter", "Perez", "Naylor", "Harper", "Semien", "Young", "Jung", "Tucker", "Lowe", "Freeman"
        ));

        List<TeamProjection> projections = teamProjectionService.projectAllTeams(rosters);
        model.addAttribute("projections", projections);
        model.addAttribute("leagueName", "BL Final Table");

        return "team-projections";
    }

    @GetMapping("/projections/team-wins/norcal")
    public String norcalTeamWins(Model model) {
        // Get league configuration
        League league = scoresheetService.getLeague("AL_BP_NorCal")
            .orElseThrow(() -> new RuntimeException("League AL_BP_NorCal not found"));

        // Team owners (hardcoded for now - could be fetched from page too)
        Map<Integer, String> teamOwners = new LinkedHashMap<>();
        teamOwners.put(1, "Bill Sanders");
        teamOwners.put(2, "Jeremy Blachman");
        teamOwners.put(3, "Adam Katz");
        teamOwners.put(4, "Stephen Shelby");
        teamOwners.put(5, "Nate Stephens");
        teamOwners.put(6, "Garth Hewitt");
        teamOwners.put(7, "Tom Cunningham/Ken");
        teamOwners.put(8, "Todd Melander");
        teamOwners.put(9, "John R. Mayne");
        teamOwners.put(10, "Andy Cleary");
        teamOwners.put(11, "Jamie Lawson");
        teamOwners.put(12, "Dan Troy");

        List<TeamProjection> projections = teamProjectionService.projectLeague(league, teamOwners);
        model.addAttribute("projections", projections);
        model.addAttribute("leagueName", "AL BP NorCal");

        return "team-projections";
    }
}
