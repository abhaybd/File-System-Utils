package com.coolioasjulio.filesystemutils;

import com.coolioasjulio.filesystemutils.filewrappers.FSFile;
import com.coolioasjulio.filesystemutils.filewrappers.IOFSFile;
import com.coolioasjulio.filesystemutils.filewrappers.SmbFSFile;
import com.google.gson.Gson;
import jcifs.smb.NtlmPasswordAuthentication;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DirectoryChecksum {
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    public static void main(String[] args) {
        try(Scanner in = new Scanner(new File("login.auth"))) {
            String user = in.nextLine();
            NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(user);
            String path = "smb://THEHUBNAS/The HubFiles/Documents/Collected User Files/Public/";

            DirectoryChecksum directoryChecksum = new DirectoryChecksum(new SmbFSFile(path, auth), HashAlgorithm.MD5);
            System.out.println("Starting...");

            long start = System.currentTimeMillis();
            directoryChecksum.writeChecksums("checksums.txt");
            long elapsedTime = System.currentTimeMillis() - start;
            System.out.printf("Finished after %d milliseconds!\n", elapsedTime);
        } catch (FileNotFoundException | MalformedURLException e) {
            e.printStackTrace();
        }
    }

    private FSFile file;
    private HashAlgorithm algorithm;
    private LinkedBlockingQueue<Checksum> computedChecksums;
    private int bufferSize;

    public DirectoryChecksum(String path, HashAlgorithm algorithm) {
        this(new IOFSFile(path), algorithm);
    }

    public DirectoryChecksum(FSFile file, HashAlgorithm algorithm) {
        if(!file.isDirectory()) throw new IllegalArgumentException(file.getPath() + " is not a valid directory!");

        this.file = file;
        this.algorithm = algorithm;
        computedChecksums = new LinkedBlockingQueue<>();
    }

    public Set<Checksum> getChecksums() {
        return getChecksums(DEFAULT_BUFFER_SIZE);
    }

    public Set<Checksum> getChecksums(int bufferSize) {
        this.bufferSize = bufferSize;
        computedChecksums.clear();

        FSFile[] files = file.listFiles();
        if(files != null) {
            ForkJoinPool.commonPool().invoke(new RecursiveChecksumAction(file, Arrays.asList(files)));
        }

        HashSet<Checksum> checksums = new HashSet<>();
        computedChecksums.drainTo(checksums);
        return checksums;
    }

    public void writeChecksums(String path) {
        writeChecksums(path, DEFAULT_BUFFER_SIZE);
    }

    public void writeChecksums(String path, int bufferSize) {
        this.bufferSize = bufferSize;
        computedChecksums.clear();

        FSFile[] files = file.listFiles();
        RecursiveAction action;
        if(files != null) {
            action = new RecursiveChecksumAction(file, Arrays.asList(files));
            ForkJoinPool.commonPool().submit(action);
        } else {
            throw new IllegalStateException("Invalid file!");
        }

        try (PrintStream out = new PrintStream(new FileOutputStream(path))){
            Gson gson = new Gson();
            while(!action.isDone()) {
                Checksum checksum = computedChecksums.poll(100, TimeUnit.MILLISECONDS);
                if(checksum != null) {
                    out.println(gson.toJson(checksum));
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private class RecursiveChecksumAction extends RecursiveAction {
        private static final long WORK_THRESHOLD = 1; // 1 file
        private static final int BRANCH_FACTOR = 2; // How many groups to create each time

        private FSFile root;
        private List<FSFile> workLoad;

        private RecursiveChecksumAction(FSFile root, List<FSFile> workLoad) {
            this.root = root;
            this.workLoad = workLoad;
        }

        @Override
        protected void compute() {
            List<FSFile> directories = workLoad.stream().filter(FSFile::isDirectory).collect(Collectors.toList());
            List<FSFile> files = workLoad.stream().filter(FSFile::isFile).collect(Collectors.toList());
            int numFiles = files.size();

            List<RecursiveChecksumAction> subtasks = new ArrayList<>();

            if(numFiles > 0) {
                if(numFiles <= WORK_THRESHOLD) {
                    // One file left, or the files in this task sum to less than the threshold file size
                    work(files);
                } else {
                    // Break into groups
                    for(int i = 0; i < BRANCH_FACTOR; i++) {
                        int start = i * numFiles/BRANCH_FACTOR;
                        int end = i == BRANCH_FACTOR - 1 ? numFiles : start + numFiles/BRANCH_FACTOR;
                        subtasks.add(new RecursiveChecksumAction(root, files.subList(start, end)));
                    }
                }
            }

            for(FSFile dir : directories) {
                FSFile[] subFiles = dir.listFiles();
                if(subFiles != null) {
                    subtasks.add(new RecursiveChecksumAction(root, new ArrayList<>(Arrays.asList(subFiles))));
                }
            }

            invokeAll(subtasks);
        }

        private void work(List<FSFile> files) {
            try {
                for(FSFile f : files) {
                    String path = f.getPath().replace(root.getPath(), "").replaceAll("^[\\\\/]+","");
                    long fileSize = f.length();
                    byte[] fileChecksum = new FileChecksum(f, algorithm).getChecksum(bufferSize);
                    Checksum checksum = new Checksum(path, fileSize, fileChecksum);
                    computedChecksums.put(checksum);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
