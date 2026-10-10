package nz.ac.auckland.se310.fairshare;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** #12: GET /groups/{id}/export over HTTP, through the real security chain. */
@Testcontainers
@SpringBootTest
class GroupExportEndpointTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    private static final String ALICE_EMAIL = "alice.export@test.com";
    private static final String BOB_EMAIL = "bob.export@test.com";
    private static final String CAROL_EMAIL = "carol.export@test.com";
    private static final String PASSWORD = "password123";

    @Autowired WebApplicationContext context;
    @Autowired UserRepository userRepository;
    @Autowired Clock clock;

    private MockMvcTester mvc;
    private MockHttpSession aliceSession;
    private MockHttpSession carolSession;
    private Number groupId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcTester.from(context, builder ->
                builder.apply(SecurityMockMvcConfigurers.springSecurity()).build());

        if (userRepository.findByEmailIgnoreCase(ALICE_EMAIL).isEmpty()) {
            register("alice_export", ALICE_EMAIL);
            register("bob_export", BOB_EMAIL);
            register("carol_export", CAROL_EMAIL);
        }
        aliceSession = login(ALICE_EMAIL);
        carolSession = login(CAROL_EMAIL);
        groupId = createGroup("Flat 3");
        assertThat(mvc.post().uri("/groups/" + groupId + "/members").session(aliceSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"%s\"}".formatted(BOB_EMAIL)))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void csvIsDownloadedAsUtf8CsvNamedAfterTheGroupAndDate() {
        MockHttpServletResponse response = export(groupId, "csv", aliceSession);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getContentType()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(attachmentFilename(response)).isEqualTo("fairshare-flat-3-" + LocalDate.now(clock) + ".csv");
        byte[] body = response.getContentAsByteArray();
        assertThat(Arrays.copyOf(body, 3)).containsExactly(0xEF, 0xBB, 0xBF);
        assertThat(new String(body, StandardCharsets.UTF_8))
                .isEqualTo("﻿Date,Description,Amount,Currency,Original Amount,Original Currency,Paid By,Split\r\n");
    }

    @Test
    void pdfIsDownloadedAsPdfNamedAfterTheGroupAndDate() {
        MockHttpServletResponse response = export(groupId, "pdf", aliceSession);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PDF_VALUE);
        assertThat(attachmentFilename(response)).isEqualTo("fairshare-flat-3-" + LocalDate.now(clock) + ".pdf");
        assertThat(new String(response.getContentAsByteArray(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void ac3_downloadIsNeverCached() {
        MockHttpServletResponse response = export(groupId, "csv", aliceSession);

        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
    }

    @Test
    void nonAsciiGroupNameSurvivesInTheFilename() {
        Number cafeGroupId = createGroup("Café Zoë");

        MockHttpServletResponse response = export(cafeGroupId, "csv", aliceSession);

        assertThat(response.getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment;");
        assertThat(attachmentFilename(response)).isEqualTo("fairshare-café-zoë-" + LocalDate.now(clock) + ".csv");
    }

    @Test
    void formatIsCaseInsensitive() {
        assertThat(export(groupId, "PDF", aliceSession).getContentType()).isEqualTo(MediaType.APPLICATION_PDF_VALUE);
    }

    @Test
    void invalidFormatIsA400() {
        assertThat(mvc.get().uri("/groups/" + groupId + "/export?format=xlsx").session(aliceSession))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.format").isEqualTo("Unsupported export format: xlsx. Use csv or pdf");
    }

    @Test
    void missingFormatIsA400() {
        assertThat(mvc.get().uri("/groups/" + groupId + "/export").session(aliceSession))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.format").isEqualTo("An export format is required: csv or pdf");
    }

    @Test
    void ac6_nonMemberGetsTheSameErrorAsOtherGroupEndpoints() {
        MockHttpServletResponse balances = mvc.get().uri("/groups/" + groupId + "/balances")
                .session(carolSession).exchange().getResponse();

        for (String format : new String[] {"csv", "pdf"}) {
            MockHttpServletResponse response = export(groupId, format, carolSession);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value()).isEqualTo(balances.getStatus());
            assertThat(contentAsString(response)).isEqualTo(contentAsString(balances));
        }
    }

    @Test
    void unauthenticatedRequestIsA401() {
        assertThat(mvc.get().uri("/groups/" + groupId + "/export?format=csv"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private MockHttpServletResponse export(Number id, String format, MockHttpSession session) {
        return mvc.get().uri("/groups/" + id + "/export?format=" + format).session(session).exchange().getResponse();
    }

    private static String attachmentFilename(MockHttpServletResponse response) {
        ContentDisposition disposition = ContentDisposition.parse(response.getHeader(HttpHeaders.CONTENT_DISPOSITION));
        assertThat(disposition.isAttachment()).isTrue();
        return disposition.getFilename();
    }

    private static String contentAsString(MockHttpServletResponse response) {
        return new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private Number createGroup(String name) {
        var created = mvc.post().uri("/groups").session(aliceSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\"}".formatted(name))
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        return JsonPath.read(contentAsString(created.getResponse()), "$.id");
    }

    private void register(String username, String email) {
        mvc.post().uri("/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"%s","email":"%s",
                         "country":"NEW_ZEALAND","currency":"NZD"}
                        """.formatted(username, PASSWORD, email))
                .exchange();
    }

    private MockHttpSession login(String email) {
        var result = mvc.post().uri("/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
                .exchange();

        assertThat(result).hasStatusOk();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
