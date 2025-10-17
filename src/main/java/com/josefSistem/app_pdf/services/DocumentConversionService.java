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
import java.io.PrintWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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

        log.info("Documento encontrado: ID={}, Status={}, OutputPath={}",
                entity.getId(), entity.getStatus(), entity.getOutputPath());

        if (entity.getStatus() != ConversionStatus.COMPLETED) {
            throw new IllegalStateException("Conversão ainda não foi concluída. Status atual: " + entity.getStatus());
        }

        if (entity.getOutputPath() == null || entity.getOutputPath().isEmpty()) {
            throw new IllegalStateException("Caminho do arquivo de saída não foi definido");
        }

        try {
            // Resolver caminho absoluto
            Path filePath = Paths.get(entity.getOutputPath()).toAbsolutePath().normalize();
            log.info("Caminho absoluto do arquivo: {}", filePath);

            // Verificar se o arquivo existe
            if (!Files.exists(filePath)) {
                log.error("❌ Arquivo não existe: {}", filePath);
                throw new RuntimeException("Arquivo não encontrado: " + filePath);
            }

            // Verificar se é legível
            if (!Files.isReadable(filePath)) {
                log.error("❌ Arquivo não é legível: {}", filePath);
                throw new RuntimeException("Arquivo não pode ser lido: " + filePath);
            }

            log.info("✅ Arquivo encontrado e legível: {}", filePath);

            Resource resource = new UrlResource(filePath.toUri());
            log.info("✅ Download iniciado: {}", resource.getFilename());

            return resource;

        } catch (MalformedURLException e) {
            log.error("❌ Erro ao criar URL do arquivo", e);
            throw new RuntimeException("Erro ao criar URL do arquivo: " + e.getMessage(), e);
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
        log.debug("Input: {}, Output: {}", inputPath, outputPath);

        switch (target) {
            case PDF:
                convertToPDF(inputPath, outputPath, source, request);
                break;

            case HTML:
                convertToHTML(inputPath, outputPath, source);
                break;

            case IMAGE_PNG:
            case IMAGE_JPG:
            case IMAGE_JPEG:
                convertToImage(inputPath, outputPath, source, target, request);
                break;

            case WORD_DOCX:
                convertToWord(inputPath, outputPath, source);
                break;

            default:
                throw new IllegalArgumentException("Tipo de destino não suportado: " + target);
        }

        // Verificar se o arquivo foi criado
        if (!Files.exists(Paths.get(outputPath))) {
            throw new RuntimeException("Arquivo de saída não foi gerado: " + outputPath);
        }

        return outputPath;
    }

    private void convertToPDF(String inputPath, String outputPath, DocumentType source, ConversionRequestDTO request) {
        try {
            switch (source) {
                case HTML:
                    // HTML -> PDF
                    try (FileInputStream is = new FileInputStream(inputPath);
                         FileOutputStream os = new FileOutputStream(outputPath)) {
                        com.itextpdf.html2pdf.HtmlConverter.convertToPdf(is, os);
                    }
                    log.info("✅ Conversão HTML -> PDF concluída: {}", outputPath);
                    break;

                case IMAGE_PNG:
                case IMAGE_JPG:
                case IMAGE_JPEG:
                    // Imagem -> PDF
                    String extension = source.getExtension();
                    ConvertPDF.generatePDFFromImage(removeExtension(inputPath), extension);

                    // Mover arquivo gerado para outputPath
                    String tempOutput = "src/output/" + extension + ".pdf";
                    Files.move(Paths.get(tempOutput), Paths.get(outputPath),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    log.info("✅ Conversão Imagem -> PDF concluída: {}", outputPath);
                    break;

                default:
                    throw new IllegalArgumentException("Conversão não suportada: " + source + " -> PDF");
            }
        } catch (IOException e) {
            throw new RuntimeException("Erro na conversão para PDF: " + e.getMessage(), e);
        }
    }

    private void convertToHTML(String inputPath, String outputPath, DocumentType source) {
        if (source != DocumentType.PDF) {
            throw new IllegalArgumentException("Conversão não suportada: " + source + " -> HTML");
        }

        try {
            // PDF -> HTML usando PDFDomTree
            try (org.apache.pdfbox.pdmodel.PDDocument pdf =
                         org.apache.pdfbox.pdmodel.PDDocument.load(new File(inputPath));
                 PrintWriter output = new PrintWriter(outputPath, "utf-8")) {

                new org.fit.pdfdom.PDFDomTree().writeText(pdf, output);
            }
            log.info("✅ Conversão PDF -> HTML concluída: {}", outputPath);

        } catch (IOException e) {
            throw new RuntimeException("Erro na conversão para HTML: " + e.getMessage(), e);
        }
    }

    private void convertToImage(String inputPath, String outputPath, DocumentType source,
                                DocumentType target, ConversionRequestDTO request) {
        if (source != DocumentType.PDF) {
            throw new IllegalArgumentException("Conversão não suportada: " + source + " -> " + target);
        }

        try {
            String extension = target.getExtension();
            int dpi = request.getDpi() != null ? request.getDpi() : 300;

            // PDF -> Imagem usando PDFBox
            try (org.apache.pdfbox.pdmodel.PDDocument document =
                         org.apache.pdfbox.pdmodel.PDDocument.load(new File(inputPath))) {

                org.apache.pdfbox.rendering.PDFRenderer pdfRenderer =
                        new org.apache.pdfbox.rendering.PDFRenderer(document);

                // Converter apenas a primeira página
                java.awt.image.BufferedImage bim = pdfRenderer.renderImageWithDPI(
                        0, dpi, org.apache.pdfbox.rendering.ImageType.RGB);

                // Salvar no outputPath
                org.apache.pdfbox.tools.imageio.ImageIOUtil.writeImage(
                        bim, outputPath, dpi);
            }
            log.info("✅ Conversão PDF -> Imagem concluída: {}", outputPath);

        } catch (IOException e) {
            throw new RuntimeException("Erro na conversão para Imagem: " + e.getMessage(), e);
        }
    }

    private void convertToWord(String inputPath, String outputPath, DocumentType source) {
        try {
            switch (source) {
                case PDF:
                    convertPdfToWord(inputPath, outputPath);
                    break;

                case HTML:
                    convertHtmlToWord(inputPath, outputPath);
                    break;

                default:
                    throw new IllegalArgumentException("Conversão não suportada: " + source + " -> DOCX");
            }
        } catch (IOException e) {
            throw new RuntimeException("Erro na conversão para Word: " + e.getMessage(), e);
        }
    }

    private void convertPdfToWord(String inputPath, String outputPath) throws IOException {
        String tempHtmlPath = outputPath.replace(".docx", "_temp.html");

        try (org.apache.pdfbox.pdmodel.PDDocument pdf =
                     org.apache.pdfbox.pdmodel.PDDocument.load(new File(inputPath));
             PrintWriter htmlOutput = new PrintWriter(tempHtmlPath, "utf-8")) {

            new org.fit.pdfdom.PDFDomTree().writeText(pdf, htmlOutput);
        }

        convertHtmlToWord(tempHtmlPath, outputPath);
        Files.deleteIfExists(Paths.get(tempHtmlPath));

        log.info("✅ Conversão PDF -> DOCX concluída: {}", outputPath);
    }

    private void convertHtmlToWord(String inputPath, String outputPath) throws IOException {
        try (org.apache.poi.xwpf.usermodel.XWPFDocument document =
                     new org.apache.poi.xwpf.usermodel.XWPFDocument();
             FileOutputStream out = new FileOutputStream(outputPath)) {

            String htmlContent = new String(Files.readAllBytes(Paths.get(inputPath)), "UTF-8");

            // Remover scripts e styles
            htmlContent = htmlContent.replaceAll("(?s)<script[^>]*>.*?</script>", "");
            htmlContent = htmlContent.replaceAll("(?s)<style[^>]*>.*?</style>", "");

            // Extrair texto limpo
            String textContent = htmlContent
                    .replaceAll("<br\\s*/?>", "\n")
                    .replaceAll("<p[^>]*>", "\n")
                    .replaceAll("</p>", "\n")
                    .replaceAll("<div[^>]*>", "\n")
                    .replaceAll("</div>", "\n")
                    .replaceAll("<[^>]*>", "")
                    .replaceAll("&nbsp;", " ")
                    .replaceAll("&amp;", "&")
                    .replaceAll("&lt;", "<")
                    .replaceAll("&gt;", ">")
                    .replaceAll("&quot;", "\"")
                    .replaceAll("\\s+", " ")
                    .trim();

            // Dividir em linhas
            String[] lines = textContent.split("\n");

            // Criar documento
            for (String line : lines) {
                String trimmedLine = line.trim();
                if (!trimmedLine.isEmpty() && trimmedLine.length() > 1) {
                    org.apache.poi.xwpf.usermodel.XWPFParagraph paragraph = document.createParagraph();
                    org.apache.poi.xwpf.usermodel.XWPFRun run = paragraph.createRun();
                    run.setText(trimmedLine);
                    run.setFontSize(11);
                    run.setFontFamily("Calibri");
                }
            }

            // Metadados
            document.createParagraph().createRun().addBreak();
            org.apache.poi.xwpf.usermodel.XWPFParagraph infoPara = document.createParagraph();
            infoPara.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
            org.apache.poi.xwpf.usermodel.XWPFRun infoRun = infoPara.createRun();
            infoRun.setText("Convertido de HTML por Document Conversion API");
            infoRun.setFontSize(9);
            infoRun.setColor("808080");
            infoRun.setItalic(true);

            document.write(out);
        }

        log.info("✅ Conversão HTML -> DOCX concluída: {}", outputPath);
    }

    private void convertWordToPdf(String inputPath, String outputPath) throws IOException {
        String tempHtmlPath = outputPath.replace(".pdf", "_temp.html");

        try (FileInputStream fis = new FileInputStream(inputPath);
             org.apache.poi.xwpf.usermodel.XWPFDocument document =
                     new org.apache.poi.xwpf.usermodel.XWPFDocument(fis);
             FileOutputStream htmlOut = new FileOutputStream(tempHtmlPath)) {

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body>");

            for (org.apache.poi.xwpf.usermodel.XWPFParagraph para : document.getParagraphs()) {
                String text = para.getText();
                if (!text.trim().isEmpty()) {
                    html.append("<p>").append(text).append("</p>");
                }
            }

            for (org.apache.poi.xwpf.usermodel.XWPFTable table : document.getTables()) {
                html.append("<table border='1'>");
                for (org.apache.poi.xwpf.usermodel.XWPFTableRow row : table.getRows()) {
                    html.append("<tr>");
                    for (org.apache.poi.xwpf.usermodel.XWPFTableCell cell : row.getTableCells()) {
                        html.append("<td>").append(cell.getText()).append("</td>");
                    }
                    html.append("</tr>");
                }
                html.append("</table>");
            }

            html.append("</body></html>");
            htmlOut.write(html.toString().getBytes("UTF-8"));
        }

        try (FileInputStream is = new FileInputStream(tempHtmlPath);
             FileOutputStream os = new FileOutputStream(outputPath)) {
            com.itextpdf.html2pdf.HtmlConverter.convertToPdf(is, os);
        }

        Files.deleteIfExists(Paths.get(tempHtmlPath));
        log.info("✅ Conversão DOCX -> PDF concluída: {}", outputPath);
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