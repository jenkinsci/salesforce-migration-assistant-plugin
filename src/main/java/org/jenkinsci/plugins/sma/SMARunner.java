package org.jenkinsci.plugins.sma;

import hudson.EnvVars;
import org.apache.commons.configuration.ConfigurationException;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

/**
 * Class that contains all of the configuration pertinent to the running job
 *
 */
public class SMARunner {
    private static final Logger LOG = Logger.getLogger(SMARunner.class.getName());

    private Boolean deployAll = false;
    private String currentCommit;
    private String previousCommit;
    private String rollbackLocation;
    private SMAGit git;
    private String pathToWorkspace;
    private List<SMAMetadata> deployMetadata = new ArrayList<SMAMetadata>();
    private List<SMAMetadata> deleteMetadata = new ArrayList<SMAMetadata>();
    private List<SMAMetadata> rollbackMetadata = new ArrayList<SMAMetadata>();
    private List<SMAMetadata> rollbackAdditions = new ArrayList<SMAMetadata>();

    /**
     * Wrapper for coordinating the configuration of the running job
     *
     * @param jobVariables
     * @param prTargetBranch
     * @throws Exception
     */
    public SMARunner(EnvVars jobVariables, String prTargetBranch, SMAJenkinsCIOrgSettings orgSettings) throws Exception {
        // Get envvars to initialize SMAGit
        Boolean shaOverride  = false;
        this.pathToWorkspace = jobVariables.get("WORKSPACE");
        String jobName       = jobVariables.get("JOB_NAME");
        String buildNumber   = jobVariables.get("BUILD_NUMBER");

        if (null != orgSettings && null != orgSettings.getGitSha1()) {
            previousCommit = orgSettings.getGitSha1();
        } else if (null == orgSettings && jobVariables.containsKey("GIT_PREVIOUS_SUCCESSFUL_COMMIT")) {
            previousCommit = jobVariables.get("GIT_PREVIOUS_SUCCESSFUL_COMMIT");
        } else {
            deployAll = true;
        }
        if (jobVariables.containsKey("SMA_DEPLOY_ALL_METADATA")) {
            deployAll = Boolean.valueOf(jobVariables.get("SMA_DEPLOY_ALL_METADATA"));
        }
        if (jobVariables.containsKey("SMA_PREVIOUS_COMMIT_OVERRIDE")
                && !jobVariables.get("SMA_PREVIOUS_COMMIT_OVERRIDE").isEmpty()
        ) {
            shaOverride = true;
            previousCommit = jobVariables.get("SMA_PREVIOUS_COMMIT_OVERRIDE");
        }
        // Configure using pull request logic
        if (!prTargetBranch.isEmpty() && !shaOverride) {
            deployAll = false;
            git = new SMAGit(pathToWorkspace, prTargetBranch, SMAGit.Mode.PRB);
            previousCommit = git.getPreviousCommit();
            
        } else if (deployAll) { // Configure for all the metadata
            git = new SMAGit(pathToWorkspace, null, SMAGit.Mode.INI);

        } else { // Configure using the previous successful commit for this job
            git = new SMAGit(pathToWorkspace, previousCommit, SMAGit.Mode.STD);
        }
        currentCommit    = git.getCurrentCommit();
        rollbackLocation = pathToWorkspace + "/sma/rollback" + jobName + buildNumber + ".zip";
    }

    /**
     * Returns whether the current job is set to deploy all the metadata in the repository
     *
     * @return deployAll
     */
    public Boolean getDeployAll() { return deployAll; }

    /**
     * Returns the SMAMetadata that is going to be deployed in this job
     *
     * @return
     * @throws Exception
     */
    public List<SMAMetadata> getPackageMembers() throws Exception {
        if (deployAll) {
            deployMetadata = buildMetadataList(git.getAllMetadata());
        } else if (deployMetadata.isEmpty()) {
            Map<String, byte[]> positiveChanges = git.getNewMetadata();
            positiveChanges.putAll(git.getUpdatedMetadata());

            deployMetadata = buildMetadataList(positiveChanges);
        }
        return deployMetadata;
    }

    /**
     * Returns the SMAMetadata that is going to be deleted in this job
     *
     * @return deleteMetadata
     * @throws Exception
     */
    public List<SMAMetadata> getDestructionMembers() throws Exception {
        if (deleteMetadata.isEmpty()) {
            Map<String, byte[]> negativeChanges = git.getDeletedMetadata();

            deleteMetadata = buildMetadataList(negativeChanges);
        }
        return deleteMetadata;
    }

    public List<SMAMetadata> getRollbackMetadata() throws Exception {
        if (deleteMetadata.isEmpty()) {
            getDestructionMembers();
        }
        rollbackMetadata = new ArrayList<SMAMetadata>();
        rollbackMetadata.addAll(deleteMetadata);
        rollbackMetadata.addAll(buildMetadataList(git.getOriginalMetadata()));

        return rollbackMetadata;
    }

    public List<SMAMetadata> getRollbackAdditions() throws Exception {
        rollbackAdditions = new ArrayList<SMAMetadata>();
        rollbackAdditions.addAll(buildMetadataList(git.getNewMetadata()));

        return rollbackAdditions;
    }

