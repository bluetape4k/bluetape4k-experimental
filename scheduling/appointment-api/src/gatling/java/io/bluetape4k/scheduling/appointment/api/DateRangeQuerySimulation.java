package io.bluetape4k.scheduling.appointment.api;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.CoreDsl;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * 기간별 예약 조회 스트레스 테스트 (일주일 / 한달).
 *
 * <p>시나리오:</p>
 * <ol>
 *   <li>Setup: 각 유저가 예약 2건 생성 (4주 분산)</li>
 *   <li>Weekly Query: 일주일 단위 예약 조회 부하 (200 users / 60s)</li>
 *   <li>Monthly Query: 한달 단위 예약 조회 부하 (100 users / 60s)</li>
 * </ol>
 *
 * <p>실행 방법:</p>
 * <pre>
 *   ./gradlew :appointment-api:bootRun
 *   ./gradlew :appointment-api:gatlingRun --simulation io.bluetape4k.scheduling.appointment.api.DateRangeQuerySimulation
 * </pre>
 */
public class DateRangeQuerySimulation extends Simulation {

    private static final AtomicInteger counter = new AtomicInteger(0);
    private static final LocalDate BASE_DATE = LocalDate.now().plusDays(60);
    private static final String WEEK_START = BASE_DATE.toString();
    private static final String WEEK_END = BASE_DATE.plusDays(6).toString();
    private static final String MONTH_START = BASE_DATE.toString();
    private static final String MONTH_END = BASE_DATE.plusDays(29).toString();

    HttpProtocolBuilder httpProtocol = http
            .baseUrl("http://localhost:8080")
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    private ChainBuilder createSeedAppointment(int slotIndex) {
        return CoreDsl.exec(
                http("Seed Appointment " + slotIndex)
                        .post("/api/appointments")
                        .body(StringBody(session -> {
                            int idx = counter.incrementAndGet();
                            int dayOffset = idx % 28;
                            int hour = 9 + (idx % 8);
                            int doctorId = 1 + (idx % 3);
                            LocalDate date = BASE_DATE.plusDays(dayOffset);
                            return "{\"clinicId\":1"
                                    + ",\"doctorId\":" + doctorId
                                    + ",\"treatmentTypeId\":1"
                                    + ",\"patientName\":\"Query Patient " + idx + "\""
                                    + ",\"patientPhone\":\"010-3000-" + String.format("%04d", idx % 10000) + "\""
                                    + ",\"appointmentDate\":\"" + date + "\""
                                    + ",\"startTime\":\"" + String.format("%02d:00", hour) + "\""
                                    + ",\"endTime\":\"" + String.format("%02d:30", hour) + "\""
                                    + "}";
                        }))
                        .check(status().is(201))
        );
    }

    // 시나리오 1: 데이터 시드 + 일주일 조회
    ScenarioBuilder weeklyQuery = scenario("Weekly Query Stress")
            .exec(createSeedAppointment(0))
            .exec(createSeedAppointment(1))
            .pause(Duration.ofMillis(50))
            .exec(http("GET Weekly Appointments")
                    .get("/api/appointments")
                    .queryParam("clinicId", "1")
                    .queryParam("startDate", WEEK_START)
                    .queryParam("endDate", WEEK_END)
                    .check(status().is(200))
                    .check(jsonPath("$.status").is("OK"))
            );

    // 시나리오 2: 한달 조회
    ScenarioBuilder monthlyQuery = scenario("Monthly Query Stress")
            .exec(createSeedAppointment(0))
            .exec(createSeedAppointment(1))
            .pause(Duration.ofMillis(50))
            .exec(http("GET Monthly Appointments")
                    .get("/api/appointments")
                    .queryParam("clinicId", "1")
                    .queryParam("startDate", MONTH_START)
                    .queryParam("endDate", MONTH_END)
                    .check(status().is(200))
                    .check(jsonPath("$.status").is("OK"))
            );

    {
        setUp(
                weeklyQuery.injectOpen(rampUsers(200).during(Duration.ofSeconds(60))),
                monthlyQuery.injectOpen(rampUsers(100).during(Duration.ofSeconds(60)))
        ).protocols(httpProtocol)
                .assertions(
                        global().responseTime().percentile(95).lte(500),
                        global().failedRequests().percent().lte(5.0)
                );
    }
}
