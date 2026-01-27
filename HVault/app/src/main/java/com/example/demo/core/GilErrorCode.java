package com.example.demo.core;

public enum GilErrorCode {
  // --- Core/Engine configuration & flow/rules/transformations ---
  CORE_CONFIG_INVALID,
  CORE_CONFIG_SCHEMA_INVALID,
  CORE_CONFIG_VERSION_UNSUPPORTED,
  CORE_FLOW_VALIDATION_FAILED,
  CORE_FLOW_EXECUTION_FAILED,
  CORE_STEP_EXECUTION_FAILED,
  CORE_MAPPING_FAILED,
  CORE_RULE_EVALUATION_FAILED,
  TRANSFORMATION_DEFINITION_INVALID,
  TRANSFORMATION_EXECUTION_ERROR,
  REGU_CONFIG_MISSING,
  REGU_CONFIG_INVALID,

  // ===== Configuration taxonomy =====
  CORE_CONFIG_MISSING,
  CORE_CONFIG_MALFORMED,
  CORE_CONFIG_REFERENCE_NOT_FOUND,
  CORE_CONFIG_CONSTRAINT_VIOLATION,

  CORE_CONFIG_FIELD_MISSING,
  CORE_CONFIG_FIELD_NULL,
  CORE_CONFIG_FIELD_INVALID_FORMAT,   // regex mismatch (topic name, API, localTimePointer, etc.)
  CORE_CONFIG_FIELD_INVALID_VALUE,    // not in allowed set (bank/bucket/direction/etc.)

  // ===== Flow & Data taxonomy =====
  CORE_PAYLOAD_INVALID,               // invalid payload / missing required fields / schema mismatch
  CORE_FLOW_ID_NOT_RESOLVABLE,        // no corresponding flow id
  CORE_ALIASING_FAILED,               // alias required fields failed
  CORE_TRANSFORMATION_FAILED,         // transformation execution failed

  UNKNOWN
}