    /**
     * Returns a map with the file name mapped to the byte contents of the metadata
     *
     * @return deploymentData
     * @throws Exception
     */
    public Map<String, byte[]> getDeploymentData() throws Exception {
        if (deployMetadata.isEmpty()) {
            getPackageMembers();
        }
        return getData(deployMetadata, currentCommit);
    }

    public Map<String, byte[]> getRollbackData() throws Exception {
        if (rollbackMetadata.isEmpty()) {
            getRollbackMetadata();
        }
        return getData(rollbackMetadata, previousCommit);
    }

    /**
     * Helper method to find the byte[] contents of given metadata
     *
     * @param metadatas
     * @param commit
     * @return
     * @throws Exception
     */
    private Map<String, byte[]> getData(List<SMAMetadata> metadatas, String commit) throws Exception {
        Map<String, byte[]> data = new HashMap<String, byte[]>();

        for (SMAMetadata metadata : metadatas) {
            data.put(metadata.toString(), metadata.getBody());

            if (metadata.hasMetaxml()) {
                String metaXml = metadata.toString() + "-meta.xml";
                String pathToXml = metadata.getPath() + metadata.getFullName() + "-meta.xml";
                data.put(metaXml, git.getBlob(pathToXml, commit));
            }
        }
        return data;
    }

    /**
     * Constructs a list of SMAMetadata objects from a Map of files and their byte[] contents
     *
     * @param repoItems
     * @return
     * @throws Exception
     */
    private List<SMAMetadata> buildMetadataList(Map<String, byte[]> repoItems) throws Exception {
        List<SMAMetadata> thisMetadata = new ArrayList<SMAMetadata>();

        for (String repoItem : repoItems.keySet()) {
            SMAMetadata mdObject = SMAMetadataTypes.createMetadataObject(repoItem, repoItems.get(repoItem));

            if (mdObject.isValid()) {
                thisMetadata.add(mdObject);
            }
        }
        return thisMetadata;
    }

    /**
     * Returns a String array of all the unit tests that should be run in this job
     *
     * @param builder
     * @return
     * @throws Exception
     */
    public String[] getSpecifiedTests(SMABuilder builder) throws Exception {
        Set<String> specifiedTestsList = new HashSet<String>();

        Set<String> apexClassesToDeploy = SMAMetadata.getApexClasses(deployMetadata);
        Set<String> allApexClasses      = SMAMetadata.getApexClasses(buildMetadataList(git.getAllMetadata()));
        Map<String, Set<String>> classMapping = getManifestClassMapping(builder);

        for (String className : apexClassesToDeploy) {
            Set<String> testsForClass = new HashSet<String>();

            String testName = getSpecifiedTestsByRegex(className, allApexClasses, builder);

            if (null != testName) {
                testsForClass.add(testName);
            }
            if (null != classMapping) {
                Set<String> manifestTests = getSpecifiedTestsByManifest(className, classMapping);
                testsForClass.addAll(manifestTests);
            }
            if (testsForClass.size() == 0) {
                LOG.warning("No test class for " + className + " found");
                continue;
            }
            specifiedTestsList.addAll(testsForClass);
        }
        specifiedTestsList.retainAll(allApexClasses);

        SortedSet<String> specifiedTestsListSorted = new TreeSet<String>();
        specifiedTestsListSorted.addAll(specifiedTestsList);
        return specifiedTestsListSorted.toArray(new String[specifiedTestsListSorted.size()]);
    }

    private Map<String, Set<String>> getManifestClassMapping(SMABuilder builder) {
        if (!builder.getRunTestManifest().isEmpty()) {
            try {
                String pathToManifest = this.pathToWorkspace + File.separator + builder.getRunTestManifest();
                SMATestManifestReader manifestReader = new SMATestManifestReader(pathToManifest);
                return manifestReader.getClassMapping();
            } catch (ConfigurationException e) {
                LOG.warning("Error found while loading test manifest '" + builder.getRunTestManifest() + "': " + e.getMessage());
            } catch (NoSuchElementException e) {
                LOG.warning("Error found in the document structure of the test manifest: " + e.getMessage());
            }
        }
        return null;
    }

    private Set<String> getSpecifiedTestsByManifest(String className, Map<String, Set<String>> classMapping) {
        return null != classMapping && classMapping.containsKey(className) ? classMapping.get(className) : Collections.<String>emptySet();
    }

    private String getSpecifiedTestsByRegex(String className, Set<String> allApexClasses, SMABuilder builder) {
        String testRegex = builder.getRunTestRegex();

        if (null == testRegex || testRegex.isEmpty()) { return null; }

        if (className.matches(testRegex)) {
            return className;
        }
        String[] regexs = new String[] { className + testRegex, testRegex + className };

        for (String regex : regexs) {
            String testClass = SMAUtility.searchForTestClass(allApexClasses, regex);

            if (null != testClass) {
                return testClass;
            }
        }
        return null;
    }

    public String getRollbackLocation() {
        File rollbackLocationFile = new File(rollbackLocation);

        if (!rollbackLocationFile.getParentFile().exists()) {
            rollbackLocationFile.getParentFile().mkdirs();
        }
        return rollbackLocation;
    }

    public String getCurrentCommit() {
        return this.currentCommit;
    }
}