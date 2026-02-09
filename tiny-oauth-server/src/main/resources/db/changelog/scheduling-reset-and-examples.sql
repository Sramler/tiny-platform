-- ========================================================================
-- 调度数据清理并插入新示例（可重复执行：先删后插）
-- 依赖：scheduling_* 表与 QRTZ_* 表已存在；tenant_id=1 已存在（如 resource/role 等）。
-- 用途：清理旧 demo 数据，插入「数据导出流水线」「报表计算流水线」「健康检查」示例。
-- ========================================================================

-- ---------- 1. 清理（子表先删，避免逻辑依赖） ----------
DELETE FROM `scheduling_task_history`;
DELETE FROM `scheduling_task_instance`;
DELETE FROM `scheduling_dag_run`;
DELETE FROM `scheduling_dag_edge`;
DELETE FROM `scheduling_dag_task`;
DELETE FROM `scheduling_dag_version`;
DELETE FROM `scheduling_dag`;
DELETE FROM `scheduling_task`;
DELETE FROM `scheduling_task_type`;
DELETE FROM `scheduling_audit` WHERE `object_type` IN ('task_type','task','dag','dag_version','dag_run','task_instance');

-- 清理旧 DAG 对应的 Quartz Job（demo_dag 的 id=1）
DELETE FROM `QRTZ_CRON_TRIGGERS` WHERE `TRIGGER_NAME` = 'dag-trigger-1' AND `TRIGGER_GROUP` = 'dag-trigger-group';
DELETE FROM `QRTZ_TRIGGERS` WHERE `JOB_NAME` = 'dag-1' AND `JOB_GROUP` = 'dag-group';
DELETE FROM `QRTZ_JOB_DETAILS` WHERE `JOB_NAME` = 'dag-1' AND `JOB_GROUP` = 'dag-group';

-- ---------- 2. 插入新示例（使用显式 ID，便于引用） ----------

-- 示例一：数据导出流水线
INSERT INTO `scheduling_task_type`
(`id`, `tenant_id`, `code`, `name`, `description`, `executor`, `default_timeout_sec`, `default_max_retry`, `enabled`, `created_at`, `updated_at`)
VALUES
(2, 1, 'EXPORT_PIPELINE', '数据导出流水线-步骤', '用于数据导出流程中的每一步，打日志并返回', 'loggingTaskExecutor', 120, 1, 1, NOW(), NOW());

INSERT INTO `scheduling_task`
(`id`, `tenant_id`, `type_id`, `code`, `name`, `description`, `params`, `max_retry`, `enabled`, `created_at`, `updated_at`)
VALUES
(3, 1, 2, 'export_prepare',   '导出-准备',   '准备导出目录与权限', '{"message":"准备导出环境","step":"prepare"}',   1, 1, NOW(), NOW()),
(4, 1, 2, 'export_validate', '导出-校验',   '校验数据范围与一致性', '{"message":"校验导出数据","step":"validate"}', 1, 1, NOW(), NOW()),
(5, 1, 2, 'export_execute',   '导出-执行',   '执行导出并落盘', '{"message":"执行导出写入","step":"execute"}',     1, 1, NOW(), NOW());

INSERT INTO `scheduling_dag`
(`id`, `tenant_id`, `code`, `name`, `description`, `enabled`, `created_at`, `updated_at`)
VALUES
(2, 1, 'export_pipeline', '数据导出流水线', '准备→校验→执行 三步串行，展示依赖与日志', 1, NOW(), NOW());

INSERT INTO `scheduling_dag_version`
(`id`, `dag_id`, `version_no`, `status`, `created_at`)
VALUES
(2, 2, 1, 'ACTIVE', NOW());

INSERT INTO `scheduling_dag_task`
(`id`, `dag_version_id`, `node_code`, `task_id`, `name`, `created_at`)
VALUES
(3, 2, 'prepare',  3, '准备', NOW()),
(4, 2, 'validate', 4, '校验', NOW()),
(5, 2, 'execute',  5, '执行', NOW());

INSERT INTO `scheduling_dag_edge`
(`id`, `dag_version_id`, `from_node_code`, `to_node_code`, `created_at`)
VALUES
(2, 2, 'prepare',  'validate', NOW()),
(3, 2, 'validate', 'execute',  NOW());

