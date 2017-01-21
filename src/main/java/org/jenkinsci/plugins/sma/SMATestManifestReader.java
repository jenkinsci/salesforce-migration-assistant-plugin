package org.jenkinsci.plugins.sma;


import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;

import java.util.*;

/**
 * Created by ronvelzeboer on 14/01/17.
 */
public class SMATestManifestReader {
    private final XMLConfiguration manifest;

    public SMATestManifestReader(String pathToManifest) throws ConfigurationException {
        this.manifest = new XMLConfiguration(pathToManifest);
    }

    public Map<String, Set<String>> getClassMapping() {
        Map<String, Set<String>> result = new HashMap<String, Set<String>>();
        List<HierarchicalConfiguration> mappingConfig = this.manifest.configurationsAt("mappings");

        if (null == mappingConfig) { return result; }

        for (HierarchicalConfiguration config : mappingConfig) {
            String className = config.getString("class");

            if (null == className || className.isEmpty()) { continue; }

            if (!result.containsKey(className)) {
                result.put(className, new HashSet<String>());
            }
            String[] testClasses = config.getStringArray("tests");

            for (String testClass : testClasses) {
                if (testClass.isEmpty()) { continue; }

                result.get(className).add(testClass);
            }
        }
        return result;
    }
}
