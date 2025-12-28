package com.tiny.platform.application.oauth.workflow;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * BPMN 验证和修复工具类
 * 用于修复常见的 BPMN 验证错误
 */
@Component
public class BpmnValidationHelper {

    /**
     * 修复 BPMN XML 中的常见验证错误
     * 
     * @param bpmnXml 原始 BPMN XML
     * @return 修复后的 BPMN XML
     */
    public String fixBpmnValidationErrors(String bpmnXml) {
        try {
            // 解析 BPMN 模型
            BpmnModelInstance modelInstance = Bpmn.readModelFromStream(
                new java.io.ByteArrayInputStream(bpmnXml.getBytes())
            );

            // 修复历史数据生存时间
            fixHistoryTimeToLive(modelInstance);
            
            // 暂时跳过排他网关修复，因为会导致 DOM 验证错误
            // fixExclusiveGatewayConditions(modelInstance);

            // 转换回 XML
            return Bpmn.convertToString(modelInstance);
        } catch (Exception e) {
            System.err.println("BPMN fix error: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("修复 BPMN 验证错误失败: " + e.getMessage(), e);
        }
    }

    /**
     * 为流程定义添加历史数据生存时间
     */
    private void fixHistoryTimeToLive(BpmnModelInstance modelInstance) {
        Collection<org.camunda.bpm.model.bpmn.instance.Process> processes = modelInstance.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.Process.class);
        
        for (org.camunda.bpm.model.bpmn.instance.Process process : processes) {
            // 使用现代方法：直接设置 camunda:historyTimeToLive 属性
            if (process.getAttributeValue("historyTimeToLive") == null) {
                process.setAttributeValueNs("http://camunda.org/schema/1.0/bpmn", "historyTimeToLive", "30");
            }
        }
    }

    /**
     * 修复排他网关的条件问题
     *
     * <p>当前未在默认修复流程中调用（可能导致 DOM 验证异常），
     * 为避免 IDE 未使用警告而保留给后续启用，添加 suppress 注解。</p>
     */
    @SuppressWarnings("unused")
    private void fixExclusiveGatewayConditions(BpmnModelInstance modelInstance) {
        try {
            Collection<ExclusiveGateway> gateways = modelInstance.getModelElementsByType(ExclusiveGateway.class);
            
            for (ExclusiveGateway gateway : gateways) {
                Collection<SequenceFlow> outgoingFlows = gateway.getOutgoing();
                
                if (outgoingFlows.size() > 1) {
                    // 检查是否有默认流
                    boolean hasDefaultFlow = false;
                    for (SequenceFlow flow : outgoingFlows) {
                        if (gateway.getDefault() != null && gateway.getDefault().equals(flow)) {
                            hasDefaultFlow = true;
                            break;
                        }
                    }
                    
                    // 如果没有默认流，将第一个流设为默认流
                    if (!hasDefaultFlow) {
                        SequenceFlow firstFlow = outgoingFlows.iterator().next();
                        gateway.setDefault(firstFlow);
                    }
                    
                    // 为没有条件的流添加默认条件
                    for (SequenceFlow flow : outgoingFlows) {
                        if (flow.getConditionExpression() == null && 
                            (gateway.getDefault() == null || !gateway.getDefault().equals(flow))) {
                            // 添加默认条件
                            addDefaultCondition(flow);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to fix exclusive gateway conditions: " + e.getMessage());
            e.printStackTrace();
            // 不抛出异常，继续处理其他修复
        }
    }

    /**
     * 为序列流添加默认条件
     */
    private void addDefaultCondition(SequenceFlow flow) {
        try {
            // 创建条件表达式
            ConditionExpression condition = flow.getModelInstance().newInstance(ConditionExpression.class);
            condition.setTextContent("${true}"); // 默认总是为真
            
            // 设置条件类型 - 使用命名空间方法
            condition.setAttributeValueNs("http://www.w3.org/2001/XMLSchema-instance", "type", "tFormalExpression");
            
            // 将条件添加到流中
            flow.setConditionExpression(condition);
        } catch (Exception e) {
            // 如果设置条件失败，记录错误但不抛出异常
            System.err.println("Failed to add default condition: " + e.getMessage());
        }
    }

    /**
     * 验证 BPMN XML 是否有效
     * 
     * @param bpmnXml BPMN XML 字符串
     * @return 验证结果
     */
    public BpmnValidationResult validateBpmnXml(String bpmnXml) {
        BpmnValidationResult result = new BpmnValidationResult();
        
        try {
            // 尝试解析 BPMN
            BpmnModelInstance modelInstance = Bpmn.readModelFromStream(
                new java.io.ByteArrayInputStream(bpmnXml.getBytes())
            );
            
            result.setValid(true);
            result.setMessage("BPMN XML 验证通过");
            
            // 检查常见问题
            checkCommonIssues(modelInstance, result);
            
        } catch (Exception e) {
            result.setValid(false);
            result.setMessage("BPMN XML 解析失败: " + e.getMessage());
            result.setError(e);
        }
        
        return result;
    }

    /**
     * 检查常见的 BPMN 问题
     */
    private void checkCommonIssues(BpmnModelInstance modelInstance, BpmnValidationResult result) {
        // 检查历史数据生存时间
        Collection<org.camunda.bpm.model.bpmn.instance.Process> processes = modelInstance.getModelElementsByType(org.camunda.bpm.model.bpmn.instance.Process.class);
        for (org.camunda.bpm.model.bpmn.instance.Process process : processes) {
            // 使用现代方法检查 historyTimeToLive 属性
            if (process.getAttributeValueNs("http://camunda.org/schema/1.0/bpmn", "historyTimeToLive") == null) {
                result.addWarning("流程 '" + process.getId() + "' 缺少 historyTimeToLive 设置");
            }
        }
        
        // 检查排他网关
        Collection<ExclusiveGateway> gateways = modelInstance.getModelElementsByType(ExclusiveGateway.class);
        for (ExclusiveGateway gateway : gateways) {
            Collection<SequenceFlow> outgoingFlows = gateway.getOutgoing();
            if (outgoingFlows.size() > 1) {
                boolean hasDefaultFlow = false;
                int flowsWithoutCondition = 0;
                
                for (SequenceFlow flow : outgoingFlows) {
                    if (gateway.getDefault() != null && gateway.getDefault().equals(flow)) {
                        hasDefaultFlow = true;
                    }
                    if (flow.getConditionExpression() == null) {
                        flowsWithoutCondition++;
                    }
                }
                
                if (!hasDefaultFlow) {
                    result.addWarning("排他网关 '" + gateway.getId() + "' 缺少默认流");
                }
                
                if (flowsWithoutCondition > 1) {
                    result.addWarning("排他网关 '" + gateway.getId() + "' 有多个无条件的流出流");
                }
            }
        }
    }

    /**
     * BPMN 验证结果类
     */
    public static class BpmnValidationResult {
        private boolean valid;
        private String message;
        private Exception error;
        private java.util.List<String> warnings = new java.util.ArrayList<>();

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Exception getError() {
            return error;
        }

        public void setError(Exception error) {
            this.error = error;
        }

        public java.util.List<String> getWarnings() {
            return warnings;
        }

        public void addWarning(String warning) {
            this.warnings.add(warning);
        }
    }
}
