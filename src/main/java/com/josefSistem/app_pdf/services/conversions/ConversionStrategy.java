package com.josefSistem.app_pdf.services.conversions;

public interface ConversionStrategy {
    String convert(String inputPath, String outputDir) throws Exception;
    String getName();
}