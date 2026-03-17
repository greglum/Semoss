# SMB Storage Engine Testing Guide

This guide covers different approaches to test the `SmbStorageEngine` without requiring a production network share.

## 🐳 Option 1: Docker Samba Container (Recommended)

**Best for:** Local development, CI/CD pipelines, integration testing

### Quick Start

```bash
# Start the SMB test server
docker-compose -f docker-compose.smb-test.yml up -d

# Check it's running
docker-compose -f docker-compose.smb-test.yml ps

# View logs
docker-compose -f docker-compose.smb-test.yml logs -f

# Stop when done
docker-compose -f docker-compose.smb-test.yml down
```

### Connection Details

- **Host:** `localhost`
- **Port:** `445`
- **Share Name:** `testshare`
- **Username:** `testuser`
- **Password:** `testpass`
- **Domain:** `WORKGROUP` (or leave empty)

### Test Data Location

Files uploaded to the SMB server will be stored in:
```
./test-data/smb-mount/
```

You can manually add/remove files here to test different scenarios.

---

## 🧪 Option 2: Testcontainers (Automated Integration Tests)

**Best for:** Automated integration testing in Java

### Requirements

1. Docker must be running
2. Add Testcontainers to `pom.xml` (already configured)

### Run Tests

```bash
mvn test -Dtest=SmbStorageEngineTest
```

The test will:
1. Automatically start a Samba container
2. Run all test cases
3. Shut down the container when complete

### Features

- ✅ No manual setup required
- ✅ Isolated test environment
- ✅ Runs in CI/CD pipelines
- ✅ Automatic cleanup

---

## 🖥️ Option 3: Windows Local Share

**Best for:** Windows developers without Docker

### Setup

1. **Enable SMB in Windows Features:**
   - Open "Turn Windows features on or off"
   - Enable "SMB 1.0/CIFS File Sharing Support" (if needed)
   - SMB 2.0/3.0 is usually already enabled

2. **Create a Test Folder:**
   ```
   C:\SMBTest
   ```

3. **Share the Folder:**
   - Right-click the folder → Properties → Sharing
   - Click "Advanced Sharing"
   - Check "Share this folder"
   - Share name: `SMBTest`
   - Set permissions (Everyone: Read/Write for testing)

4. **Update Test Configuration:**
   ```properties
   SMB_HOST=127.0.0.1
   SMB_PORT=445
   SMB_USER=YourWindowsUsername
   SMB_PASS=YourWindowsPassword
   SMB_DOMAIN=
   SMB_SHARE_NAME=SMBTest
   ```

5. **Run Manual Test:**
   ```bash
   java -cp ... prerna.engine.impl.storage.SmbStorageEngineManualTest
   ```

### Notes

- Windows 11 may block SMB 1.0 by default
- Use Windows credentials (your login username/password)
- Port 445 must not be blocked by firewall

---

## 🌐 Option 4: Existing Network Share

**Best for:** Testing against real infrastructure

### Configuration

Update `test-data/smb-test-config.properties`:

```properties
SMB_HOST=your-smb-server.domain.com
SMB_PORT=445
SMB_USER=your-username
SMB_PASS=your-password
SMB_DOMAIN=YOUR-DOMAIN
SMB_SHARE_NAME=your-share-name
SMB_TIMEOUT=60
```

### Security Warning

⚠️ Never commit real credentials to version control!
- Use environment variables
- Use a `.env` file (add to `.gitignore`)
- Use a credential manager

---

## 📝 Manual Testing

Run the manual test class to verify everything works:

```java
// Run in your IDE or via command line
prerna.engine.impl.storage.SmbStorageEngineManualTest
```

The test will:
1. Connect to the SMB server
2. List files
3. Upload a test file
4. Download it back
5. Read file to memory
6. Delete the file
7. Verify deletion

---

## 🐛 Troubleshooting

### Connection Refused

```
Caused by: java.net.ConnectException: Connection refused
```

**Solutions:**
- Check if Docker container is running: `docker ps`
- Verify port 445 is mapped: `docker port semoss-smb-test`
- Check firewall isn't blocking port 445
- On Windows, check SMB service is running

### Authentication Failed

```
SMBApiException: STATUS_LOGON_FAILURE
```

**Solutions:**
- Verify username/password are correct
- Check domain name (use `WORKGROUP` for Docker container)
- For Windows shares, use full username: `DOMAIN\username`

### Share Not Found

```
SMBApiException: STATUS_BAD_NETWORK_NAME
```

**Solutions:**
- Verify share name (not the path)
- For Docker: use `testshare`
- For Windows: check share name in folder properties
- List shares: `smbclient -L //localhost -U testuser` (requires smbclient)

### Permission Denied

```
SMBApiException: STATUS_ACCESS_DENIED
```

**Solutions:**
- Check user has read/write permissions on the share
- Verify folder permissions in the container/Windows
- For Docker, files should be writable in `./test-data/smb-mount/`

### Port Already in Use

```
Error: Ports are not available: exposing port TCP 0.0.0.0:445
```

**Solutions:**
- Another service is using port 445 (usually Windows SMB)
- Change port in `docker-compose.smb-test.yml`:
  ```yaml
  ports:
    - "44445:445"  # Map to different host port
  ```
- Update test config to use new port: `SMB_PORT=44445`

---

## 🔄 CI/CD Integration

### GitHub Actions Example

```yaml
name: SMB Storage Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest

    services:
      samba:
        image: dperson/samba:latest
        ports:
          - 445:445
        env:
          USER: testuser;testpass
          SHARE: testshare;/mount;yes;no;no;testuser;testuser
          WORKGROUP: WORKGROUP

    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '11'
          distribution: 'temurin'

      - name: Run SMB Tests
        run: mvn test -Dtest=SmbStorageEngineTest
```

---

## 📚 Additional Resources

- [SMBJ Documentation](https://github.com/hierynomus/smbj)
- [Docker Samba Image](https://hub.docker.com/r/dperson/samba)
- [Testcontainers Documentation](https://www.testcontainers.org/)
- [SMB Protocol Info](https://en.wikipedia.org/wiki/Server_Message_Block)

---

## 🎯 Quick Reference

| Scenario | Setup Time | Realism | Best For |
|----------|-----------|---------|----------|
| Docker Samba | 1 min | High | Most developers |
| Testcontainers | 0 min* | High | Automated tests |
| Windows Share | 5 min | Medium | Windows-only devs |
| Real Network | 0 min | Highest | Integration testing |

\* Requires Docker to be running
