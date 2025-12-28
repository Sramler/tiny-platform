-- Liquibase SQL changelog for demo_export_usage stored procedure
-- 仅管理存储过程，表结构仍由 schema.sql 管理

CREATE PROCEDURE sp_generate_demo_export_usage(
    IN p_days INT,
    IN p_rows_per_day INT,
    IN p_target_rows INT,
    IN p_clear_existing TINYINT(1)
)
BEGIN
    DECLARE d INT DEFAULT 0;
    DECLARE i INT;
    DECLARE usage_dt DATE;
    DECLARE tenant_code VARCHAR(64);
    DECLARE product_code VARCHAR(64);
    DECLARE product_name VARCHAR(128);
    DECLARE plan_tier VARCHAR(32);
    DECLARE region VARCHAR(64);
    DECLARE unit VARCHAR(32);
    DECLARE unit_price DECIMAL(12,4);
    DECLARE qty DECIMAL(18,4);
    DECLARE status_val VARCHAR(20);
    DECLARE currency_val CHAR(3);
    DECLARE tax_val DECIMAL(5,4);
    DECLARE target_rows INT;
    DECLARE inserted_rows INT DEFAULT 0;
    -- 新增字段变量
    DECLARE description_val TEXT;
    DECLARE priority_val VARCHAR(20);
    DECLARE tags_val JSON;
    DECLARE usage_time_val TIME;
    DECLARE quality_score_val TINYINT;
    DECLARE discount_percentage_val DECIMAL(5,2);
    DECLARE category_path_val VARCHAR(200);
    DECLARE department_id_val BIGINT;
    DECLARE customer_name_val VARCHAR(128);
    DECLARE theme_color_val VARCHAR(7);
    DECLARE api_key_val VARCHAR(128);
    DECLARE search_keyword_val VARCHAR(128);
    DECLARE start_date_val DATE;
    DECLARE end_date_val DATE;
    DECLARE selected_features_val JSON;
    DECLARE min_value_val DECIMAL(18,4);
    DECLARE max_value_val DECIMAL(18,4);
    DECLARE start_time_val TIME;
    DECLARE end_time_val TIME;
    DECLARE payment_method_val VARCHAR(32);
    DECLARE notes_val TEXT;
    DECLARE attachment_info_val JSON;

    IF p_clear_existing IS NOT NULL AND p_clear_existing = 1 THEN
        TRUNCATE TABLE demo_export_usage;
    END IF;

    IF p_rows_per_day IS NULL OR p_rows_per_day <= 0 THEN SET p_rows_per_day = 2000; END IF;
    IF p_days IS NULL OR p_days <= 0 THEN SET p_days = 7; END IF;

    IF p_target_rows IS NOT NULL AND p_target_rows > 0 THEN
        SET target_rows = p_target_rows;
    ELSE
        SET target_rows = p_days * p_rows_per_day;
    END IF;

    WHILE inserted_rows < target_rows DO
        SET d = inserted_rows DIV p_rows_per_day;
        SET usage_dt = CURDATE() - INTERVAL d DAY;
        SET i = 0;
        WHILE i < p_rows_per_day AND inserted_rows < target_rows DO
            SET tenant_code = ELT(1 + FLOOR(RAND() * 3), 'tenant-alpha', 'tenant-beta', 'tenant-gamma');
            SET product_code = ELT(1 + FLOOR(RAND() * 5), 'cdn', 'oss', 'api', 'mq', 'db');
            SET product_name = CASE product_code
                WHEN 'cdn' THEN 'CDN 流量'
                WHEN 'oss' THEN '对象存储'
                WHEN 'api' THEN 'API 调用'
                WHEN 'mq'  THEN '消息队列'
                ELSE '托管数据库'
            END;
            SET plan_tier = ELT(1 + FLOOR(RAND() * 3), 'basic', 'standard', 'enterprise');
            SET region = ELT(1 + FLOOR(RAND() * 4), 'cn-north-1', 'ap-southeast-1', 'us-west-1', 'eu-central-1');
            SET unit = CASE product_code
                WHEN 'cdn' THEN 'GB'
                WHEN 'oss' THEN 'GB'
                WHEN 'api' THEN 'req'
                WHEN 'mq'  THEN 'msg'
                ELSE 'hours'
            END;
            SET unit_price = CASE product_code
                WHEN 'cdn' THEN 0.1200
                WHEN 'oss' THEN 0.0800
                WHEN 'api' THEN 0.0008
                WHEN 'mq'  THEN 0.0005
                ELSE 2.8000
            END;
            SET qty = ROUND(
                CASE unit
                    WHEN 'GB' THEN (50 + RAND() * 150)
                    WHEN 'req' THEN (50000 + RAND() * 150000)
                    WHEN 'msg' THEN (10000 + RAND() * 80000)
                    ELSE (10 + RAND() * 80)
                END, 4);
            SET currency_val = ELT(1 + FLOOR(RAND() * 2), 'CNY', 'USD');
            SET tax_val = CASE currency_val WHEN 'USD' THEN 0.0725 ELSE 0.0600 END;
            SET status_val = ELT(1 + FLOOR(RAND() * 3), 'UNBILLED', 'BILLED', 'ADJUSTED');
            
            -- 生成新字段的测试数据
            SET description_val = CASE FLOOR(RAND() * 3)
                WHEN 0 THEN CONCAT('这是关于 ', product_name, ' 的详细描述信息，用于演示多行文本展示。')
                WHEN 1 THEN CONCAT('产品 ', product_code, ' 在区域 ', region, ' 的使用情况说明。')
                ELSE NULL
            END;
            SET priority_val = ELT(1 + FLOOR(RAND() * 4), 'LOW', 'MEDIUM', 'HIGH', 'URGENT');
            SET tags_val = CASE FLOOR(RAND() * 3)
                WHEN 0 THEN JSON_ARRAY('重要', 'VIP')
                WHEN 1 THEN JSON_ARRAY('测试', '生产')
                ELSE JSON_ARRAY('重要', '测试', '生产')
            END;
            SET usage_time_val = TIME(CONCAT(LPAD(FLOOR(RAND() * 24), 2, '0'), ':', LPAD(FLOOR(RAND() * 60), 2, '0'), ':', LPAD(FLOOR(RAND() * 60), 2, '0')));
            SET quality_score_val = FLOOR(RAND() * 6); -- 0-5
            SET discount_percentage_val = ROUND(RAND() * 50, 2); -- 0-50%
            SET category_path_val = CASE FLOOR(RAND() * 3)
                WHEN 0 THEN 'cloud/storage/oss'
                WHEN 1 THEN 'cloud/compute/ecs'
                ELSE 'network/cdn'
            END;
            SET department_id_val = CASE FLOOR(RAND() * 3)
                WHEN 0 THEN 1
                WHEN 1 THEN 11
                ELSE 12
            END;
            SET customer_name_val = ELT(1 + FLOOR(RAND() * 5), '阿里巴巴', '腾讯', '百度', '字节跳动', '美团');
            SET theme_color_val = ELT(1 + FLOOR(RAND() * 5), '#1890ff', '#52c41a', '#faad14', '#f5222d', '#722ed1');
            SET api_key_val = CONCAT('api_key_', LPAD(FLOOR(RAND() * 1000000), 6, '0'));
            SET search_keyword_val = CASE FLOOR(RAND() * 3)
                WHEN 0 THEN CONCAT(product_code, ' 搜索')
                WHEN 1 THEN CONCAT(region, ' 查询')
                ELSE NULL
            END;
            SET start_date_val = usage_dt - INTERVAL FLOOR(RAND() * 7) DAY;
            SET end_date_val = usage_dt + INTERVAL FLOOR(RAND() * 7) DAY;
            SET selected_features_val = CASE FLOOR(RAND() * 3)
                WHEN 0 THEN JSON_ARRAY('feature1', 'feature2')
                WHEN 1 THEN JSON_ARRAY('feature2', 'feature3', 'feature4')
                ELSE JSON_ARRAY('feature1', 'feature3')
            END;
            SET min_value_val = ROUND(RAND() * 100, 4);
            SET max_value_val = min_value_val + ROUND(RAND() * 100, 4);
            SET start_time_val = TIME(CONCAT(LPAD(FLOOR(RAND() * 12), 2, '0'), ':', LPAD(FLOOR(RAND() * 60), 2, '0'), ':00'));
            SET end_time_val = TIME(CONCAT(LPAD(12 + FLOOR(RAND() * 12), 2, '0'), ':', LPAD(FLOOR(RAND() * 60), 2, '0'), ':00'));
            SET payment_method_val = ELT(1 + FLOOR(RAND() * 4), 'ALIPAY', 'WECHAT', 'CREDIT_CARD', 'BANK_TRANSFER');
            SET notes_val = CASE FLOOR(RAND() * 2)
                WHEN 0 THEN CONCAT('备注信息：', product_name, ' 在 ', region, ' 区域的特殊说明。')
                ELSE NULL
            END;
            SET attachment_info_val = CASE FLOOR(RAND() * 2)
                WHEN 0 THEN JSON_OBJECT('file1', CONCAT('attachment_', i, '.pdf'), 'file2', CONCAT('document_', i, '.docx'))
                ELSE NULL
            END;

            INSERT INTO demo_export_usage (
                tenant_code, usage_date, product_code, product_name, plan_tier, region,
                usage_qty, unit, unit_price, amount, currency, tax_rate, is_billable,
                status, metadata, created_at,
                description, priority, tags, usage_time, quality_score, discount_percentage,
                category_path, department_id, customer_name, theme_color, api_key, search_keyword,
                start_date, end_date, selected_features, min_value, max_value,
                start_time, end_time, payment_method, notes, attachment_info
            ) VALUES (
                tenant_code,
                usage_dt,
                product_code,
                product_name,
                plan_tier,
                region,
                qty,
                unit,
                unit_price,
                ROUND(qty * unit_price, 4),
                currency_val,
                tax_val,
                ELT(1 + FLOOR(RAND() * 2), TRUE, FALSE),
                status_val,
                JSON_OBJECT(
                    'run', CONCAT('day-', d, '-row-', i),
                    'tier', plan_tier,
                    'region', region
                ),
                NOW() - INTERVAL d DAY,
                description_val,
                priority_val,
                tags_val,
                usage_time_val,
                quality_score_val,
                discount_percentage_val,
                category_path_val,
                department_id_val,
                customer_name_val,
                theme_color_val,
                api_key_val,
                search_keyword_val,
                start_date_val,
                end_date_val,
                selected_features_val,
                min_value_val,
                max_value_val,
                start_time_val,
                end_time_val,
                payment_method_val,
                notes_val,
                attachment_info_val
            );
            SET i = i + 1;
            SET inserted_rows = inserted_rows + 1;
        END WHILE;
    END WHILE;
END;


