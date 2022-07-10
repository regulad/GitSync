package gitsync;

import lombok.Data;

import java.io.File;
import java.util.List;

@Data
public class Repository {
    private String name;
    private boolean enabled;
    private String remote;
    private boolean localRepoCreated;
    private File directory;
    private List<String> ignoreList;
}
