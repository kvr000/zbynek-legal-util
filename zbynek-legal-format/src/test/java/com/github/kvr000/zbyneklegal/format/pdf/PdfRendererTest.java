package com.github.kvr000.zbyneklegal.format.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.testng.annotations.Test;

import java.io.File;
import java.util.List;

public class PdfRendererTest
{
    @Test
    public void cloneSourcePages_whenRepeated_success() throws Exception
    {
        try (DocumentWrapper source = new DocumentWrapper(new File("src/test/resources/com/github/kvr000/zbyneklegal/format/pdf/FivePageDocument.pdf"))) {
            try (PDDocument doc = new PDDocument(); PdfRenderer renderer = new PdfRenderer(doc)) {
                List<PDPage> pages = renderer.cloneSourcePages(source);
            }
            try (PDDocument doc = new PDDocument(); PdfRenderer renderer = new PdfRenderer(doc)) {
                List<PDPage> pages = renderer.cloneSourcePages(source);
            }
        }
    }
}
