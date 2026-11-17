package com.riansares.r4r.usage;

import com.riansares.r4r.config.ClientNetwork;
import com.riansares.r4r.rag.AnswerStatus;
import com.riansares.r4r.rag.CitedRagService;
import com.riansares.r4r.rag.RagAuditResult;
import com.riansares.r4r.rag.RagProgress;
import com.riansares.r4r.rag.RagResult;
import com.riansares.r4r.rag.api.RagQueryController;
import com.riansares.r4r.rag.api.RagQueryResponse;
import java.nio.file.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real PostgreSQL, isolated fixture database only. No live LLM, private env or production database. */
@EnabledIfSystemProperty(named="r4r.test.usage-postgres.enabled", matches="true",
        disabledReason="NOT RUN: explicit isolated usage PostgreSQL fixture required")
class AssistantUsagePostgresIT {
    static JdbcTemplate jdbc;
    @BeforeAll static void database() throws Exception {
        String url = System.getProperty("r4r.test.usage-postgres.url", "");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/usage_network_check"))
            throw new IllegalArgumentException("Only the dedicated loopback usage_network_check fixture is permitted");
        var source = new DriverManagerDataSource(url + "?currentSchema=usage_network_check", "usage_check", "TEST_ONLY_USAGE_NETWORK");
        Path migrations = Files.createTempDirectory("usage-network-migrations-");
        try {
            for (String name : List.of("V5__assistant_usage.sql", "V6__assistant_history.sql", "V7__technical_usage_without_tracking.sql")) {
                Path file = Path.of("src/main/resources/db/migration", name);
                Files.copy(file, migrations.resolve(file.getFileName()));
            }
            Flyway.configure().dataSource(source).schemas("usage_network_check").defaultSchema("usage_network_check")
                    .locations("filesystem:" + migrations).load().migrate();
        } finally {
            try (var files = Files.list(migrations)) { for (Path file : files.toList()) Files.delete(file); }
            Files.delete(migrations);
        }
        jdbc = new JdbcTemplate(source);
    }
    private AssistantUsage usage() {
        var usage = new AssistantUsage(jdbc, true);
        usage.privacy(true, true);
        usage.network(new ClientNetwork("172.20.0.6", true), true);
        return usage;
    }
    private MockHttpServletRequest request(String origin, String ip, String country, UUID id) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("172.20.0.6");
        request.addHeader("Origin", "https://" + origin);
        request.addHeader("X-R4R-Request-Id", id.toString());
        if (ip != null) request.addHeader("CF-Connecting-IP", ip);
        if (country != null) request.addHeader("CF-IPCountry", country);
        return request;
    }
    private Map<String,Object> row(UUID id) {
        return jdbc.queryForMap("SELECT origin,host(client_ip) AS ip,masklen(client_ip) AS bits,country_code,network_basis,question,answer,model,generation_ms,effective_outcome,audit_result FROM assistant_usage WHERE id=?", id);
    }
    @ParameterizedTest
    @CsvSource({"athagon.tech,8.8.8.8,ES,ATHAGON,32", "riansares4r.com,2606:4700:4700::1111,IE,RIANSARES4R,128",
            "athagon.tech,2001:4860:4860::8888,GB,ATHAGON,128", "riansares4r.com,1.1.1.1,ES,RIANSARES4R,32"})
    void completeNetworkAndRagRecordReachPostgres(String origin, String ip, String country, String expectedOrigin, int bits) {
        UUID id=UUID.randomUUID(), auditId=UUID.randomUUID();
        var usage=usage();
        try {
            var ticket=usage.begin(request(origin,ip,country,id));
            ticket.question("How can I compare flooring materials?");
            ticket.response(new RagQueryResponse("Compare the documented properties.",false,List.of(),
                    new RagQueryResponse.Provider("fixture", "TEST_ONLY_MODEL", null),AnswerStatus.ANSWERED,auditId.toString()),12.5);
            usage.audit(auditId.toString(),new RagAuditResult(AnswerStatus.ANSWERED,AnswerStatus.PARTIAL,AnswerStatus.PARTIAL,true,"Verify suitability on site."));
        } finally { usage.close(); }
        var row=row(id);
        assertThat(row.get("origin")).isEqualTo(expectedOrigin);
        assertThat(row.get("bits")).isEqualTo(bits);
        assertThat(jdbc.queryForObject("SELECT client_ip=?::inet FROM assistant_usage WHERE id=?",Boolean.class,ip,id)).isTrue();
        assertThat(row.get("country_code")).isEqualTo(country);
        assertThat(row.get("network_basis")).isEqualTo("security-interest-v1");
        assertThat(row.get("question")).isEqualTo("How can I compare flooring materials?");
        assertThat(row.get("answer")).isEqualTo("Compare the documented properties.");
        assertThat(row.get("model")).isEqualTo("TEST_ONLY_MODEL");
        assertThat(row.get("generation_ms")).isEqualTo(12.5);
        assertThat(row.get("effective_outcome")).isEqualTo("PARTIAL");
        assertThat(row.get("audit_result")).isNotNull();
    }
    @Test void objectionSpoofingAndMissingMetadataAreStoredWithoutInventedNetworkData() {
        for (String scenario : List.of("objected", "untrusted", "missing", "unknown-origin")) {
            UUID id=UUID.randomUUID();
            var request=request(scenario.equals("unknown-origin")?"unknown.example":"athagon.tech","8.8.8.8","IE",id);
            if (scenario.equals("objected")) request.addHeader("X-R4R-Usage-Opt-Out","1");
            if (scenario.equals("untrusted")) request.setRemoteAddr("172.20.0.1");
            if (scenario.equals("missing")) { request.removeHeader("CF-Connecting-IP");request.removeHeader("CF-IPCountry"); }
            var usage=usage();
            try { var ticket=usage.begin(request); ticket.question("Flooring question"); ticket.finish("ABSTAINED"); }
            finally { usage.close(); }
            var row = scenario.equals("unknown-origin")
                    ? jdbc.queryForMap("SELECT origin,host(client_ip) AS ip,country_code,network_basis,question FROM assistant_usage WHERE origin='UNKNOWN' ORDER BY occurred_at DESC LIMIT 1")
                    : row(id);
            assertThat(row.get("ip")).isNull();
            assertThat(row.get("country_code")).isEqualTo(scenario.equals("objected")?"IE":null);
            assertThat(row.get("network_basis")).isEqualTo(scenario.equals("objected")?"technical-geography-v1":null);
            if (scenario.equals("objected") || scenario.equals("unknown-origin")) assertThat(row.get("question")).isNull();
            if (scenario.equals("unknown-origin")) assertThat(row.get("origin")).isEqualTo("UNKNOWN");
        }
    }
    @ParameterizedTest
    @CsvSource({"athagon.tech,8.8.8.8,ES,ATHAGON", "riansares4r.com,2606:4700:4700::1111,IE,RIANSARES4R"})
    void restQuestionIsRecordedByTheExistingController(String origin,String ip,String country,String expectedOrigin) throws Exception {
        UUID id=UUID.randomUUID();var usage=usage();
        var rag=mock(CitedRagService.class);
        when(rag.answer(anyString(),nullable(String.class),nullable(String.class),any(RagProgress.class)))
                .thenReturn(new RagResult("Fixture answer [S1].",false,List.of(new com.riansares.r4r.rag.Citation("[S1]","fixture.md",List.of(),0))));
        var controller=new RagQueryController(rag);
        ReflectionTestUtils.setField(controller,"usage",usage);
        try {
            MockMvcBuilders.standaloneSetup(controller).build().perform(post("/api/rag/answers")
                .with(r->{r.setRemoteAddr("172.20.0.6");return r;})
                .header("Origin","https://"+origin).header("X-R4R-Request-Id",id.toString())
                .header("CF-Connecting-IP",ip).header("CF-IPCountry",country)
                .contentType("application/json").content("{\"question\":\"How can I compare flooring?\",\"responseLanguage\":\"en\"}"))
                .andExpect(status().isOk());
        } finally { usage.close(); }
        var row=row(id);
        assertThat(row.get("origin")).isEqualTo(expectedOrigin);
        assertThat(row.get("ip")).isNotNull();
        assertThat(row.get("country_code")).isEqualTo(country);
        assertThat(row.get("answer")).isEqualTo("Fixture answer [S1].");
    }
}
