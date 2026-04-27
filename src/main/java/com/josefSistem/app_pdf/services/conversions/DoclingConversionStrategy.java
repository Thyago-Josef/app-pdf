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
public class DoclingConversionStrategy implements ConversionStrategy {

    @Value("${docling.service.url:http://localhost:5000}")
    private String doclingServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getName() {
        return "Pdf2DocxConverter";
    }

    @Override
    public String convert(String inputPath, String outputDir) throws Exception {
        return convert(inputPath, outputDir, "pdf2docx");
    }

    public String convert(String inputPath, String outputDir, String strategy) throws Exception {
        log.info("📡 [{}] Enviando PDF para conversão: {}", strategy, inputPath);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(inputPath));
        body.add("strategy", strategy);

        String url = doclingServiceUrl + "/convert/docx";
        ResponseEntity<Map> response = restTemplate.postForEntity(
                url,
                new HttpEntity<>(body, headers),
                Map.class
        );

        if (response.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Serviço retornou: " + response.getStatusCode());
        }

        Map<String, String> responseBody = response.getBody();
        String downloadUrl = responseBody.get("download_url");
        String fileId = responseBody.get("file_id");

        byte[] fileBytes = restTemplate.getForObject(
                doclingServiceUrl + downloadUrl,
                byte[].class
        );

        String outputPath = outputDir + File.separator + fileId + ".docx";
        Files.write(Paths.get(outputPath), fileBytes);

        log.info("✅ DOCX recebido e salvo: {}", outputPath);
        return outputPath;
    }
}





