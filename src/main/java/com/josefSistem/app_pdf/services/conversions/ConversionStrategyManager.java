package com.josefSistem.app_pdf.services.conversions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Service
@Slf4j
public class ConversionStrategyManager {

    private final DoclingConversionStrategy docling;
    private final PandocConversionStrategy pandoc;

    public ConversionStrategyManager(
            DoclingConversionStrategy docling,
            PandocConversionStrategy pandoc) {
        this.docling = docling;
        this.pandoc = pandoc;
    }

    public String convert(String inputPath, String outputDir) {
        String markdownPath = null;

        try {
            // PASSO 1: PDF -> Markdown via Docling
            log.info("🔄 [Passo 1] PDF -> Markdown via Docling...");
            markdownPath = docling.convertToMarkdown(inputPath, outputDir);

            // PASSO 1.5: Limpa caracteres problemáticos do Markdown
            log.info("🧹 [Passo 1.5] Limpando Markdown...");
            cleanMarkdown(markdownPath);

            // PASSO 2: Markdown -> DOCX via Pandoc
            log.info("🔄 [Passo 2] Markdown -> DOCX via Pandoc...");
            String docxPath = pandoc.convertMarkdownToDocx(markdownPath, outputDir);

            return docxPath;

        } catch (Exception e) {
            log.error("❌ Conversão falhou: {}", e.getMessage());
            throw new RuntimeException("Falha na conversão: " + e.getMessage(), e);

        } finally {
            if (markdownPath != null) {
                new File(markdownPath).delete();
            }
        }
    }

    private void cleanMarkdown(String markdownPath) throws IOException {
        String content = Files.readString(Paths.get(markdownPath));

        content = content
                // Remove caracteres coreanos/japoneses/chineses que vieram de ícones
                .replaceAll("[\\u1100-\\u11FF\\u3130-\\u318F\\uAC00-\\uD7AF]", "")
                // Remove outros caracteres Unicode fora do Latin/comum
                .replaceAll("[^\\x00-\\x7F\\u00C0-\\u024F\\u0300-\\u036F\\n\\r\\t]", "")
                // Remove linhas que ficaram vazias após a limpeza
                .replaceAll("(?m)^\\s*$\\n", "")
                // Remove múltiplas linhas em branco consecutivas
                .replaceAll("\\n{3,}", "\n\n");

        Files.writeString(Paths.get(markdownPath), content);
        log.info("✅ Markdown limpo com sucesso");
    }
}

