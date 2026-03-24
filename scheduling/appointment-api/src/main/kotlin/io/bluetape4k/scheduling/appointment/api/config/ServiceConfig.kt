package io.bluetape4k.scheduling.appointment.api.config

import io.bluetape4k.scheduling.appointment.repository.AppointmentRepository
import io.bluetape4k.scheduling.appointment.repository.ClinicRepository
import io.bluetape4k.scheduling.appointment.repository.DoctorRepository
import io.bluetape4k.scheduling.appointment.repository.HolidayRepository
import io.bluetape4k.scheduling.appointment.repository.RescheduleCandidateRepository
import io.bluetape4k.scheduling.appointment.repository.TreatmentTypeRepository
import io.bluetape4k.scheduling.appointment.service.ClosureRescheduleService
import io.bluetape4k.scheduling.appointment.service.SlotCalculationService
import io.bluetape4k.scheduling.appointment.statemachine.AppointmentStateMachine
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ServiceConfig {

    @Bean
    fun appointmentRepository(): AppointmentRepository = AppointmentRepository()

    @Bean
    fun clinicRepository(): ClinicRepository = ClinicRepository()

    @Bean
    fun doctorRepository(): DoctorRepository = DoctorRepository()

    @Bean
    fun treatmentTypeRepository(): TreatmentTypeRepository = TreatmentTypeRepository()

    @Bean
    fun holidayRepository(): HolidayRepository = HolidayRepository()

    @Bean
    fun rescheduleCandidateRepository(): RescheduleCandidateRepository = RescheduleCandidateRepository()

    @Bean
    fun slotCalculationService(
        clinicRepository: ClinicRepository,
        doctorRepository: DoctorRepository,
        treatmentTypeRepository: TreatmentTypeRepository,
        appointmentRepository: AppointmentRepository,
        holidayRepository: HolidayRepository,
    ): SlotCalculationService = SlotCalculationService(
        clinicRepository,
        doctorRepository,
        treatmentTypeRepository,
        appointmentRepository,
        holidayRepository,
    )

    @Bean
    fun closureRescheduleService(
        slotCalculationService: SlotCalculationService,
        appointmentRepository: AppointmentRepository,
        rescheduleCandidateRepository: RescheduleCandidateRepository,
    ): ClosureRescheduleService = ClosureRescheduleService(
        slotCalculationService,
        appointmentRepository,
        rescheduleCandidateRepository,
    )

    @Bean
    fun appointmentStateMachine(): AppointmentStateMachine = AppointmentStateMachine()
}
