/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class OperateProfileService {

  public static final String SSO_AUTH_PROFILE = "sso-auth";
  public static final String IDENTITY_AUTH_PROFILE = "identity-auth";
  public static final String AUTH_BASIC = "auth-basic";
  public static final String AUTH_PROFILE = "auth";
  public static final String DEFAULT_AUTH = AUTH_PROFILE;
  public static final String LDAP_AUTH_PROFILE = "ldap-auth";
  public static final Set<String> AUTH_PROFILES =
      Set.of(AUTH_PROFILE, LDAP_AUTH_PROFILE, SSO_AUTH_PROFILE, IDENTITY_AUTH_PROFILE, AUTH_BASIC);

  private static final Set<String> CANT_LOGOUT_AUTH_PROFILES = Set.of(SSO_AUTH_PROFILE);

  @Autowired private Environment environment;

  public String getMessageByProfileFor(final Exception exception) {
    if (exception != null && isDevelopmentProfileActive()) {
      return exception.getMessage();
    }
    return "";
  }

  public boolean isDevelopmentProfileActive() {
    return List.of(environment.getActiveProfiles()).contains("dev");
  }

  public boolean isDevelopmentDataProfileActive() {
    return List.of(environment.getActiveProfiles()).contains("dev-data");
  }

  public boolean isSSOProfile() {
    return Arrays.asList(environment.getActiveProfiles()).contains(SSO_AUTH_PROFILE);
  }

  public boolean isIdentityProfile() {
    return Arrays.asList(environment.getActiveProfiles()).contains(IDENTITY_AUTH_PROFILE);
  }

  public boolean currentProfileCanLogout() {
    return Arrays.stream(environment.getActiveProfiles())
        .noneMatch(CANT_LOGOUT_AUTH_PROFILES::contains);
  }

  public boolean isLoginDelegated() {
    return isIdentityProfile() || isSSOProfile();
  }
}
