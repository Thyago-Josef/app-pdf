package com.josefSistem.app_pdf.conversores;

// ============================================
// IMPORTS DO iTEXT 7 (CORRETOS!)
// ============================================
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.io.font.constants.StandardFontFamilies;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;              // ✅ CORRETO: do iText
import com.itextpdf.layout.element.Image;         // ✅ CORRETO: do iText
import com.itextpdf.io.image.ImageDataFactory;

// ============================================
// IMPORTS DO PDFBOX
// ============================================
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.fit.pdfdom.PDFDomTree;
import org.springframework.stereotype.Component;

// ============================================
// IMPORTS DO JAVA PADRÃO
// ============================================
import java.awt.image.BufferedImage;  // ✅ OK: BufferedImage é necessário
import java.io.*;

@Component
public class ConvertPDF {

    private void generateHTMLFromPDF(String filename) {
        try (
                PDDocument pdf = PDDocument.load(new File(filename));
                PrintWriter output = new PrintWriter("src/output/pdf.html", "utf-8")
        ) {
            new PDFDomTree().writeText(pdf, output);
            System.out.println("✅ Conversão concluída com sucesso: " + filename + " -> src/output/pdf.html");

        } catch (IOException e) {
            System.err.println("❌ Erro de I/O durante a conversão PDF para HTML:");
            e.printStackTrace();
        }
    }


    public static void generatePDFFromHTMLModern(String filename) {
        try (
                FileInputStream is = new FileInputStream(filename);
                FileOutputStream os = new FileOutputStream("src/output/html.pdf")
        ) {
            HtmlConverter.convertToPdf(is, os);
            System.out.println("✅ Conversão para PDF concluída (iText 7 + pdfHTML).");

        } catch (IOException e) {
            System.err.println("❌ Erro durante a conversão HTML para PDF:");
            e.printStackTrace();
        }
    }

    private void generateImageFromPDF(String filename, String extension) {
        try (
                PDDocument document = PDDocument.load(new File(filename))
        ) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);

            for (int page = 0; page < document.getNumberOfPages(); ++page) {
                BufferedImage bim = pdfRenderer.renderImageWithDPI(
                        page,
                        300,
                        ImageType.RGB
                );

                ImageIOUtil.writeImage(
                        bim,
                        String.format("src/output/pdf-%d.%s", page + 1, extension),
                        300
                );
            }

