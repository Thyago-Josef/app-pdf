package com.josefSistem.app_pdf.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

@Service
@Slf4j
public class DoclingClient {

    @Value("${docling.service.url}")
    private String doclingServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public String convertPdfToDocx(String inputPath, String outputDir) {
        log.info("📡 Enviando para Docling: {}", inputPath);

        try {
            // Monta o multipart com o arquivo PDF
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(inputPath));

            HttpEntity<MultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(body, headers);

            // Chama o microserviço
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    doclingServiceUrl + "/convert",
                    requestEntity,
                    Map.class
            );

            if (response.getStatusCode() != HttpStatus.OK) {
                throw new RuntimeException("Docling retornou status: " + response.getStatusCode());
            }

            Map<String, String> responseBody = response.getBody();
            String downloadUrl = responseBody.get("download_url");
            String fileId = responseBody.get("file_id");

            log.info("✅ Conversão concluída. Baixando arquivo...");

            // Baixa o DOCX gerado
            return downloadDocx(downloadUrl, fileId, outputDir);

        } catch (Exception e) {
            log.error("❌ Erro ao chamar Docling: {}", e.getMessage());
            throw new RuntimeException("Falha na conversão via Docling: " + e.getMessage(), e);
        }
    }

    private String downloadDocx(String downloadUrl, String fileId, String outputDir) throws IOException {
        byte[] fileBytes = restTemplate.getForObject(
                doclingServiceUrl + downloadUrl,
                byte[].class
        );

        String outputPath = outputDir + File.separator + fileId + ".docx";
        Files.write(Paths.get(outputPath), fileBytes);

        log.info("✅ DOCX salvo em: {}", outputPath);
        return outputPath;
    }
}