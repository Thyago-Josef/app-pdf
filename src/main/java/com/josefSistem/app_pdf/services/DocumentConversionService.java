package com.josefSistem.app_pdf.services;

import com.josefSistem.app_pdf.conversores.ConvertPDF;
import com.josefSistem.app_pdf.dto.ConversionRequestDTO;
import com.josefSistem.app_pdf.dto.ConversionResponseDTO;
import com.josefSistem.app_pdf.entities.DocumentEntity;
import com.josefSistem.app_pdf.entities.DocumentEntity.ConversionStatus;
import com.josefSistem.app_pdf.entities.DocumentEntity.DocumentType;
import com.josefSistem.app_pdf.mappers.DocumentMapper;
import com.josefSistem.app_pdf.repositories.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentConversionService {

    private final DocumentRepository documentRepository;
    private final DocumentMapper documentMapper; // ✅ Injeção do MapStruct Mapper
    private final ConvertPDF convertPDF;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.output.dir:output}")
    private String outputDir;

    /**
     * Converte um documento enviado pelo usuário
     */
    @Transactional
    public ConversionResponseDTO convertDocument(ConversionRequestDTO request) {
        log.info("📄 Iniciando conversão: {} -> {}", request.getSourceType(), request.getTargetType());

        validateConversion(request.getSourceType(), request.getTargetType());

        // 1. Salvar arquivo de entrada
        String inputPath = saveUploadedFile(request.getFile());
        log.debug("Arquivo salvo em: {}", inputPath);

        // 2. Criar entidade no banco
        DocumentEntity entity = createEntity(request, inputPath);
        entity = documentRepository.save(entity);
        log.info("Entidade criada com ID: {}", entity.getId());

        // 3. Processar conversão
        try {
            entity.setStatus(ConversionStatus.PROCESSING);
            entity = documentRepository.save(entity);

            String outputPath = performConversion(inputPath, entity, request);

            entity.setOutputPath(outputPath);
            entity.setStatus(ConversionStatus.COMPLETED);
            entity.setCompletedAt(LocalDateTime.now());

            log.info("✅ Conversão concluída: ID {} em {}s",
                    entity.getId(),
                    java.time.Duration.between(entity.getCreatedAt(), entity.getCompletedAt()).getSeconds());

        } catch (Exception e) {
            log.error("❌ Erro na conversão: ID {}", entity.getId(), e);
            entity.setStatus(ConversionStatus.FAILED);
            entity.setErrorMessage(e.getMessage());
        }

        entity = documentRepository.save(entity);

        // ✅ Usar MapStruct para converter Entity -> DTO
        return documentMapper.toResponseDTO(entity);
    }

    /**
     * Busca uma conversão por ID
     */
    @Transactional(readOnly = true)
    public ConversionResponseDTO getConversionById(Long id) {
        log.debug("Buscando conversão ID: {}", id);
        DocumentEntity entity = findEntityById(id);

        // ✅ Usar MapStruct
        return documentMapper.toResponseDTO(entity);
    }

    /**
     * Lista todas as conversões
     */
    @Transactional(readOnly = true)
    public List<ConversionResponseDTO> getAllConversions() {
        log.debug("Listando todas as conversões");
        List<DocumentEntity> entities = documentRepository.findAll();

        // ✅ Usar MapStruct para lista
        return documentMapper.toResponseDTOList(entities);
    }

    /**
     * Lista conversões por status
     */
    @Transactional(readOnly = true)
    public List<ConversionResponseDTO> getConversionsByStatus(ConversionStatus status) {
        log.debug("Buscando conversões com status: {}", status);
        List<DocumentEntity> entities = documentRepository.findByStatus(status);

        // ✅ Usar MapStruct para lista
        return documentMapper.toResponseDTOList(entities);
    }

    /**
     * Faz download do arquivo convertido
     */
    public Resource downloadFile(Long id) {
        log.debug("Download solicitado para ID: {}", id);

        DocumentEntity entity = findEntityById(id);

        if (entity.getStatus() != ConversionStatus.COMPLETED) {
            throw new IllegalStateException("Conversão ainda não foi concluída");
        }

        try {
            Path filePath = Paths.get(entity.getOutputPath());
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                log.info("✅ Download iniciado: {}", resource.getFilename());
                return resource;
            } else {
                throw new RuntimeException("Arquivo não encontrado ou não pode ser lido: " + filePath);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Erro ao criar URL do arquivo", e);
        }
    }

    /**
     * Deleta uma conversão e seus arquivos
     */
    @Transactional
    public void deleteConversion(Long id) {
        log.info("Deletando conversão ID: {}", id);

        DocumentEntity entity = findEntityById(id);

        // Deletar arquivos físicos
        deleteFileIfExists(entity.getFilePath());
        deleteFileIfExists(entity.getOutputPath());

        // Deletar do banco
        documentRepository.delete(entity);
        log.info("✅ Conversão deletada: ID {}", id);
    }

    // ==================== MÉTODOS PRIVADOS ====================

    private DocumentEntity createEntity(ConversionRequestDTO request, String inputPath) {
        return DocumentEntity.builder()
                .originalFileName(request.getFile().getOriginalFilename())
                .fileName(generateFileName())
                .sourceType(request.getSourceType())
                .targetType(request.getTargetType())
                .filePath(inputPath)
                .fileSize(request.getFile().getSize())
                .status(ConversionStatus.PENDING)
                .build();
    }

    private String performConversion(String inputPath, DocumentEntity entity, ConversionRequestDTO request) {
        String outputPath = generateOutputPath(entity);
        createDirectoryIfNotExists(outputDir);

        DocumentType source = entity.getSourceType();
        DocumentType target = entity.getTargetType();

        log.debug("Realizando conversão: {} -> {}", source, target);

        switch (target) {
            case PDF:
                convertToPDF(inputPath, source, request);
                break;

            case HTML:
                convertToHTML(inputPath, source);
                break;

            case IMAGE_PNG:
            case IMAGE_JPG:
            case IMAGE_JPEG:
                convertToImage(inputPath, source, target, request);
                break;

            default:
                throw new IllegalArgumentException("Tipo de destino não suportado: " + target);
        }

        return outputPath;
    }

    private void convertToPDF(String inputPath, DocumentType source, ConversionRequestDTO request) {
        switch (source) {
            case HTML:
                ConvertPDF.generatePDFFromHTMLModern(inputPath);
                break;

            case IMAGE_PNG:
            case IMAGE_JPG:
            case IMAGE_JPEG:
                String extension = source.getExtension();
                ConvertPDF.generatePDFFromImage(removeExtension(inputPath), extension);
                break;

            default:
                throw new IllegalArgumentException("Conversão não suportada: " + source + " -> PDF");
        }
    }

    private void convertToHTML(String inputPath, DocumentType source) {
        if (source != DocumentType.PDF) {
            throw new IllegalArgumentException("Conversão não suportada: " + source + " -> HTML");
        }
        // Implementar conversão PDF -> HTML
        throw new UnsupportedOperationException("Conversão PDF -> HTML ainda não implementada");
    }

    private void convertToImage(String inputPath, DocumentType source, DocumentType target, ConversionRequestDTO request) {
        if (source != DocumentType.PDF) {
            throw new IllegalArgumentException("Conversão não suportada: " + source + " -> " + target);
        }
        // Implementar conversão PDF -> Imagem
        throw new UnsupportedOperationException("Conversão PDF -> Imagem ainda não implementada");
    }

    private void validateConversion(DocumentType source, DocumentType target) {
        if (source == target) {
            throw new IllegalArgumentException("Tipo de origem e destino não podem ser iguais");
        }
        // Adicionar mais validações conforme necessário
    }

    private String saveUploadedFile(MultipartFile file) {
        try {
            createDirectoryIfNotExists(uploadDir);
            String fileName = UUID.randomUUID() + "_" + sanitizeFileName(file.getOriginalFilename());
            Path path = Paths.get(uploadDir, fileName);
            Files.write(path, file.getBytes());
            return path.toString();
        } catch (IOException e) {
            throw new RuntimeException("Erro ao salvar arquivo: " + e.getMessage(), e);
        }
    }

    private void createDirectoryIfNotExists(String dir) {
        try {
            Path path = Paths.get(dir);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.debug("Diretório criado: {}", path);
            }
        } catch (IOException e) {
            throw new RuntimeException("Erro ao criar diretório: " + dir, e);
        }
    }

    private String generateFileName() {
        return UUID.randomUUID().toString();
    }

    private String generateOutputPath(DocumentEntity entity) {
        String extension = entity.getTargetType().getExtension();
        return outputDir + File.separator + entity.getFileName() + "." + extension;
    }

    private String removeExtension(String path) {
        int lastDot = path.lastIndexOf('.');
        return lastDot > 0 ? path.substring(0, lastDot) : path;
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null) return "unnamed";
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private DocumentEntity findEntityById(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Documento não encontrado: ID " + id));
    }

    private void deleteFileIfExists(String filePath) {
        if (filePath != null) {
            try {
                Path path = Paths.get(filePath);
                if (Files.exists(path)) {
                    Files.delete(path);
                    log.debug("Arquivo deletado: {}", filePath);
                }
            } catch (IOException e) {
                log.warn("Erro ao deletar arquivo: {}", filePath, e);
            }
        }
    }
}