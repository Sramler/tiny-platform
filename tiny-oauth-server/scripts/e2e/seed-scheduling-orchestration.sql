-- ========================================================================
-- 调度模块真前后端联动 E2E 种子数据
-- 依赖：基础租户/用户/角色/菜单数据已存在（tenant_id=1, admin 可访问调度菜单）
-- 用途：为 real Playwright E2E 生成两条 DAG：
--   1) sales_report_pipeline    并行子统计 -> 归并汇总
--   2) serial_sales_pipeline    串行阶段推进
-- ========================================================================

SET @tenant_id = 1;

SET @parallel_dag_code = 'sales_report_pipeline';
SET @serial_dag_code = 'serial_sales_pipeline';
SET @parallel_stat_type_code = 'E2E_REPORT_STAT';
SET @parallel_summary_type_code = 'E2E_REPORT_SUMMARY';
SET @serial_stage_type_code = 'E2E_SERIAL_STAGE';

-- 先清理目标 DAG 的历史运行数据，保证每次 real e2e 都从空 run 开始
DELETE h
FROM scheduling_task_history h
JOIN scheduling_task_instance ti ON ti.id = h.task_instance_id
JOIN scheduling_dag d ON d.id = ti.dag_id
WHERE d.tenant_id = @tenant_id
  AND d.code IN (@parallel_dag_code, @serial_dag_code);

DELETE ti
FROM scheduling_task_instance ti
JOIN scheduling_dag d ON d.id = ti.dag_id
WHERE d.tenant_id = @tenant_id
  AND d.code IN (@parallel_dag_code, @serial_dag_code);

DELETE r
FROM scheduling_dag_run r
JOIN scheduling_dag d ON d.id = r.dag_id
WHERE d.tenant_id = @tenant_id
  AND d.code IN (@parallel_dag_code, @serial_dag_code);

DELETE e
FROM scheduling_dag_edge e
JOIN scheduling_dag_version v ON v.id = e.dag_version_id
JOIN scheduling_dag d ON d.id = v.dag_id
WHERE d.tenant_id = @tenant_id
  AND d.code IN (@parallel_dag_code, @serial_dag_code);

DELETE t
FROM scheduling_dag_task t
JOIN scheduling_dag_version v ON v.id = t.dag_version_id
JOIN scheduling_dag d ON d.id = v.dag_id
WHERE d.tenant_id = @tenant_id
  AND d.code IN (@parallel_dag_code, @serial_dag_code);

DELETE v
FROM scheduling_dag_version v
JOIN scheduling_dag d ON d.id = v.dag_id
WHERE d.tenant_id = @tenant_id
  AND d.code IN (@parallel_dag_code, @serial_dag_code);

DELETE FROM scheduling_dag
WHERE tenant_id = @tenant_id
  AND code IN (@parallel_dag_code, @serial_dag_code);

DELETE FROM scheduling_task
WHERE tenant_id = @tenant_id
  AND code IN (
    'e2e_report_user',
    'e2e_report_order',
    'e2e_report_summary',
    'e2e_serial_extract',
    'e2e_serial_normalize',
    'e2e_serial_aggregate',
    'e2e_serial_finalize'
  );

DELETE FROM scheduling_task_type
WHERE tenant_id = @tenant_id
  AND code IN (@parallel_stat_type_code, @parallel_summary_type_code, @serial_stage_type_code);

-- 任务类型
INSERT INTO scheduling_task_type
  (tenant_id, code, name, description, executor, default_timeout_sec, default_max_retry, enabled, created_by, created_at, updated_at)
VALUES
  (@tenant_id, @parallel_stat_type_code, 'E2E-报表统计', 'real e2e 并行统计节点', 'delayTaskExecutor', 120, 0, 1, 'real-e2e', NOW(), NOW()),
  (@tenant_id, @parallel_summary_type_code, 'E2E-报表汇总', 'real e2e 归并汇总节点', 'loggingTaskExecutor', 60, 0, 1, 'real-e2e', NOW(), NOW()),
  (@tenant_id, @serial_stage_type_code, 'E2E-串行阶段', 'real e2e 串行链路阶段节点', 'delayTaskExecutor', 120, 0, 1, 'real-e2e', NOW(), NOW());

