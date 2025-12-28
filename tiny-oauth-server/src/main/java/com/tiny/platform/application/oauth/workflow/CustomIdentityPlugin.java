package com.tiny.platform.application.oauth.workflow;

import com.tiny.platform.infrastructure.auth.role.service.RoleService;
import com.tiny.platform.infrastructure.auth.user.service.UserService;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.ProcessEnginePlugin;

public class CustomIdentityPlugin implements ProcessEnginePlugin {

    private final UserService userService;
    private final RoleService roleService;

    public CustomIdentityPlugin(UserService userService, RoleService roleService) {
        this.userService = userService;
        this.roleService = roleService;
    }

    @Override
    public void preInit(ProcessEngineConfigurationImpl processEngineConfiguration) {
        // 不使用数据库身份表
        processEngineConfiguration.setDbIdentityUsed(false);
        // 注册只读身份提供者工厂
        var factories = processEngineConfiguration.getCustomSessionFactories();
        if (factories == null) {
            factories = new java.util.ArrayList<>();
            processEngineConfiguration.setCustomSessionFactories(factories);
        }
        factories.add(new CamundaIdentityProviderSessionFactory(userService, roleService));
    }

    @Override
    public void postInit(ProcessEngineConfigurationImpl processEngineConfiguration) {}

    @Override
    public void postProcessEngineBuild(org.camunda.bpm.engine.ProcessEngine processEngine) {}
}