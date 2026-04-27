package com.josefSistem.app_pdf.dto;




import com.josefSistem.app_pdf.entities.DocumentEntity.DocumentType;
import com.josefSistem.app_pdf.entities.ConversionStrategy;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversionRequestDTO {

    @NotNull(message = "Arquivo é obrigatório")
    private MultipartFile file;

    @NotNull(message = "Tipo de origem é obrigatório")
    private DocumentType sourceType;

    @NotNull(message = "Tipo de destino é obrigatório")
    private DocumentType targetType;

    // Campos opcionais para configurações adicionais
    private Integer dpi; // Para conversão de PDF para imagem
    private String imageFormat; // png, jpg, jpeg
    private Boolean compressOutput; // Comprimir arquivo de saída

    // Estratégia de conversão PDF -> DOCX
    // Opções: PDF2DOCX (alta fidelidade), DOCLING (texto puro)
    // Padrão: PDF2DOCX
    private ConversionStrategy conversionStrategy;
}