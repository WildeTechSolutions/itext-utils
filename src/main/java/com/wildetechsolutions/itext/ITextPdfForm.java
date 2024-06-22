package com.wildetechsolutions.itext;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.forms.xfa.XfaForm;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ITextPdfForm {

    private Logger log = LoggerFactory.getLogger(ITextPdfForm.class);

    private PdfDocument pdf;
    private PdfAcroForm form;
    private Map<String, PdfFormField> fields;

    public ITextPdfForm(String src, String dest) throws IOException{
        PdfReader reader = new PdfReader(src);

        pdf = new PdfDocument(reader, new PdfWriter(dest));
        form = PdfAcroForm.getAcroForm(pdf, true);
        fields = form.getAllFormFields();

    }

    public void setFieldValue(String fieldName, String value){
        PdfFormField formField = form.getField(fieldName);

        if(formField != null){
            formField.setValue(value);
        }else{
            log.warn("Did not find field {}", fieldName);
        }
    }

    public void commit(){
        if(pdf != null){
            pdf.close();
        }
    }

    public Map<String, PdfFormField> getFields() {
        return fields;
    }

    public void setFields(Map<String, PdfFormField> fields) {
        this.fields = fields;
    }

    public Map<String, String> getFormFieldValues(Path docPath) throws IOException {
        XfaForm xfa = new XfaForm(pdf);

        Map<String, String> fieldValues = new HashMap<>();
        if (xfa.isXfaPresent()) {
            Node datasetsNode = xfa.getDatasetsNode();
            NodeList fieldNodes = datasetsNode.getChildNodes();

            for (int i = 0; i < fieldNodes.getLength(); i++) {
                Node fieldNode = fieldNodes.item(i);
                String fieldName = fieldNode.getLocalName();
                String fieldValue = fieldNode.getTextContent();
                fieldValues.put(fieldName, fieldValue);
            }
        }

        pdf.close();

        return fieldValues;
    }
}
