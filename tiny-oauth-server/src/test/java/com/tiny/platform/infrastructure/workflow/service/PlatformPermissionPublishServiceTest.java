package com.tiny.platform.infrastructure.workflow.service;

import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.workflow.model.ProcessModelScopeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformPermissionPublishServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private RoleRepository roleRepository;

    @Test
    void publishCreate_insertsPermissionAndReturnsBusinessResult() throws Exception {
        PlatformPermissionPublishService service = new PlatformPermissionPublishService(jdbcTemplate, roleRepository);
        AtomicInteger queryCount = new AtomicInteger();
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenAnswer(invocation -> {
                if (queryCount.incrementAndGet() == 1) {
                    return List.of();
                }
                ResultSet resultSet = mock(ResultSet.class);
                when(resultSet.getLong("id")).thenReturn(501L);
                when(resultSet.getString("permission_code")).thenReturn("workflow:platform:demo:start");
                RowMapper<?> rowMapper = invocation.getArgument(2);
                return List.of(rowMapper.mapRow(resultSet, 0));
            });

        Map<String, Object> result = service.publish(
            Map.of(
                "permissionCode",
                "workflow:platform:demo:start",
                "permissionName",
                "演示流程启动",
                "changeType",
                "CREATE",
                "impactScope",
                "平台流程权限矩阵"
            ),
            null,
            ProcessModelScopeType.PLATFORM,
            null
        );

        assertThat(result)
            .containsEntry("permissionId", 501L)
            .containsEntry("permissionCode", "workflow:platform:demo:start")
            .containsEntry("permissionName", "演示流程启动")
            .containsEntry("changeType", "CREATE")
            .containsEntry("enabled", true);
        verify(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class));
    }
}
