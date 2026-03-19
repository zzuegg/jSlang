package dev.slang.bindings;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class NativeLoader {

    private static volatile SymbolLookup INSTANCE;

    private NativeLoader() {}

    public static SymbolLookup load() {
        if (INSTANCE == null) {
            synchronized (NativeLoader.class) {
                if (INSTANCE == null) {
                    INSTANCE = loadLibrary();
                }
            }
        }
        return INSTANCE;
    }

    static SymbolLookup loadLibrary() {
        String libName = platformLibraryName();
        String resourcePath = "/natives/" + platformDir() + "/" + libName;

        try (InputStream in = NativeLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                // Try java.library.path directories for the native library
                String libPath = System.getProperty("java.library.path", "");
                for (String dir : libPath.split(java.io.File.pathSeparator)) {
                    if (dir.isEmpty()) continue;
                    Path candidate = Path.of(dir, libName);
                    if (java.nio.file.Files.exists(candidate)) {
                        return SymbolLookup.libraryLookup(candidate, Arena.global());
                    }
                }
                // Fall back to system library path
                return SymbolLookup.libraryLookup(System.mapLibraryName("slang"), Arena.global());
            }
            Path tempDir = Files.createTempDirectory("slang-native");
            Path tempLib = tempDir.resolve(libName);
            Files.copy(in, tempLib, StandardCopyOption.REPLACE_EXISTING);
            tempLib.toFile().deleteOnExit();
            tempDir.toFile().deleteOnExit();
            return SymbolLookup.libraryLookup(tempLib, Arena.global());
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract Slang native library", e);
        }
    }

    public static String platformLibraryName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("linux")) return "libslang.so";
        if (os.contains("win")) return "slang.dll";
        if (os.contains("mac")) return "libslang.dylib";
        throw new UnsupportedOperationException("Unsupported OS: " + os);
    }

    static String platformDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        String osName;
        if (os.contains("linux")) osName = "linux";
        else if (os.contains("win")) osName = "windows";
        else if (os.contains("mac")) osName = "macos";
        else throw new UnsupportedOperationException("Unsupported OS: " + os);

        String archName;
        if (arch.equals("amd64") || arch.equals("x86_64")) archName = "x86_64";
        else if (arch.equals("aarch64") || arch.equals("arm64")) archName = "aarch64";
        else throw new UnsupportedOperationException("Unsupported arch: " + arch);

        return osName + "-" + archName;
    }
}
