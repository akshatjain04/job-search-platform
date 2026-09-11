package io.myjobai.resume;

import io.myjobai.application.Ports;
import io.myjobai.domain.DomainException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class DocumentResumeParser implements Ports.ResumeParser {
    static { ZipSecureFile.setMinInflateRatio(.01);ZipSecureFile.setMaxEntrySize(20*1024*1024);ZipSecureFile.setMaxTextSize(500000); }
    public String extract(byte[] bytes,String mediaType){
        if(bytes.length<4||bytes.length>10*1024*1024)throw DomainException.invalid("Resume file size is invalid");
        try{
            String result;
            if(mediaType.equals("application/pdf")){
                if(!new String(bytes,0,4,StandardCharsets.US_ASCII).equals("%PDF"))throw DomainException.invalid("File is not a PDF");
                try(var document=Loader.loadPDF(bytes)){if(document.isEncrypted()||document.getNumberOfPages()>30)throw DomainException.invalid("Encrypted resumes or resumes longer than 30 pages are unsupported");result=new PDFTextStripper().getText(document);}
            }else if(mediaType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")){
                if(bytes[0]!='P'||bytes[1]!='K')throw DomainException.invalid("File is not a DOCX");
                try(var document=new XWPFDocument(new ByteArrayInputStream(bytes));var extractor=new XWPFWordExtractor(document)){result=extractor.getText();}
            }else throw DomainException.invalid("Only PDF and DOCX are supported");
            result=result.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]","").strip();
            if(result.isBlank()||result.length()>100000)throw DomainException.invalid("No usable resume text, or extracted text exceeds 100000 characters");
            return result;
        }catch(DomainException e){throw e;}catch(Exception e){throw DomainException.invalid("Resume parsing failed: unsupported, corrupt or protected document");}
    }
}
