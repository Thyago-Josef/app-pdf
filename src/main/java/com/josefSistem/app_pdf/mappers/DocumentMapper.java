package com.josefSistem.app_pdf.mappers;


import com.josefSistem.app_pdf.dto.ConversionResponseDTO;
import com.josefSistem.app_pdf.entities.DocumentEntity;
import org.mapstruct.*;

import java.time.Duration;
import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface DocumentMapper {

    /**
     * Converte Entity para Response DTO com mapeamentos customizados
     */
    @Mapping(target = "statusDescription", source = "status", qualifiedByName = "statusToDescription")
    @Mapping(target = "downloadUrl", expression = "java(generateDownloadUrl(entity))")
    @Mapping(target = "fileSizeFormatted", expression = "java(formatFileSize(entity.getFileSize()))")
    @Mapping(target = "processingTimeSeconds", expression = "java(calculateProcessingTime(entity))")
    ConversionResponseDTO toResponseDTO(DocumentEntity entity);

    /**
     * Converte lista de Entities para lista de Response DTOs
     */
    List<ConversionResponseDTO> toResponseDTOList(List<DocumentEntity> entities);

    /**
     * Atualiza uma Entity existente com dados do DTO (útil para PATCH)
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntityFromDTO(ConversionResponseDTO dto, @MappingTarget DocumentEntity entity);

    /**
     * Mapeia o enum Status para sua descrição
     */
    @Named("statusToDescription")
    default String statusToDescription(DocumentEntity.ConversionStatus status) {
        return status != null ? status.getDescription() : null;
    }

    /**
     * Gera a URL de download se o documento estiver completo
     */
    default String generateDownloadUrl(DocumentEntity entity) {
        if (entity.getStatus() == DocumentEntity.ConversionStatus.COMPLETED
                && entity.getId() != null) {
            return "/api/documents/" + entity.getId() + "/download";
        }
        return null;
    }

    /**
     * Formata o tamanho do arquivo para formato legível (KB, MB, GB)
     */
    default String formatFileSize(Long sizeInBytes) {
        if (sizeInBytes == null || sizeInBytes == 0) {
            return "0 B";
        }

        final String[] units = {"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(sizeInBytes) / Math.log10(1024));

        return String.format("%.2f %s",
                sizeInBytes / Math.pow(1024, digitGroups),
                units[digitGroups]);
    }

    /**
     * Calcula o tempo de processamento em segundos
     */
    default Long calculateProcessingTime(DocumentEntity entity) {
        if (entity.getCreatedAt() != null && entity.getCompletedAt() != null) {
            Duration duration = Duration.between(entity.getCreatedAt(), entity.getCompletedAt());
            return duration.getSeconds();
        }
        return null;
    }
}
