-- 调度中心示例数据（与 data.sql 中调度段一致，便于 003 已执行过的库补数据）
-- 使用 INSERT IGNORE 幂等
-- 重要：本脚本会创建 node_a、node_b 节点（scheduling_dag_task）；未执行本脚本时节点不存在，触发 DAG 会失败。
INSERT IGNORE INTO `scheduling_task_type`
(`id`, `tenant_id`, `code`, `name`, `description`, `executor`, `default_timeout_sec`, `default_max_retry`, `enabled`, `created_at`, `updated_at`)
VALUES
(1, 1, 'DEMO_SHELL', '示例Shell任务', '用于示例的 Shell 执行器，便于 DAG 计划与运行历史验证', 'shellExecutor', 300, 2, 1, NOW(), NOW());

INSERT IGNORE INTO `scheduling_task`
(`id`, `tenant_id`, `type_id`, `code`, `name`, `description`, `enabled`, `created_at`, `updated_at`)
VALUES
(1, 1, 1, 'demo_task_a', '示例任务A-准备', '示例流程第一步：准备', 1, NOW(), NOW()),
(2, 1, 1, 'demo_task_b', '示例任务B-执行', '示例流程第二步：执行', 1, NOW(), NOW());

INSERT IGNORE INTO `scheduling_dag`
(`id`, `tenant_id`, `code`, `name`, `description`, `enabled`, `created_at`, `updated_at`)
VALUES
(1, 1, 'demo_dag', '示例DAG计划', 'DAG 管理 → 任务类型 → 任务 → 运行历史 示例流转，用于前端验证', 1, NOW(), NOW());

INSERT IGNORE INTO `scheduling_dag_version`
(`id`, `dag_id`, `version_no`, `status`, `created_at`)
VALUES
(1, 1, 1, 'ACTIVE', NOW());

INSERT IGNORE INTO `scheduling_dag_task`
(`id`, `dag_version_id`, `node_code`, `task_id`, `name`, `created_at`)
VALUES
(1, 1, 'node_a', 1, '准备节点', NOW()),
(2, 1, 'node_b', 2, '执行节点', NOW());

INSERT IGNORE INTO `scheduling_dag_edge`
(`id`, `dag_version_id`, `from_node_code`, `to_node_code`, `created_at`)
VALUES
(1, 1, 'node_a', 'node_b', NOW());

INSERT IGNORE INTO `scheduling_dag_run`
(`id`, `dag_id`, `dag_version_id`, `run_no`, `tenant_id`, `trigger_type`, `triggered_by`, `status`, `start_time`, `end_time`, `created_at`)
VALUES
(1, 1, 1, 'RUN-DEMO-001', 1, 'MANUAL', 'admin', 'SUCCESS', NOW() - INTERVAL 1 HOUR, NOW() - INTERVAL 1 HOUR + INTERVAL 5 MINUTE, NOW() - INTERVAL 1 HOUR);

INSERT IGNORE INTO `scheduling_task_instance`
(`id`, `dag_run_id`, `dag_id`, `dag_version_id`, `node_code`, `task_id`, `tenant_id`, `attempt_no`, `status`, `scheduled_at`, `params`, `result`, `created_at`, `updated_at`)
VALUES
(1, 1, 1, 1, 'node_a', 1, 1, 1, 'SUCCESS', NOW() - INTERVAL 1 HOUR, NULL, '{"ok":true}', NOW() - INTERVAL 1 HOUR, NOW()),
(2, 1, 1, 1, 'node_b', 2, 1, 1, 'SUCCESS', NOW() - INTERVAL 1 HOUR + INTERVAL 2 MINUTE, NULL, '{"ok":true}', NOW() - INTERVAL 1 HOUR, NOW());

INSERT IGNORE INTO `scheduling_task_history`
(`id`, `task_instance_id`, `dag_run_id`, `dag_id`, `node_code`, `task_id`, `attempt_no`, `status`, `start_time`, `end_time`, `duration_ms`, `created_at`)
VALUES
(1, 1, 1, 1, 'node_a', 1, 1, 'SUCCESS', NOW() - INTERVAL 1 HOUR, NOW() - INTERVAL 1 HOUR + INTERVAL 1 MINUTE, 60000, NOW()),
(2, 2, 1, 1, 'node_b', 2, 1, 'SUCCESS', NOW() - INTERVAL 1 HOUR + INTERVAL 2 MINUTE, NOW() - INTERVAL 1 HOUR + INTERVAL 5 MINUTE, 180000, NOW());
