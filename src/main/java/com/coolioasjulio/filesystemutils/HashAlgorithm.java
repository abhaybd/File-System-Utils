package com.coolioasjulio.filesystemutils;

public enum HashAlgorithm {
    MD5("MD5"), SHA1("SHA-1");

    private String name;
    HashAlgorithm(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
