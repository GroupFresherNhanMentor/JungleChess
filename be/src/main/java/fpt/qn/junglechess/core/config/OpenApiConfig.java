package fpt.qn.junglechess.core.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("HMS-IoT – Hospital Management System API")
                        .version("1.0.0")
                        .description("""
                                REST API documentation for HMS-IoT (Hospital Management System tích hợp IoT).
                                Kiến trúc Monolith Modular – Spring Boot 4.1 + jOOQ + PostgreSQL + HiveMQ.
                                """)
                        .contact(new Contact()
                                .name("Team 8 – FPT")
                                .email("team8@hospital.local")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Nhập JWT Access Token (không cần prefix 'Bearer ')")));
    }

    // ─── Module 1: Auth & User Management ────────────────────────────────────

    @Bean
    public GroupedOpenApi authApiGroup() {
        return GroupedOpenApi.builder()
                .group("1-auth")
                .displayName("1. Auth & User Management")
                .pathsToMatch("/api/auth/**", "/api/users/**")
                .packagesToScan("fpt.qn.junglechess.auth", "fpt.qn.junglechess.user")
                .build();
    }

    // ─── Module 2: Patient & Department ──────────────────────────────────────

    @Bean
    public GroupedOpenApi patientApiGroup() {
        return GroupedOpenApi.builder()
                .group("2-patient")
                .displayName("2. Patient & Department/Room/Bed")
                .pathsToMatch("/api/patients/**", "/api/admissions/**", "/api/departments/**",
                        "/api/rooms/**", "/api/beds/**")
                .packagesToScan("fpt.qn.junglechess.patient")
                .build();
    }

    // ─── Module 3: Medicine & Pharmacy ───────────────────────────────────────

    @Bean
    public GroupedOpenApi medicineApiGroup() {
        return GroupedOpenApi.builder()
                .group("3-medicine")
                .displayName("3. Medicine & Pharmacy")
                .pathsToMatch("/api/medicines/**", "/api/inventory/**")
                .packagesToScan("fpt.qn.junglechess.medicine")
                .build();
    }

    // ─── Module 4: Appointment & Staff ───────────────────────────────────────

    @Bean
    public GroupedOpenApi appointmentApiGroup() {
        return GroupedOpenApi.builder()
                .group("4-appointment")
                .displayName("4. Appointment, Queue & Staff/Shift")
                .pathsToMatch("/api/appointments/**", "/api/queues/**", "/api/staff/**", "/api/shifts/**")
                .packagesToScan("fpt.qn.junglechess.appointment")
                .build();
    }

    // ─── Module 5: Medical Record & Prescription ─────────────────────────────

    @Bean
    public GroupedOpenApi medicalRecordApiGroup() {
        return GroupedOpenApi.builder()
                .group("5-medical-record")
                .displayName("5. Medical Record & Prescription")
                .pathsToMatch("/api/medical-records/**", "/api/prescriptions/**")
                .packagesToScan("fpt.qn.junglechess.medicalrecord")
                .build();
    }

    // ─── Module 6: Lab & Diagnostics ─────────────────────────────────────────

    @Bean
    public GroupedOpenApi labApiGroup() {
        return GroupedOpenApi.builder()
                .group("6-lab")
                .displayName("6. Lab & Diagnostics (CLS/Xét nghiệm)")
                .pathsToMatch("/api/lab/**", "/api/lab-orders/**", "/api/lab-results/**")
                .packagesToScan("fpt.qn.junglechess.lab")
                .build();
    }

    // ─── Module 7: Alert & Clinical Rules ────────────────────────────────────

    @Bean
    public GroupedOpenApi alertApiGroup() {
        return GroupedOpenApi.builder()
                .group("7-alert")
                .displayName("7. Alert & Clinical Rule Engine")
                .pathsToMatch("/api/alerts/**", "/api/clinical-rules/**")
                .packagesToScan("fpt.qn.junglechess.alert")
                .build();
    }

    // ─── Module 8: Billing & Dashboard ───────────────────────────────────────

    @Bean
    public GroupedOpenApi billingApiGroup() {
        return GroupedOpenApi.builder()
                .group("8-billing")
                .displayName("8. Billing & Reporting Dashboard")
                .pathsToMatch("/api/invoices/**", "/api/billing/**", "/api/dashboard/**", "/api/reports/**")
                .packagesToScan("fpt.qn.junglechess.billing")
                .build();
    }
}
