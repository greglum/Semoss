/*******************************************************************************
 * Copyright 2015 Defense Health Agency (DHA)
 *
 * If your use of this software does not include any GPLv2 components:
 * 	Licensed under the Apache License, Version 2.0 (the "License");
 * 	you may not use this file except in compliance with the License.
 * 	You may obtain a copy of the License at
 *
 * 	  http://www.apache.org/licenses/LICENSE-2.0
 *
 * 	Unless required by applicable law or agreed to in writing, software
 * 	distributed under the License is distributed on an "AS IS" BASIS,
 * 	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * 	See the License for the specific language governing permissions and
 * 	limitations under the License.
 * ----------------------------------------------------------------------------
 * If your use of this software includes any GPLv2 components:
 * 	This program is free software; you can redistribute it and/or
 * 	modify it under the terms of the GNU General Public License
 * 	as published by the Free Software Foundation; either version 2
 * 	of the License, or (at your option) any later version.
 *
 * 	This program is distributed in the hope that it will be useful,
 * 	but WITHOUT ANY WARRANTY; without even the implied warranty of
 * 	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * 	GNU General Public License for more details.
 *******************************************************************************/
package prerna.engine.impl.storage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.protocol.commons.EnumWithValue;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.Directory;
import com.hierynomus.smbj.share.File;

import prerna.engine.api.StorageTypeEnum;
import prerna.util.Utility;

public class SmbStorageEngine extends AbstractStorageEngine {

    private static final Logger classLogger = LogManager.getLogger(SmbStorageEngine.class);

    public static final String SMB_HOST = "SMB_HOST";
    public static final String SMB_USER = "SMB_USER";
    public static final String SMB_PORT = "SMB_PORT";
    public static final String SMB_PASS = "SMB_PASS";
    public static final String SMB_DOMAIN = "SMB_DOMAIN";
    public static final String SMB_SHARE_NAME = "SMB_SHARE_NAME";
    public static final String SMB_TIMEOUT = "SMB_TIMEOUT";

    private static final int BUFFER_SIZE = 8192;

    private transient String host = null;
    private transient String user = null;
    private transient int port = 445;
    private transient String pass = null;
    private transient String domain = null;
    private transient String shareName = null;
    private transient int timeout = 60;

    private SMBClient client = null;

    @Override
    public void open(Properties smssProp) throws Exception {
        super.open(smssProp);

        this.host = smssProp.getProperty(SMB_HOST);
        if (this.host == null || this.host.trim().isEmpty()) {
            throw new IllegalArgumentException("Must provide " + SMB_HOST);
        }

        this.user = smssProp.getProperty(SMB_USER);
        if (this.user == null || this.user.trim().isEmpty()) {
            throw new IllegalArgumentException("Must provide " + SMB_USER);
        }

        this.pass = smssProp.getProperty(SMB_PASS);
        if (this.pass == null) {
            this.pass = "";
        }

        this.domain = smssProp.getProperty(SMB_DOMAIN);
        if (this.domain == null || this.domain.trim().isEmpty()) {
            this.domain = null;
        }

        this.shareName = smssProp.getProperty(SMB_SHARE_NAME);
        if (this.shareName == null || this.shareName.trim().isEmpty()) {
            throw new IllegalArgumentException("Must provide " + SMB_SHARE_NAME);
        }

        String portString = smssProp.getProperty(SMB_PORT);
        if (portString != null && !portString.trim().isEmpty()) {
            try {
                port = Integer.parseInt(portString.trim());
                if (port <= 0) {
                    port = 445;
                }
            } catch (NumberFormatException e) {
                classLogger.warn("Invalid port value '{}', using default 445", portString);
            }
        }

        String timeoutString = smssProp.getProperty(SMB_TIMEOUT);
        if (timeoutString != null && !timeoutString.trim().isEmpty()) {
            try {
                timeout = Integer.parseInt(timeoutString.trim());
                if (timeout <= 0) {
                    timeout = 60;
                }
            } catch (NumberFormatException e) {
                classLogger.warn("Invalid timeout value '{}', using default 60", timeoutString);
            }
        }

        createServiceClient();
    }

    public void createServiceClient() {
        // timeout == read, write, and transact
        SmbConfig config = SmbConfig.builder()
            .withTimeout(timeout, TimeUnit.SECONDS)
            .build();
        client = new SMBClient(config);
    }

    protected Connection makeConnection() throws IOException {
        if (client == null) {
            createServiceClient();
        }
        return client.connect(host, port);
    }

    protected DiskShare connectShare(Connection connection) throws IOException {
        AuthenticationContext ac = new AuthenticationContext(user, pass.toCharArray(), domain);
        Session session = connection.authenticate(ac);
        DiskShare share = (DiskShare) session.connectShare(shareName);
        return share;
    }

