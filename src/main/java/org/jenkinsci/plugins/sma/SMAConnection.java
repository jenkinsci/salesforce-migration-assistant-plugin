package org.jenkinsci.plugins.sma;

import com.sforce.soap.metadata.*;
import com.sforce.soap.metadata.Error;
import com.sforce.soap.metadata.FieldType;
import com.sforce.soap.partner.*;
import com.sforce.soap.partner.Connector;
import com.sforce.soap.partner.sobject.SObject;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;

import java.io.ByteArrayOutputStream;
import java.text.DecimalFormat;
import java.util.NoSuchElementException;
import java.util.logging.Logger;

/**
 * This class handles the API connection and actions against the Salesforce instance
 *
 */
public class SMAConnection {
    private static final Logger LOG = Logger.getLogger(SMAConnection.class.getName());

    private final ConnectorConfig initConfig = new ConnectorConfig();
    private final ConnectorConfig metadataConfig = new ConnectorConfig();

    private final MetadataConnection metadataConnection;
    private final PartnerConnection partnerConnection;

    private final String pollWaitString;
    private final String maxPollString;

    private DeployResult deployResult;
    private DeployDetails deployDetails;
    private double API_VERSION;

    /**
     * Constructor that sets up the connection to a Salesforce organization
     *
     * @param username
     * @param password
     * @param securityToken
     * @param server
     * @param pollWaitString
     * @param maxPollString
     * @param proxyServer
     * @param proxyUser
     * @param proxyPort
     * @param proxyPass
     * @throws Exception
     */
    public SMAConnection(String username,
                         String password,
                         String securityToken,
                         String server,
                         String pollWaitString,
                         String maxPollString,
                         String proxyServer,
                         String proxyUser,
                         String proxyPass,
                         Integer proxyPort) throws Exception
    {
        System.setProperty("https.protocols", "TLSv1,TLSv1.1,TLSv1.2");

        API_VERSION = Double.valueOf(SMAMetadataTypes.getAPIVersion());
        this.pollWaitString = pollWaitString;
        this.maxPollString = maxPollString;

        String endpoint = server + "/services/Soap/u/" + String.valueOf(API_VERSION);

        initConfig.setUsername(username);
        initConfig.setPassword(password + securityToken);
        initConfig.setAuthEndpoint(endpoint);
        initConfig.setServiceEndpoint(endpoint);
        initConfig.setManualLogin(true);

        //Proxy support
        if (!proxyServer.isEmpty()) {
            initConfig.setProxy(proxyServer, proxyPort);
            if (!proxyPass.isEmpty()) {
                initConfig.setProxyUsername(proxyUser);
                initConfig.setProxyPassword(proxyPass);
            }
        }
        PartnerConnection partnerTmpConnection = Connector.newConnection(initConfig);

        LoginResult loginResult = partnerTmpConnection.login(initConfig.getUsername(), initConfig.getPassword());
        metadataConfig.setServiceEndpoint(loginResult.getMetadataServerUrl());
        metadataConfig.setSessionId(loginResult.getSessionId());
        metadataConfig.setProxy(initConfig.getProxy());
        metadataConfig.setProxyUsername(initConfig.getProxyUsername());
        metadataConfig.setProxyPassword(initConfig.getProxyPassword());

        metadataConnection = new MetadataConnection(metadataConfig);

        ConnectorConfig signedInConfig = new ConnectorConfig();
        signedInConfig.setSessionId(loginResult.getSessionId());
        signedInConfig.setServiceEndpoint(loginResult.getServerUrl());
        partnerConnection = Connector.newConnection(signedInConfig);
    }

    public PartnerConnection getPartnerConnection() {
        return this.partnerConnection;
    }

