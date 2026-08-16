package com.matrixlive.security.auth;

/** Roles are deliberately small and are evaluated with an activity scope where applicable. */
public enum UserRole {
  SYSTEM_ADMIN,
  ACTIVITY_ADMIN,
  STAFF,
  PARTICIPANT
}
