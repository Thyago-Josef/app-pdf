package com.josefSistem.app_pdf.conversores;


// ============================================
// IMPORTS DO iTEXT 7 (CORRETOS!)
// ============================================
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;              // ✅ CORRETO: do iText
import com.itextpdf.layout.element.Image;         // ✅ CORRETO: do iText
import com.itextpdf.io.image.ImageDataFactory;

// ============================================
// IMPORTS DO PDFBOX
// ============================================
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;
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
}