    /**
     * Sets configuration and performs the deployment of metadata to a Salesforce organization
     *
     * @param bytes
     * @param validateOnly
     * @param testLevel
     * @param specifiedTests
     * @param containsApex
     * @return
     * @throws Exception
     */
    public boolean deployToServer(ByteArrayOutputStream bytes,
                                  TestLevel testLevel,
                                  String[] specifiedTests,
                                  boolean validateOnly,
                                  boolean containsApex) throws Exception
    {
        DeployOptions deployOptions = new DeployOptions();
        deployOptions.setPerformRetrieve(false);
        deployOptions.setRollbackOnError(true);
        deployOptions.setSinglePackage(true);
        deployOptions.setCheckOnly(validateOnly);

        // We need to make sure there are actually tests supplied for RunSpecifiedTests...
        if (testLevel.equals(TestLevel.RunSpecifiedTests)) {
            if (specifiedTests.length > 0) {
                deployOptions.setTestLevel(testLevel);
                deployOptions.setRunTests(specifiedTests);
            } else {
                deployOptions.setTestLevel(TestLevel.NoTestRun);
            }
        } else if (containsApex) { // And that we should even set a TestLevel
            deployOptions.setTestLevel(testLevel);
        }

        AsyncResult asyncResult = metadataConnection.deploy(bytes.toByteArray(), deployOptions);
        String asyncResultId = asyncResult.getId();

        int poll = 0;
        int maxPoll = Integer.valueOf(maxPollString);
        long pollWait = Long.valueOf(pollWaitString);
        boolean fetchDetails;
        do {
            Thread.sleep(pollWait);

            if (poll++ > maxPoll) {
                throw new Exception("[SMA] Request timed out. You can check the results later by using this AsyncResult Id: " + asyncResultId);
            }
            // Only fetch the details every three poll attempts
            fetchDetails = (poll % 3 == 0);
            deployResult = metadataConnection.checkDeployStatus(asyncResultId, fetchDetails);
        } while (!deployResult.isDone());

        // This is more to do with errors related to Salesforce. Actual deployment failures are not returned as error codes.
        if (!deployResult.isSuccess() && deployResult.getErrorStatusCode() != null) {
            throw new Exception(deployResult.getErrorStatusCode() + " msg:" + deployResult.getErrorMessage());
        }

        if (!fetchDetails) {
            // Get the final result with details if we didn't do it in the last attempt.
            deployResult = metadataConnection.checkDeployStatus(asyncResultId, true);
        }
        deployDetails = deployResult.getDetails();

        return deployResult.isSuccess();
    }

    /**
     * Returns a formatted string of test failures for printing to the Jenkins console
     *
     * @return
     */
    public String getTestFailures() {
        RunTestsResult rtr = deployDetails.getRunTestResult();
        StringBuilder buf = new StringBuilder();

        if (rtr.getFailures().length > 0) {
            buf.append("[SMA] Test Failures\n");

            for (RunTestFailure failure : rtr.getFailures()) {
                String n = (failure.getNamespace() == null ? "" :
                        (failure.getNamespace() + ".")) + failure.getName();
                buf.append("Test failure, method: " + n + "." +
                        failure.getMethodName() + " -- " +
                        failure.getMessage() + " stack " +
                        failure.getStackTrace() + "\n\n");
            }
        }
        return buf.toString();
    }

    /**
     * Returns a formatted string of component failures for printing to the Jenkins console
     *
     * @return
     */
    public String getComponentFailures() {
        DeployMessage messages[] = deployDetails.getComponentFailures();
        StringBuilder buf = new StringBuilder();

        for (DeployMessage message : messages) {
            if (!message.isSuccess()) {
                buf.append("[SMA] Component Failures\n");

                String loc = null;
                if (message.getLineNumber() > 0) {
                    loc = "(" + message.getLineNumber() + "," + message.getColumnNumber() + ")";
                } else if (!message.getFileName().equals(message.getFullName())) {
                    loc = "(" + message.getFullName() + ")";
                }
                buf.append(message.getFileName() + loc + ":" + message.getProblem()).append('\n');
            }
        }
        return buf.toString();
    }

    /**
     * Returns a formatted string of the code coverage information for this deployment
     *
     * @return
     */
    public String getCodeCoverage() {
        RunTestsResult rtr = deployDetails.getRunTestResult();
        StringBuilder buf = new StringBuilder();
        DecimalFormat df = new DecimalFormat("#.##");

        //Get the individual coverage results
        CodeCoverageResult[] ccresult = rtr.getCodeCoverage();

        if (ccresult.length > 0) {
            buf.append("[SMA] Code Coverage Results\n");

            double loc = 0;
            double locUncovered = 0;
            for (CodeCoverageResult ccr : ccresult) {
                buf.append(ccr.getName() + ".cls");
                buf.append(" -- ");
                loc = ccr.getNumLocations();
                locUncovered = ccr.getNumLocationsNotCovered();

                double coverage = 0;
                if (loc > 0) {
                    coverage = calculateCoverage(locUncovered, loc);
                }
                buf.append(df.format(coverage) + "%\n");
            }

            // Get the total code coverage for this deployment
            double totalCoverage = getTotalCodeCoverage(ccresult);
            buf.append("\nTotal code coverage for this deployment -- ");
            buf.append(df.format(totalCoverage) + "%\n");
        }
        return buf.toString();
    }

    /**
     * Returns a formatted string of code coverage warnings for printing to the Jenkins console
     *
     * @return
     */
    public String getCodeCoverageWarnings() {
        RunTestsResult rtr = deployDetails.getRunTestResult();
        StringBuilder buf = new StringBuilder();
        CodeCoverageWarning[] ccwarn = rtr.getCodeCoverageWarnings();

        if (ccwarn.length > 0) {
            buf.append("[SMA] Code Coverage Warnings\n");

            for (CodeCoverageWarning ccw : ccwarn) {
                buf.append("Code coverage issue");

                if (ccw.getName() != null) {
                    String n = (ccw.getNamespace() == null ? "" :
                            (ccw.getNamespace() + ".")) + ccw.getName();
                    buf.append(", class: " + n);
                }
                buf.append(" -- " + ccw.getMessage() + "\n");
            }
        }
        return buf.toString();
    }

