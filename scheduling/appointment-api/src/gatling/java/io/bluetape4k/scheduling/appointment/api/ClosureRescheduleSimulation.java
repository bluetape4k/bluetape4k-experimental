package io.bluetape4k.scheduling.appointment.api;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.DayOfWeek;
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
 * 휴진 일괄 재스케줄 스트레스 테스트.
 *
 * <p>시나리오:</p>
 * <ol>
 *   <li>각 가상 사용자가 고유 날짜에 예약 5건 생성</li>
 *   <li>해당 날짜로 closure reschedule 호출</li>
 *   <li>TP95 &lt; 1초 검증</li>
 * </ol>
 *
 * <p>실행 방법:</p>
 * <pre>
 *   ./gradlew :appointment-api:bootRun
 *   ./gradlew :appointment-api:gatlingRun --simulation io.bluetape4k.scheduling.appointment.api.ClosureRescheduleSimulation
 * </pre>
 */
public class ClosureRescheduleSimulation extends Simulation {

    private static final AtomicInteger userCounter = new AtomicInteger(0);

    private static String nextWeekday(int offset) {
        LocalDate base = LocalDate.now().plusDays(30);
        LocalDate date = base;
        int weekdayCount = 0;
        while (weekdayCount < offset) {
            date = date.plusDays(1);
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY
                    && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                weekdayCount++;
            }
        }
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY
                || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        return date.toString();
    }

    HttpProtocolBuilder httpProtocol = http
            .baseUrl("http://localhost:8080")
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    private io.gatling.javaapi.core.ChainBuilder createAppointment(int slotIndex) {
        return io.gatling.javaapi.core.CoreDsl.exec(
                http("Create Appointment " + slotIndex)
                        .post("/api/appointments")
                        .body(StringBody(session -> {
                            String date = session.getString("closureDate");
                            int userIdx = session.getInt("userIdx");
                            int hour = 9 + slotIndex;
                            int doctorId = 1 + (slotIndex % 3);
                            return "{\"clinicId\":1"
                                    + ",\"doctorId\":" + doctorId
                                    + ",\"treatmentTypeId\":1"
                                    + ",\"patientName\":\"Closure Patient " + userIdx + "-" + slotIndex + "\""
                                    + ",\"patientPhone\":\"010-2000-" + String.format("%04d", userIdx % 10000) + "\""
                                    + ",\"appointmentDate\":\"" + date + "\""
                                    + ",\"startTime\":\"" + String.format("%02d:00", hour) + "\""
                                    + ",\"endTime\":\"" + String.format("%02d:30", hour) + "\""
                                    + "}";
                        }))
                        .check(status().is(201))
                        .check(jsonPath("$.data.id").saveAs("lastApptId"))
        );
    }

    ScenarioBuilder closureReschedule = scenario("Closure Reschedule Stress")
            .exec(session -> {
                int idx = userCounter.incrementAndGet();
                String date = nextWeekday(idx);
                return session
                        .set("closureDate", date)
                        .set("userIdx", idx);
            })
            // 예약 5건 생성 (REQUESTED 상태 → closure reschedule 대상)
            .exec(createAppointment(0))
            .exec(createAppointment(1))
            .exec(createAppointment(2))
            .exec(createAppointment(3))
            .exec(createAppointment(4))
            .pause(Duration.ofMillis(100))
            // 휴진 일괄 재스케줄 (핵심 측정 대상)
            .exec(http("Closure Reschedule")
                    .post("/api/appointments/#{lastApptId}/reschedule/closure")
                    .queryParam("clinicId", "1")
                    .queryParam("closureDate", "#{closureDate}")
                    .queryParam("searchDays", "7")
                    .check(status().is(200))
                    .check(jsonPath("$.status").is("OK"))
            );

    {
        setUp(
                closureReschedule.injectOpen(rampUsers(50).during(Duration.ofSeconds(60)))
        ).protocols(httpProtocol)
                .assertions(
                        global().responseTime().percentile(95).lte(1000),
                        global().failedRequests().percent().lte(5.0)
                );
    }
}
