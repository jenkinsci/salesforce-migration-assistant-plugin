package org.jenkinsci.plugins.sma;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

import java.util.Map;
import java.util.Set;

/**
 * Created by ronvelzeboer on 24/01/17.
 */
public class SMATestManifestReaderTest {
    private Map<String, Set<String>> mapping;

    @Before
    public void setUp() throws Exception {
        SMATestManifestReader reader = new SMATestManifestReader("SMAManifest.xml");
        this.mapping = reader.getClassMapping();
    }

    @Test
    public void testClassWithMultipleTests() {
        assertEquals(true, mapping.containsKey("test1"));
        assertEquals(2, mapping.get("test1").size());
    }

    @Test
    public void testMappingWithEmptyTestContainers() {
        assertEquals(true, mapping.containsKey("test3"));
        assertEquals(0, mapping.get("test3").size());
    }

    @Test
    public void testMappingWithNoTestContainers() {
        assertEquals(true, mapping.containsKey("test4"));
        assertEquals(0, mapping.get("test4").size());
    }
}
