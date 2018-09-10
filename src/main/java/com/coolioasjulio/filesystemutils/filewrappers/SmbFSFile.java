package com.coolioasjulio.filesystemutils.filewrappers;

/**
 * This is a wrapper for jcifs.smb.SmbFile that standardizes it into FSFile.
 */

import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.Arrays;

public class SmbFSFile implements FSFile{
    private SmbFile file;

    public SmbFSFile(String url, NtlmPasswordAuthentication auth) throws MalformedURLException {
        file = new SmbFile(url, auth);
    }

    public SmbFSFile(SmbFile file) {
        this.file = file;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return file.getInputStream();
    }

    @Override
    public boolean isFile() {
        try {
            return file.isFile();
        } catch (SmbException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isDirectory() {
        try {
            return file.isDirectory();
        } catch (SmbException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public FSFile[] listFiles() {
        try {
            return Arrays.stream(file.listFiles()).map(SmbFSFile::new).toArray(FSFile[]::new);
        } catch (SmbException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public long length() {
        try {
            return file.length();
        } catch (SmbException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getPath() {
        return file.getPath();
    }
}
