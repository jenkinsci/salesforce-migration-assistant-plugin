package org.jenkinsci.plugins.sma;

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
import java.util.ArrayList;
import java.util.List;

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

    @DataBoundConstructor
    public SMABuilder(Boolean validateEnabled,
                      String username,
                      String password,
                      String securityToken,
                      String serverType,
                      String testLevel,
                      String prTargetBranch,
                      String runTestRegex,
                      String runTestManifest
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
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        String smaDeployResult = "";
        boolean JOB_SUCCESS = false;

        PrintStream writeToConsole = listener.getLogger();
        List<ParameterValue> parameterValues = new ArrayList<ParameterValue>();

        try {
            // Initialize the runner for this job
            SMARunner currentJob = new SMARunner(build.getEnvironment(listener), getPrTargetBranch());

            // Build the package and destructiveChanges manifests
            SMAPackage packageXml = new SMAPackage(currentJob.getPackageMembers(), false);
            writeToConsole.println("[SMA] Deploying the following metadata:");
            SMAUtility.printMetadataToConsole(listener, currentJob.getPackageMembers());
            SMAPackage destructiveChanges;

            if (currentJob.getDeployAll() || currentJob.getDestructionMembers().isEmpty()) {
                destructiveChanges = new SMAPackage(new ArrayList<SMAMetadata>(), true);
            } else {
                destructiveChanges = new SMAPackage(currentJob.getDestructionMembers(), true);
                writeToConsole.println("[SMA] Deleting the following metadata:");
                SMAUtility.printMetadataToConsole(listener, currentJob.getDestructionMembers());
            }
            // Build the zipped deployment package
            ByteArrayOutputStream deploymentPackage = SMAUtility.zipPackage(
                    currentJob.getDeploymentData(),
                    packageXml,
                    destructiveChanges
            );
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
                smaDeployResult = smaDeployResult + "\n[SMA] Deployment Succeeded";

                if (!currentJob.getDeployAll()) {
                    SMAPackage rollbackPackageXml = new SMAPackage(
                            currentJob.getRollbackMetadata(),
                            false
                    );

                    SMAPackage rollbackDestructiveXml = new SMAPackage(
                            currentJob.getRollbackAdditions(),
                            true
                    );

                    ByteArrayOutputStream rollbackPackage = SMAUtility.zipPackage(
                            currentJob.getRollbackData(),
                            rollbackPackageXml,
                            rollbackDestructiveXml
                    );

                    SMAUtility.writeZip(rollbackPackage, currentJob.getRollbackLocation());
                }
            } else {
                smaDeployResult = sfConnection.getComponentFailures();

                if (!TestLevel.valueOf(getTestLevel()).equals(TestLevel.NoTestRun)) {
                    smaDeployResult = smaDeployResult + sfConnection.getTestFailures();
                    smaDeployResult = smaDeployResult + sfConnection.getCodeCoverageWarnings();
                }
                smaDeployResult = smaDeployResult + "\n[SMA] Deployment Failed";
            }
        } catch (Exception e) {
            e.printStackTrace(writeToConsole);
        }
        parameterValues.add(new StringParameterValue("smaDeployResult", smaDeployResult));
        build.addAction(new ParametersAction(parameterValues));

        writeToConsole.println(smaDeployResult);

        return JOB_SUCCESS;
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
