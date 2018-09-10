package com.coolioasjulio.filesystemutils.filewrappers;

/**
 * This is a wrapper for the java.io.File class that standardizes it into the FLFile architecture.
 * This will allow it to be used in conjunction to jcifs.smb.SmbFile.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class IOFSFile implements FSFile {
    private File file;

    public IOFSFile(File file) {
        this.file = file;
    }

    public IOFSFile(String path) {
        this(new File(path));
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new FileInputStream(file);
    }

    @Override
    public boolean isFile() {
        return file.isFile();
    }

    @Override
    public boolean isDirectory() {
        return file.isDirectory();
    }

    @Override
    public FSFile[] listFiles() {
        File[] files = file.listFiles();
        if(files != null) {
            return Arrays.stream(files).map(IOFSFile::new).toArray(IOFSFile[]::new);
        }
        return null;
    }

    @Override
    public long length() {
        return file.length();
    }

    @Override
    public String getPath() {
        return file.getPath();
    }
}
