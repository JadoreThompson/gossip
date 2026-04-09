package com.zenz.gossip.route.api.request.validator;

import com.zenz.gossip.route.api.request.RequestType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class RequestTypeValidator implements ConstraintValidator<ValidRequestType, RequestType> {

    private RequestType expectedType;

    @Override
    public void initialize(ValidRequestType constraintAnnotation) {
        expectedType = constraintAnnotation.value();
    }

    @Override
    public boolean isValid(RequestType requestType, ConstraintValidatorContext context) {
        return requestType == expectedType;
    }
}