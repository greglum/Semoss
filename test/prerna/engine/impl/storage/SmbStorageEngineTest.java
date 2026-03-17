package prerna.engine.impl.storage;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import prerna.util.Utility;

/**
 * Integration tests for SmbStorageEngine using Testcontainers
 *
 * Requirements:
 * - Docker must be running
 * - Testcontainers dependency in pom.xml
 *
 * To run without Docker, use SmbStorageEngineManualTest with manual setup
 */
public class SmbStorageEngineTest {

    // Start a Samba container for testing
    @ClassRule
    public static GenericContainer<?> sambaContainer = new GenericContainer<>(DockerImageName.parse("dperson/samba:latest"))
        .withExposedPorts(445)
        .withEnv("USER", "testuser;testpass")
        .withEnv("SHARE", "testshare;/mount;yes;no;no;testuser;testuser")
        .withEnv("WORKGROUP", "WORKGROUP")
        .withFileSystemBind(createTempMountDir(), "/mount");

    private SmbStorageEngine engine;
    private File testLocalDir;

    private static String createTempMountDir() {
        try {
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "smb-test-mount");
            tempDir.mkdirs();
            return tempDir.getAbsolutePath();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create temp mount directory", e);
        }
    }

    @Before
    public void setUp() throws Exception {
        engine = new SmbStorageEngine();

        Properties props = new Properties();
        props.setProperty(SmbStorageEngine.SMB_HOST, sambaContainer.getHost());
        props.setProperty(SmbStorageEngine.SMB_PORT, String.valueOf(sambaContainer.getMappedPort(445)));
        props.setProperty(SmbStorageEngine.SMB_USER, "testuser");
        props.setProperty(SmbStorageEngine.SMB_PASS, "testpass");
        props.setProperty(SmbStorageEngine.SMB_DOMAIN, "WORKGROUP");
        props.setProperty(SmbStorageEngine.SMB_SHARE_NAME, "testshare");
        props.setProperty(SmbStorageEngine.SMB_TIMEOUT, "60");

        engine.open(props);

        // Create a local test directory
        testLocalDir = new File(System.getProperty("java.io.tmpdir"), "smb-local-test");
        testLocalDir.mkdirs();
    }

    @After
    public void tearDown() throws Exception {
        if (engine != null) {
            engine.close();
        }

        // Clean up local test directory
        if (testLocalDir != null && testLocalDir.exists()) {
            deleteDirectory(testLocalDir);
        }
    }

    @Test
    public void testList() throws Exception {
        List<String> files = engine.list("/");
        assertNotNull(files);
        // Fresh container should be empty or have minimal content
    }

    @Test
    public void testListDetails() throws Exception {
        List<Map<String, Object>> details = engine.listDetails("/");
        assertNotNull(details);
    }

    @Test
    public void testCopyToStorageAndBack() throws Exception {
        // Create a test file
        File testFile = new File(testLocalDir, "test.txt");
        try (FileWriter writer = new FileWriter(testFile)) {
            writer.write("Hello SMB Test!");
        }

        // Upload to storage
        engine.copyToStorage(testFile.getAbsolutePath(), "/upload", null);

        // Verify file exists
        List<String> files = engine.list("/upload");
        assertTrue("File should be in the list", files.contains("test.txt"));

        // Download back
        File downloadDir = new File(testLocalDir, "download");
        downloadDir.mkdirs();
        engine.copyToLocal("/upload/test.txt", downloadDir.getAbsolutePath());

        File downloadedFile = new File(downloadDir, "test.txt");
        assertTrue("Downloaded file should exist", downloadedFile.exists());
    }

    @Test
    public void testDeleteFromStorage() throws Exception {
        // Create and upload a test file
        File testFile = new File(testLocalDir, "delete-test.txt");
        try (FileWriter writer = new FileWriter(testFile)) {
            writer.write("Delete me!");
        }
        engine.copyToStorage(testFile.getAbsolutePath(), "/", null);

        // Verify it exists
        List<String> beforeDelete = engine.list("/");
        assertTrue("File should exist before delete", beforeDelete.contains("delete-test.txt"));

        // Delete it
        engine.deleteFromStorage("/delete-test.txt");

        // Verify it's gone
        List<String> afterDelete = engine.list("/");
        assertFalse("File should not exist after delete", afterDelete.contains("delete-test.txt"));
    }

    @Test
    public void testReadBlobToMemory() throws Exception {
        // Create and upload a test file
        File testFile = new File(testLocalDir, "memory-test.txt");
        String content = "Test content for memory read";
        try (FileWriter writer = new FileWriter(testFile)) {
            writer.write(content);
        }
        engine.copyToStorage(testFile.getAbsolutePath(), "/", null);

        // Read to memory
        byte[] data = engine.readBlobToMemory("/memory-test.txt");
        assertNotNull("Data should not be null", data);
        String readContent = new String(data, "UTF-8");
        assertEquals("Content should match", content, readContent);
    }

    private void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        dir.delete();
    }
}
