package com.josefSistem.app_pdf.entities;

public enum ConversionStrategy {
    PDF2DOCX("pdf2docx", "Alta fidelidade - mantém layout"),
    DOCLING("docling", "Texto puro - via Markdown");

    private final String value;
    private final String description;

    ConversionStrategy(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public static ConversionStrategy fromValue(String value) {
        for (ConversionStrategy strategy : values()) {
            if (strategy.value.equalsIgnoreCase(value)) {
                return strategy;
            }
        }
        return PDF2DOCX;
    }
}