package com.wildetechsolutions.itext;

import com.itextpdf.forms.xfa.XfaForm;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor;
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy;
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy;
import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ITextUtil {

        private static Logger log = LoggerFactory.getLogger(ITextUtil.class);


        public static String getText(String path) throws IOException {
                try {
                        PdfDocument pdfDoc = new PdfDocument(new PdfReader(path));

                        // Create a text extraction renderer
                        SimpleTextExtractionStrategy strategy = new SimpleTextExtractionStrategy();

                        // Note: if you want to re-use the PdfCanvasProcessor, you must call PdfCanvasProcessor.reset()
                        PdfCanvasProcessor parser = new PdfCanvasProcessor(strategy);
                        for (int i = 1; i <= pdfDoc.getNumberOfPages(); i++) {
                                parser.processPageContent(pdfDoc.getPage(i));
                        }

                        byte[] content = strategy.getResultantText().getBytes(StandardCharsets.UTF_8);


                        pdfDoc.close();

                        return new String(content);
                } catch (Exception e) {
//                        e.printStackTrace();
                        log.warn(e.getMessage());
                        log.warn("Could not get text for path {}", path);
                }
                return "";
        }

        public static Map<String, String> getFormFields(String path) throws IOException {
                Map<String, String> map = new LinkedHashMap<>();
                PdfDocument pdfDoc = new PdfDocument(new PdfReader(path));
                PdfAcroForm form = PdfAcroForm.getAcroForm(pdfDoc, true);
                Map<String, PdfFormField> fields = form.getAllFormFields();

                for (var entry : fields.entrySet()) {
                        log.trace("Field name: {}", entry.getKey());
                        PdfFormField formField = entry.getValue();
                        log.trace("value: {}", formField.getValueAsString());
                        map.put(entry.getKey(), formField.getValueAsString());

                }

                return map;
        }

        public static Map<String, String> getFormFieldValues(Path docPath) throws IOException {
                PdfReader reader = new PdfReader(docPath.toString());
                PdfDocument pdf = new PdfDocument(reader);
                XfaForm xfa = new XfaForm(pdf);

                Map<String, String> fieldValues = new HashMap<>();
                if (xfa.isXfaPresent()) {
                        Node datasetsNode = xfa.getDatasetsNode();
                        traverseNodes(datasetsNode, fieldValues);
                }

                pdf.close();
                return fieldValues;
        }

        private static void traverseNodes(Node node, Map<String, String> fieldValues) {
                if (node.getNodeType() == Node.ELEMENT_NODE && node.hasChildNodes()) {
                        NodeList childNodes = node.getChildNodes();
                        for (int i = 0; i < childNodes.getLength(); i++) {
                                Node childNode = childNodes.item(i);
                                if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                                        String fieldName = childNode.getLocalName();
                                        String fieldValue = childNode.getTextContent().trim();
                                        if (!fieldValue.isEmpty()) {
                                                fieldValues.put(fieldName, fieldValue);
                                        }
                                        traverseNodes(childNode, fieldValues);
                                }
                        }
                }
        }

        public static void fillAndSaveXfaForm(Map<String, String> formValues, Path sourcePath, Path targetPath) throws Exception {
                PdfReader reader = new PdfReader(sourcePath.toString());
                PdfWriter writer = new PdfWriter(targetPath.toString());
                PdfDocument pdf = new PdfDocument(reader, writer);
                XfaForm xfa = new XfaForm(pdf);

                if (xfa.isXfaPresent()) {
                        Node datasetsNode = xfa.getDatasetsNode();
                        Document doc = datasetsNode.getOwnerDocument();

                        for (Map.Entry<String, String> entry : formValues.entrySet()) {
                                updateNodeValue(doc, datasetsNode, entry.getKey(), entry.getValue());
                        }

                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        Transformer transformer = TransformerFactory.newInstance().newTransformer();
                        transformer.transform(new DOMSource(doc), new StreamResult(outputStream));
                        xfa.setDomDocument(doc); // Set the updated XML document back to the XFA form
                } else {
                        throw new IOException("No XFA form found in the provided PDF.");
                }

                pdf.close();
        }

        private static void updateNodeValue(Document doc, Node parentNode, String fieldName, String fieldValue) {
                NodeList nodeList = parentNode.getChildNodes();
                boolean fieldUpdated = false;

                for (int i = 0; i < nodeList.getLength(); i++) {
                        Node node = nodeList.item(i);
                        if (node.getNodeType() == Node.ELEMENT_NODE) {
                                if (node.getLocalName().equals(fieldName)) {
                                        node.setTextContent(fieldValue);
                                        fieldUpdated = true;
                                        break;
                                }
                                // Recursively update nested fields
                                updateNodeValue(doc, node, fieldName, fieldValue);
                        }
                }

                // If the field is not found, create it
                if (!fieldUpdated) {
                        Node newFieldNode = doc.createElement(fieldName);
                        newFieldNode.setTextContent(fieldValue);
                        parentNode.appendChild(newFieldNode);
                }
        }

}