    /**
     * Returns the DeployDetails from this deployment
     *
     * @return
     */
    public DeployDetails getDeployDetails() { return deployDetails; }

    /**
     * Sets the DeployDetails for this deployment. For unit tests
     *
     * @param deployDetails
     */
    public void setDeployDetails(DeployDetails deployDetails) { this.deployDetails = deployDetails; }

    /**
     * Helper method to calculate the total code coverage in this deployment
     *
     * @param ccresult
     * @return
     */
    private Double getTotalCodeCoverage(CodeCoverageResult[] ccresult) {
        double zeroCoverage = 0;

        if (ccresult.length == 0) { return zeroCoverage; }

        double totalLoc = 0;
        double totalLocUncovered = 0;

        for (CodeCoverageResult ccr : ccresult) {
            totalLoc += ccr.getNumLocations();
            totalLocUncovered += ccr.getNumLocationsNotCovered();
        }
        if (totalLoc == 0) { return zeroCoverage; }

        return calculateCoverage(totalLocUncovered, totalLoc);
    }

    /**
     * Helper method to calculate the double for the coverage
     *
     * @param totalLocUncovered
     * @param totalLoc
     * @return
     */
    private double calculateCoverage(double totalLocUncovered, double totalLoc) {
        return (1 - (totalLocUncovered / totalLoc)) * 100;
    }

    public void createJenkinsCICustomSettingsSObject() throws ConnectionException {
        CustomObject cs = new CustomObject();
        cs.setCustomSettingsType(CustomSettingsType.Hierarchy);
        String name = "JenkinsCISettings";
        cs.setFullName(name + "__c");
        cs.setLabel(name);

        String gitSha1FieldDevName = "GitSha1";
        CustomField gitSha1Field = new CustomField();
        gitSha1Field.setType(FieldType.Text);
        gitSha1Field.setLength(255);
        gitSha1Field.setLabel("Git SHA1");
        gitSha1Field.setFullName(gitSha1FieldDevName + "__c");

        String gitDeploymentDateDevName = "GitDeploymentDate";
        CustomField gitDeploymentDateField = new CustomField();
        gitDeploymentDateField.setType(FieldType.DateTime);
        gitDeploymentDateField.setLabel("Git Deployment Date");
        gitDeploymentDateField.setFullName(gitDeploymentDateDevName + "__c");

        String jobNameDevName = "JenkinsJobName";
        CustomField jobNameField = new CustomField();
        jobNameField.setType(FieldType.Text);
        jobNameField.setLength(255);
        jobNameField.setLabel("Jenkins Job Name");
        jobNameField.setFullName(jobNameDevName + "__c");

        String buildNumberDevName = "JenkinsBuildNumber";
        CustomField buildNumberField = new CustomField();
        buildNumberField.setType(FieldType.Text);
        buildNumberField.setLength(255);
        buildNumberField.setLabel("Jenkins Build Number");
        buildNumberField.setFullName(buildNumberDevName + "__c");

        cs.setFields(new CustomField[] { gitSha1Field, gitDeploymentDateField, jobNameField, buildNumberField });

        com.sforce.soap.metadata.SaveResult[] results = metadataConnection.createMetadata(new Metadata[] { cs });

        for (com.sforce.soap.metadata.SaveResult r : results) {
            if (r.isSuccess()) {
                LOG.warning("Component '" + r.getFullName() + "' created successfully!");
            } else {
                LOG.warning("Could not create component '" + r.getFullName() + "'. Errors: ");
                for (Error err : r.getErrors()) {
                    LOG.warning("- " + err.getMessage());

                }
            }
        }
    }

    public void saveJenkinsCISettings(SObject settings) throws ConnectionException {
        com.sforce.soap.partner.UpsertResult[] res = partnerConnection.upsert("Name", new SObject[] { settings });
        for (com.sforce.soap.partner.UpsertResult r : res) {
            if (r.isSuccess()) {
                LOG.warning("Upsert of JenkinsCISettings should have been successful");

            } else {
                LOG.warning("Error while saving JenkinsCISettings: ");
                for (com.sforce.soap.partner.Error err : r.getErrors()) {
                    LOG.warning("- " + err.getMessage());
                }
            }
        }
    }

    public SObject retrieveJenkinsCISettingsFromOrg() throws Exception {
        QueryResult qr = partnerConnection.query("SELECT Name, GitSha1__c, GitDeploymentDate__c FROM JenkinsCISettings__c WHERE Name = 'SMA' LIMIT 1");
        SObject[] sobjs = qr.getRecords();

        if (sobjs.length == 0) {
            throw new NoSuchElementException("Could not find a JenkinsCISettings record");
        }
        return sobjs[0];
    }
}