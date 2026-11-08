package com.riansares.r4r.vector.s3vectors;

import com.riansares.r4r.chunking.MarkdownChunk;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.document.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class S3VectorMetadataMapperTest {
    private final S3VectorMetadataMapper mapper = new S3VectorMetadataMapper();

    @Test void roundTripPreservesExactTextHeadingOrderSourceAndOrdinal() {
        var chunk = new MarkdownChunk("faqs/guía.md", List.of("Reformas", "Baños"), 4, "## Baños\nInformación exacta.\n");
        var encoded = mapper.toMetadata(chunk);
        assertThat(encoded).containsOnlyKeys("source", "headingPath", "ordinal", "source_text");
        assertThat(mapper.fromMetadata(encoded)).isEqualTo(chunk);
    }

    @Test void decodesRealSdkDocumentIncludingNumericOrdinalAndEmptyHeadings() {
        var document = Document.fromMap(Map.of("source", Document.fromString("a.md"),
                "headingPath", Document.fromList(List.of()), "ordinal", Document.fromNumber(0),
                "source_text", Document.fromString("Texto.")));
        assertThat(mapper.fromDocument(document)).isEqualTo(new MarkdownChunk("a.md", List.of(), 0, "Texto."));
    }

    @Test void absentHeadingPathDecodesAsEmptyForMapAndSdkDocument() {
        var metadata = valid();
        metadata.remove("headingPath");
        assertThat(mapper.fromMetadata(metadata))
                .isEqualTo(new MarkdownChunk("a.md", List.of(), 1, "Evidence."));
        var document = Document.fromMap(Map.of("source", Document.fromString("a.md"),
                "ordinal", Document.fromNumber(1), "source_text", Document.fromString("Evidence.")));
        assertThat(mapper.fromDocument(document)).isEqualTo(mapper.fromMetadata(metadata));
    }

    @Test void emptyHeadingPathIsOmittedWhenEncodingAndRoundTrips() {
        var chunk = new MarkdownChunk("a.md", List.of(), 1, "Evidence.");
        var encoded = mapper.toMetadata(chunk);
        assertThat(encoded).containsOnlyKeys("source", "ordinal", "source_text")
                .doesNotContainKey("headingPath");
        assertThat(mapper.fromMetadata(encoded)).isEqualTo(chunk);
    }

    @Test void missingFieldsAndWrongTypesNeverCreateInventedMetadata() {
        for (String key : List.of("source", "ordinal", "source_text")) {
            var metadata = valid();
            metadata.remove(key);
            assertThatThrownBy(() -> mapper.fromMetadata(metadata)).isInstanceOf(IllegalStateException.class);
        }
        for (Object value : List.of("1", -1, .5, Double.NaN, Double.POSITIVE_INFINITY, Long.MAX_VALUE)) {
            var metadata = valid();
            metadata.put("ordinal", value);
            assertThatThrownBy(() -> mapper.fromMetadata(metadata)).isInstanceOf(IllegalStateException.class);
        }
        for (Object headings : new Object[]{null, "Section", List.of(1)}) {
            var metadata = valid(); metadata.put("headingPath", headings);
            assertThatThrownBy(() -> mapper.fromMetadata(metadata)).isInstanceOf(IllegalStateException.class);
        }
        for (String field : List.of("source", "source_text")) {
            var metadata = valid(); metadata.put(field, "   ");
            assertThatThrownBy(() -> mapper.fromMetadata(metadata)).isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(() -> mapper.fromMetadata(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> mapper.fromDocument(Document.fromList(List.of()))).isInstanceOf(IllegalStateException.class);
    }

    @Test void budgetsCountUtf8AndIncludeFilterableHeadingsButExcludeSourceText() {
        var metadata = valid();
        metadata.put("source_text", "á".repeat(2500)); // Larger than the filterable budget, still valid.
        assertThat(mapper.fromMetadata(metadata).content()).hasSize(2500);
        metadata.put("headingPath", List.of("á".repeat(1100)));
        assertThatThrownBy(() -> mapper.fromMetadata(metadata)).hasMessageContaining("filterable metadata");
        var oversized = valid(); oversized.put("source_text", "x".repeat(41 * 1024));
        assertThatThrownBy(() -> mapper.fromMetadata(oversized)).hasMessageContaining("total metadata");
    }

    @Test void rawHeadingRequiresExplicitManualVerificationAndExpansionIsNotTruncated() {
        var metadata = valid(); metadata.put("source_text", "## Presupuesto");
        assertThatThrownBy(() -> mapper.fromMetadata(metadata)).hasMessageContaining("manual PG context expansion");
        metadata.put("r4r_context", S3VectorMetadataMapper.HEADING_CONTEXT_VERSION);
        assertThat(mapper.fromMetadata(metadata).content()).isEqualTo("## Presupuesto");
        metadata.remove("r4r_context");
        metadata.put("source_text", "## Presupuesto\n\n### FAQ\nUbicación y medidas.");
        assertThat(mapper.fromMetadata(metadata).content()).isEqualTo(metadata.get("source_text"));
    }

    private static Map<String, Object> valid() {
        return new HashMap<>(Map.of("source", "a.md", "headingPath", List.of("Section"), "ordinal", 1, "source_text", "Evidence."));
    }
}
