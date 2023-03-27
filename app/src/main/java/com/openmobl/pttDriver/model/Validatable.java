package com.openmobl.pttDriver.model;

import java.util.List;
import java.util.Map;

public interface Validatable {
    boolean isValid();
    List<String> getValidationErrors();
    Map<String, List<String>> getAllValidationErrors();
}
