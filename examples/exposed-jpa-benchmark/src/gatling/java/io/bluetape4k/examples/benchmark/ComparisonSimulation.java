package io.bluetape4k.examples.benchmark;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Gatling simulation comparing Exposed vs JPA CRUD performance.
 *
 * <p>Run the application first with {@code ./gradlew :exposed-jpa-benchmark:bootRun},
 * then execute {@code ./gradlew :exposed-jpa-benchmark:gatlingRun} in a separate terminal.</p>
 */
public class ComparisonSimulation extends Simulation {

    private static final AtomicInteger exposedCounter = new AtomicInteger(0);
    private static final AtomicInteger jpaCounter = new AtomicInteger(0);

    HttpProtocolBuilder httpProtocol = http
            .baseUrl("http://localhost:8080")
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    // Exposed CRUD: POST → GET → PUT → DELETE
    ScenarioBuilder exposedCrud = scenario("Exposed CRUD")
            .exec(http("1-POST Exposed Author")
                    .post("/api/exposed/authors")
                    .body(io.gatling.javaapi.core.CoreDsl.StringBody(session -> {
                        int idx = exposedCounter.incrementAndGet();
                        long ts = System.nanoTime();
                        return "{\"name\":\"Exposed Author " + idx
                                + "\",\"email\":\"exposed" + idx + "-" + ts + "@bench.com\""
                                + ",\"books\":[{\"title\":\"Book " + idx
                                + "\",\"isbn\":\"E" + (ts % 1_000_000_000_000L)
                                + "\",\"price\":29.99}]}";
                    }))
                    .check(status().is(201))
                    .check(jsonPath("$.id").saveAs("exposedAuthorId")))
            .pause(Duration.ofMillis(30))
            .exec(http("2-GET Exposed Author")
                    .get("/api/exposed/authors/#{exposedAuthorId}")
                    .check(status().is(200)))
            .pause(Duration.ofMillis(30))
            .exec(http("3-PUT Exposed Author")
                    .put("/api/exposed/authors/#{exposedAuthorId}")
                    .body(io.gatling.javaapi.core.CoreDsl.StringBody(session -> {
                        int idx = exposedCounter.get();
                        long ts = System.nanoTime();
                        return "{\"name\":\"Updated Exposed " + idx
                                + "\",\"email\":\"exposed-upd" + idx + "-" + ts + "@bench.com\""
                                + ",\"books\":[]}";
                    }))
                    .check(status().is(200)))
            .pause(Duration.ofMillis(30))
            .exec(http("4-DELETE Exposed Author")
                    .delete("/api/exposed/authors/#{exposedAuthorId}")
                    .check(status().is(204)));

    // JPA CRUD: POST → GET → PUT → DELETE
    ScenarioBuilder jpaCrud = scenario("JPA CRUD")
            .exec(http("1-POST JPA Author")
                    .post("/api/jpa/authors")
                    .body(io.gatling.javaapi.core.CoreDsl.StringBody(session -> {
                        int idx = jpaCounter.incrementAndGet();
                        long ts = System.nanoTime();
                        return "{\"name\":\"JPA Author " + idx
                                + "\",\"email\":\"jpa" + idx + "-" + ts + "@bench.com\""
                                + ",\"books\":[{\"title\":\"Book " + idx
                                + "\",\"isbn\":\"J" + (ts % 1_000_000_000_000L)
                                + "\",\"price\":39.99}]}";
                    }))
                    .check(status().is(201))
                    .check(jsonPath("$.id").saveAs("jpaAuthorId")))
            .pause(Duration.ofMillis(30))
            .exec(http("2-GET JPA Author")
                    .get("/api/jpa/authors/#{jpaAuthorId}")
                    .check(status().is(200)))
            .pause(Duration.ofMillis(30))
            .exec(http("3-PUT JPA Author")
                    .put("/api/jpa/authors/#{jpaAuthorId}")
                    .body(io.gatling.javaapi.core.CoreDsl.StringBody(session -> {
                        int idx = jpaCounter.get();
                        long ts = System.nanoTime();
                        return "{\"name\":\"Updated JPA " + idx
                                + "\",\"email\":\"jpa-upd" + idx + "-" + ts + "@bench.com\""
                                + ",\"books\":[]}";
                    }))
                    .check(status().is(200)))
            .pause(Duration.ofMillis(30))
            .exec(http("4-DELETE JPA Author")
                    .delete("/api/jpa/authors/#{jpaAuthorId}")
                    .check(status().is(204)));

    // List 시나리오 (별도 측정)
    ScenarioBuilder exposedList = scenario("Exposed List")
            .exec(http("5-GET Exposed Authors List")
                    .get("/api/exposed/authors")
                    .check(status().is(200)));

    ScenarioBuilder jpaList = scenario("JPA List")
            .exec(http("5-GET JPA Authors List")
                    .get("/api/jpa/authors")
                    .check(status().is(200)));

    {
        setUp(
                exposedCrud.injectOpen(rampUsers(300).during(Duration.ofSeconds(60))),
                jpaCrud.injectOpen(rampUsers(300).during(Duration.ofSeconds(60))),
                exposedList.injectOpen(rampUsers(50).during(Duration.ofSeconds(60))),
                jpaList.injectOpen(rampUsers(50).during(Duration.ofSeconds(60)))
        ).protocols(httpProtocol)
                .assertions(global().failedRequests().percent().lte(1.0));
    }
}
