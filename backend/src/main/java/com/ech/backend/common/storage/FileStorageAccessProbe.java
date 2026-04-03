package com.ech.backend.common.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 첨부파일 저장 루트에 대해 디렉터리 생성·임시 파일 쓰기·삭제까지 수행해 접근 가능 여부를 판별한다.
 * {@link FileStorageStartupValidator} 및 관리자 진단 API에서 공통 사용.
 */
public final class FileStorageAccessProbe {

    static final String WRITE_PROBE_FILENAME = ".ech-storage-write-probe";

    private FileStorageAccessProbe() {
    }

    /**
     * @param baseDir 저장 루트(UNC 가능)
     */
    public static Result probe(String baseDir) {
        if (baseDir == null || baseDir.isBlank()) {
            return new Result(null, false, "Storage path is blank.");
        }
        Path root = Paths.get(baseDir.trim()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            Path probeFile = root.resolve(WRITE_PROBE_FILENAME);
            Files.writeString(probeFile, "ok", StandardCharsets.UTF_8);
            Files.deleteIfExists(probeFile);
            return new Result(root, true, "ok");
        } catch (IOException e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new Result(root, false, e.getClass().getSimpleName() + ": " + msg);
        }
    }

    public static boolean looksLikeUnc(Path path) {
        if (path == null) {
            return false;
        }
        String s = path.toString().replace('/', '\\');
        return s.startsWith("\\\\");
    }

    public record Result(Path resolvedAbsolutePath, boolean writable, String detail) {
    }
}
