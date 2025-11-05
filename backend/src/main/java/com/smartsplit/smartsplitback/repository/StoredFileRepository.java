package com.smartsplit.smartsplitback.repository;

import com.smartsplit.smartsplitback.model.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileRepository extends JpaRepository<StoredFile, Long> {
}
