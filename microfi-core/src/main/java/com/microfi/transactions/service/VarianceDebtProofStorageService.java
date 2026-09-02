package com.microfi.transactions.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/**
 * Local-disk storage for variance-debt write-off proofs (waiver memo, manager sign-off, whatever
 * documents the decision to clear an agent's shortage) — one file per {@code VarianceDebt} row,
 * same small-dedicated-service shape as {@link EscrowDepositProofStorageService} rather than a
 * shared abstraction.
 */
@Service
public class VarianceDebtProofStorageService {

    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "application/pdf", "pdf",
            "image/jpeg", "jpg"
    );

    private final Path storageRoot;
    private final long maxSizeBytes;

    public VarianceDebtProofStorageService(
            @Value("${variance.write-off-proofs.storage-path}") String storagePath,
            @Value("${variance.write-off-proofs.max-size-bytes}") long maxSizeBytes) {
        this.storageRoot = Paths.get(storagePath).toAbsolutePath().normalize();
        this.maxSizeBytes = maxSizeBytes;
    }

    /** Streams {@code part} straight to disk (no in-memory buffering) and returns the path relative to the storage root. */
    public Mono<String> store(UUID debtId, FilePart part) {
        MediaType contentType = part.headers().getContentType();
        String extension = contentType != null ? ALLOWED_CONTENT_TYPES.get(contentType.toString()) : null;
        if (extension == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported file type for write-off proof: only PDF or JPEG accepted"));
        }
        String relativePath = debtId + "." + extension;
        Path target = storageRoot.resolve(relativePath);

        return Mono.fromCallable(() -> {
                    Files.createDirectories(target.getParent());
                    return target;
                }).subscribeOn(Schedulers.boundedElastic())
                .flatMap(path -> part.transferTo(path).thenReturn(path))
                .flatMap(path -> Mono.fromCallable(() -> {
                    long size = Files.size(path);
                    if (size > maxSizeBytes) {
                        Files.deleteIfExists(path);
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Write-off proof exceeds the maximum allowed size");
                    }
                    return relativePath;
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    public Mono<Resource> load(String relativePath) {
        return Mono.fromCallable(() -> {
            Path resolved = storageRoot.resolve(relativePath).normalize();
            if (!resolved.startsWith(storageRoot)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid document path");
            }
            if (!Files.exists(resolved)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Write-off proof not found");
            }
            return (Resource) new FileSystemResource(resolved);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public MediaType contentTypeFor(String relativePath) {
        return relativePath.endsWith(".pdf") ? MediaType.APPLICATION_PDF : MediaType.IMAGE_JPEG;
    }
}
