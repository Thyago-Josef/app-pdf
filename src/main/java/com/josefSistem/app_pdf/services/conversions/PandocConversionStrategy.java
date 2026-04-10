package com.josefSistem.app_pdf.services.conversions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class PandocConversionStrategy {

    @Value("${pandoc.path:C:\\Program Files\\Pandoc\\pandoc.exe}")
    private String pandocPath;

    public String convertMarkdownToDocx(String markdownPath, String outputDir) throws Exception {
        log.info("📄 [Pandoc] Convertendo Markdown -> DOCX: {}", markdownPath);

        String fileId = new File(markdownPath)
                .getName()
                .replace(".md", "");

        String outputPath = outputDir + File.separator + fileId + ".docx";

        List<String> command = List.of(
                pandocPath,
                markdownPath,
                "-o", outputPath,
                "--from", "markdown",
                "--to", "docx",
                "--standalone"  // gera DOCX completo com estilos
        );

        executeProcess(command);

        File output = new File(outputPath);
        if (!output.exists() || output.length() < 500) {
            throw new RuntimeException("Pandoc gerou arquivo vazio ou inválido");
        }

        log.info("✅ [Pandoc] DOCX gerado: {}", outputPath);
        return outputPath;
    }

    private void executeProcess(List<String> command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[Pandoc] {}", line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Pandoc falhou com código: " + exitCode);
        }
    }
}
