package org.jenkinsci.plugins.sma;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;

/**
 * Creates an object representation of a Salesforce Metadata file.
 *
 */
public class SMAMetadata implements Comparable<SMAMetadata>
{
    private static final Logger LOG = Logger.getLogger(SMAMetadata.class.getName());

    private String extension;
    private String container;
    private String member;
    private String metadataType;
    private String path;
    private boolean destructible;
    private boolean valid;
    private boolean metaxml;
    private byte[] body;

    /**
     * Constructor for SMAMetadata object
     *
     * @param extension
     * @param container
     * @param member
     * @param metadataType
     * @param path
     * @param destructible
     * @param valid
     * @param metaxml
     * @param body
     */
    public SMAMetadata(String extension,
                       String container,
                       String member,
                       String metadataType,
                       String path,
                       boolean destructible,
                       boolean valid,
                       boolean metaxml,
                       byte[] body)
    {
        this.extension = extension;
        this.container = container;
        this.member = member;
        this.metadataType = metadataType;
        this.path = path;
        this.destructible = destructible;
        this.valid = valid;
        this.metaxml = metaxml;
        this.body = body;
    }

    /**
     * Returns the extension for this metadata file.
     *
     * @return A string representation of the extension type of the metadata file.
     */
    public String getExtension() { return extension; }

    /**
     * Returns the parent container for this metadata file.
     *
     * @return A string representation of the parent container for this metadata file.
     */
    public String getContainer() { return container; }

    /**
     * Returns the path of the metadata file.
     *
     * @return A string representation of the path of the metadata file.
     */
    public String getPath() { return path; }

    /**
     * Returns the name of the metadata file.
     *
     * @return A string representation of the metadata file's name.
     */
    public String getMember() { return member; }

    /**
     * Returns the metadata type of this metadata file.
     *
     * @return A string representation of the metadata file's type.
     */
    public String getMetadataType() { return metadataType; }

    /**
     * Returns whether or not this metadata object can be deleted using the Salesforce API.
     *
     * @return A boolean that describes whether or not this metadata object can be deleted using the Salesforce API.
     */
    public boolean isDestructible() { return destructible; }

    /**
     * Returns whether or not this metadata object is a valid member of the Salesforce API.
     *
     * @return A boolean that describes wheter or not this metadata object is a valid member of the Salesforce API.
     */
    public boolean isValid() { return valid; }

    /**
     * Returns whether or not this metadata object has an accompanying -meta.xml file.
     *
     * @return
     */
    public boolean hasMetaxml() { return metaxml; }

    /**
     * A toString() like method that returns a concatenation of the name and extension of the metadata object.
     *
     * @return A string of the name and extension of the metadata object.
     */
    public String getFullName() {
        return member + "." + extension;
    }

    public String toString() {
        return container + "/" + getFullName();
    }

    /**
     * The blob data in String format of the metadata's content.
     *
     * @return
     */
    public byte[] getBody() { return body; }

    /**
     * For sorting metadata by extension followed by member
     *
     * @param comparison
     * @return
     */
    @Override
    public int compareTo(SMAMetadata comparison)
    {
        int extCompare = this.extension.compareToIgnoreCase(comparison.extension);
        return extCompare == 0 ? this.member.compareToIgnoreCase(comparison.member) : extCompare;
    }

    /**
     * Get all apex files in the provided list
     *
     * @param contents
     * @return
     */
    public static HashSet<String> getApexClasses(List<SMAMetadata> contents)
    {
        HashSet<String> allApex = new HashSet<String>();

        for (SMAMetadata md : contents)
        {
            if (md.getMetadataType().equals("ApexClass"))
            {
                allApex.add(md.getMember());
            }
        }

        return allApex;
    }
}
