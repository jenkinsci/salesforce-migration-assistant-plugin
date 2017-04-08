package org.jenkinsci.plugins.sma;

import com.sforce.soap.partner.fault.InvalidSObjectFault;
import com.sforce.soap.partner.sobject.SObject;

import java.util.Calendar;
import java.util.NoSuchElementException;
import java.util.TimeZone;

/**
 * Created by ronvelzeboer on 08/02/17.
 */
public class SMAJenkinsCIOrgSettings {
    private static final String NAME = "SMA";

    private SMAConnection connection;
    private SObject sfCustomSetting;

    private SMAJenkinsCIOrgSettings(SMAConnection connection) throws Exception {
        this.connection = connection;
        initCustomSetting();
    }

    private void initCustomSetting() throws Exception {
        connection.createJenkinsCICustomSettingsSObject();
        try {
            sfCustomSetting = connection.retrieveJenkinsCISettingsFromOrg();
        } catch (InvalidSObjectFault e) {
            sfCustomSetting = createNewCustomSetting();
        } catch (NoSuchElementException e) {
            sfCustomSetting = createNewCustomSetting();
        }
    }

    private SObject createNewCustomSetting() {
        SObject so = new SObject();
        so.setType("JenkinsCISettings__c");
        so.setField("Name", NAME);
        so.setField("GitSha1__c", null);
        so.setField("GitDeploymentDate__c", null);
        so.setField("JenkinsJobName__c", null);
        so.setField("JenkinsBuildNumber__c", null);
        return so;
    }

    public static SMAJenkinsCIOrgSettings getInstance(SMAConnection connection) throws Exception {
        return new SMAJenkinsCIOrgSettings(connection);
    }

    private SObject getCustomSetting() {
        return sfCustomSetting;
    }

    public String getGitSha1() {
        return null == getCustomSetting().getField("GitSha1__c") ? null : getCustomSetting().getField("GitSha1__c").toString();
    }

    public void setGitSha1(String sha1) {
        getCustomSetting().setField("GitSha1__c", sha1);
    }

    public void setJenkinsJobName(String name) {
        getCustomSetting().setField("JenkinsJobName__c", name);
    }

    public void setJenkinsBuildNumber(String build) {
        getCustomSetting().setField("JenkinsBuildNumber__c", build);
    }

    public void save() throws Exception {
        getCustomSetting().setField("GitDeploymentDate__c", Calendar.getInstance(TimeZone.getTimeZone("GMT")));
        connection.saveJenkinsCISettings(getCustomSetting());
    }

}
