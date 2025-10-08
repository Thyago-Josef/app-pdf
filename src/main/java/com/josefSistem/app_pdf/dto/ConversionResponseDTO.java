package com.josefSistem.app_pdf.dto;




import com.fasterxml.jackson.annotation.JsonFormat;
import com.josefSistem.app_pdf.entities.DocumentEntity.ConversionStatus;
import com.josefSistem.app_pdf.entities.DocumentEntity.DocumentType;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversionResponseDTO {

    private Long id;

    private String fileName;

    private String originalFileName;

    private DocumentType sourceType;

    private DocumentType targetType;

    private ConversionStatus status;

    private String statusDescription;

    private String errorMessage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;

    private Long fileSize;

    private String fileSizeFormatted;

    private String downloadUrl;

    private Long processingTimeSeconds;
}