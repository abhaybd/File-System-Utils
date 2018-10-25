package com.coolioasjulio.filesystemutils.filewrappers;

import java.io.IOException;
import java.io.InputStream;

public interface FSFile {
    InputStream getInputStream() throws IOException;

    boolean isFile();

    boolean isDirectory();

    FSFile[] listFiles();

    long length();

    String getPath();
}
