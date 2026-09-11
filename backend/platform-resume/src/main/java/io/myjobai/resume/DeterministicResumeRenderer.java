package io.myjobai.resume;

import io.myjobai.application.Ports;
import io.myjobai.domain.*;
import java.io.*;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.poi.xwpf.usermodel.*;

public final class DeterministicResumeRenderer implements Ports.ResumeRenderer {
  private final DocumentResumeParser parser;

  public DeterministicResumeRenderer(DocumentResumeParser parser) {
    this.parser = parser;
  }

  private record Line(String text, int size, boolean bold) {}

  public Ports.Rendered render(Resume.Structured resume, String template) {
    if (!Set.of("classic", "compact").contains(template))
      throw DomainException.invalid("Resume template must be classic or compact");
    var lines = new ArrayList<Line>();
    lines.add(new Line(resume.identity().name(), 18, true));
    lines.add(
        new Line(resume.identity().email() + " | " + resume.identity().location(), 10, false));
    if (!resume.identity().phone().isBlank())
      lines.add(new Line(resume.identity().phone(), 10, false));
    for (String link : resume.identity().links()) lines.add(new Line(link, 10, false));
    lines.add(new Line("Skills", 12, true));
    lines.add(new Line(String.join(", ", resume.skills()), 10, false));
    for (var section : resume.sections()) {
      lines.add(new Line(section.heading(), 12, true));
      for (var bullet : section.bullets()) lines.add(new Line("- " + bullet.text(), 10, false));
    }
    if (!resume.education().isEmpty()) {
      lines.add(new Line("Education", 12, true));
      for (var education : resume.education()) {
        lines.add(new Line(education.institution() + " — " + education.qualification(), 10, false));
        if (!education.dates().isBlank()) lines.add(new Line(education.dates(), 10, false));
      }
    }
    try {
      byte[] pdf = pdf(lines, template.equals("compact") ? 13 : 15);
      byte[] docx = docx(lines);
      return new Ports.Rendered(
          pdf,
          docx,
          parser.extract(pdf, "application/pdf"),
          parser.extract(
              docx, "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    } catch (DomainException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Resume rendering failed", e);
    }
  }

  private byte[] pdf(List<Line> lines, int leading) throws IOException {
    try (var document = new PDDocument();
        var output = new ByteArrayOutputStream()) {
      // Bundled font assets are provided by the runtime image; tests use an explicitly configured
      // local font.
      String fontPath =
          System.getProperty(
              "myjobai.resume.font",
              System.getenv()
                  .getOrDefault(
                      "RESUME_FONT_PATH", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"));
      PDFont font;
      try (var input = new FileInputStream(fontPath)) {
        font = PDType0Font.load(document, input);
      }
      PDPageContentStream stream = null;
      float y = 0;
      try {
        for (var line : lines) {
          for (String text : wrap(line.text(), font, line.size(), 500)) {
            if (stream == null || y < 55) {
              if (stream != null) stream.close();
              var page = new PDPage(PDRectangle.A4);
              document.addPage(page);
              stream = new PDPageContentStream(document, page);
              y = 790;
            }
            if (line.bold()) y -= 6;
            stream.beginText();
            stream.setFont(font, line.size());
            stream.newLineAtOffset(48, y);
            stream.showText(text);
            stream.endText();
            y -= Math.max(leading, line.size() + 3);
          }
        }
      } finally {
        if (stream != null) stream.close();
      }
      var info = document.getDocumentInformation();
      info.setProducer("MyJobAI deterministic renderer");
      var date = GregorianCalendar.from(Instant.EPOCH.atZone(java.time.ZoneOffset.UTC));
      info.setCreationDate(date);
      info.setModificationDate(date);
      var id = new org.apache.pdfbox.cos.COSArray();
      var hash = Normalization.hash(lines.toString());
      id.add(new org.apache.pdfbox.cos.COSString(hash));
      id.add(new org.apache.pdfbox.cos.COSString(hash));
      document.getDocument().setDocumentID(id);
      document.save(output);
      return output.toByteArray();
    }
  }

  private static List<String> wrap(String text, PDFont font, int size, float width)
      throws IOException {
    var result = new ArrayList<String>();
    var current = new StringBuilder();
    for (String word : text.replaceAll("[\\r\\n\\t]+", " ").split(" +")) {
      if (font.getStringWidth(word) / 1000 * size > width) {
        if (!current.isEmpty()) {
          result.add(current.toString());
          current.setLength(0);
        }
        StringBuilder chunk = new StringBuilder();
        for (int cp : word.codePoints().toArray()) {
          String next = new String(Character.toChars(cp));
          if (font.getStringWidth(chunk + next) / 1000 * size > width) {
            result.add(chunk.toString());
            chunk.setLength(0);
          }
          chunk.append(next);
        }
        current.append(chunk);
        continue;
      }
      String next = current.isEmpty() ? word : current + " " + word;
      if (font.getStringWidth(next) / 1000 * size > width) {
        result.add(current.toString());
        current.setLength(0);
        current.append(word);
      } else {
        if (!current.isEmpty()) current.append(' ');
        current.append(word);
      }
    }
    if (!current.isEmpty()) result.add(current.toString());
    return result;
  }

  private static byte[] docx(List<Line> lines) throws Exception {
    byte[] raw;
    try (var document = new XWPFDocument();
        var output = new ByteArrayOutputStream()) {
      var section = document.getDocument().getBody().addNewSectPr();
      var margin = section.addNewPgMar();
      margin.setLeft(BigInteger.valueOf(720));
      margin.setRight(BigInteger.valueOf(720));
      margin.setTop(BigInteger.valueOf(720));
      margin.setBottom(BigInteger.valueOf(720));
      for (var line : lines) {
        var p = document.createParagraph();
        p.setSpacingAfter(line.bold() ? 100 : 60);
        p.setKeepNext(line.bold());
        var run = p.createRun();
        run.setFontFamily("DejaVu Sans");
        run.setFontSize(line.size());
        run.setBold(line.bold());
        run.setText(line.text());
      }
      var props = document.getProperties().getCoreProperties();
      props.setCreator("MyJobAI");
      props.setCreated("1970-01-01T00:00:00Z");
      props.setModified("1970-01-01T00:00:00Z");
      document.write(output);
      raw = output.toByteArray();
    }
    // ZIP timestamps and entry ordering otherwise vary across render calls.
    var entries = new TreeMap<String, byte[]>();
    try (var zip = new ZipInputStream(new ByteArrayInputStream(raw))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) entries.put(entry.getName(), zip.readAllBytes());
    }
    try (var output = new ByteArrayOutputStream();
        var zip = new ZipOutputStream(output)) {
      for (var entry : entries.entrySet()) {
        var item = new ZipEntry(entry.getKey());
        item.setTime(0);
        zip.putNextEntry(item);
        zip.write(entry.getValue());
        zip.closeEntry();
      }
      zip.finish();
      return output.toByteArray();
    }
  }
}
