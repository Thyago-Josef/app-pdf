package com.josefSistem.app_pdf.services.conversions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static java.io.File.*;

@Component
@Slf4j
public class DoclingConversionStrategy {

    @Value("${docling.service.url:http://localhost:5000}")
    private String doclingServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public String convertToMarkdown(String inputPath, String outputDir) throws Exception {
        log.info("📡 [Docling] Convertendo PDF -> Markdown: {}", inputPath);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(inputPath));

        HttpEntity<MultiValueMap<String, Object>> requestEntity =
                new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                doclingServiceUrl + "/convert/markdown",
                requestEntity,
                Map.class
        );

        if (response.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Docling retornou status: " + response.getStatusCode());
        }

        Map<String, String> responseBody = response.getBody();
        String downloadUrl = responseBody.get("download_url");
        String fileId = responseBody.get("file_id");

        // Baixa o Markdown gerado
        return downloadMarkdown(downloadUrl, fileId, outputDir);
    }

    private String downloadMarkdown(String downloadUrl, String fileId, String outputDir)
            throws IOException {
        byte[] fileBytes = restTemplate.getForObject(
                doclingServiceUrl + downloadUrl,
                byte[].class
        );

        String mdPath = outputDir + File.separator + fileId + ".md";
        Files.write(Paths.get(mdPath), fileBytes);

        log.info("✅ [Docling] Markdown salvo: {}", mdPath);
        return mdPath;
    }
}
