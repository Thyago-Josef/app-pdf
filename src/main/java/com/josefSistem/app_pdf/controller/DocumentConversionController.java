package com.josefSistem.app_pdf.controller;

import com.josefSistem.app_pdf.dto.ConversionRequestDTO;
import com.josefSistem.app_pdf.dto.ConversionResponseDTO;
import com.josefSistem.app_pdf.entities.DocumentEntity.ConversionStatus;
import com.josefSistem.app_pdf.entities.DocumentEntity.DocumentType;
import com.josefSistem.app_pdf.services.DocumentConversionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
@Tag(name = "Document Conversion", description = "APIs para conversão de documentos")
public class DocumentConversionController {

    private final DocumentConversionService conversionService;

    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Converter documento", description = "Converte um documento de um formato para outro")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Conversão criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Requisição inválida"),
            @ApiResponse(responseCode = "500", description = "Erro interno no servidor")
    })
    public ResponseEntity<ConversionResponseDTO> convertDocument(
            @Parameter(description = "Arquivo a ser convertido", required = true)
            @RequestParam("file") MultipartFile file,

            @Parameter(description = "Tipo do arquivo de origem (PDF, HTML, IMAGE_PNG, etc)", required = true)
            @RequestParam("sourceType") DocumentType sourceType,

            @Parameter(description = "Tipo do arquivo de destino", required = true)
            @RequestParam("targetType") DocumentType targetType,

            @Parameter(description = "DPI para conversão de imagens (padrão: 300)")
            @RequestParam(value = "dpi", required = false) Integer dpi,

            @Parameter(description = "Comprimir saída")
            @RequestParam(value = "compress", required = false) Boolean compress) {

        log.info("📥 POST /api/documents/convert - {} -> {}", sourceType, targetType);

        ConversionRequestDTO request = ConversionRequestDTO.builder()
                .file(file)
                .sourceType(sourceType)
                .targetType(targetType)
                .dpi(dpi)
                .compressOutput(compress)
                .build();

        ConversionResponseDTO response = conversionService.convertDocument(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar conversão por ID", description = "Retorna os detalhes de uma conversão específica")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Conversão encontrada"),
            @ApiResponse(responseCode = "404", description = "Conversão não encontrada")
    })
    public ResponseEntity<ConversionResponseDTO> getConversion(
            @Parameter(description = "ID da conversão", required = true)
            @PathVariable Long id) {

        log.info("📄 GET /api/documents/{}", id);
        ConversionResponseDTO response = conversionService.getConversionById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Listar todas as conversões", description = "Retorna uma lista com todas as conversões realizadas")
    @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
    public ResponseEntity<List<ConversionResponseDTO>> getAllConversions(
            @Parameter(description = "Filtrar por status (PENDING, PROCESSING, COMPLETED, FAILED)")
            @RequestParam(value = "status", required = false) ConversionStatus status) {

        log.info("📋 GET /api/documents - Status: {}", status);

        List<ConversionResponseDTO> responses = status != null
                ? conversionService.getConversionsByStatus(status)
                : conversionService.getAllConversions();

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download do arquivo convertido", description = "Faz o download do arquivo já convertido")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Download iniciado"),
            @ApiResponse(responseCode = "404", description = "Arquivo não encontrado"),
            @ApiResponse(responseCode = "409", description = "Conversão ainda não concluída")
    })
    public ResponseEntity<Resource> downloadFile(
            @Parameter(description = "ID da conversão", required = true)
            @PathVariable Long id) {

        log.info("⬇️ GET /api/documents/{}/download", id);

        Resource resource = conversionService.downloadFile(id);

        String fileName = resource.getFilename() != null ? resource.getFilename() : "download";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deletar conversão", description = "Remove a conversão e seus arquivos associados")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Conversão deletada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Conversão não encontrada")
    })
    public ResponseEntity<Void> deleteConversion(
            @Parameter(description = "ID da conversão", required = true)
            @PathVariable Long id) {

        log.info("🗑️ DELETE /api/documents/{}", id);
        conversionService.deleteConversion(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Verifica se o serviço está funcionando")
    @ApiResponse(responseCode = "200", description = "Serviço está funcionando")
    public ResponseEntity<Map<String, Object>> health() {
        log.debug("💚 GET /api/documents/health");

        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "Document Conversion Service");
        health.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(health);
    }

    @GetMapping("/stats")
    @Operation(summary = "Estatísticas", description = "Retorna estatísticas sobre as conversões")
    @ApiResponse(responseCode = "200", description = "Estatísticas retornadas")
    public ResponseEntity<Map<String, Object>> getStats() {
        log.info("📊 GET /api/documents/stats");

        List<ConversionResponseDTO> all = conversionService.getAllConversions();

        long completed = all.stream()
                .filter(dto -> dto.getStatus() == ConversionStatus.COMPLETED)
                .count();

        long failed = all.stream()
                .filter(dto -> dto.getStatus() == ConversionStatus.FAILED)
                .count();

        long processing = all.stream()
                .filter(dto -> dto.getStatus() == ConversionStatus.PROCESSING)
                .count();

        long pending = all.stream()
                .filter(dto -> dto.getStatus() == ConversionStatus.PENDING)
                .count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", all.size());
        stats.put("completed", completed);
        stats.put("failed", failed);
        stats.put("processing", processing);
        stats.put("pending", pending);
        stats.put("successRate", all.isEmpty() ? 0 : (double) completed / all.size() * 100);

        return ResponseEntity.ok(stats);
    }
}