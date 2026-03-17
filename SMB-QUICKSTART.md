# SMB Storage Engine - Quick Start Guide

Get up and running with SMB storage testing in under 5 minutes! 🚀

## 🎯 TL;DR - Fastest Way to Test

```bash
# 1. Start test server
./test-smb.sh start        # Linux/Mac
test-smb.bat start          # Windows

# 2. Run test
./test-smb.sh test         # Linux/Mac
test-smb.bat test           # Windows

# 3. Stop server
./test-smb.sh stop         # Linux/Mac
test-smb.bat stop           # Windows
```

## 📋 What You Get

The `SmbStorageEngine` implementation includes:

✅ Full SMB/CIFS network share support using modern `smbj` library
✅ SMB2 and SMB3 protocol support (not just old SMB1)
✅ All IStorageEngine methods implemented
✅ Comprehensive test suite
✅ Easy local testing with Docker

## 🚀 Quick Start Options

### Option A: Automated Testing (Recommended)

**Best for most developers**

```bash
# One command - does everything
./test-smb.sh start && ./test-smb.sh test
```

This will:
1. Start a Docker Samba container
2. Run comprehensive tests
3. Show results

### Option B: Integration Testing

**For CI/CD and automated builds**

```bash
# Testcontainers - fully automated
mvn test -Dtest=SmbStorageEngineTest
```

Requires: Docker running, Testcontainers in pom.xml (see setup below)

### Option C: Manual Testing

**For debugging and exploration**

```java
// Run in your IDE
prerna.engine.impl.storage.SmbStorageEngineManualTest
```

## 📦 Configuration

### Test Configuration (Docker)

```properties
SMB_HOST=localhost
SMB_PORT=445
SMB_USER=testuser
SMB_PASS=testpass
SMB_DOMAIN=WORKGROUP
SMB_SHARE_NAME=testshare
SMB_TIMEOUT=60
```

### Production Configuration

```properties
SMB_HOST=your-smb-server.company.com
SMB_PORT=445
SMB_USER=service-account
SMB_PASS=***********
SMB_DOMAIN=COMPANY
SMB_SHARE_NAME=shared-data
SMB_TIMEOUT=60
```

## 🛠️ Setup Steps

### 1. Add Dependency (Already Done)

The `smbj` dependency is already in `pom.xml`:

```xml
<dependency>
    <groupId>com.hierynomus</groupId>
    <artifactId>smbj</artifactId>
    <version>0.13.0</version>
</dependency>
```

### 2. Install Docker (If Not Already)

