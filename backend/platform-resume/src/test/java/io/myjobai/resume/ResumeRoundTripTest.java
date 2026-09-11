package io.myjobai.resume;

import static org.assertj.core.api.Assertions.*;

import io.myjobai.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ResumeRoundTripTest {
  @Test
  void renderBothFormatsWithExtractableTextAndStableBytes() throws Exception {
    var parser = new DocumentResumeParser();
    var renderer = new DeterministicResumeRenderer(parser);
    var resume =
        new Resume.Structured(
            new Candidate.Identity("Alice Engineer", "alice@example.com", "", "Bengaluru", null),
            List.of(
                new Resume.Section(
                    "Acme — Engineer",
                    List.of(
                        new Resume.Bullet(
                            UUID.fromString("00000000-0000-0000-0000-000000000001"),
                            "Built Java services handling 100 requests per second.")))),
            List.of("Java"),
            List.of(new Candidate.Education("State University", "Computer Science", "2016–2020")));
    var a = renderer.render(resume, "classic");
    var b = renderer.render(resume, "classic");
    assertThat(a.pdfText())
        .contains(
            "Alice Engineer",
            "100 requests per second",
            "Education",
            "State University",
            "2016–2020");
    assertThat(a.docxText())
        .contains(
            "Alice Engineer",
            "100 requests per second",
            "Education",
            "State University",
            "2016–2020");
    assertThat(a.pdf()).isEqualTo(b.pdf());
    assertThat(a.docx()).isEqualTo(b.docx());
    var artifacts =
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("target", "test-artifacts"));
    java.nio.file.Files.write(artifacts.resolve("resume.pdf"), a.pdf());
    java.nio.file.Files.write(artifacts.resolve("resume.docx"), a.docx());
    try (var pdf = org.apache.pdfbox.Loader.loadPDF(a.pdf())) {
      javax.imageio.ImageIO.write(
          new org.apache.pdfbox.rendering.PDFRenderer(pdf).renderImageWithDPI(0, 100),
          "png",
          artifacts.resolve("resume.png").toFile());
    }
  }

  @Test
  void rejectsSpoofedOrCorruptFiles() {
    var parser = new DocumentResumeParser();
    assertThatThrownBy(() -> parser.extract("not a pdf".getBytes(), "application/pdf"))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(
            () ->
                parser.extract(
                    "PKcorrupted".getBytes(),
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        .isInstanceOf(DomainException.class);
  }
}
