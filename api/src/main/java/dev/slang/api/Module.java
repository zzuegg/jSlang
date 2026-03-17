package dev.slang.api;

import dev.slang.bindings.raw.IModule;
import dev.slang.bindings.raw.ISlangBlob;
import dev.slang.bindings.enums.Stage;
import java.lang.foreign.Arena;

public class Module extends ComponentType {
    private final IModule rawModule;

    public Module(IModule raw) {
        super(raw);
        this.rawModule = raw;
    }

    public EntryPoint findEntryPoint(String name) {
        try (Arena arena = Arena.ofConfined()) {
            return new EntryPoint(rawModule.findEntryPointByName(arena, name));
        }
    }

    public EntryPoint findAndCheckEntryPoint(String name, Stage stage) {
        try (Arena arena = Arena.ofConfined()) {
            return new EntryPoint(
                rawModule.findAndCheckEntryPoint(arena, name, stage.value()));
        }
    }

    public String getName() {
        return rawModule.getName();
    }

    public int getDefinedEntryPointCount() {
        return rawModule.getDefinedEntryPointCount();
    }

    public EntryPoint getDefinedEntryPoint(int index) {
        try (Arena arena = Arena.ofConfined()) {
            return new EntryPoint(rawModule.getDefinedEntryPoint(arena, index));
        }
    }

    public byte[] serialize() {
        try (Arena arena = Arena.ofConfined()) {
            ISlangBlob blob = rawModule.serialize(arena);
            try {
                return blob.toByteArray();
            } finally {
                blob.close();
            }
        }
    }

    public void writeToFile(String fileName) {
        try (Arena arena = Arena.ofConfined()) {
            rawModule.writeToFile(arena, fileName);
        }
    }

    public String getFilePath() {
        return rawModule.getFilePath();
    }

    public String getUniqueIdentity() {
        return rawModule.getUniqueIdentity();
    }

    public int getDependencyFileCount() {
        return rawModule.getDependencyFileCount();
    }

    public String getDependencyFilePath(int index) {
        return rawModule.getDependencyFilePath(index);
    }

    public String disassemble() {
        try (Arena arena = Arena.ofConfined()) {
            ISlangBlob blob = rawModule.disassemble(arena);
            try {
                return new String(blob.toByteArray());
            } finally {
                blob.close();
            }
        }
    }
}
