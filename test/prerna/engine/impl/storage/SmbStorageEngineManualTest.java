package prerna.engine.impl.storage;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Manual test for SmbStorageEngine - for use with a running SMB server
 *
 * Setup Options:
 *
 * Option 1: Docker Samba (Recommended)
 *   1. Run: docker-compose -f docker-compose.smb-test.yml up -d
 *   2. Use the default configuration below
 *   3. Run this test's main method
 *   4. Stop: docker-compose -f docker-compose.smb-test.yml down
 *
 * Option 2: Windows Local Share
 *   1. Enable SMB in Windows Features
 *   2. Create a shared folder (e.g., C:\SMBTest)
 *   3. Update the configuration below with your Windows username/password
 *   4. Run this test's main method
 *
 * Option 3: Existing Network Share
 *   1. Update the configuration below with your network details
 *   2. Run this test's main method
 */
public class SmbStorageEngineManualTest {

    public static void main(String[] args) {
        SmbStorageEngine engine = null;
        File testDir = null;

        try {
            System.out.println("=== SMB Storage Engine Manual Test ===\n");

            // Configuration
            Properties props = new Properties();

            // Option 1: Docker Samba (default)
            props.setProperty(SmbStorageEngine.SMB_HOST, "localhost");
            props.setProperty(SmbStorageEngine.SMB_PORT, "445");
            props.setProperty(SmbStorageEngine.SMB_USER, "testuser");
            props.setProperty(SmbStorageEngine.SMB_PASS, "testpass");
            props.setProperty(SmbStorageEngine.SMB_DOMAIN, "WORKGROUP");
            props.setProperty(SmbStorageEngine.SMB_SHARE_NAME, "testshare");
            props.setProperty(SmbStorageEngine.SMB_TIMEOUT, "60");

            // Option 2: Windows Local Share (uncomment and update)
            // props.setProperty(SmbStorageEngine.SMB_HOST, "127.0.0.1");
            // props.setProperty(SmbStorageEngine.SMB_PORT, "445");
            // props.setProperty(SmbStorageEngine.SMB_USER, "YourWindowsUsername");
            // props.setProperty(SmbStorageEngine.SMB_PASS, "YourWindowsPassword");
            // props.setProperty(SmbStorageEngine.SMB_DOMAIN, "");
            // props.setProperty(SmbStorageEngine.SMB_SHARE_NAME, "SMBTest"); // Share name, not path
            // props.setProperty(SmbStorageEngine.SMB_TIMEOUT, "60");

            System.out.println("1. Initializing SMB Storage Engine...");
            engine = new SmbStorageEngine();
            engine.open(props);
            System.out.println("   ✓ Connected to SMB share\n");

            // Test 1: List root directory
            System.out.println("2. Listing root directory...");
            List<String> files = engine.list("/");
            System.out.println("   Files found: " + files.size());
            for (String file : files) {
                System.out.println("   - " + file);
            }
            System.out.println();

            // Test 2: List with details
            System.out.println("3. Listing root directory with details...");
            List<Map<String, Object>> details = engine.listDetails("/");
            for (Map<String, Object> detail : details) {
                System.out.println("   Name: " + detail.get("Name"));
                System.out.println("   Path: " + detail.get("Path"));
                System.out.println("   Size: " + detail.get("Size"));
                System.out.println("   IsDir: " + detail.get("IsDir"));
                System.out.println("   ModTime: " + detail.get("ModTime"));
                System.out.println();
            }

            // Test 3: Create a local test file
            System.out.println("4. Creating local test file...");
            testDir = new File(System.getProperty("java.io.tmpdir"), "smb-manual-test");
            testDir.mkdirs();
            File testFile = new File(testDir, "test-" + System.currentTimeMillis() + ".txt");
            try (FileWriter writer = new FileWriter(testFile)) {
                writer.write("This is a test file created at " + new java.util.Date());
            }
            System.out.println("   ✓ Created: " + testFile.getAbsolutePath() + "\n");

            // Test 4: Upload to storage
            System.out.println("5. Uploading file to SMB storage...");
            engine.copyToStorage(testFile.getAbsolutePath(), "/test-uploads", null);
            System.out.println("   ✓ Uploaded to /test-uploads/" + testFile.getName() + "\n");

            // Test 5: List the uploaded file
            System.out.println("6. Verifying upload...");
            List<String> uploadedFiles = engine.list("/test-uploads");
            System.out.println("   Files in /test-uploads: " + uploadedFiles.size());
            for (String file : uploadedFiles) {
                System.out.println("   - " + file);
            }
            System.out.println();

            // Test 6: Download the file back
            System.out.println("7. Downloading file back from SMB storage...");
            File downloadDir = new File(testDir, "downloads");
            downloadDir.mkdirs();
            String remoteFilePath = "/test-uploads/" + testFile.getName();
            engine.copyToLocal(remoteFilePath, downloadDir.getAbsolutePath());
            File downloadedFile = new File(downloadDir, testFile.getName());
            System.out.println("   ✓ Downloaded to: " + downloadedFile.getAbsolutePath());
            System.out.println("   File exists: " + downloadedFile.exists());
            System.out.println("   File size: " + downloadedFile.length() + " bytes\n");

            // Test 7: Read blob to memory
            System.out.println("8. Reading file directly to memory...");
            byte[] data = engine.readBlobToMemory(remoteFilePath);
            System.out.println("   ✓ Read " + data.length + " bytes");
            System.out.println("   Content: " + new String(data, "UTF-8") + "\n");

            // Test 8: Delete the file
            System.out.println("9. Deleting file from SMB storage...");
            engine.deleteFromStorage(remoteFilePath);
            System.out.println("   ✓ Deleted\n");

            // Test 9: Verify deletion
            System.out.println("10. Verifying deletion...");
            List<String> afterDelete = engine.list("/test-uploads");
            boolean fileStillExists = afterDelete.contains(testFile.getName());
            System.out.println("   File still exists: " + fileStillExists);
            if (!fileStillExists) {
                System.out.println("   ✓ File successfully deleted\n");
            }

            System.out.println("=== All Tests Completed Successfully! ===");

        } catch (Exception e) {
            System.err.println("\n!!! Test Failed !!!");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.err.println("\nTroubleshooting:");
            System.err.println("1. Is the SMB server running? (docker-compose -f docker-compose.smb-test.yml ps)");
            System.err.println("2. Can you reach the host? (ping localhost or your SMB host)");
            System.err.println("3. Is port 445 accessible? (telnet localhost 445)");
            System.err.println("4. Are credentials correct?");
            System.err.println("5. Is the share name correct?");
        } finally {
            // Cleanup
            if (engine != null) {
                try {
                    engine.close();
                } catch (Exception e) {
                    System.err.println("Error closing engine: " + e.getMessage());
                }
            }

            if (testDir != null && testDir.exists()) {
                System.out.println("\nCleaning up local test directory...");
                deleteDirectory(testDir);
            }
        }
    }

    private static void deleteDirectory(File dir) {
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
