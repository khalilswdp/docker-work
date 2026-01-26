package com.example.demo.data.validation.constraint;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Size;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(value={ElementType.FIELD})
@Retention(value= RetentionPolicy.RUNTIME)
@Constraint(validatedBy={})
@Size(max=20)
@ReportAsSingleViolation
public @interface InviteLimit {
    public String message() default "Limit exceeded";

    public Class<?>[] groups() default {};

    public Class<? extends Payload>[] payload() default {};
}