package org.jenkinsci.plugins.sma;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SMAGitTest
{

    private Repository repository;
    private SMAGit git;
    private File addition, addMeta;
    private File modification, modifyMeta;
    private File deletion, deleteMeta;
    private File localPath;
    private String oldSha, gitDir;
    private final String contents = "\n";

    /**
     * Before to setup the test.
     *
     * @throws Exception
     */
    @Before
    public void setUp() throws Exception
    {
        //Setup the fake repository
        localPath = File.createTempFile("TestGitRepository", "");
        localPath.delete();
        repository = FileRepositoryBuilder.create(new File(localPath, ".git"));
        repository.create();

        File classesPath = new File(repository.getDirectory().getParent() + "/src/classes");
        classesPath.mkdirs();
        File pagesPath = new File(repository.getDirectory().getParent() + "/src/pages");
        pagesPath.mkdirs();
        File triggersPath = new File(repository.getDirectory().getParent() + "/src/triggers");
        triggersPath.mkdirs();


        //Add the first collection of files
        deletion = createFile("deleteThis.cls", classesPath);
        deleteMeta = createFile("deleteThis.cls-meta.xml", classesPath);
        modification = createFile("modifyThis.page", pagesPath);
        modifyMeta = createFile("modifyThis.page-meta.xml", pagesPath);
        new Git(repository).add().addFilepattern("src/classes/deleteThis.cls").call();
        new Git(repository).add().addFilepattern("src/classes/deleteThis.cls-meta.xml").call();
        new Git(repository).add().addFilepattern("src/pages/modifyThis.page").call();
        new Git(repository).add().addFilepattern("src/pages/modifyThis.page-meta.xml").call();

        //Create the first commit
        RevCommit firstCommit = new Git(repository).commit().setMessage("Add deleteThis and modifyThis").call();
        oldSha = firstCommit.getName();

        //Delete the deletion file, modify the modification file, and add the addition file
        new Git(repository).rm().addFilepattern("src/classes/deleteThis.cls").call();
        new Git(repository).rm().addFilepattern("src/classes/deleteThis.cls-meta.xml").call();
        PrintWriter out = new PrintWriter(modification.getPath());
        out.println("Modified the page");
        out.close();
        addition = createFile("addThis.trigger", triggersPath);
        addMeta = createFile("addThis.trigger-meta.xml", triggersPath);
        new Git(repository).add().addFilepattern("src/pages/modifyThis.page").call();
        new Git(repository).add().addFilepattern("src/pages/modifyThis.page-meta.xml").call();
        new Git(repository).add().addFilepattern("src/triggers/addThis.trigger").call();
        new Git(repository).add().addFilepattern("src/triggers/addThis.trigger-meta.xml").call();
        new Git(repository).add().addFilepattern("src/classes/deleteThis.cls").call();
        new Git(repository).add().addFilepattern("src/classes/deleteThis.cls-meta.xml").call();

        //Create the second commit
        RevCommit secondCommit = new Git(repository).commit().setMessage("Remove deleteThis. Modify " +
                "modifyThis. Add addThis.").call();

        gitDir = localPath.getPath();
    }

    /**
     * After to tear down the test.
     *
     * @throws Exception
     */
    @After
    public void tearDown() throws Exception
    {
        repository.close();
        FileUtils.deleteDirectory(localPath);
    }

    /**
     * Test the diff capability of the wrapper.
     *
     * @throws Exception
     */
    @Test
    public void testDiff() throws Exception
    {
        Map<String, byte[]> expectedDelete = new HashMap<String, byte[]>();
        expectedDelete.put("src/classes/deleteThis.cls", contents.getBytes());

        Map<String, byte[]> expectedMods = new HashMap<String, byte[]>();
        expectedMods.put("src/pages/modifyThis.page", contents.getBytes());

        Map<String, byte[]> expectedAdds = new HashMap<String, byte[]>();
        expectedAdds.put("src/triggers/addThis.trigger", contents.getBytes());

        git = new SMAGit(gitDir, oldSha, SMAGit.Mode.STD);

        Map<String, byte[]> deletedContents = git.getDeletedMetadata();
        Map<String, byte[]> modifiedContents = git.getUpdatedMetadata();
        Map<String, byte[]> addedContents = git.getNewMetadata();

        assertEquals(expectedAdds.size(), addedContents.size());
        assertEquals(expectedMods.size(), modifiedContents.size());
        assertEquals(expectedDelete.size(), deletedContents.size());
    }

    /**
     * Test the overloaded constructors.
     *
     * @throws Exception
     */
    @Test
    public void testInitialCommit() throws Exception
    {
        Map<String, byte[]> expectedContents = new HashMap<String, byte[]>();
        expectedContents.put("src/pages/modifyThis.page", contents.getBytes());
        expectedContents.put("src/pages/modifyThis.page-meta.xml", contents.getBytes());
        expectedContents.put("src/triggers/addThis.trigger", contents.getBytes());
        expectedContents.put("src/triggers/addThis.trigger-meta.xml", contents.getBytes());

        git = new SMAGit(gitDir, null, SMAGit.Mode.INI);

        Map<String, byte[]> allMetadata = git.getAllMetadata();

        assertEquals(expectedContents.size(), allMetadata.size());
    }

    /**
     * Test the ghprb constructor.
     *
     * @throws Exception
     */
    @Test
    public void testPullRequest() throws Exception
    {
        Map<String, byte[]> expectedContents = new HashMap<String, byte[]>();
        expectedContents.put("src/pages/modifyThis.page", contents.getBytes());
        expectedContents.put("src/pages/modifyThis.page-meta.xml", contents.getBytes());
        expectedContents.put("src/triggers/addThis.trigger", contents.getBytes());
        expectedContents.put("src/triggers/addThis.trigger-meta.xml", contents.getBytes());

        String oldBranch = "refs/remotes/origin/oldBranch";
        CreateBranchCommand cbc = new Git(repository).branchCreate();
        cbc.setName(oldBranch);
        cbc.setStartPoint(oldSha);
        cbc.call();

        git = new SMAGit(gitDir, "oldBranch", SMAGit.Mode.PRB);

        Map<String, byte[]> allMetadata = git.getAllMetadata();

        assertEquals(expectedContents.size(), allMetadata.size());
    }

    /**
     * Test the ability to update the package manifest.
     *
     * @throws Exception
     */
    @Test
    public void testCommitPackageXML() throws Exception
    {
        Map<String, byte[]> metadataContents = new HashMap<String, byte[]>();
        List<SMAMetadata> metadata = new ArrayList<SMAMetadata>();

        git = new SMAGit(gitDir, oldSha, SMAGit.Mode.STD);
        metadataContents = git.getUpdatedMetadata();
        metadataContents.putAll(git.getNewMetadata());

        for (String s : metadataContents.keySet())
        {
            metadata.add(SMAMetadataTypes.createMetadataObject(s, metadataContents.get(s)));
        }

        SMAPackage manifest = new SMAPackage(metadata, false);

        Boolean createdManifest = git.updatePackageXML(
                localPath.getPath(),
                "Test Guy",
                "testguy@example.net",
                manifest
        );

        assertTrue(createdManifest);
    }

    /**
     * Test the ability to update the package manifest.
     *
     * @throws Exception
     */
    @Test
    public void testCommitExistingPackage() throws Exception
    {
        File sourceDir = new File(localPath.getPath() + "/src");
        File existingPackage = createFile("package.xml", sourceDir);

        new Git(repository).add().addFilepattern("src/package.xml").call();
        new Git(repository).commit().setMessage("Add package.xml").call();

        Map<String, byte[]> metadataContents = new HashMap<String, byte[]>();
        List<SMAMetadata> metadata = new ArrayList<SMAMetadata>();

        git = new SMAGit(gitDir, oldSha, SMAGit.Mode.STD);
        metadataContents = git.getUpdatedMetadata();
        metadataContents.putAll(git.getNewMetadata());

        for (String s : metadataContents.keySet())
        {
            metadata.add(SMAMetadataTypes.createMetadataObject(s, metadataContents.get(s)));
        }

        SMAPackage manifest = new SMAPackage(metadata, false);

        Boolean createdManifest = git.updatePackageXML(
                localPath.getPath(),
                "Test Guy",
                "testguy@example.net",
                manifest
        );

        assertTrue(createdManifest);

        // Also check to make sure we didn't create the default package
        File unexpectedPackage = new File(localPath.getPath() + "/unpackaged/package.xml");
        assertTrue(!unexpectedPackage.exists());
    }

    private File createFile(String name, File path) throws Exception
    {
        File thisFile;

        thisFile = new File(path, name);
        thisFile.createNewFile();

        PrintWriter print = new PrintWriter(thisFile);
        print.println(contents);
        print.close();

        return thisFile;
    }
}