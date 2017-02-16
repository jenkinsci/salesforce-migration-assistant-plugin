package org.jenkinsci.plugins.sma;

import com.sforce.soap.metadata.Package;
import com.sforce.soap.metadata.PackageTypeMembers;
import com.sforce.ws.bind.TypeMapper;
import com.sforce.ws.parser.XmlOutputStream;

import javax.xml.namespace.QName;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wrapper for com.sforce.soap.metadata.Package.
 *
 */
public class SMAPackage
{
    private List<SMAMetadata> contents;
    private boolean destructiveChange;
    private Package packageManifest;
    private final String METADATA_URI = "http://soap.sforce.com/2006/04/metadata";

    /**
     * Constructor for SMAPackage
     * Takes the SMAMetdata contents that are to be represented by the manifest file and generates a Package for deployment
     *
     * @param contents
     * @param destructiveChange
     */
    public SMAPackage(List<SMAMetadata> contents,
                      boolean destructiveChange) throws Exception
    {
        this.contents = contents;
        this.destructiveChange = destructiveChange;

        packageManifest = new Package();
        packageManifest.setVersion(SMAMetadataTypes.getAPIVersion());
        packageManifest.setTypes(determinePackageTypes().toArray(new PackageTypeMembers[0]));
    }

    public List<SMAMetadata> getContents() { return contents; }

    /**
     * Returns the name of the manifest file for this SMAPackage
     * @return
     */
    public String getName() {
        return destructiveChange ? "destructiveChanges.xml" : "package.xml";
    }

    /**
     * Transforms the Package into a ByteArray
     *
     * @return String(packageStream.toByteArray())
     * @throws Exception
     */
    public String getPackage() throws Exception {
        TypeMapper typeMapper = new TypeMapper();
        ByteArrayOutputStream packageStream = new ByteArrayOutputStream();
        QName packageQName = new QName(METADATA_URI, "Package");
        XmlOutputStream xmlOutputStream = null;
        try {
            xmlOutputStream = new XmlOutputStream(packageStream, true);
            xmlOutputStream.setPrefix("", METADATA_URI);
            xmlOutputStream.setPrefix("xsi", "http://www.w3.org/2001/XMLSchema-instance");
            packageManifest.write(packageQName, xmlOutputStream, typeMapper);
        } finally {
            if (null != xmlOutputStream) { xmlOutputStream.close(); }
        }
        return new String(packageStream.toByteArray());
    }

    /**
     * Returns whether or not this package contains Apex components
     *
     * @return containsApex
     */
    public boolean containsApex() {
        for (SMAMetadata thisMetadata : contents) {
            if (thisMetadata.getMetadataType().equals("ApexClass")
                    || thisMetadata.getMetadataType().equals("ApexTrigger")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sorts the metadata into types and members for the manifest
     *
     * @return
     */
    private List<PackageTypeMembers> determinePackageTypes() {
        List<PackageTypeMembers> types = new ArrayList<PackageTypeMembers>();
        Map<String, List<String>> contentsByType = new HashMap<String, List<String>>();

        // Sort the metadata objects by metadata type
        for (SMAMetadata mdObject : contents) {
            if (destructiveChange && !mdObject.isDestructible()) {
                // Don't include non destructible metadata in destructiveChanges
                continue;
            }
            if (!contentsByType.containsKey(mdObject.getMetadataType())) {
                contentsByType.put(mdObject.getMetadataType(), new ArrayList<String>());
            }
            contentsByType.get(mdObject.getMetadataType()).add(mdObject.getMember());
        }
        // Put the members into list of PackageTypeMembers
        for (String metadataType : contentsByType.keySet()) {
            PackageTypeMembers members = new PackageTypeMembers();
            members.setName(metadataType);
            members.setMembers(contentsByType.get(metadataType).toArray(new String[0]));
            types.add(members);
        }
        return types;
    }
}