    protected String normalizeSmbPath(String path) {
        if (path == null) {
            return "";
        }
        path = Utility.normalizePath(path);
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    @Override
    public StorageTypeEnum getStorageType() {
        return StorageTypeEnum.SMB_CIFS;
    }

    @Override
    public List<String> list(String path) throws Exception {
        List<Map<String, Object>> details = listDetails(path);
        List<String> names = new ArrayList<>(details.size());
        for (Map<String, Object> item : details) {
            Object nameObj = item.get("Name");
            if (nameObj == null) {
                continue;
            }
            String name = nameObj.toString();
            boolean isDir = Boolean.TRUE.equals(item.get("IsDir"));
            names.add(isDir ? name + "/" : name);
        }
        return names;
    }

    @Override
    public List<Map<String, Object>> listDetails(String path) throws Exception {
        return listDetails(path, null);
    }

    public List<Map<String, Object>> listDetails(String path, String searchPattern) throws Exception {
        List<Map<String, Object>> fileDetailsList = new ArrayList<>();

        try (Connection connection = makeConnection()) {
            try (DiskShare share = connectShare(connection)) {
                String normalizedPath = normalizeSmbPath(path);

                if (normalizedPath.isEmpty()) {
                    normalizedPath = "";
                }

                for (FileIdBothDirectoryInformation f : share.list(normalizedPath, searchPattern)) {
                    String fileName = f.getFileName();

                    // Skip . and .. entries
                    if (".".equals(fileName) || "..".equals(fileName)) {
                        continue;
                    }

                    Map<String, Object> fileData = new HashMap<>();
                    boolean isDir = EnumWithValue.EnumUtils.isSet(f.getFileAttributes(), FileAttributes.FILE_ATTRIBUTE_DIRECTORY);

                    String fullPath = normalizedPath.isEmpty() ? fileName : normalizedPath + "/" + fileName;
                    if (!fullPath.startsWith("/")) {
                        fullPath = "/" + fullPath;
                    }

                    fileData.put("Path", fullPath);
                    fileData.put("Name", fileName);
                    fileData.put("Size", isDir ? 0L : f.getEndOfFile());
                    fileData.put("MimeType", isDir ? "inode/directory" : null);
                    fileData.put("ModTime", f.getChangeTime().toEpochSecond() != 0
                        ? Instant.ofEpochSecond(f.getChangeTime().toEpochSecond()).toString()
                        : null);
                    fileData.put("ID", f.getFileId());
                    fileData.put("IsDir", isDir);
                    fileData.put("Metadata", Collections.emptyMap());

                    fileDetailsList.add(fileData);
                }
            }
        }

        return fileDetailsList;
    }

    @Override
    public void syncLocalToStorage(String localPath, String storagePath, Map<String, Object> metadata)
            throws Exception {
        if (localPath == null || localPath.isEmpty()) {
            throw new NullPointerException("Must define the local location to sync");
        }
        if (storagePath == null || storagePath.isEmpty()) {
            throw new NullPointerException("Must define the storage location to sync to");
        }

        java.io.File localFile = new java.io.File(Utility.normalizePath(localPath));
        if (!localFile.exists()) {
            throw new IllegalArgumentException("Local path does not exist: " + localPath);
        }

        try (Connection connection = makeConnection()) {
            try (DiskShare share = connectShare(connection)) {
                String normalizedStoragePath = normalizeSmbPath(storagePath);

                if (localFile.isDirectory()) {
                    syncDirectoryToStorage(share, localFile, normalizedStoragePath);
                } else {
                    // For single file, upload to the storage path
                    uploadFile(share, localFile, normalizedStoragePath);
                }
            }
        }
    }

    private void syncDirectoryToStorage(DiskShare share, java.io.File localDir, String storagePath) throws IOException {
        // Ensure directory exists on remote
        if (!share.folderExists(storagePath)) {
            share.mkdir(storagePath);
        }

        java.io.File[] files = localDir.listFiles();
        if (files != null) {
            for (java.io.File file : files) {
                String remotePath = storagePath.isEmpty() ? file.getName() : storagePath + "\\" + file.getName();

                if (file.isDirectory()) {
                    syncDirectoryToStorage(share, file, remotePath);
                } else {
                    uploadFile(share, file, remotePath);
                }
            }
        }
    }

    private void uploadFile(DiskShare share, java.io.File localFile, String remotePath) throws IOException {
        try (File remoteFile = share.openFile(
                remotePath,
                EnumWithValue.EnumUtils.toEnumSet(AccessMask.GENERIC_WRITE, AccessMask.DELETE),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                null)) {

            try (OutputStream os = remoteFile.getOutputStream();
                 FileInputStream fis = new FileInputStream(localFile)) {

                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                os.flush();
            }
        }
    }

    @Override
    public void syncStorageToLocal(String storagePath, String localPath) throws Exception {
        if (storagePath == null || storagePath.isEmpty()) {
            throw new NullPointerException("Must define the storage location to sync from");
        }
        if (localPath == null || localPath.isEmpty()) {
            throw new NullPointerException("Must define the local location to sync to");
        }

        try (Connection connection = makeConnection()) {
            try (DiskShare share = connectShare(connection)) {
                String normalizedStoragePath = normalizeSmbPath(storagePath);
                java.io.File localFile = new java.io.File(Utility.normalizePath(localPath));

                if (share.folderExists(normalizedStoragePath)) {
                    syncDirectoryToLocal(share, normalizedStoragePath, localFile);
                } else if (share.fileExists(normalizedStoragePath)) {
                    downloadFile(share, normalizedStoragePath, localFile);
                } else {
                    throw new IllegalArgumentException("Storage path does not exist: " + storagePath);
                }
            }
        }
    }

    private void syncDirectoryToLocal(DiskShare share, String remotePath, java.io.File localDir) throws IOException {
        if (!localDir.exists()) {
            localDir.mkdirs();
        }

        for (FileIdBothDirectoryInformation fileInfo : share.list(remotePath)) {
            String fileName = fileInfo.getFileName();

            if (".".equals(fileName) || "..".equals(fileName)) {
                continue;
            }

            String remoteFilePath = remotePath.isEmpty() ? fileName : remotePath + "\\" + fileName;
            java.io.File localFile = new java.io.File(localDir, fileName);

            boolean isDir = EnumWithValue.EnumUtils.isSet(fileInfo.getFileAttributes(), FileAttributes.FILE_ATTRIBUTE_DIRECTORY);

            if (isDir) {
                syncDirectoryToLocal(share, remoteFilePath, localFile);
            } else {
                downloadFile(share, remoteFilePath, localFile);
            }
        }
    }

    private void downloadFile(DiskShare share, String remotePath, java.io.File localFile) throws IOException {
        // Ensure parent directory exists
        java.io.File parentDir = localFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        try (File remoteFile = share.openFile(
                remotePath,
                EnumWithValue.EnumUtils.toEnumSet(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null)) {

            try (InputStream is = remoteFile.getInputStream();
                 FileOutputStream fos = new FileOutputStream(localFile)) {

                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
                fos.flush();
            }
        }
    }

    @Override
    public void copyToStorage(String localFilePath, String storageFolderPath, Map<String, Object> metadata)
            throws Exception {
        if (localFilePath == null || localFilePath.isEmpty()) {
            throw new NullPointerException("Must define the local location of the file to push");
        }
        if (storageFolderPath == null || storageFolderPath.isEmpty()) {
            throw new NullPointerException("Must define the location of the storage folder to move to");
        }

        java.io.File localFile = new java.io.File(Utility.normalizePath(localFilePath));
        if (!localFile.exists()) {
            throw new IllegalArgumentException("Local file does not exist: " + localFilePath);
        }
        if (!localFile.isFile()) {
            throw new IllegalArgumentException("Local path is not a file: " + localFilePath);
        }

        try (Connection connection = makeConnection()) {
            try (DiskShare share = connectShare(connection)) {
                String normalizedStoragePath = normalizeSmbPath(storageFolderPath);

                // Ensure parent directory exists
                if (!normalizedStoragePath.isEmpty() && !share.folderExists(normalizedStoragePath)) {
                    createDirectories(share, normalizedStoragePath);
                }

                // Append filename to the storage folder path
                String fileName = localFile.getName();
                String remoteFilePath = normalizedStoragePath.isEmpty() ? fileName : normalizedStoragePath + "\\" + fileName;

                uploadFile(share, localFile, remoteFilePath);
            }
        }
    }

    private void createDirectories(DiskShare share, String path) {
        if (path == null || path.isEmpty()) {
            return;
        }

        String[] parts = path.split("[\\\\/]");
        StringBuilder currentPath = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }

            if (currentPath.length() > 0) {
                currentPath.append("\\");
            }
            currentPath.append(part);

            String pathStr = currentPath.toString();
            if (!share.folderExists(pathStr)) {
                share.mkdir(pathStr);
            }
        }
    }

