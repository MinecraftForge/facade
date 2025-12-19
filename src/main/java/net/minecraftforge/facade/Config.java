/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.facade;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Config {
    private Map<String, List<Entry>> entries = new HashMap<>();

    void load(Path file) throws IOException {
        if (!Files.exists(file))
            throw new IllegalArgumentException("Missing config file: " + file.toAbsolutePath());

        var lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (var line : lines) {
            var entry = Entry.parse(line);
            if (entry != null)
                entries.computeIfAbsent(entry.target, _ -> new ArrayList<>()).add(entry);
        }
    }

    List<Entry> get(String cls) {
        return this.entries.get(cls);
    }

    record Entry(String target, String value) {

        private static Entry parse(final String input) {
            var idx = input.indexOf('#');
            var line = (idx == -1 ? input : input.substring(0, idx)).trim();
            if (line.isEmpty())
                return null;

            idx = line.indexOf(' ');
            if (idx == -1)
                throw new IllegalArgumentException("Invalid Config Line: " + input);

            var cls   = line.substring(0, idx);
            var value = line.substring(idx + 1);

            return new Entry(cls, value);
        }
    }
}