            System.out.printf("✅ Conversão de PDF para imagem concluída. %d páginas salvas como *.%s.%n",
                    document.getNumberOfPages(), extension);

        } catch (IOException e) {
            System.err.println("❌ Erro durante a conversão de PDF para Imagem:");
            e.printStackTrace();
        }
    }

    /**
     * Converte um arquivo de imagem local em um documento PDF, onde a imagem preenche a primeira página.
     *
     * @param filename  O nome base do arquivo de imagem (ex: "minha_foto").
     * @param extension A extensão do arquivo de imagem (ex: "png", "jpg", "jpeg").
     */
    public static void generatePDFFromImage(String filename, String extension) {
        String inputPath = filename + "." + extension;
        String outputPath = String.format("src/output/%s.pdf", extension);

        try (FileOutputStream fos = new FileOutputStream(outputPath)) {

            // Configuração do PDF (iText 7)
            PdfWriter writer = new PdfWriter(fos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);  // ✅ Este é do iText!

            // Carregar imagem
            Image image = new Image(ImageDataFactory.create(inputPath));  // ✅ Este é do iText!

            // Ajustar imagem à página
            float width = pdf.getDefaultPageSize().getWidth() - document.getLeftMargin() - document.getRightMargin();
            float height = pdf.getDefaultPageSize().getHeight() - document.getTopMargin() - document.getBottomMargin();

            image.setWidth(width);
            image.setHeight(height);

            // Adicionar ao documento
            document.add(image);
            document.close();

            System.out.printf("✅ Conversão de imagem para PDF concluída: %s -> %s%n", inputPath, outputPath);

        } catch (IOException e) {
            System.err.println("❌ Erro durante a conversão de imagem para PDF.");
            System.err.println("Caminho do arquivo de entrada: " + inputPath);
            e.printStackTrace();
        }
    }


    public static void generateTxtFromPDF(String filename) {

        // O bloco 'try-with-resources' garante que o PDDocument e o PrintWriter
        // sejam fechados automaticamente, mesmo em caso de erro.
        try (
                // 1. CARREGAR O PDF: Abre e carrega o arquivo PDF.
                PDDocument document = PDDocument.load(new File(filename));

                // 2. CONFIGURAR A SAÍDA: Cria um PrintWriter para escrever o texto no arquivo.
                PrintWriter pw = new PrintWriter("src/output/pdf.txt")
        ) {
            // 3. EXTRAIR O TEXTO:
            // Cria um PDFTextStripper, a ferramenta do PDFBox para extração de texto.
            PDFTextStripper pdfStripper = new PDFTextStripper();

            // Extrai todo o texto do documento.
            String parsedText = pdfStripper.getText(document);

            // 4. ESCREVER A SAÍDA:
            // Escreve o texto extraído no arquivo de saída (.txt).
            pw.print(parsedText);

            System.out.println("✅ Extração de texto para TXT concluída. Saída: src/output/pdf.txt");

        } catch (IOException e) {
            // 5. TRATAMENTO DE ERROS:
            // Captura erros de I/O (carregar o PDF, escrever o TXT) ou erros internos do PDFBox.
            System.err.println("❌ Erro durante a extração de texto do PDF:");
            e.printStackTrace();
        }
    }

    /**
     * Converte um arquivo de texto simples (.txt) em um documento PDF, formatando
     * o conteúdo em parágrafos e usando justificação de texto.
     * <p>
     * O arquivo de saída é salvo em "src/output/txt.pdf".
     * Esta função usa o iText 7 Core, a versão moderna e segura da biblioteca.
     * </p>
     *
     * @param inputFilename O nome base do arquivo de texto de entrada (ex: "documento.txt").
     */
    public static void generatePDFFromTxt(String inputFilename) {

        String outputPath = "src/output/txt.pdf";

        try (
                FileOutputStream fos = new FileOutputStream(outputPath);
                PdfWriter writer = new PdfWriter(fos);
                PdfDocument pdf = new PdfDocument(writer);
                Document document = new Document(pdf, PageSize.A4);

                BufferedReader br = new BufferedReader(new FileReader(inputFilename))
        ) {
            float fontSize = 11f;

            // 1. CRIA O OBJETO PDF FONT UMA VEZ
            // Usamos a fonte padrão Helvetica que não precisa ser carregada do sistema de arquivos.
            PdfFont font = PdfFontFactory.createFont(StandardFontFamilies.HELVETICA);

            // 2. CONFIGURA AS PROPRIEDADES PADRÃO DO DOCUMENTO
            document.setFont(font).setFontSize(fontSize);

            String strLine;
            while ((strLine = br.readLine()) != null) {
                // 3. CRIA O PARÁGRAFO E APLICA JUSTIFICAÇÃO
                Paragraph para = new Paragraph(strLine)
                        // Não precisa de .setFont() e .setFontSize() aqui se já for definido no Document
                        .setTextAlignment(TextAlignment.JUSTIFIED);

                document.add(para);
            }

            document.close();

            System.out.printf("✅ Conversão de TXT para PDF concluída: %s -> %s%n", inputFilename, outputPath);

        } catch (IOException e) {
            System.err.println("❌ Erro durante a conversão de TXT para PDF.");
            System.err.println("Caminho do arquivo de entrada: " + inputFilename);
            e.printStackTrace();
        }
    }

    /**
     * Converte o conteúdo textual de um arquivo PDF em um arquivo DOCX (Microsoft Word).
     * <p>
     * O DOCX resultante é salvo em "src/output/pdf.docx".
     * Esta função utiliza Apache PDFBox para a extração de texto e Apache POI para a criação do arquivo DOCX.
     * </p>
     *
     * @param inputFilename O caminho (path) completo ou relativo do arquivo PDF de entrada.
     */
    private static void generateDocxFromPDF(String inputFilename) {

        String outputPath = "src/output/pdf.docx";

        // 1. EXTRAÇÃO DE TEXTO DO PDF (usando Apache PDFBox)
        String fullText;
        try (PDDocument document = PDDocument.load(new File(inputFilename))) {

            PDFTextStripper pdfStripper = new PDFTextStripper();
            fullText = pdfStripper.getText(document);

        } catch (IOException e) {
            System.err.println("❌ Erro ao extrair texto do PDF com PDFBox.");
            e.printStackTrace();
            return; // Encerra o método se a extração falhar.
        }

        // 2. CRIAÇÃO DO DOCX (usando Apache POI)
        // O bloco 'try-with-resources' garante o fechamento seguro do XWPFDocument e do FileOutputStream.
        try (
                XWPFDocument doc = new XWPFDocument();
                FileOutputStream out = new FileOutputStream(outputPath)
        ) {
            // Cria um novo parágrafo e um "Run" para o texto
            XWPFParagraph p = doc.createParagraph();
            XWPFRun run = p.createRun();

            // Adiciona o texto extraído.
            // Nota: O método .setText(String) do POI lida com quebras de linha.
            run.setText(fullText);

            // Escreve o documento no FileOutputStream
            doc.write(out);

            System.out.printf("✅ Conversão de PDF para DOCX concluída. Saída: %s%n", outputPath);

        } catch (IOException e) {
            System.err.println("❌ Erro ao escrever o arquivo DOCX.");
            e.printStackTrace();
        }
    }


}