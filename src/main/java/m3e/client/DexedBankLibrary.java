package m3e.client;

import net.fabricmc.loader.api.FabricLoader;
import m3e.dexed.DexedPatch;
import m3e.dexed.DexedSysex;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Player-local DX7 bank library shared by every Synth item. */
final class DexedBankLibrary {
    private static final String[] FACTORY = {
            "rom1a", "rom1b", "rom2a", "rom2b", "rom3a", "rom3b", "rom4a", "rom4b"
    };
    private DexedBankLibrary() { }

    private static Path userBanks() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("m3e").resolve("synth-banks");
    }

    static List<Bank> banks() {
        ArrayList<Bank> result = new ArrayList<>(factoryBanks());
        Path userBanks = userBanks();
        if (!Files.isDirectory(userBanks)) return List.copyOf(result);
        try (var paths = Files.list(userBanks)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)
                            .endsWith(".syx"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(),
                            String.CASE_INSENSITIVE_ORDER))
                    .map(DexedBankLibrary::readUserBank)
                    .filter(java.util.Objects::nonNull)
                    .forEach(result::add);
        } catch (IOException ignored) {
            // The import dialog reports actionable I/O failures. A damaged
            // optional bank must not prevent the Synth itself from opening.
        }
        return List.copyOf(result);
    }

    static Bank importBank(Path source) throws IOException {
        return importBank(userBanks(), source);
    }

    static Bank importBank(Path directory, Path source) throws IOException {
        byte[] bytes = Files.readAllBytes(source);
        List<DexedPatch> voices = DexedSysex.importVoices(bytes);
        if (voices.size() != 32) {
            throw new IllegalArgumentException("Choose a complete 32-patch DX7 bank");
        }
        Files.createDirectories(directory);
        String stem = stem(source);
        Path destination = uniqueDestination(directory, stem);
        atomicWrite(destination, DexedSysex.exportCartridge(voices));
        atomicWrite(operatorMaskPath(destination), operatorMasks(voices));
        return new Bank("user:" + destination.getFileName(), friendlyName(stem),
                voices, DexedSysex.exportCartridge(voices), destination, false);
    }

    static Bank createBank(String name) throws IOException {
        return createBank(userBanks(), name);
    }

    static Bank createBank(Path directory, String name) throws IOException {
        Files.createDirectories(directory);
        String requested = name == null ? "User Bank" : name.strip();
        if (requested.isBlank()) requested = "User Bank";
        Path destination = uniqueDestination(directory, requested);
        List<DexedPatch> voices = java.util.Collections.nCopies(
                32, DexedPatch.initVoice());
        byte[] bytes = DexedSysex.exportCartridge(voices);
        atomicWrite(destination, bytes);
        atomicWrite(operatorMaskPath(destination), operatorMasks(voices));
        return new Bank("user:" + destination.getFileName(), friendlyName(requested),
                voices, bytes, destination, false);
    }

    static Bank saveVoice(Bank bank, int slot, DexedPatch patch) throws IOException {
        if (bank == null || bank.factory() || bank.path() == null) {
            throw new IllegalArgumentException("Factory banks are read-only");
        }
        byte[] bytes = DexedSysex.replaceVoice(bank.originalSysex(), slot, patch);
        atomicWrite(bank.path(), bytes);
        ArrayList<DexedPatch> voices = new ArrayList<>(bank.voices());
        voices.set(slot, patch);
        atomicWrite(operatorMaskPath(bank.path()), operatorMasks(voices));
        return new Bank(bank.id(), bank.name(), voices, bytes, bank.path(), false);
    }

    static void exportBank(Bank bank, Path destination) throws IOException {
        if (bank == null) throw new IllegalArgumentException("Choose a concrete bank to export");
        Path target = destination;
        Path targetName = target.getFileName();
        String fileName = targetName == null ? "DX7_Bank.syx" : targetName.toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".syx")) {
            target = targetName == null ? target.resolve("DX7_Bank.syx")
                    : target.resolveSibling(fileName + ".syx");
        }
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        atomicWrite(target, bank.originalSysex());
    }

    static boolean delete(Bank bank) throws IOException {
        if (bank == null || bank.factory() || bank.path() == null) return false;
        boolean deleted = Files.deleteIfExists(bank.path());
        Files.deleteIfExists(operatorMaskPath(bank.path()));
        return deleted;
    }

    private static List<Bank> factoryBanks() {
        List<Bank> result = new ArrayList<>(FACTORY.length);
        for (String name : FACTORY) {
            String resource = "/assets/m3e/dexed/banks/" + name + ".syx";
            try (InputStream input = DexedBankLibrary.class.getResourceAsStream(resource)) {
                if (input == null) throw new IOException("Missing " + resource);
                byte[] sysex = input.readAllBytes();
                String suffix = name.substring(3).toUpperCase(Locale.ROOT);
                result.add(new Bank("factory:" + name, "Factory " + suffix,
                        DexedSysex.importVoices(sysex), sysex, null, true));
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to load packaged DX7 bank " + name,
                        exception);
            }
        }
        return List.copyOf(result);
    }

    static Bank readUserBank(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            List<DexedPatch> voices = applyOperatorMasks(path,
                    DexedSysex.importVoices(bytes));
            if (voices.size() != 32) return null;
            String stem = stem(path);
            return new Bank("user:" + path.getFileName(), friendlyName(stem),
                    voices, bytes, path, false);
        } catch (IOException | IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Path uniqueDestination(Path directory, String stem) {
        String safe = stem.replaceAll("[^A-Za-z0-9._-]+", "_");
        if (safe.isBlank()) safe = "Imported_Bank";
        Path candidate = directory.resolve(safe + ".syx");
        int suffix = 2;
        while (Files.exists(candidate)) {
            candidate = directory.resolve(safe + "_" + suffix++ + ".syx");
        }
        return candidate;
    }

    private static List<DexedPatch> applyOperatorMasks(Path bank,
                                                        List<DexedPatch> voices) {
        Path sidecar = operatorMaskPath(bank);
        if (!Files.isRegularFile(sidecar)) return voices;
        try {
            byte[] masks = Files.readAllBytes(sidecar);
            if (masks.length != 32) return voices;
            ArrayList<DexedPatch> result = new ArrayList<>(voices);
            for (int slot = 0; slot < result.size(); slot++) {
                result.set(slot, result.get(slot).with(155, masks[slot] & 0x3f));
            }
            return List.copyOf(result);
        } catch (IOException ignored) {
            return voices;
        }
    }

    private static byte[] operatorMasks(List<DexedPatch> voices) {
        byte[] masks = new byte[32];
        for (int slot = 0; slot < Math.min(voices.size(), masks.length); slot++) {
            masks[slot] = (byte) voices.get(slot).value(155);
        }
        return masks;
    }

    private static Path operatorMaskPath(Path bank) {
        Path name = bank.getFileName();
        if (name == null) throw new IllegalArgumentException("Bank path has no file name");
        return bank.resolveSibling(name + ".ops");
    }

    private static void atomicWrite(Path destination, byte[] bytes) throws IOException {
        Path absolute = destination.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent == null) throw new IOException("Bank destination has no parent folder");
        Path fileName = absolute.getFileName();
        if (fileName == null) throw new IOException("Bank destination has no file name");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent,
                fileName.toString(), ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String stem(Path path) {
        Path file = path.getFileName();
        String name = file == null ? "Imported Bank" : file.toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String friendlyName(String raw) {
        String clean = raw.replace('_', ' ').replace('-', ' ').strip()
                .replaceAll("\\s+", " ");
        if (clean.isBlank()) return "Imported Bank";
        if (!clean.equals(clean.toLowerCase(Locale.ROOT))
                && !clean.equals(clean.toUpperCase(Locale.ROOT))) return clean;
        StringBuilder result = new StringBuilder();
        for (String word : clean.toLowerCase(Locale.ROOT).split(" ")) {
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    record Bank(String id, String name, List<DexedPatch> voices, byte[] originalSysex,
                Path path, boolean factory) {
        Bank {
            voices = List.copyOf(voices);
            originalSysex = originalSysex.clone();
        }

        @Override public byte[] originalSysex() { return originalSysex.clone(); }
    }
}
