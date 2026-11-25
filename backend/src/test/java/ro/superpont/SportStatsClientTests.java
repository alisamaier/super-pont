package ro.superpont;

import static org.assertj.core.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import ro.superpont.football.sportstats.client.*;
import ro.superpont.football.sportstats.model.SportStatsProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SportStatsClientTests {
    private HttpServer server;
    private final JsonMapper json = JsonMapper.builder().build();
    private SportStatsClient client;
    private JsonNode fixture;
    private List<Map<String,String>> queries;
    private List<String> authorization;
    private AtomicInteger requests;
    private int responseStatus;
    private String responseBody, retryAfter;
    private boolean paged, slow;

    @BeforeEach
    void setup() throws Exception {
        try (var input = new ClassPathResource("sportstats/superliga-real-response.json").getInputStream()) { fixture = json.readTree(input); }
        queries = new CopyOnWriteArrayList<>(); authorization = new CopyOnWriteArrayList<>(); requests = new AtomicInteger();
        responseStatus=200; responseBody=fixture.toString(); retryAfter=null; paged=false; slow=false;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.setExecutor(Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory()));
        server.createContext("/matches", exchange -> {
            requests.incrementAndGet();
            var query = new HashMap<String,String>();
            for (String pair : exchange.getRequestURI().getRawQuery().split("&")) {
                var values = pair.split("=",2);
                query.put(URLDecoder.decode(values[0],StandardCharsets.UTF_8),URLDecoder.decode(values[1],StandardCharsets.UTF_8));
            }
            queries.add(query); authorization.add(exchange.getRequestHeaders().getFirst("Authorization"));
            try {
                if (slow) Thread.sleep(2200);
                String body=responseBody;
                if (paged) {
                    int offset=Integer.parseInt(query.get("offset"));
                    var rows=new ArrayList<JsonNode>();
                    for(int i=offset;i<Math.min(offset+8,16);i++) rows.add(fixture.get("data").get(i));
                    body=json.writeValueAsString(Map.of("success",true,"data",rows,"meta",Map.of("total",16,"limit",8,"offset",offset)));
                }
                if(retryAfter!=null) exchange.getResponseHeaders().set("Retry-After",retryAfter);
                exchange.getResponseHeaders().set("Location","/do-not-follow");
                var bytes=body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(responseStatus,bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch(Exception ignored) { /* The timeout test deliberately cancels its request. */ }
            finally { exchange.close(); }
        });
        server.start();
        client = client("offline-test-key",50,Duration.ofSeconds(5));
    }
    @AfterEach void close() { client.close(); server.stop(0); }
    private SportStatsClient client(String key,int pages,Duration timeout) {
        var properties = new SportStatsProperties(key,false,Duration.ofMinutes(30),Duration.ofSeconds(1),timeout,pages,7,14,7,1,"dx2=REGULAR");
        assertThat(properties.toString()).doesNotContain(key);
        return new SportStatsClient(properties,json,URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/matches"));
    }

    @Test void encodesParametersAndUsesBearerAuthenticationWhileFollowingProviderPagination() {
        paged=true;
        var result=client.fetch(LocalDate.parse("2026-11-15"),LocalDate.parse("2026-12-06"));
        assertThat(result.pages()).isEqualTo(2); assertThat(result.matches()).hasSize(16);
        assertThat(authorization).containsExactly("Bearer offline-test-key","Bearer offline-test-key");
        assertThat(queries).extracting(row->row.get("offset")).containsExactly("0","8");
        assertThat(queries).allSatisfy(row->assertThat(row).containsEntry("league_name","Romania: Superliga")
                .containsEntry("date_from","2026-11-15").containsEntry("date_to","2026-12-06").containsEntry("limit","100"));
    }

    @Test void handlesRateLimitAndRetryAfterWithoutRetryingOrRetainingProviderErrorBodies() {
        responseStatus=429; retryAfter="120"; responseBody="Authorization Bearer offline-test-key";
        Instant now=Instant.now();
        assertThatThrownBy(()->client.fetch(LocalDate.now(),LocalDate.now())).isInstanceOf(SportStatsException.class)
                .hasMessage("API_RATE_LIMITED").satisfies(error->{
                    assertThat(((SportStatsException)error).retryAfter()).isBetween(now.plusSeconds(119),Instant.now().plusSeconds(121));
                    assertThat(error.toString()).doesNotContain("offline-test-key");
                });
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test void refusesAutomaticPaymentAndAuthenticationRetriesAndRedirects() {
        for(var status : List.of(401,403,402,500,302)) {
            responseStatus=status;
            assertThatThrownBy(()->client.fetch(LocalDate.now(),LocalDate.now())).isInstanceOf(SportStatsException.class)
                    .hasMessage(status==401||status==403?"API_AUTHENTICATION":status==402?"PAYMENT_REQUIRED":"API_HTTP_ERROR");
        }
        assertThat(requests.get()).isEqualTo(5);
        responseStatus=200; responseBody="{\"success\":false,\"error\":{\"code\":\"PAYMENT_REQUIRED\",\"message\":\"never expose offline-test-key\"}}";
        assertThatThrownBy(()->client.fetch(LocalDate.now(),LocalDate.now())).hasMessage("PAYMENT_REQUIRED");
    }

    @Test void abortsInvalidOrRepeatingPagesAndEnforcesABoundedPageCount() {
        responseBody="{\"success\":true,\"data\":[],\"meta\":{\"total\":16,\"limit\":8,\"offset\":0}}";
        assertThatThrownBy(()->client.fetch(LocalDate.now(),LocalDate.now())).hasMessage("INCOMPLETE_API_PAGE");
        responseBody="not json";
        assertThatThrownBy(()->client.fetch(LocalDate.now(),LocalDate.now())).hasMessage("INVALID_API_JSON");
        paged=true;
        client.close(); client=client("offline-test-key",1,Duration.ofSeconds(5));
        assertThatThrownBy(()->client.fetch(LocalDate.now(),LocalDate.now())).hasMessage("API_PAGE_LIMIT");
    }

    @Test void timeoutsAreBoundedAndDoNotRetry() {
        slow=true;
        client.close(); client=client("offline-test-key",50,Duration.ofSeconds(1));
        var started=System.nanoTime();
        assertThatThrownBy(()->client.fetch(LocalDate.now(),LocalDate.now())).hasMessage("API_TIMEOUT");
        assertThat(Duration.ofNanos(System.nanoTime()-started)).isLessThan(Duration.ofSeconds(2));
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test void anEmptyPageIsValidAndAMissingKeySendsNoRequests() {
        responseBody="{\"success\":true,\"data\":[],\"meta\":{\"total\":0,\"limit\":0,\"offset\":0}}";
        assertThat(client.fetch(LocalDate.now(),LocalDate.now()).matches()).isEmpty();
        client.close();
        // Whitespace key is intentionally omitted from the assertion helper's toString check.
        var properties=new SportStatsProperties("",false,Duration.ofMinutes(30),Duration.ofSeconds(1),Duration.ofSeconds(1),50,7,14,7,1,"dx2=REGULAR");
        client=new SportStatsClient(properties,json,URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/matches"));
        assertThatThrownBy(()->client.fetch(LocalDate.now(),LocalDate.now())).hasMessage("API_KEY_MISSING");
        assertThat(requests.get()).isEqualTo(1);
    }
}