SET @parallel_stat_type_id = (
  SELECT id FROM scheduling_task_type WHERE tenant_id = @tenant_id AND code = @parallel_stat_type_code LIMIT 1
);
SET @parallel_summary_type_id = (
  SELECT id FROM scheduling_task_type WHERE tenant_id = @tenant_id AND code = @parallel_summary_type_code LIMIT 1
);
SET @serial_stage_type_id = (
  SELECT id FROM scheduling_task_type WHERE tenant_id = @tenant_id AND code = @serial_stage_type_code LIMIT 1
);

-- 任务定义
INSERT INTO scheduling_task
  (tenant_id, type_id, code, name, description, params, timeout_sec, max_retry, concurrency_policy, enabled, created_by, created_at, updated_at)
VALUES
  (@tenant_id, @parallel_stat_type_id, 'e2e_report_user', 'E2E-用户统计', '并行统计分支：用户', JSON_OBJECT('delayMs', 4000, 'message', '用户统计'), 30, 0, 'PARALLEL', 1, 'real-e2e', NOW(), NOW()),
  (@tenant_id, @parallel_stat_type_id, 'e2e_report_order', 'E2E-订单统计', '并行统计分支：订单', JSON_OBJECT('delayMs', 3500, 'message', '订单统计'), 30, 0, 'PARALLEL', 1, 'real-e2e', NOW(), NOW()),
  (@tenant_id, @parallel_summary_type_id, 'e2e_report_summary', 'E2E-汇总输出', '并行统计完成后的归并节点', JSON_OBJECT('message', '日报汇总完成', 'step', 'summary'), 30, 0, 'PARALLEL', 1, 'real-e2e', NOW(), NOW()),
  (@tenant_id, @serial_stage_type_id, 'e2e_serial_extract', 'E2E-提取阶段', '串行阶段 1', JSON_OBJECT('delayMs', 2500, 'message', 'extract'), 30, 0, 'SEQUENTIAL', 1, 'real-e2e', NOW(), NOW()),
  (@tenant_id, @serial_stage_type_id, 'e2e_serial_normalize', 'E2E-标准化阶段', '串行阶段 2', JSON_OBJECT('delayMs', 2000, 'message', 'normalize'), 30, 0, 'SEQUENTIAL', 1, 'real-e2e', NOW(), NOW()),
  (@tenant_id, @serial_stage_type_id, 'e2e_serial_aggregate', 'E2E-聚合阶段', '串行阶段 3', JSON_OBJECT('delayMs', 1500, 'message', 'aggregate'), 30, 0, 'SEQUENTIAL', 1, 'real-e2e', NOW(), NOW()),
  (@tenant_id, @serial_stage_type_id, 'e2e_serial_finalize', 'E2E-收尾阶段', '串行阶段 4', JSON_OBJECT('delayMs', 1000, 'message', 'finalize'), 30, 0, 'SEQUENTIAL', 1, 'real-e2e', NOW(), NOW());

SET @task_user_id = (
  SELECT id FROM scheduling_task WHERE tenant_id = @tenant_id AND code = 'e2e_report_user' LIMIT 1
);
SET @task_order_id = (
  SELECT id FROM scheduling_task WHERE tenant_id = @tenant_id AND code = 'e2e_report_order' LIMIT 1
);
SET @task_summary_id = (
  SELECT id FROM scheduling_task WHERE tenant_id = @tenant_id AND code = 'e2e_report_summary' LIMIT 1
);
SET @task_extract_id = (
  SELECT id FROM scheduling_task WHERE tenant_id = @tenant_id AND code = 'e2e_serial_extract' LIMIT 1
);
SET @task_normalize_id = (
  SELECT id FROM scheduling_task WHERE tenant_id = @tenant_id AND code = 'e2e_serial_normalize' LIMIT 1
);
SET @task_aggregate_id = (
  SELECT id FROM scheduling_task WHERE tenant_id = @tenant_id AND code = 'e2e_serial_aggregate' LIMIT 1
);
SET @task_finalize_id = (
  SELECT id FROM scheduling_task WHERE tenant_id = @tenant_id AND code = 'e2e_serial_finalize' LIMIT 1
);

-- DAG 主表
INSERT INTO scheduling_dag
  (tenant_id, code, name, description, enabled, cron_expression, cron_timezone, cron_enabled, created_by, created_at, updated_at)