    @Override
    public void copyToLocal(String storageFilePath, String localFolderPath) throws Exception {
        if (storageFilePath == null || storageFilePath.isEmpty()) {
            throw new NullPointerException("Must define the storage location of the file to download");
        }
        if (localFolderPath == null || localFolderPath.isEmpty()) {
            throw new NullPointerException("Must define the location of the local folder to move to");
        }

        try (Connection connection = makeConnection()) {
            try (DiskShare share = connectShare(connection)) {
                String normalizedStoragePath = normalizeSmbPath(storageFilePath);

                if (!share.fileExists(normalizedStoragePath)) {
                    throw new IllegalArgumentException("Storage file does not exist: " + storageFilePath);
                }

                // Get the filename from the storage path
                String fileName = normalizedStoragePath;
                int lastSlash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
                if (lastSlash >= 0) {
                    fileName = fileName.substring(lastSlash + 1);
                }

                java.io.File localFolder = new java.io.File(Utility.normalizePath(localFolderPath));
                if (!localFolder.exists()) {
                    localFolder.mkdirs();
                }

                java.io.File localFile = new java.io.File(localFolder, fileName);
                downloadFile(share, normalizedStoragePath, localFile);
            }
        }
    }

    @Override
    public void deleteFromStorage(String storagePath) throws Exception {
        deleteFromStorage(storagePath, false);
    }

