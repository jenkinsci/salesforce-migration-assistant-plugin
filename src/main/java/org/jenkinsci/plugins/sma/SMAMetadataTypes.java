package org.jenkinsci.plugins.sma;

import org.apache.commons.io.FilenameUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.logging.Logger;

/**
 * Class for the salesforceMetadata.xml document that contains Salesforce Metadata API information.
 *
 */
public class SMAMetadataTypes {
    private static final Logger LOG = Logger.getLogger(SMAMetadataTypes.class.getName());

    private static final ClassLoader loader = SMAMetadataTypes.class.getClassLoader();
    private static String pathToResource = loader.getResource("org/jenkinsci/plugins/sma/salesforceMetadata.xml").toString();
    private static Document doc;
    private static Boolean docAlive = false;

    /**
     * Initializes the Document representation of the salesforceMetadata.xml file
     *
     * @throws Exception
     */
    private static void initDocument() throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dbBuilder = dbFactory.newDocumentBuilder();
        doc = dbBuilder.parse(pathToResource);
        docAlive = true;
    }

    /**
     * Returns the Salesforce Metadata API Version
     *
     * @return version
     */
    public static String getAPIVersion() throws Exception {
        if (!docAlive) {
            initDocument();
        }
        String version = null;

        doc.getDocumentElement().normalize();

        NodeList verNodes = doc.getElementsByTagName("version");

        //There should only be one node in this list
        for (int iterator = 0; iterator < verNodes.getLength(); iterator++) {
            Node curNode = verNodes.item(iterator);
            Element verElement = (Element) curNode;
            //If for some reason there is more than one, get the first one
            version = verElement.getAttribute("API");
        }
        return version;
    }

    /**
     * Creates an SMAMetadata object from a string representation of a file's path and filename.
     *
     * @param filepath
     * @return SMAMetadata
     * @throws Exception
     */
    public static SMAMetadata createMetadataObject(String filepath, byte[] data) throws Exception {
        if (!docAlive) {
            initDocument();
        }
        String container = "empty";
        String metadataType = "Invalid";
        boolean destructible = false;
        boolean valid = false;
        boolean metaxml = false;

        File file = new File(filepath);
        String object = file.getName();
        String member = FilenameUtils.removeExtension(object);
        String extension = FilenameUtils.getExtension(filepath);
        String path = FilenameUtils.getFullPath(filepath);

        //Normalize the salesforceMetadata.xml configuration file
        doc.getDocumentElement().normalize();

        NodeList extNodes = doc.getElementsByTagName("extension");

        //Get the node with the corresponding extension and get the relevant information for
        //creating the SMAMetadata object
        for (int iterator = 0; iterator < extNodes.getLength(); iterator++) {
            Node curNode = extNodes.item(iterator);

            Element element = (Element) curNode;
            if (element.getAttribute("name").equals(extension)) {
                container = element.getElementsByTagName("container").item(0).getTextContent();
                metadataType = element.getElementsByTagName("metadata").item(0).getTextContent();
                destructible = Boolean.parseBoolean(
                    element.getElementsByTagName("destructible").item(0).getTextContent()
                );
                valid = true;
                metaxml = Boolean.parseBoolean(element.getElementsByTagName("metaxml").item(0).getTextContent());
                break;
            }
        }
        return new SMAMetadata(
            extension, container, member, metadataType, path, destructible, valid, metaxml, data
        );
    }
}