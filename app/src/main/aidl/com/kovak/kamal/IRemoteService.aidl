// IRemoteService.aidl
package com.kovak.kamal;

interface IRemoteService {
    // List files in any path — returns JSON array string
    String listFiles(String path);

    // Read file bytes
    byte[] readFile(String path);

    // Write bytes to a file path
    boolean writeFile(String path, in byte[] data);

    // Delete file or directory
    boolean deleteFile(String path);

    // Copy src to dest
    boolean copyFile(String src, String dest);

    // Move (rename) src to dest
    boolean moveFile(String src, String dest);

    // Create a new folder
    boolean createDirectory(String path);

    // Get single file info — returns JSON object string
    String getFileInfo(String path);

    // Check if path exists
    boolean exists(String path);

    // Get free space on storage (bytes)
    long getFreeSpace(String path);

    // Get total space on storage (bytes)
    long getTotalSpace(String path);
}
