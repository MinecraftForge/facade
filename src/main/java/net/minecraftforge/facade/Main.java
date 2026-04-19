/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.facade;

import joptsimple.OptionParser;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class Main {
    private static final Checksum CRC32 = new CRC32();

    public static void main(String[] args) throws Exception {
        var parser = new OptionParser();
        var inputO = parser.accepts("input", "Input file")
                .withRequiredArg().ofType(File.class).required();
        var outputO = parser.accepts("output", "Output file")
                .withRequiredArg().ofType(File.class).required();
        var configO = parser.accepts("config", "Configuration File")
                .withRequiredArg().ofType(File.class).required();
        var storeO = parser.accepts("store", "Uses STORED method, disabling compression");
        var slimO = parser.accepts("slim", "Only output modified classes");
        var helpO = parser.accepts("help")
                .forHelp();

        var options = parser.parse(args);

        if (options.has(helpO)) {
            parser.printHelpOn(System.out);
            return;
        }

        var input = options.valueOf(inputO);
        var output = options.valueOf(outputO);
        var store = options.has(storeO);
        var slim = options.has(slimO);

        System.out.println("Input:  " + (input == null ? "null" : input.getAbsolutePath()));
        System.out.println("Output: " + (output == null ? "null" : output.getAbsolutePath()));
        System.out.println("Store:  " + store);
        System.out.println("Slim:   " + slim);

        var config = new Config();
        for (var configFile : options.valuesOf(configO)) {
            System.out.println("Config: " + configFile.getAbsolutePath());
            config.load(configFile.toPath());
        }

        if (!input.exists())
            throw new IllegalArgumentException("Input file does not exist: " + input);

        var parent = output.getParentFile();
        if (parent != null && !parent.exists())
            Files.createDirectories(parent.toPath());

        if (output.exists())
            output.delete();

        try (
            var zipIn = new ZipFile(input);
            var zipOut = new ZipOutputStream(new FileOutputStream(output));
        ) {
            if (store)
                zipOut.setMethod(ZipOutputStream.STORED); // Different compression libraries cause different final data. So just store

            var seen = new HashSet<String>();

            for (var itr = zipIn.entries().asIterator(); itr.hasNext(); ) {
                var entry = itr.next();
                var name = entry.getName();
                var isClass = name.endsWith(".class");
                if (entry.isDirectory())
                    continue;

                // We only want class files
                if (slim && !isClass)
                    continue;

                var data = zipIn.getInputStream(entry).readAllBytes();
                var transformed = slim ? null : data;

                if (isClass) {
                    var cls = name.substring(0, name.length() - 6);
                    var entries = config.get(cls);
                    if (entries != null) {
                        System.out.println("Processing " + cls);
                        var classTransform = ClassTransform.ofStateful(() -> new Transformer(entries));
                        var classFile = ClassFile.of();
                        transformed = classFile.transformClass(classFile.parse(data), classTransform);
                    }
                }

                if (transformed != null)
                    write(zipOut, seen, store, entry, transformed);
            }
        }
    }

    private static void write(ZipOutputStream out, Set<String> seen, boolean store, ZipEntry entry, byte[] data) throws IOException {
        var idx = entry.getName().lastIndexOf('/');
        if (idx != -1)
            writeDirectory(out, seen, entry.getTime(), entry.getName().substring(0, idx));

        var next = new ZipEntry(entry.getName());
        next.setTime(entry.getTime());

        if (store) {
            next.setSize(data.length);
            next.setCompressedSize(data.length);
            next.setCrc(crc32(data));
        }

        out.putNextEntry(next);
        out.write(data);
        out.closeEntry();
    }

    private static void writeDirectory(ZipOutputStream out, Set<String> seen, long time, String directory) throws IOException {
        if (seen.contains(directory))
            return;

        var idx = directory.lastIndexOf('/');
        if (idx != -1)
            writeDirectory(out, seen, time, directory.substring(0, idx));
        seen.add(directory);

        var next = new ZipEntry(directory + '/');
        next.setTime(time);
        out.putNextEntry(next);
        out.closeEntry();
    }

    private static long crc32(byte[] data) {
        CRC32.reset();
        CRC32.update(data);
        return CRC32.getValue();
    }
}
