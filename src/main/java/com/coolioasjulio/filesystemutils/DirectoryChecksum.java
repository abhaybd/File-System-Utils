package com.coolioasjulio.filesystemutils;

import com.coolioasjulio.filesystemutils.filewrappers.FSFile;
import com.coolioasjulio.filesystemutils.filewrappers.IOFSFile;
import com.coolioasjulio.filesystemutils.filewrappers.SmbFSFile;
import jcifs.smb.NtlmPasswordAuthentication;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DirectoryChecksum {
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    public static void main(String[] args) {
        try(Scanner in = new Scanner(new File("login.auth"))) {
            String user = in.nextLine();
            NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication(user);
            String path = "smb://THEHUBNAS/The HubFiles/Documents/Collected User Files/Public/";

            int numIterations = 2;
            DirectoryChecksum directoryChecksum = new DirectoryChecksum(new SmbFSFile(path, auth), HashAlgorithm.MD5);
            System.out.println("Starting...");

            benchmarkSingleThreaded(directoryChecksum, numIterations);
            benchmarkMultiThreaded(directoryChecksum, numIterations);
        } catch (FileNotFoundException | MalformedURLException e) {
            e.printStackTrace();
        }
    }

    private static void benchmarkSingleThreaded(DirectoryChecksum directoryChecksum, int numIterations) {
        double[] times = IntStream.range(0,numIterations)
                .mapToDouble(e -> calculateChecksums(directoryChecksum, new ForkJoinPool(1))).toArray();
        double averageTime = Arrays.stream(times).average().orElse(0.0);
        double standardDeviation = Math.sqrt(Arrays.stream(times).map(e -> Math.pow(averageTime-e,2)).average().orElse(0.0));
        System.out.printf("Single threaded performance on %d iterations: %.0fms +- %.3fms std. dev\n",
                numIterations, averageTime, standardDeviation);
    }

    private static void benchmarkMultiThreaded(DirectoryChecksum directoryChecksum, int numIterations) {
        ForkJoinPool pool = ForkJoinPool.commonPool();
        double[] times = IntStream.range(0,numIterations)
                .mapToDouble(e -> calculateChecksums(directoryChecksum, pool)).toArray();
        double averageTime = Arrays.stream(times).average().orElse(0.0);
        double standardDeviation = Math.sqrt(Arrays.stream(times).map(e -> Math.pow(averageTime-e,2)).average().orElse(0.0));
        System.out.printf("Multithreaded performance on %d iterations with %d workers: %.0fms +- %.3fms std. dev\n",
                numIterations, pool.getParallelism(), averageTime, standardDeviation);
    }

    private static long calculateChecksums(DirectoryChecksum directoryChecksum, ForkJoinPool pool) {
        long start = System.currentTimeMillis();
        Set<Checksum> checksums = directoryChecksum.getChecksums(DEFAULT_BUFFER_SIZE, pool);
        return System.currentTimeMillis() - start;
    }

    private FSFile file;
    private HashAlgorithm algorithm;
    private Set<Checksum> checksums;
    private int bufferSize;

    public DirectoryChecksum(String path, HashAlgorithm algorithm) {
        this(new IOFSFile(path), algorithm);
    }

    public DirectoryChecksum(FSFile file, HashAlgorithm algorithm) {
        if(!file.isDirectory()) throw new IllegalArgumentException(file.getPath() + " is not a valid directory!");

        this.file = file;
        this.algorithm = algorithm;
        checksums = Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    public Set<Checksum> getChecksums() {
        return getChecksums(DEFAULT_BUFFER_SIZE, ForkJoinPool.commonPool());
    }

    public Set<Checksum> getChecksums(int bufferSize, ForkJoinPool forkJoinPool) {
        this.bufferSize = bufferSize;
        FSFile[] files = file.listFiles();
        if(files != null) {
            forkJoinPool.invoke(new RecursiveChecksumAction(file, Arrays.asList(files)));
        }
        return new HashSet<>(checksums);
    }

    private class RecursiveChecksumAction extends RecursiveAction {
        private static final long WORK_THRESHOLD = 1; // 1MB
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
            List<Checksum> checksums = new LinkedList<>();
            for(FSFile f : files) {
                String path = f.getPath().replace(root.getPath(), "").replaceAll("^/+","");
                long fileSize = f.length();
                byte[] fileChecksum = new FileChecksum(f, algorithm).getChecksum(bufferSize);
                Checksum checksum = new Checksum(path, fileSize, fileChecksum);
                checksums.add(checksum);
            }
            DirectoryChecksum.this.checksums.addAll(checksums);
        }
    }
}
