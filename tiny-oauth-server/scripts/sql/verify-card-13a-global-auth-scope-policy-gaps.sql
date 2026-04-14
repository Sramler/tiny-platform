-- CARD-13A rollout guard：在部署读侧单 scope_key 之后，目标库不应再存在「仅 GLOBAL、未覆盖 TENANT/PLATFORM」的缺口。
-- 期望结果：下一查询返回 0。若 > 0，先执行 Liquibase `135-duplicate-global-auth-scope-policy-card-13a`（或等价手工修复）后再验收。
--
-- 缺口定义（与迁移 135 对齐）：
-- 1) scope_key=GLOBAL 的策略行，若同一 credential 尚无 PLATFORM 行，则平台态登录无法命中材料策略。
-- 2) scope_key=GLOBAL 的策略行，若用户存在 ACTIVE tenant_user，但同一 credential 尚无对应 TENANT:{tenant_id} 行，则租户态登录无法命中材料策略。

SELECT COUNT(DISTINCT g.id) AS gap_row_count
FROM user_auth_scope_policy g
INNER JOIN user_auth_credential c ON c.id = g.credential_id
WHERE g.scope_key = 'GLOBAL'
  AND (
    NOT EXISTS (
      SELECT 1
      FROM user_auth_scope_policy p
      WHERE p.credential_id = g.credential_id
        AND p.scope_key = 'PLATFORM'
    )
    OR EXISTS (
      SELECT 1
      FROM tenant_user tu
      WHERE tu.user_id = c.user_id
        AND tu.status = 'ACTIVE'
        AND NOT EXISTS (
          SELECT 1
          FROM user_auth_scope_policy p
          WHERE p.credential_id = g.credential_id
            AND p.scope_key = CONCAT('TENANT:', tu.tenant_id)
        )
    )
  );
