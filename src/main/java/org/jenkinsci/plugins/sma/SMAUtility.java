package org.jenkinsci.plugins.sma;

import hudson.model.BuildListener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Utility class for performing a variety of tasks in SMA.
 *
 * @author aesanch2
 */
public class SMAUtility {
    private static final Logger LOG = Logger.getLogger(SMAUtility.class.getName());


    /**
     * Creates a zipped byte array of the deployment or rollback package
     *
     * @param deployData
     * @param packageManifest
     * @param destructiveChange
     * @return
     * @throws Exception
     */
    public static ByteArrayOutputStream zipPackage(Map<String, byte[]> deployData,
                                                   SMAPackage packageManifest,
                                                   SMAPackage destructiveChange) throws Exception
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = null;
        try {
            zos = new ZipOutputStream(baos);

            ZipEntry manifestFile = new ZipEntry(packageManifest.getName());
            zos.putNextEntry(manifestFile);
            zos.write(packageManifest.getPackage().getBytes());
            zos.closeEntry();

            ZipEntry destructiveChanges = new ZipEntry(destructiveChange.getName());
            zos.putNextEntry(destructiveChanges);
            zos.write(destructiveChange.getPackage().getBytes());
            zos.closeEntry();

            for (String metadata : deployData.keySet()) {
                ZipEntry metadataEntry = new ZipEntry(metadata);
                zos.putNextEntry(metadataEntry);
                zos.write(deployData.get(metadata));
                zos.closeEntry();
            }
        } finally {
            if (null != zos) { zos.close(); }
        }
        return baos;
    }

    /**
     * Helper to write the zip to a file location
     *
     * @param zipBytes
     * @param location
     * @throws Exception
     */
    public static void writeZip(ByteArrayOutputStream zipBytes, String location) throws Exception {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(location);
            fos.write(zipBytes.toByteArray());
        } finally {
            if (null != fos) { fos.close(); }
        }
    }

    /**
     * Helper to find an existing package.xml file in the provided repository
     *
     * @param directory
     * @return
     */
    public static String findPackage(File directory) {
        String location = "";
        File[] filesInDir = directory.listFiles();

        for (File f : filesInDir) {
            if (f.isDirectory()) {
                location = findPackage(f);
            } else if (f.getName().equals("package.xml")) {
                location = f.getPath();
            }
            if (!location.isEmpty()) {
                break;
            }
        }
        return location;
    }

    /**
     * We don't actually want to load the -meta.xml files, so we use this to get the real item and handle the -metas
     * elsewhere since both components are required for deployment.
     *
     * @param repoItem
     * @return
     */
    public static String checkMeta(String repoItem) {
        String actualItem = repoItem;

        if (repoItem.contains("-meta")) {
            actualItem = repoItem.substring(0, repoItem.length() - 9);
        }
        return actualItem;
    }

    /**
     * Prints a set of metadata names to the Jenkins console
     *
     * @param listener
     * @param metadataList
     */
    public static void printMetadataToConsole(BuildListener listener, List<SMAMetadata> metadataList) {
        // Sorts by extension, then by member name
        Collections.sort(metadataList);

        for (SMAMetadata metadata : metadataList) {
            listener.getLogger().println("- " + metadata.getFullName());
        }
        listener.getLogger().println();
    }

    /**
     * Searches for a possible unit tests in the repository for a given set of metadata
     *
     * @param allMetadata
     * @param testClassRegex
     * @return
     */
    public static String searchForTestClass(Set<String> allMetadata, String testClassRegex) {
        String match = null;
        Matcher matcher;

        for (String s : allMetadata) {
            matcher = Pattern.compile(testClassRegex).matcher(s);
            if (matcher.find()) {
                match = s;
                break;
            }
        }
        return match;
    }
}
