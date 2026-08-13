package it.signflow.fse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.PDNameTreeNode;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

@Component
public class PdfBoxCdaInjector implements PdfCdaInjector {
    public static final String CDA_FILENAME = "cda.xml";

    @Override
    public byte[] inject(byte[] pdf, byte[] cdaXml) {
        if (pdf == null || pdf.length == 0) throw new IllegalArgumentException("PDF is required");
        if (cdaXml == null || cdaXml.length == 0) throw new IllegalArgumentException("CDA XML is required");
        requireCdaDocument(cdaXml);
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
            PDDocumentNameDictionary names = catalog.getNames() == null
                    ? new PDDocumentNameDictionary(catalog) : catalog.getNames();
            PDEmbeddedFilesNameTreeNode previous = names.getEmbeddedFiles();
            Map<String, PDComplexFileSpecification> files = new HashMap<>();
            collect(previous, files);
            files.put(CDA_FILENAME, specification);
            PDEmbeddedFilesNameTreeNode embeddedFiles = new PDEmbeddedFilesNameTreeNode();
            embeddedFiles.setNames(files);
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

    private void requireCdaDocument(byte[] cdaXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override public void error(SAXParseException exception) throws SAXException { throw exception; }
                @Override public void fatalError(SAXParseException exception) throws SAXException { throw exception; }
            });
            var root = builder.parse(new ByteArrayInputStream(cdaXml)).getDocumentElement();
            if (!"ClinicalDocument".equals(root.getLocalName()) || !"urn:hl7-org:v3".equals(root.getNamespaceURI())) {
                throw new IllegalArgumentException("CDA XML root must be hl7:ClinicalDocument");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("CDA XML must be well-formed and safe to parse", exception);
        }
    }

    private void collect(PDNameTreeNode<PDComplexFileSpecification> node,
                         Map<String, PDComplexFileSpecification> files)
            throws IOException {
        if (node == null) return;
        Map<String, PDComplexFileSpecification> direct = node.getNames();
        if (direct != null) files.putAll(direct);
        if (node.getKids() != null) {
            for (PDNameTreeNode<PDComplexFileSpecification> child : node.getKids()) collect(child, files);
        }
    }
}
