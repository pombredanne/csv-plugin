package org.whitesource.csv;

import au.com.bytecode.opencsv.CSVReader;
import org.apache.commons.lang.StringUtils;
import org.whitesource.agent.api.dispatch.UpdateInventoryResult;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.client.WhitesourceService;
import org.whitesource.agent.client.WssServiceException;

import java.io.*;
import java.util.*;

/**
 * Java command line application that reads a given CSV file to update WhiteSource projects.
 */
public class WhitesourceCsv {

     /* --- Static members --- */

    public static final int VALID_ENTRY_LENGTH = 3;

    public static final int GROUP_ID_INDEX = 0;
    public static final int ARTIFACT_ID_INDEX = 1;
    public static final int VERSION_INDEX = 2;

    public static final String API_KEY_PROPERTY = "apiKey";
    public static final String PROJECT_TOKEN_PROPERTY = "projectToken";
    public static final String WSS_URL_PROPERTY = "wssUrl";
    public static final String DEBUG_PROPERTY = "debug";

    public static final String PROPERTIES_FILE = "wss.properties";

    /* --- Members --- */

    private String csvFilePath;
    private WhitesourceService service;
    private AgentProjectInfo project;
    private Properties properties;

    // properties
    private String apiKey;
    private String projectToken;
    private String wssUrl;
    private boolean debug;

    /* --- Constructors --- */

    public WhitesourceCsv(String csvFilePath) {
        this.csvFilePath = csvFilePath;
        debug = false;
    }

    /* --- Public methods --- */

    public void execute() {
        readProperties();
        validateAndPrepare();
        readCsvFile();
        createService();
        updateInventory();
    }

    /* --- Private methods --- */

    private void readProperties() {
        properties = new Properties();
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(PROPERTIES_FILE);
            properties.load(inputStream);
        } catch (IOException e) {
            log(Level.ERROR, e.getMessage());
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void validateAndPrepare() {
        // api key
        apiKey = properties.getProperty(API_KEY_PROPERTY);
        if (StringUtils.isBlank(apiKey)) {
            log(Level.ERROR, "Missing API key");
        }

        // project
        projectToken = properties.getProperty(PROJECT_TOKEN_PROPERTY);
        if (StringUtils.isBlank(projectToken)) {
            log(Level.ERROR, "Missing project token");
        }

        // wss url
        wssUrl = properties.getProperty(WSS_URL_PROPERTY);

        // debug
        debug = Boolean.valueOf(properties.getProperty(DEBUG_PROPERTY));
    }

    private void readCsvFile() {
        try {
            CSVReader reader = new CSVReader(new FileReader(csvFilePath));
            List<String[]> csvEntries = reader.readAll();

            int line = 1;
            project = new AgentProjectInfo();
            project.setProjectToken(projectToken);

            for (String[] entry : csvEntries) {
                // validate entry
                if (entry.length == VALID_ENTRY_LENGTH) {
                    String groupId = entry[GROUP_ID_INDEX];
                    String artifactId = entry[ARTIFACT_ID_INDEX];
                    String version = entry[VERSION_INDEX];

                    boolean validDependency = true;
                    if (StringUtils.isBlank(groupId)) {
                        validDependency = false;
                        log(Level.INFO, "Invalid dependency - missing groupId");
                    }
                    if (StringUtils.isBlank(artifactId)) {
                        validDependency = false;
                        log(Level.INFO, "Invalid dependency - missing artifactId");
                    }
                    if (StringUtils.isBlank(version)) {
                        validDependency = false;
                        log(Level.INFO, "Invalid dependency - missing version");
                    }

                    if (validDependency) {
                        project.getDependencies().add(new DependencyInfo(groupId, artifactId, version));
                        log(Level.DEBUG, "Found dependency " + groupId + ":" + artifactId + ":" + version);
                    }
                } else {
                    log(Level.INFO, "Invalid entry in line " + line + ", skipping");
                }
                line++;
            }
        } catch (FileNotFoundException e) {
            log(Level.INFO, "File " + csvFilePath + " not found!");
        } catch (IOException e) {
            log(Level.INFO, "Error reading file " + csvFilePath);
        }
    }

    private void createService() {
        if (!StringUtils.isBlank(wssUrl)) {
            log(Level.DEBUG, "Service Url is " + wssUrl);
        }
        service = new WhitesourceService(Constants.AGENT_TYPE, Constants.AGENT_VERSION, wssUrl);
    }

    private void updateInventory() {
        log(Level.INFO, "Updating White Source");
        try {
            UpdateInventoryResult result = service.update(apiKey, null, null, Arrays.asList(project));
            logUpdateResult(result);
        } catch (WssServiceException e) {
            log(Level.ERROR, "A problem occurred while updating projects: " + e.getMessage());
        }
    }

    private void logUpdateResult(UpdateInventoryResult result) {
        log(Level.INFO, "White Source update results:");
        log(Level.INFO, "White Source organization: " + result.getOrganization());

        // newly created projects
        Collection<String> createdProjects = result.getCreatedProjects();
        if (createdProjects.isEmpty()) {
            log(Level.INFO, "No new projects found");
        } else {
            log(Level.INFO, createdProjects.size() + " Newly created projects:");
            for (String projectName : createdProjects) {
                log(Level.INFO, projectName);
            }
        }

        // updated projects
        Collection<String> updatedProjects = result.getUpdatedProjects();
        if (updatedProjects.isEmpty()) {
            log(Level.INFO, "No projects were updated");
        } else {
            log(Level.INFO, updatedProjects.size() + " existing project(s) were updated:");
            for (String projectName : updatedProjects) {
                log(Level.INFO, projectName);
            }
        }
    }

    private void log(Level level, String msg) {
        if (level != Level.DEBUG || (level == Level.DEBUG && debug)) {
            System.out.println("[" + level + "] " + msg);
        }

        // exit on error
        if (level == Level.ERROR) {
            System.exit(1);
        }
    }

}
