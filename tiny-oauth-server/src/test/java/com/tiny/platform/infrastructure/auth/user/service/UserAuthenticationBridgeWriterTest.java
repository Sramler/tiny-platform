package com.tiny.platform.infrastructure.auth.user.service;

import com.tiny.platform.infrastructure.auth.user.domain.UserAuthCredential;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthScopePolicy;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthCredentialRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthScopePolicyRepository;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("null")
class UserAuthenticationBridgeWriterTest {

  @Test
  void buildScopeKey_should_follow_single_contract() {
    assertThat(UserAuthenticationBridgeWriter.buildScopeKey("GLOBAL", null)).isEqualTo("GLOBAL");
    assertThat(UserAuthenticationBridgeWriter.buildScopeKey("PLATFORM", null)).isEqualTo("PLATFORM");
    assertThat(UserAuthenticationBridgeWriter.buildScopeKey("TENANT", 9L)).isEqualTo("TENANT:9");
  }

  @Test
  void upsert_should_write_credential_and_scope_policy() {
    UserAuthCredentialRepository credentialRepository = mock(UserAuthCredentialRepository.class);
    UserAuthScopePolicyRepository scopePolicyRepository = mock(UserAuthScopePolicyRepository.class);
    UserAuthenticationBridgeWriter writer =
        new UserAuthenticationBridgeWriter(credentialRepository, scopePolicyRepository);

    UserAuthCredential existing = new UserAuthCredential();
    existing.setId(10L);
    when(credentialRepository.findByUserIdAndAuthenticationProviderAndAuthenticationType(1L, "LOCAL", "PASSWORD"))
        .thenReturn(Optional.of(existing));
    when(scopePolicyRepository.findByCredentialIdAndScopeKey(10L, "TENANT:9"))
        .thenReturn(Optional.empty());
    when(credentialRepository.save(any(UserAuthCredential.class)))
        .thenAnswer(invocation -> Objects.requireNonNull(invocation.<UserAuthCredential>getArgument(0)));
    when(scopePolicyRepository.save(any(UserAuthScopePolicy.class)))
        .thenAnswer(invocation -> Objects.requireNonNull(invocation.<UserAuthScopePolicy>getArgument(0)));

    writer.upsert(
        1L,
        "LOCAL",
        "PASSWORD",
        Map.of("password", "{bcrypt}x"),
        null,
        null,
        null,
        "TENANT",
        9L,
        true,
        true,
        0
    );

    verify(credentialRepository).save(notNull());
    verify(scopePolicyRepository).save(notNull());
  }

  @Test
  void deleteScope_should_delete_credential_when_no_policy_left() {
    UserAuthCredentialRepository credentialRepository = mock(UserAuthCredentialRepository.class);
    UserAuthScopePolicyRepository scopePolicyRepository = mock(UserAuthScopePolicyRepository.class);
    UserAuthenticationBridgeWriter writer =
        new UserAuthenticationBridgeWriter(credentialRepository, scopePolicyRepository);

    UserAuthCredential credential = new UserAuthCredential();
    credential.setId(20L);
    when(credentialRepository.findByUserIdAndAuthenticationProviderAndAuthenticationType(1L, "LOCAL", "MFA_REMIND"))
        .thenReturn(Optional.of(credential));
    when(scopePolicyRepository.countByCredentialId(20L)).thenReturn(0L);

    writer.deleteScope(1L, "LOCAL", "MFA_REMIND", "TENANT", 9L);

    verify(scopePolicyRepository).deleteByCredentialIdAndScopeKey(20L, "TENANT:9");
    verify(credentialRepository).deleteById(20L);
  }

  @Test
  void deleteScope_should_keep_credential_when_policy_still_exists() {
    UserAuthCredentialRepository credentialRepository = mock(UserAuthCredentialRepository.class);
    UserAuthScopePolicyRepository scopePolicyRepository = mock(UserAuthScopePolicyRepository.class);
    UserAuthenticationBridgeWriter writer =
        new UserAuthenticationBridgeWriter(credentialRepository, scopePolicyRepository);

    UserAuthCredential credential = new UserAuthCredential();
    credential.setId(21L);
    when(credentialRepository.findByUserIdAndAuthenticationProviderAndAuthenticationType(1L, "LOCAL", "MFA_REMIND"))
        .thenReturn(Optional.of(credential));
    when(scopePolicyRepository.countByCredentialId(21L)).thenReturn(1L);

    writer.deleteScope(1L, "LOCAL", "MFA_REMIND", "PLATFORM", null);

    verify(scopePolicyRepository).deleteByCredentialIdAndScopeKey(21L, "PLATFORM");
    verify(credentialRepository, never()).deleteById(eq(21L));
  }
}
