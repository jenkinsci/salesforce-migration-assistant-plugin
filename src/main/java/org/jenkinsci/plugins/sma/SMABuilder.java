package org.jenkinsci.plugins.sma;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.*;

import com.sforce.soap.metadata.TestLevel;
import net.sf.json.JSONObject;

/**
 * @author Anthony Sanchez <senninha09@gmail.com>
 */
public class SMABuilder extends Builder {
    private boolean validateEnabled;
    private String username;
    private String password;
    private String securityToken;
    private String serverType;
    private String testLevel;
    private String prTargetBranch;
    private String runTestRegex;
    private String runTestManifest;
    private boolean useCustomSettings;

    @DataBoundConstructor
    public SMABuilder(Boolean validateEnabled,
                      String username,
                      String password,
                      String securityToken,
                      String serverType,
                      String testLevel,
                      String prTargetBranch,
                      String runTestRegex,
                      String runTestManifest,
                      Boolean useCustomSettings
    ) {
        this.username = username;
        this.password = password;
        this.securityToken = securityToken;
        this.serverType = serverType;
        this.validateEnabled = validateEnabled;
        this.testLevel = testLevel;
        this.prTargetBranch = prTargetBranch;
        this.runTestRegex = runTestRegex;
        this.runTestManifest = runTestManifest;
        this.useCustomSettings = useCustomSettings;
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        String smaDeployResult = "";
        boolean JOB_SUCCESS = false;

        PrintStream writeToConsole = listener.getLogger();
        List<ParameterValue> parameterValues = new ArrayList<ParameterValue>();

        try {
            // Initialize the connection to Salesforce for this job
            SMAConnection sfConnection = new SMAConnection(
                    getUsername(),
                    getPassword(),
                    getSecurityToken(),
                    getServerType(),
                    getDescriptor().getPollWait(),
                    getDescriptor().getMaxPoll(),
                    getDescriptor().getProxyServer(),
                    getDescriptor().getProxyUser(),
                    getDescriptor().getProxyPass(),
                    getDescriptor().getProxyPort()
            );

            // Initialize the runner for this job
            SMAJenkinsCIOrgSettings orgSettings = null;
            if (getUseCustomSettings()) {
                orgSettings = SMAJenkinsCIOrgSettings.getInstance(sfConnection);
                writeToConsole.println("[SMA] Using Custom Settings on Org. Current settings: ");
                writeToConsole.println("- Git SHA1: " + orgSettings.getGitSha1());
                writeToConsole.println();
            }
            EnvVars jobVariables = build.getEnvironment(listener);
            SMARunner currentJob = new SMARunner(jobVariables, getPrTargetBranch(), orgSettings);

            // Build the package and destructiveChanges manifests
            SMAPackage packageXml = new SMAPackage(currentJob.getPackageMembers(), false);

            writeToConsole.println("[SMA] Deploying the following metadata:");
            SMAUtility.printMetadataToConsole(listener, currentJob.getPackageMembers());

            SMAPackage destructiveChanges = buildDestructiveChangesPackage(currentJob);

            if (destructiveChanges.getContents().size() > 0) {
                writeToConsole.println("[SMA] Deleting the following metadata:");
                SMAUtility.printMetadataToConsole(listener, destructiveChanges.getContents());
            }
            // Build the zipped deployment package
            ByteArrayOutputStream deploymentPackage = SMAUtility.zipPackage(
                    currentJob.getDeploymentData(),
                    packageXml,
                    destructiveChanges
            );

            // Deploy to the server
            String[] specifiedTests = null;
            TestLevel testLevel = TestLevel.valueOf(getTestLevel());

            if (testLevel.equals(TestLevel.RunSpecifiedTests)) {
                specifiedTests = currentJob.getSpecifiedTests(this);

                writeToConsole.println("[SMA] Specified Apex tests to run:");
                for (String testName : specifiedTests) {
                    writeToConsole.println("- " + testName);
                }
                writeToConsole.println("");
            }

            JOB_SUCCESS = sfConnection.deployToServer(
                    deploymentPackage,
                    testLevel,
                    specifiedTests,
                    getValidateEnabled(),
                    packageXml.containsApex()
            );
            if (JOB_SUCCESS) {
                if (!testLevel.equals(TestLevel.NoTestRun)) {
                    smaDeployResult = sfConnection.getCodeCoverage();
                }
                smaDeployResult += "\n[SMA] " + (getValidateEnabled() ? "Validation" : "Deployment") + " Succeeded";

                if (!getValidateEnabled()) {
                    if (!currentJob.getDeployAll()) {
                        createRollbackPackageZip(currentJob);
                    }
                    if (getUseCustomSettings()) {
                        orgSettings.setGitSha1(currentJob.getCurrentCommit());
                        orgSettings.setJenkinsJobName(jobVariables.get("JOB_NAME"));
                        orgSettings.setJenkinsBuildNumber(jobVariables.get("BUILD_NUMBER"));
                        orgSettings.save();
                    }
                    writeToConsole.println("Setting GitSha1 to: " + currentJob.getCurrentCommit());
                }
            } else {
                smaDeployResult = sfConnection.getComponentFailures();

                if (!testLevel.equals(TestLevel.NoTestRun)) {
                    smaDeployResult += sfConnection.getTestFailures() + sfConnection.getCodeCoverageWarnings();
                }
                smaDeployResult += "\n[SMA] " + (getValidateEnabled() ? "Validation" : "Deployment") + " Failed";
            }
        } catch (Exception e) {
            e.printStackTrace(writeToConsole);
        }
        parameterValues.add(new StringParameterValue("smaDeployResult", smaDeployResult));
        build.addAction(new ParametersAction(parameterValues));
        writeToConsole.println(smaDeployResult);

        return JOB_SUCCESS;
    }

