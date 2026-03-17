package dev.slang.api;

import dev.slang.bindings.raw.IModule;
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
}
