package io.myjobai.resume;

import io.myjobai.domain.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ResumeRoundTripTest {
    @Test void renderBothFormatsWithExtractableTextAndStableBytes(){
        var parser=new DocumentResumeParser();var renderer=new DeterministicResumeRenderer(parser);
        var resume=new Resume.Structured(new Candidate.Identity("Alice Engineer","alice@example.com","","Bengaluru",null),List.of(new Resume.Section("Acme — Engineer",List.of(new Resume.Bullet(UUID.fromString("00000000-0000-0000-0000-000000000001"),"Built Java services handling 100 requests per second.")))),List.of("Java"));
        var a=renderer.render(resume,"classic");var b=renderer.render(resume,"classic");
        assertThat(a.pdfText()).contains("Alice Engineer","100 requests per second");assertThat(a.docxText()).contains("Alice Engineer","100 requests per second");assertThat(a.pdf()).isEqualTo(b.pdf());assertThat(a.docx()).isEqualTo(b.docx());
    }
    @Test void rejectsSpoofedOrCorruptFiles(){var parser=new DocumentResumeParser();assertThatThrownBy(()->parser.extract("not a pdf".getBytes(),"application/pdf")).isInstanceOf(DomainException.class);assertThatThrownBy(()->parser.extract("PKcorrupted".getBytes(),"application/vnd.openxmlformats-officedocument.wordprocessingml.document")).isInstanceOf(DomainException.class);}
}
