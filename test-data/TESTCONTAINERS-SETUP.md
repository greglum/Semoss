# Testcontainers Setup for SMB Testing

## 📦 Add Dependencies to pom.xml

Add these dependencies to your `pom.xml` to enable Testcontainers-based integration tests:

```xml
<!-- Test Dependencies for SMB Integration Tests -->

<!-- JUnit 4 (if not already present) -->
<dependency>
    <groupId>junit</groupId>
    <artifactId>junit</artifactId>
    <version>4.13.2</version>
    <scope>test</scope>
</dependency>

<!-- Testcontainers Core -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>

<!-- Testcontainers JUnit 4 Support -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
```

## 🔧 Alternative: Without Testcontainers

If you prefer **not** to use Testcontainers, you can:

1. **Use Docker Compose manually:**
   ```bash
   docker-compose -f docker-compose.smb-test.yml up -d
   ```

2. **Modify the test to use manual connection:**
   ```java
   // In your test setup
   Properties props = new Properties();
   props.setProperty(SmbStorageEngine.SMB_HOST, "localhost");
   props.setProperty(SmbStorageEngine.SMB_PORT, "445");
   props.setProperty(SmbStorageEngine.SMB_USER, "testuser");
   props.setProperty(SmbStorageEngine.SMB_PASS, "testpass");
   props.setProperty(SmbStorageEngine.SMB_SHARE_NAME, "testshare");
   ```

3. **Run tests:**
   ```bash
   mvn test -Dtest=YourManualTest
   ```

## 🎯 Benefits of Each Approach

### Testcontainers

✅ **Pros:**
- Automatic container lifecycle management
- Isolated test environment
- Works in CI/CD without manual setup
- Parallel test execution support
- Dynamic port allocation

❌ **Cons:**
- Requires Docker
- Slower test startup (container creation)
- Additional dependency

### Manual Docker Compose

✅ **Pros:**
- Simple and straightforward
- No additional Java dependencies
- Faster test reruns (container stays running)
- Easy to inspect/debug

❌ **Cons:**
- Manual lifecycle management
- Requires remembering to start/stop
- Port conflicts if not cleaned up
- Doesn't work well in CI without extra setup

### Recommendation

- **Local Development:** Use Docker Compose (simpler, faster iterations)
- **CI/CD Pipelines:** Use Testcontainers (automatic, isolated)
- **Quick Tests:** Use `SmbStorageEngineManualTest` (no setup)

## 🚀 Running Tests

### With Testcontainers

```bash
# Ensure Docker is running
docker ps

# Run the test
mvn test -Dtest=SmbStorageEngineTest
```

### With Docker Compose

```bash
# Terminal 1: Start SMB server
docker-compose -f docker-compose.smb-test.yml up

# Terminal 2: Run tests (use manual test or modify automated test)
mvn test -Dtest=SmbStorageEngineManualTest

# Terminal 1: Stop when done (Ctrl+C)
docker-compose -f docker-compose.smb-test.yml down
```

## 📊 Test Coverage

The test suite covers:

- ✅ Connection establishment
- ✅ Directory listing (`list()`, `listDetails()`)
- ✅ File upload (`copyToStorage()`)
- ✅ File download (`copyToLocal()`)
- ✅ Directory sync (`syncLocalToStorage()`, `syncStorageToLocal()`)
- ✅ File deletion (`deleteFromStorage()`)
- ✅ Folder deletion (`deleteFolderFromStorage()`)
- ✅ Read to memory (`readBlobToMemory()`)
- ✅ Error handling (invalid paths, permissions, etc.)

## 🐛 Common Issues

### "No tests found"

Make sure test files are in the correct location:
```
Semoss/test/prerna/engine/impl/storage/SmbStorageEngineTest.java
```

### "Container not starting"

```bash
# Check Docker is running
docker ps

# Check logs
docker-compose -f docker-compose.smb-test.yml logs

# Try pulling image manually
docker pull dperson/samba:latest
```

### "Permission denied"

The test creates files in the container. Make sure:
```bash
# Check mount directory exists and is writable
ls -la test-data/smb-mount/

# If on Linux/Mac, fix permissions
chmod 777 test-data/smb-mount/
```

## 🔗 See Also

- [SMB Testing README](./SMB-TESTING-README.md) - Full testing guide
- [Docker Compose Config](../docker-compose.smb-test.yml) - SMB server setup
- [Test Config](./smb-test-config.properties) - Connection properties
