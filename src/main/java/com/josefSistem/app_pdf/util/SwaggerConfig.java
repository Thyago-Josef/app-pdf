package com.josefSistem.app_pdf.util;


import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Document Conversion API")
                        .version("1.0.0")
                        .description("""
                                API REST para conversão de documentos entre diferentes formatos.
                                
                                ### Formatos Suportados:
                                - **PDF** ↔ HTML
                                - **PDF** ↔ Imagens (PNG, JPG, JPEG)
                                - **HTML** → PDF
                                - **Imagens** → PDF
                                
                                ### Funcionalidades:
                                - Upload e conversão de documentos
                                - Rastreamento de status em tempo real
                                - Download de arquivos convertidos
                                - Histórico de conversões
                                - Estatísticas de uso
                                """)
                        .contact(new Contact()
                                .name("Josef System")
                                .email("contato@josefsystem.com")
                                .url("https://github.com/josefsystem"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Servidor Local"),
                        new Server()
                                .url("https://api.josefsystem.com")
                                .description("Servidor de Produção")
                ));
    }
}
