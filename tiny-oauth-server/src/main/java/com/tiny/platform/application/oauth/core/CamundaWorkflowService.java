//package com.tiny.oauthserver.core;
//
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.stereotype.Service;
//
//import java.util.Map;
//
////@Service
////@ConditionalOnProperty(name = "workflow.engine", havingValue = "camunda")
//public class CamundaWorkflowService implements WorkflowService {
//    @Override
//    public String startProcess(String key, Map<String, Object> vars) {
//        return "";
//    }
//
//    @Override
//    public String getCurrentTask(String processInstanceId) {
//        return "";
//    }
//
//    @Override
//    public void completeTask(String taskId) {
//
//    }
//    // 调用 Camunda RuntimeService
//}