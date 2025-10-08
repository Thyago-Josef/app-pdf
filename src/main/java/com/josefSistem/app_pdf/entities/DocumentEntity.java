package com.josefSistem.app_pdf.entities;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private String originalFileName;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DocumentType sourceType;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DocumentType targetType;

    @Column(nullable = false)
    private String filePath;

    @Column
    private String outputPath;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ConversionStatus status;

    @Column(length = 1000)
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime completedAt;

    @Column(nullable = false)
    private Long fileSize;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = ConversionStatus.PENDING;
        }
    }

    public enum DocumentType {
        PDF("pdf"),
        HTML("html"),
        IMAGE_PNG("png"),
        IMAGE_JPG("jpg"),
        IMAGE_JPEG("jpeg"),
        WORD_DOCX("docx"),
        EXCEL_XLSX("xlsx");

        private final String extension;

        DocumentType(String extension) {
            this.extension = extension;
        }

        public String getExtension() {
            return extension;
        }
    }

    public enum ConversionStatus {
        PENDING("Aguardando processamento"),
        PROCESSING("Em processamento"),
        COMPLETED("Concluído"),
        FAILED("Falhou");

        private final String description;

        ConversionStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}