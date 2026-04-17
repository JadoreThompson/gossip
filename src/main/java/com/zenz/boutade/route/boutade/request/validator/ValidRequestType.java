package com.zenz.boutade.route.boutade.request.validator;

import com.zenz.boutade.route.boutade.request.RequestType;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;


@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = RequestTypeValidator.class)
public @interface ValidRequestType {

    RequestType value();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
