package io.myjobai.mail;

import io.myjobai.application.Ports;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.activation.DataHandler;
import jakarta.mail.util.ByteArrayDataSource;
import java.io.ByteArrayOutputStream;
import java.util.*;

final class MimeMessages {
    private MimeMessages(){}
    static byte[] build(Ports.ApprovedMail mail){
        try{
            var mime=new MimeMessage(Session.getInstance(new Properties()));mime.setFrom(new InternetAddress(mail.mailboxAddress()));mime.setRecipient(Message.RecipientType.TO,new InternetAddress(mail.version().recipientValue()));mime.setSubject(mail.version().subject(),"UTF-8");
            var multipart=new MimeMultipart("mixed");var text=new MimeBodyPart();text.setText(mail.version().body(),"UTF-8");multipart.addBodyPart(text);var attachment=new MimeBodyPart();attachment.setDataHandler(new DataHandler(new ByteArrayDataSource(mail.attachment(),"application/pdf")));attachment.setFileName("resume.pdf");multipart.addBodyPart(attachment);mime.setContent(multipart);mime.saveChanges();
            mime.setHeader("Message-ID","<"+mail.approval().id()+"@myjobai.local>");mime.setHeader("X-MyJobAI-Approval",mail.approval().id().toString());
            try(var output=new ByteArrayOutputStream()){mime.writeTo(output);return output.toByteArray();}
        }catch(Exception e){throw new IllegalStateException("Approved email could not be encoded",e);}
    }
}