- **Windows:** [Docker Desktop](https://www.docker.com/products/docker-desktop)
- **Mac:** [Docker Desktop](https://www.docker.com/products/docker-desktop)
- **Linux:** `sudo apt install docker.io` or equivalent

### 3. Start Test Environment

```bash
# Linux/Mac
chmod +x test-smb.sh
./test-smb.sh start

# Windows
test-smb.bat start
```

### 4. Verify It's Working

```bash
# Check status
./test-smb.sh status    # Linux/Mac
test-smb.bat status      # Windows

# Should show: "[OK] SMB server is running"
```

## 🧪 Available Commands

### Linux/Mac (`test-smb.sh`)

```bash
./test-smb.sh start      # Start test server
./test-smb.sh stop       # Stop test server
./test-smb.sh restart    # Restart server
./test-smb.sh status     # Check if running
./test-smb.sh logs       # View logs
./test-smb.sh test       # Run manual test
./test-smb.sh junit      # Run JUnit tests
./test-smb.sh files      # List files in share
./test-smb.sh clean      # Delete all files
./test-smb.sh shell      # Shell access
```

### Windows (`test-smb.bat`)

Same commands as above, just use `.bat` instead of `.sh`

## 📁 Project Structure

```
Semoss/
├── src/prerna/engine/impl/storage/
│   └── SmbStorageEngine.java           # Main implementation
├── test/prerna/engine/impl/storage/
│   ├── SmbStorageEngineTest.java       # Automated tests
│   └── SmbStorageEngineManualTest.java # Manual test class
├── test-data/
│   ├── smb-mount/                      # Test files go here
│   ├── smb-test-config.properties      # Test config
│   ├── SMB-TESTING-README.md           # Full testing guide
│   └── TESTCONTAINERS-SETUP.md         # Testcontainers info
├── docker-compose.smb-test.yml         # Docker setup
├── test-smb.sh                         # Control script (Linux/Mac)
├── test-smb.bat                        # Control script (Windows)
└── SMB-QUICKSTART.md                   # This file
```

## 🔍 Testing Workflow

### Development Cycle

```bash
# 1. Start server (once)
./test-smb.sh start

# 2. Make code changes
# ... edit SmbStorageEngine.java ...

# 3. Run tests
./test-smb.sh test

# 4. Check files if needed
./test-smb.sh files

# 5. Clean up between runs
./test-smb.sh clean

# 6. Stop when done
./test-smb.sh stop
```

### Quick Verification

```bash
# One-liner to test everything
./test-smb.sh start && ./test-smb.sh test && ./test-smb.sh stop
```

## 🎓 Example Usage

### In Your Application

```java
import prerna.engine.impl.storage.SmbStorageEngine;
import java.util.Properties;

// Create engine
SmbStorageEngine storage = new SmbStorageEngine();

// Configure
Properties props = new Properties();
props.setProperty(SmbStorageEngine.SMB_HOST, "file-server.company.com");
props.setProperty(SmbStorageEngine.SMB_USER, "serviceaccount");
props.setProperty(SmbStorageEngine.SMB_PASS, "password");
props.setProperty(SmbStorageEngine.SMB_SHARE_NAME, "data-share");

// Initialize
storage.open(props);

// Use it
List<String> files = storage.list("/reports");
storage.copyToStorage("/local/file.pdf", "/reports/2024", null);
byte[] data = storage.readBlobToMemory("/reports/2024/file.pdf");

// Cleanup
storage.close();
```

## 🐛 Troubleshooting

### Docker container won't start

```bash
# Check Docker is running
docker ps

# Pull image manually
docker pull dperson/samba:latest

# Check logs
docker-compose -f docker-compose.smb-test.yml logs
```

### Port 445 already in use

Windows SMB service uses port 445. Either:

**Option 1:** Use different port
```yaml
# Edit docker-compose.smb-test.yml
ports:
  - "44445:445"  # Use port 44445 instead
```

**Option 2:** Stop Windows SMB (not recommended)
```powershell
# As Administrator
net stop LanmanServer
```

### Tests fail to connect

```bash
# Verify server is running
./test-smb.sh status

# Check connectivity
telnet localhost 445

# Restart server
./test-smb.sh restart
```

### Permission denied errors

```bash
# On Linux/Mac, fix mount permissions
chmod 777 test-data/smb-mount/

# Clean and restart
./test-smb.sh clean
./test-smb.sh restart
```

## 📚 Learn More

- **Full Testing Guide:** `test-data/SMB-TESTING-README.md`
- **Testcontainers Setup:** `test-data/TESTCONTAINERS-SETUP.md`
- **SMBJ Library:** https://github.com/hierynomus/smbj
- **Docker Samba:** https://hub.docker.com/r/dperson/samba

## ✅ Checklist

Before committing your changes:

- [ ] Run `./test-smb.sh test` - all tests pass
- [ ] Code compiles: `mvn compile`
- [ ] No hardcoded credentials
- [ ] Updated documentation if needed
- [ ] Cleaned up test files: `./test-smb.sh clean`
- [ ] Stopped test server: `./test-smb.sh stop`

## 🎉 You're Ready!

The SMB storage engine is fully implemented and ready to use. Whether you're:
- Testing locally with Docker
- Running in production against real SMB shares
- Integrating with CI/CD pipelines

Everything is set up and ready to go! 🚀
