package com.tiny.platform.application.oauth.core;

import java.util.Map;

public interface WorkflowService {
    String startProcess(String key, Map<String, Object> vars);
    String getCurrentTask(String processInstanceId);
    void completeTask(String taskId);
}