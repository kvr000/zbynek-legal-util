package com.github.kvr000.zbyneklegal.format.table;

import org.apache.commons.io.FilenameUtils;

import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;


@Singleton
public class TableUpdatorFactory
{
    public TableUpdator openTableUpdator(Path filename, String idColumn) throws IOException
    {
        String lastname = filename.getFileName().toString();
        if (FilenameUtils.isExtension(lastname, "tsv")) {
            return new TsvTableUpdator(filename, idColumn);
        }
        else if (FilenameUtils.isExtension(lastname, "xls", "xlsx")) {
            return new XlsTableUpdator(filename, idColumn);
        }
        else {
            throw new IOException("Unsupported file extension: " + lastname);
        }
    }
}
