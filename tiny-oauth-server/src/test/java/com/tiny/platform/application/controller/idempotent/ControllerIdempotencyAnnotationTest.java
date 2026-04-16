package com.tiny.platform.application.controller.idempotent;

import com.tiny.platform.application.controller.dict.DictController;
import com.tiny.platform.application.controller.menu.MenuController;
import com.tiny.platform.application.controller.resource.ResourceController;
import com.tiny.platform.application.controller.role.RoleController;
import com.tiny.platform.application.controller.tenant.TenantController;
import com.tiny.platform.application.controller.user.UserController;
import com.tiny.platform.application.oauth.workflow.ProcessController;
import com.tiny.platform.core.dict.dto.DictItemCreateUpdateDto;
import com.tiny.platform.core.dict.dto.DictTypeCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.resource.dto.ResourceCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.role.dto.RoleCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.user.dto.UserCreateUpdateDto;
import com.tiny.platform.infrastructure.idempotent.sdk.annotation.Idempotent;
import com.tiny.platform.infrastructure.scheduling.controller.SchedulingController;
import com.tiny.platform.infrastructure.scheduling.dto.SchedulingDagCreateUpdateDto;
import com.tiny.platform.infrastructure.scheduling.dto.SchedulingDagEdgeCreateDto;
import com.tiny.platform.infrastructure.scheduling.dto.SchedulingDagTaskCreateUpdateDto;
import com.tiny.platform.infrastructure.scheduling.dto.SchedulingDagVersionCreateUpdateDto;
import com.tiny.platform.infrastructure.scheduling.dto.SchedulingTaskCreateUpdateDto;
import com.tiny.platform.infrastructure.scheduling.dto.SchedulingTaskTypeCreateUpdateDto;
import com.tiny.platform.infrastructure.tenant.dto.TenantCreateUpdateDto;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ControllerIdempotencyAnnotationTest {

    @Test
    void create_and_update_endpoints_should_require_idempotency() throws Exception {
        assertIdempotent(UserController.class.getDeclaredMethod("create", UserCreateUpdateDto.class));
        assertIdempotent(UserController.class.getDeclaredMethod("update", Long.class, UserCreateUpdateDto.class));
        assertIdempotent(UserController.class.getDeclaredMethod("delete", Long.class));
        assertIdempotent(UserController.class.getDeclaredMethod("batchEnable", java.util.List.class));
        assertIdempotent(UserController.class.getDeclaredMethod("batchDisable", java.util.List.class));
        assertIdempotent(UserController.class.getDeclaredMethod("batchDelete", java.util.List.class));
        assertIdempotent(UserController.class.getDeclaredMethod("updateUserRoles", Long.class, Object.class));

        assertIdempotent(RoleController.class.getDeclaredMethod("create", RoleCreateUpdateDto.class));
        assertIdempotent(RoleController.class.getDeclaredMethod("update", Long.class, RoleCreateUpdateDto.class));
        assertIdempotent(RoleController.class.getDeclaredMethod("delete", Long.class));
        assertIdempotent(RoleController.class.getDeclaredMethod("updateRoleUsers", Long.class, Object.class));
        assertIdempotent(RoleController.class.getDeclaredMethod("updateRoleResources", Long.class, Object.class));

        assertIdempotent(ResourceController.class.getDeclaredMethod("create", ResourceCreateUpdateDto.class));
        assertIdempotent(ResourceController.class.getDeclaredMethod("update", Long.class, ResourceCreateUpdateDto.class));
        assertIdempotent(ResourceController.class.getDeclaredMethod("delete", Long.class));
        assertIdempotent(ResourceController.class.getDeclaredMethod("batchDelete", java.util.List.class));
        assertIdempotent(ResourceController.class.getDeclaredMethod("updateSort", Long.class, Integer.class));

        assertIdempotent(MenuController.class.getDeclaredMethod("createMenu", ResourceCreateUpdateDto.class));
        assertIdempotent(MenuController.class.getDeclaredMethod("updateMenu", Long.class, ResourceCreateUpdateDto.class));
        assertIdempotent(MenuController.class.getDeclaredMethod("deleteMenu", Long.class));
        assertIdempotent(MenuController.class.getDeclaredMethod("batchDeleteMenus", java.util.List.class));

        assertIdempotent(DictController.class.getDeclaredMethod("createDictType", DictTypeCreateUpdateDto.class));
        assertIdempotent(DictController.class.getDeclaredMethod("updateDictType", Long.class, DictTypeCreateUpdateDto.class));
        assertIdempotent(DictController.class.getDeclaredMethod("deleteDictType", Long.class));
        assertIdempotent(DictController.class.getDeclaredMethod("batchDeleteDictTypes", java.util.List.class));
        assertIdempotent(DictController.class.getDeclaredMethod("createDictItem", DictItemCreateUpdateDto.class));
        assertIdempotent(DictController.class.getDeclaredMethod("updateDictItem", Long.class, DictItemCreateUpdateDto.class));
        assertIdempotent(DictController.class.getDeclaredMethod("deleteDictItem", Long.class));
        assertIdempotent(DictController.class.getDeclaredMethod("batchDeleteDictItems", java.util.List.class));

        assertIdempotent(TenantController.class.getDeclaredMethod("create", TenantCreateUpdateDto.class));
        assertIdempotent(TenantController.class.getDeclaredMethod("update", Long.class, TenantCreateUpdateDto.class));
        assertIdempotent(TenantController.class.getDeclaredMethod("delete", Long.class));

        assertIdempotent(SchedulingController.class.getDeclaredMethod("createTaskType", SchedulingTaskTypeCreateUpdateDto.class));
        assertIdempotent(SchedulingController.class.getDeclaredMethod("updateTaskType", Long.class, SchedulingTaskTypeCreateUpdateDto.class));
        assertIdempotent(SchedulingController.class.getDeclaredMethod("deleteTaskType", Long.class));
        assertIdempotent(SchedulingController.class.getDeclaredMethod("createTask", SchedulingTaskCreateUpdateDto.class));
        assertIdempotent(SchedulingController.class.getDeclaredMethod("updateTask", Long.class, SchedulingTaskCreateUpdateDto.class));
        assertIdempotent(SchedulingController.class.getDeclaredMethod("deleteTask", Long.class));
        assertIdempotent(SchedulingController.class.getDeclaredMethod("createDag", SchedulingDagCreateUpdateDto.class));
        assertIdempotent(SchedulingController.class.getDeclaredMethod("updateDag", Long.class, SchedulingDagCreateUpdateDto.class));
        assertIdempotent(SchedulingController.class.getDeclaredMethod("deleteDag", Long.class));
        assertIdempotent(SchedulingController.class.getDeclaredMethod("createDagVersion", Long.class, SchedulingDagVersionCreateUpdateDto.class));
        assertIdempotent(SchedulingController.class.getDeclaredMethod("updateDagVersion", Long.class, Long.class, SchedulingDagVersionCreateUpdateDto.class));
        assertIdempotent(SchedulingController.class.getDeclaredMethod("createDagNode", Long.class, Long.class, SchedulingDagTaskCreateUpdateDto.class));
        assertIdempotent(SchedulingController.class.getDeclaredMethod("updateDagNode", Long.class, Long.class, Long.class, SchedulingDagTaskCreateUpdateDto.class));
        assertIdempotent(SchedulingController.class.getDeclaredMethod("deleteDagNode", Long.class, Long.class, Long.class));
        assertIdempotent(SchedulingController.class.getDeclaredMethod("createDagEdge", Long.class, Long.class, SchedulingDagEdgeCreateDto.class));
        assertIdempotent(SchedulingController.class.getDeclaredMethod("deleteDagEdge", Long.class, Long.class, Long.class));
        assertIdempotent(SchedulingController.class.getDeclaredMethod("triggerDag", Long.class));
        assertIdempotent(SchedulingController.class.getDeclaredMethod("pauseDag", Long.class));
        assertIdempotent(SchedulingController.class.getDeclaredMethod("resumeDag", Long.class));
        assertIdempotent(SchedulingController.class.getDeclaredMethod("stopDag", Long.class));
        assertIdempotent(SchedulingController.class.getDeclaredMethod("retryDag", Long.class));
        assertIdempotent(SchedulingController.class.getDeclaredMethod("triggerNode", Long.class, Long.class));
        assertIdempotent(SchedulingController.class.getDeclaredMethod("retryNode", Long.class, Long.class));
        assertIdempotent(SchedulingController.class.getDeclaredMethod("pauseNode", Long.class, Long.class));
        assertIdempotent(SchedulingController.class.getDeclaredMethod("resumeNode", Long.class, Long.class));

        assertIdempotent(ProcessController.class.getDeclaredMethod("deploy", String.class));
        assertIdempotent(ProcessController.class.getDeclaredMethod("deployWithInfo", Map.class, Principal.class));
        assertIdempotent(ProcessController.class.getDeclaredMethod("deleteDeployment", String.class));
        assertIdempotent(ProcessController.class.getDeclaredMethod("start", String.class, Map.class));
        assertIdempotent(ProcessController.class.getDeclaredMethod("suspendInstance", String.class));
        assertIdempotent(ProcessController.class.getDeclaredMethod("activateInstance", String.class));
        assertIdempotent(ProcessController.class.getDeclaredMethod("deleteInstance", String.class));
        assertIdempotent(ProcessController.class.getDeclaredMethod("claimTask", String.class, String.class));
        assertIdempotent(ProcessController.class.getDeclaredMethod("completeTask", String.class, Map.class));
        assertIdempotent(ProcessController.class.getDeclaredMethod("createTenant", Map.class));
        assertIdempotent(ProcessController.class.getDeclaredMethod("deleteProcessDefinition", String.class));
    }

    private static void assertIdempotent(Method method) {
        Idempotent idempotent = method.getAnnotation(Idempotent.class);
        assertThat(idempotent)
            .as(method.toGenericString())
            .isNotNull();
        assertThat(idempotent.key()).isEqualTo("#request.getHeader('X-Idempotency-Key')");
        assertThat(idempotent.failOpen()).isFalse();
    }
}