    @Override
    public void deleteFromStorage(String storagePath, boolean leaveFolderStructure) throws Exception {
        if (storagePath == null || storagePath.isEmpty()) {
            throw new NullPointerException("Must define the storage location of the file to delete");
        }

        try (Connection connection = makeConnection()) {
            try (DiskShare share = connectShare(connection)) {
                String normalizedStoragePath = normalizeSmbPath(storagePath);

                if (share.folderExists(normalizedStoragePath)) {
                    if (leaveFolderStructure) {
                        deleteFilesRecursively(share, normalizedStoragePath);
                    } else {
                        deleteFolderRecursively(share, normalizedStoragePath);
                    }
                } else if (share.fileExists(normalizedStoragePath)) {
                    share.rm(normalizedStoragePath);
                } else {
                    throw new IllegalArgumentException("Storage path does not exist: " + storagePath);
                }
            }
        }
    }

    private void deleteFilesRecursively(DiskShare share, String dirPath) throws IOException {
        for (FileIdBothDirectoryInformation fileInfo : share.list(dirPath)) {
            String fileName = fileInfo.getFileName();

            if (".".equals(fileName) || "..".equals(fileName)) {
                continue;
            }

            String filePath = dirPath.isEmpty() ? fileName : dirPath + "\\" + fileName;
            boolean isDir = EnumWithValue.EnumUtils.isSet(fileInfo.getFileAttributes(), FileAttributes.FILE_ATTRIBUTE_DIRECTORY);

            if (isDir) {
                deleteFilesRecursively(share, filePath);
            } else {
                share.rm(filePath);
            }
        }
    }

    private void deleteFolderRecursively(DiskShare share, String dirPath) throws IOException {
        for (FileIdBothDirectoryInformation fileInfo : share.list(dirPath)) {
            String fileName = fileInfo.getFileName();

            if (".".equals(fileName) || "..".equals(fileName)) {
                continue;
            }

            String filePath = dirPath.isEmpty() ? fileName : dirPath + "\\" + fileName;
            boolean isDir = EnumWithValue.EnumUtils.isSet(fileInfo.getFileAttributes(), FileAttributes.FILE_ATTRIBUTE_DIRECTORY);

            if (isDir) {
                deleteFolderRecursively(share, filePath);
            } else {
                share.rm(filePath);
            }
        }

        // Delete the directory itself
        share.rmdir(dirPath);
    }

    @Override
    public void deleteFolderFromStorage(String storageFolderPath) throws Exception {
        if (storageFolderPath == null || storageFolderPath.isEmpty()) {
            throw new NullPointerException("Must define the storage location of the folder to delete");
        }

        try (Connection connection = makeConnection()) {
            try (DiskShare share = connectShare(connection)) {
                String normalizedStoragePath = normalizeSmbPath(storageFolderPath);

                if (!share.folderExists(normalizedStoragePath)) {
                    throw new IllegalArgumentException("Storage folder does not exist: " + storageFolderPath);
                }

                deleteFolderRecursively(share, normalizedStoragePath);
            }
        }
    }

    @Override
    public byte[] readBlobToMemory(String storagePath) throws Exception {
        if (storagePath == null || storagePath.isEmpty()) {
            throw new NullPointerException("Must define the storage location of the file to read");
        }

        try (Connection connection = makeConnection()) {
            try (DiskShare share = connectShare(connection)) {
                String normalizedStoragePath = normalizeSmbPath(storagePath);

                if (!share.fileExists(normalizedStoragePath)) {
                    throw new IllegalArgumentException("Storage file does not exist: " + storagePath);
                }

                try (File remoteFile = share.openFile(
                        normalizedStoragePath,
                        EnumWithValue.EnumUtils.toEnumSet(AccessMask.GENERIC_READ),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OPEN,
                        null)) {

                    try (InputStream is = remoteFile.getInputStream();
                         ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                        byte[] buffer = new byte[BUFFER_SIZE];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            baos.write(buffer, 0, bytesRead);
                        }

                        return baos.toByteArray();
                    }
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                classLogger.error("Error closing SMB client", e);
            }
        }
    }
}
