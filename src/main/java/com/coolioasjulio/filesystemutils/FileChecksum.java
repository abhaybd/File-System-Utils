package com.coolioasjulio.filesystemutils;

import com.coolioasjulio.filesystemutils.filewrappers.FSFile;
import com.coolioasjulio.filesystemutils.filewrappers.IOFSFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileChecksum {
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private FSFile file;
    private MessageDigest digest;

    public FileChecksum(String path, HashAlgorithm algorithm) {
        this(new IOFSFile(path), algorithm);
    }

    public FileChecksum(FSFile file, HashAlgorithm algorithm) {
        this.file = file;

        try {
            digest = MessageDigest.getInstance(algorithm.getName());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(
                    String.format("Fatal error! Something is screwed up and %s is not a valid name!",
                            algorithm.getName()));
        }
    }

    public byte[] getChecksum() {
        return getChecksum(DEFAULT_BUFFER_SIZE);
    }

    public byte[] getChecksum(int bufferSize) {
        if(!file.isFile()) {
            throw new IllegalStateException("Invalid file: " + file.getPath());
        }

        digest.reset();

        byte[] arr = new byte[bufferSize];
        int bytesRead;

        try {
            InputStream in = file.getInputStream();

            while((bytesRead = in.read(arr)) != -1) {
                digest.update(arr, 0, bytesRead);
            }

            return digest.digest();
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
}