-- 示例二：报表计算流水线
INSERT INTO `scheduling_task_type`
(`id`, `tenant_id`, `code`, `name`, `description`, `executor`, `default_timeout_sec`, `default_max_retry`, `enabled`, `created_at`, `updated_at`)
VALUES
(3, 1, 'REPORT_STAT',   '报表统计-模拟计算', 'Delay 模拟统计耗时', 'delayTaskExecutor', 300, 1, 1, NOW(), NOW()),
(4, 1, 'REPORT_SUMMARY', '报表汇总-写日志',   '汇总步骤打日志',   'loggingTaskExecutor', 60, 0, 1, NOW(), NOW());

INSERT INTO `scheduling_task`
(`id`, `tenant_id`, `type_id`, `code`, `name`, `description`, `params`, `max_retry`, `enabled`, `created_at`, `updated_at`)
VALUES
(6, 1, 3, 'report_user',  '报表-用户统计', '模拟用户统计计算', '{"delayMs":2000}', 1, 1, NOW(), NOW()),
(7, 1, 3, 'report_order', '报表-订单统计', '模拟订单统计计算', '{"delayMs":1500}', 1, 1, NOW(), NOW()),
(8, 1, 4, 'report_summary','报表-汇总',     '日报汇总写日志',   '{"message":"日报汇总完成","step":"summary"}', 0, 1, NOW(), NOW());

INSERT INTO `scheduling_dag`
(`id`, `tenant_id`, `code`, `name`, `description`, `enabled`, `created_at`, `updated_at`)
VALUES
(3, 1, 'report_pipeline', '报表计算流水线', '用户统计与订单统计并行，完成后汇总', 1, NOW(), NOW());

INSERT INTO `scheduling_dag_version`
(`id`, `dag_id`, `version_no`, `status`, `created_at`)
VALUES
(3, 3, 1, 'ACTIVE', NOW());

INSERT INTO `scheduling_dag_task`
(`id`, `dag_version_id`, `node_code`, `task_id`, `name`, `created_at`)
VALUES
(6, 3, 'user_report',  6, '用户统计', NOW()),
(7, 3, 'order_report', 7, '订单统计', NOW()),
(8, 3, 'summary',      8, '汇总',     NOW());

INSERT INTO `scheduling_dag_edge`
(`id`, `dag_version_id`, `from_node_code`, `to_node_code`, `created_at`)
VALUES
(4, 3, 'user_report',  'summary', NOW()),
(5, 3, 'order_report', 'summary', NOW());

-- 示例三：健康检查
INSERT INTO `scheduling_task_type`
(`id`, `tenant_id`, `code`, `name`, `description`, `executor`, `default_timeout_sec`, `default_max_retry`, `enabled`, `created_at`, `updated_at`)
VALUES
(5, 1, 'HEALTH_CHECK', '健康检查-记录', '健康检测与记录', 'loggingTaskExecutor', 30, 0, 1, NOW(), NOW());

INSERT INTO `scheduling_task`
(`id`, `tenant_id`, `type_id`, `code`, `name`, `description`, `params`, `enabled`, `created_at`, `updated_at`)
VALUES
(9,  1, 5, 'health_ping', '健康检查-检测', 'Ping 检测', '{"message":"Ping 检测","step":"ping"}', 1, NOW(), NOW()),
(10, 1, 5, 'health_log',  '健康检查-记录', '记录结果',   '{"message":"记录健康结果","step":"log"}',  1, NOW(), NOW());

INSERT INTO `scheduling_dag`
(`id`, `tenant_id`, `code`, `name`, `description`, `enabled`, `created_at`, `updated_at`)
VALUES
(4, 1, 'health_check', '健康检查', '检测→记录，可配置 Cron 定时执行', 1, NOW(), NOW());

INSERT INTO `scheduling_dag_version`
(`id`, `dag_id`, `version_no`, `status`, `created_at`)
VALUES
(4, 4, 1, 'ACTIVE', NOW());

INSERT INTO `scheduling_dag_task`
(`id`, `dag_version_id`, `node_code`, `task_id`, `name`, `created_at`)
VALUES
(9,  4, 'ping', 9,  '检测', NOW()),
(10, 4, 'log',  10, '记录', NOW());

INSERT INTO `scheduling_dag_edge`
(`id`, `dag_version_id`, `from_node_code`, `to_node_code`, `created_at`)
VALUES
(6, 4, 'ping', 'log', NOW());
