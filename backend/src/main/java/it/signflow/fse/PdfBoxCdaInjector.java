package it.signflow.fse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.springframework.stereotype.Component;

@Component
public class PdfBoxCdaInjector implements PdfCdaInjector {
    public static final String CDA_FILENAME = "cda.xml";

    @Override
    public byte[] inject(byte[] pdf, byte[] cdaXml) {
        if (pdf == null || pdf.length == 0) throw new IllegalArgumentException("PDF is required");
        if (cdaXml == null || cdaXml.length == 0) throw new IllegalArgumentException("CDA XML is required");
        try (PDDocument document = Loader.loadPDF(pdf); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDComplexFileSpecification specification = new PDComplexFileSpecification();
            specification.setFile(CDA_FILENAME);
            specification.setFileUnicode(CDA_FILENAME);

            PDEmbeddedFile embedded = new PDEmbeddedFile(document, new ByteArrayInputStream(cdaXml));
            embedded.setSubtype("application/xml");
            embedded.setSize(cdaXml.length);
            specification.setEmbeddedFile(embedded);
            specification.setEmbeddedFileUnicode(embedded);
            specification.getCOSObject().setName(COSName.AF_RELATIONSHIP, "Data");

            PDDocumentCatalog catalog = document.getDocumentCatalog();
            PDDocumentNameDictionary names = new PDDocumentNameDictionary(catalog);
            PDEmbeddedFilesNameTreeNode embeddedFiles = new PDEmbeddedFilesNameTreeNode();
            embeddedFiles.setNames(Map.of(CDA_FILENAME, specification));
            names.setEmbeddedFiles(embeddedFiles);
            catalog.setNames(names);
            org.apache.pdfbox.cos.COSArray associatedFiles = new org.apache.pdfbox.cos.COSArray();
            associatedFiles.add(specification);
            catalog.getCOSObject().setItem(COSName.AF, associatedFiles);
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to inject CDA XML into PDF", exception);
        }
    }
}
