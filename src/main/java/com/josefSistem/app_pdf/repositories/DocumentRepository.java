package com.josefSistem.app_pdf.repositories;





import com.josefSistem.app_pdf.entities.DocumentEntity;
import com.josefSistem.app_pdf.entities.DocumentEntity.ConversionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {

    List<DocumentEntity> findByStatus(ConversionStatus status);

    List<DocumentEntity> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    List<DocumentEntity> findByOriginalFileNameContaining(String fileName);

    long countByStatus(ConversionStatus status);
}