VALUES
  (@tenant_id, @parallel_dag_code, '销售报表流水线', 'real e2e 并行统计 -> 归并汇总', 1, NULL, NULL, 0, 'real-e2e', NOW(), NOW()),
  (@tenant_id, @serial_dag_code, '串行销售处理流水线', 'real e2e 串行阶段推进', 1, NULL, NULL, 0, 'real-e2e', NOW(), NOW());

SET @parallel_dag_id = (
  SELECT id FROM scheduling_dag WHERE tenant_id = @tenant_id AND code = @parallel_dag_code LIMIT 1
);
SET @serial_dag_id = (
  SELECT id FROM scheduling_dag WHERE tenant_id = @tenant_id AND code = @serial_dag_code LIMIT 1
);

-- ACTIVE 版本
INSERT INTO scheduling_dag_version
  (dag_id, version_no, status, definition, created_by, created_at, activated_at)
VALUES
  (
    @parallel_dag_id,
    1,
    'ACTIVE',
    JSON_OBJECT(
      'nodes', JSON_ARRAY('user_stat', 'order_stat', 'merge_report'),
      'edges', JSON_ARRAY(
        JSON_OBJECT('from', 'user_stat', 'to', 'merge_report'),
        JSON_OBJECT('from', 'order_stat', 'to', 'merge_report')
      )
    ),
    'real-e2e',
    NOW(),
    NOW()
  ),
  (
    @serial_dag_id,
    1,
    'ACTIVE',
    JSON_OBJECT(
      'nodes', JSON_ARRAY('extract', 'normalize', 'aggregate', 'finalize'),
      'edges', JSON_ARRAY(
        JSON_OBJECT('from', 'extract', 'to', 'normalize'),
        JSON_OBJECT('from', 'normalize', 'to', 'aggregate'),
        JSON_OBJECT('from', 'aggregate', 'to', 'finalize')
      )
    ),
    'real-e2e',
    NOW(),
    NOW()
  );

SET @parallel_version_id = (
  SELECT id FROM scheduling_dag_version WHERE dag_id = @parallel_dag_id AND status = 'ACTIVE' LIMIT 1
);
SET @serial_version_id = (
  SELECT id FROM scheduling_dag_version WHERE dag_id = @serial_dag_id AND status = 'ACTIVE' LIMIT 1
);

-- 节点
INSERT INTO scheduling_dag_task
  (dag_version_id, node_code, task_id, name, override_params, timeout_sec, max_retry, parallel_group, meta, created_at)
VALUES
  (@parallel_version_id, 'user_stat', @task_user_id, '用户统计', NULL, 30, 0, 'report-root', JSON_OBJECT('e2e', TRUE), NOW()),
  (@parallel_version_id, 'order_stat', @task_order_id, '订单统计', NULL, 30, 0, 'report-root', JSON_OBJECT('e2e', TRUE), NOW()),
  (@parallel_version_id, 'merge_report', @task_summary_id, '汇总输出', NULL, 30, 0, NULL, JSON_OBJECT('e2e', TRUE), NOW()),
  (@serial_version_id, 'extract', @task_extract_id, '提取', NULL, 30, 0, NULL, JSON_OBJECT('e2e', TRUE), NOW()),
  (@serial_version_id, 'normalize', @task_normalize_id, '标准化', NULL, 30, 0, NULL, JSON_OBJECT('e2e', TRUE), NOW()),
  (@serial_version_id, 'aggregate', @task_aggregate_id, '聚合', NULL, 30, 0, NULL, JSON_OBJECT('e2e', TRUE), NOW()),
  (@serial_version_id, 'finalize', @task_finalize_id, '收尾', NULL, 30, 0, NULL, JSON_OBJECT('e2e', TRUE), NOW());

-- 边
INSERT INTO scheduling_dag_edge
  (dag_version_id, from_node_code, to_node_code, `condition`, created_at)
VALUES
  (@parallel_version_id, 'user_stat', 'merge_report', NULL, NOW()),
  (@parallel_version_id, 'order_stat', 'merge_report', NULL, NOW()),
  (@serial_version_id, 'extract', 'normalize', NULL, NOW()),
  (@serial_version_id, 'normalize', 'aggregate', NULL, NOW()),
  (@serial_version_id, 'aggregate', 'finalize', NULL, NOW());
