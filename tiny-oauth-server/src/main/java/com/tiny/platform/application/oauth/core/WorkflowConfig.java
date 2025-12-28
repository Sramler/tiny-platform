//package com.tiny.oauthserver.core;
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class WorkflowConfig {
//    @Value("${workflow.engine}")
//    private String engine;
//
//    @Bean
//    public WorkflowService workflowService(
//        CamundaWorkflowService camunda,
//        FlowableWorkflowService flowable) {
//        return switch (engine) {
//            case "camunda" -> camunda;
//            case "flowable" -> flowable;
//            default -> throw new IllegalArgumentException("Unsupported engine");
//        };
//    }
//}