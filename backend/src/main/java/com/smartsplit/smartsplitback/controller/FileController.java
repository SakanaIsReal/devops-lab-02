package com.smartsplit.smartsplitback.controller;

import com.smartsplit.smartsplitback.model.StoredFile;
import com.smartsplit.smartsplitback.repository.StoredFileRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/files")
public class FileController {

    private final StoredFileRepository repo;

    public FileController(StoredFileRepository repo) {
        this.repo = repo;
    }

    @GetMapping(value = "/{id}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getBase64(@PathVariable Long id) {
        StoredFile f = repo.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "File not found"));

        return ResponseEntity.ok(f.getDataUrl());
    }
}
