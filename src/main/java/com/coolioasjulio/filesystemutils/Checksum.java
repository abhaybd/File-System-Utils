package com.coolioasjulio.filesystemutils;

import com.google.gson.Gson;

import java.util.Objects;

public class Checksum {
    private String relativePath;
    private long fileSize;
    private String checkSum;

    public Checksum(String relativePath, long fileSize, byte[] checkSum) {
        this.relativePath = relativePath;
        this.fileSize = fileSize;

        StringBuilder sb = new StringBuilder();
        for (byte b : checkSum) {
            sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        }
        this.checkSum = sb.toString();
    }

    public String getRelativePath() {
        return relativePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getCheckSum() {
        return checkSum;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Checksum)) return false;
        Checksum c = ((Checksum) o);
        return fileSize == c.fileSize && checkSum.equals(c.checkSum) && relativePath.equals(c.relativePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(relativePath, fileSize, checkSum);
    }
}