    private void createRollbackPackageZip(SMARunner currentJob) throws Exception {
        SMAPackage rollbackPackageXml = new SMAPackage(currentJob.getRollbackMetadata(), false);
        SMAPackage rollbackDestructiveXml = new SMAPackage(currentJob.getRollbackAdditions(), true);

        ByteArrayOutputStream rollbackPackage = SMAUtility.zipPackage(
                currentJob.getRollbackData(),
                rollbackPackageXml,
                rollbackDestructiveXml
        );
        SMAUtility.writeZip(rollbackPackage, currentJob.getRollbackLocation());
    }

    private SMAPackage buildDestructiveChangesPackage(SMARunner currentJob) throws Exception {
        List<SMAMetadata> destructionMembers = new ArrayList<SMAMetadata>();
        if (!currentJob.getDeployAll()) {
            destructionMembers = currentJob.getDestructionMembers();
        }
        return new SMAPackage(destructionMembers, true);
    }

    public boolean getValidateEnabled() { return validateEnabled; }

    public String getUsername() { return username; }

    public String getSecurityToken() { return securityToken; }

    public String getPassword() { return password; }

    public String getServerType() { return serverType; }

    public String getTestLevel() { return testLevel; }

    public String getPrTargetBranch() { return prTargetBranch; }

    public String getRunTestRegex() { return runTestRegex; }

    public String getRunTestManifest() { return runTestManifest; }

    public Boolean getUseCustomSettings() { return useCustomSettings; }

    @Override
    public DescriptorImpl getDescriptor() { return (DescriptorImpl) super.getDescriptor(); }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        private String maxPoll = "200";
        private String pollWait = "30000";
        private String runTestRegex = ".*[T|t]est.*";
        private String proxyServer = "";
        private String proxyUser = "";
        private String proxyPass = "";
        private Integer proxyPort = 0;


        public DescriptorImpl() {
            load();
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        public String getDisplayName() { return "Salesforce Migration Assistant"; }

        public String getMaxPoll() { return maxPoll; }

        public String getPollWait() { return pollWait; }

        public String getRunTestRegex() { return runTestRegex; }

        public String getProxyServer() { return proxyServer; }

        public String getProxyUser() { return proxyUser; }

        public String getProxyPass() { return proxyPass; }

        public Integer getProxyPort() { return proxyPort; }

        public ListBoxModel doFillServerTypeItems() {
            return new ListBoxModel(
                    new ListBoxModel.Option("Production (https://login.salesforce.com)", "https://login.salesforce.com"),
                    new ListBoxModel.Option("Sandbox (https://test.salesforce.com)", "https://test.salesforce.com")
            );
        }

        public ListBoxModel doFillTestLevelItems() {
            return new ListBoxModel(
                    new ListBoxModel.Option("None", "NoTestRun"),
                    new ListBoxModel.Option("Relevant", "RunSpecifiedTests"),
                    new ListBoxModel.Option("Local", "RunLocalTests"),
                    new ListBoxModel.Option("All", "RunAllTestsInOrg")
            );
        }

        public boolean configure(StaplerRequest request, JSONObject formData) throws FormException {
            maxPoll = formData.getString("maxPoll");
            pollWait = formData.getString("pollWait");
            proxyServer = formData.getString("proxyServer");
            proxyUser = formData.getString("proxyUser");
            proxyPass = formData.getString("proxyPass");
            proxyPort = formData.optInt("proxyPort");

            save();
            return false;
        }
    }
}
