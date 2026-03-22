package io.bluetape4k.scheduling.appointment.api;

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
 * Appointment API 스트레스 테스트 시뮬레이션.
 *
 * <p>실행 방법:</p>
 * <ol>
 *   <li>앱 시작: {@code ./gradlew :appointment-api:bootRun}</li>
 *   <li>Gatling 실행: {@code ./gradlew :appointment-api:gatlingRun}</li>
 * </ol>
 *
 * <p>시나리오:</p>
 * <ul>
 *   <li>CRUD Flow: POST(생성) → GET(조회) → PATCH(상태변경) → DELETE(취소)</li>
 *   <li>Slot Query: 빈 슬롯 조회 부하 테스트</li>
 *   <li>Create Only: 예약 생성 쓰기 부하 테스트</li>
 * </ul>
 */
public class AppointmentApiSimulation extends Simulation {

    private static final AtomicInteger counter = new AtomicInteger(0);
    private static final String BASE_DATE = LocalDate.now().plusDays(7).toString();

    HttpProtocolBuilder httpProtocol = http
            .baseUrl("http://localhost:8080")
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    // 시나리오 1: 예약 CRUD 전체 흐름
    // POST → GET → PATCH(CONFIRMED) → DELETE(CANCEL)
    ScenarioBuilder appointmentCrud = scenario("Appointment CRUD")
            .exec(http("1-POST Create Appointment")
                    .post("/api/appointments")
                    .body(StringBody(session -> {
                        int idx = counter.incrementAndGet();
                        int hour = 9 + (idx % 9);
                        int minute = (idx % 2) * 30;
                        return "{\"clinicId\":1"
                                + ",\"doctorId\":1"
                                + ",\"treatmentTypeId\":1"
                                + ",\"patientName\":\"Patient " + idx + "\""
                                + ",\"patientPhone\":\"010-0000-" + String.format("%04d", idx % 10000) + "\""
                                + ",\"appointmentDate\":\"" + BASE_DATE + "\""
                                + ",\"startTime\":\"" + String.format("%02d:%02d", hour, minute) + "\""
                                + ",\"endTime\":\"" + String.format("%02d:%02d", hour, minute + 30) + "\""
                                + "}";
                    }))
                    .check(status().is(201))
                    .check(jsonPath("$.data.id").saveAs("appointmentId")))
            .pause(Duration.ofMillis(50))
            .exec(http("2-GET Appointment")
                    .get("/api/appointments/#{appointmentId}")
                    .check(status().is(200))
                    .check(jsonPath("$.data.id").exists()))
            .pause(Duration.ofMillis(50))
            .exec(http("3-PATCH Confirm Appointment")
                    .patch("/api/appointments/#{appointmentId}/status")
                    .body(StringBody("{\"status\":\"CONFIRMED\"}"))
                    .check(status().is(200))
                    .check(jsonPath("$.data.status").is("CONFIRMED")))
            .pause(Duration.ofMillis(50))
            .exec(http("4-DELETE Cancel Appointment")
                    .delete("/api/appointments/#{appointmentId}")
                    .check(status().is(200)));

    // 시나리오 2: 빈 슬롯 조회 (읽기 부하)
    ScenarioBuilder slotQuery = scenario("Slot Query")
            .exec(http("5-GET Available Slots")
                    .get("/api/clinics/1/slots")
                    .queryParam("doctorId", "1")
                    .queryParam("date", BASE_DATE)
                    .queryParam("treatmentTypeId", "1")
                    .check(status().is(200)));

    // 시나리오 3: 예약 생성만 집중 (쓰기 부하)
    ScenarioBuilder createOnly = scenario("Create Only")
            .exec(http("6-POST Create Appointment (burst)")
                    .post("/api/appointments")
                    .body(StringBody(session -> {
                        int idx = counter.incrementAndGet();
                        return "{\"clinicId\":1"
                                + ",\"doctorId\":1"
                                + ",\"treatmentTypeId\":1"
                                + ",\"patientName\":\"Burst Patient " + idx + "\""
                                + ",\"patientPhone\":\"010-1111-" + String.format("%04d", idx % 10000) + "\""
                                + ",\"appointmentDate\":\"" + BASE_DATE + "\""
                                + ",\"startTime\":\"09:00\""
                                + ",\"endTime\":\"09:30\""
                                + "}";
                    }))
                    .check(status().is(201)));

    {
        setUp(
                appointmentCrud.injectOpen(rampUsers(200).during(Duration.ofSeconds(60))),
                slotQuery.injectOpen(rampUsers(100).during(Duration.ofSeconds(60))),
                createOnly.injectOpen(rampUsers(100).during(Duration.ofSeconds(30)))
        ).protocols(httpProtocol)
                .assertions(global().failedRequests().percent().lte(5.0));
    }
}
