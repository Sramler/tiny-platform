package com.tiny.platform.application.oauth.workflow;

import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.service.RoleService;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.service.UserService;
import org.camunda.bpm.engine.identity.Group;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CamundaIdentityProviderTest {

    @Test
    void findGroupsByUser_should_use_assignment_backed_role_ids() {
        UserService userService = mock(UserService.class);
        RoleService roleService = mock(RoleService.class);
        CamundaIdentityProvider provider = new CamundaIdentityProvider(userService, roleService);

        User user = new User();
        user.setId(7L);
        user.setUsername("alice");

        Role role = new Role();
        role.setId(100L);
        role.setName("审批管理员");

        when(userService.findById(7L)).thenReturn(Optional.of(user));
        when(userService.getRoleIdsByUserId(7L)).thenReturn(List.of(100L));
        when(roleService.findById(100L)).thenReturn(Optional.of(role));

        List<Group> groups = provider.findGroupsByUser("7");

        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().getId()).isEqualTo("100");
        assertThat(groups.getFirst().getName()).isEqualTo("审批管理员");
        verify(userService).getRoleIdsByUserId(7L);
    }
}
