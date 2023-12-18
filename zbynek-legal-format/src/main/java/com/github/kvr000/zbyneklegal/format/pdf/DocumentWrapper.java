package com.github.kvr000.zbyneklegal.format.pdf;

import lombok.Getter;
import lombok.Setter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;


public class DocumentWrapper implements Closeable
{
    @Getter
    private File inputFile;

    @Getter
    private PDDocument document;

    @Getter
    @Setter
    private boolean needRefresh;

    public DocumentWrapper(File inputFile) throws IOException
    {
        this.inputFile = inputFile;
        this.needRefresh = true;
        getFreshDocument();
        this.needRefresh = false;
    }

    public PDDocument getFreshDocument() throws IOException
    {
        if (needRefresh) {
            if (this.document != null) {
                this.document.close();
                this.document = null;
            }
            this.document = Loader.loadPDF(inputFile);
        }
        this.needRefresh = true;
        return this.document;
    }

    @Override
    public void close() throws IOException
    {
        document.close();
    }
}
