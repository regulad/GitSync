package gitsync;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Config {
    private static Config instance;

    @Getter
    @Setter
    private List<Repository> repositories = new ArrayList<>();
    private Scenario scenarioWhileDailySync;

    private Config() {
    }

    public static Config getInstance() {
        if (instance == null) {
            instance = new Config();
        }

        return instance;
    }

    public static void clear() {
        instance = new Config();
    }

    public Scenario getScenarioWhileDailySync() {
        return this.scenarioWhileDailySync;
    }

    public void setScenarioWhileDailySync(Scenario scenarioWhileDailySync) {
        this.scenarioWhileDailySync = scenarioWhileDailySync;
    }

    public Repository findRepoByName(String name) {
        Optional<Repository> repositoryToGive = this.repositories.stream().filter((repository) -> repository.getName().equals(name)).findFirst();
        return repositoryToGive.orElse(null);
    }

    public enum Scenario {
        FAVORABLE,
        FORCE,
        ALL;

        // $FF: synthetic method
        private static Scenario[] $values() {
            return new Scenario[]{FAVORABLE, FORCE, ALL};
        }
    }
}
