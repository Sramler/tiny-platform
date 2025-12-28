package com.tiny.platform.infrastructure.export.demo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Predicate;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DemoExportUsageService {

    private final DemoExportUsageRepository repository;
    private final JdbcTemplate jdbcTemplate;

    public DemoExportUsageService(DemoExportUsageRepository repository, JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public DemoExportUsageEntity create(DemoExportUsageEntity entity) {
        entity.setId(null);
        return repository.save(entity);
    }

    public DemoExportUsageEntity update(Long id, DemoExportUsageEntity entity) {
        DemoExportUsageEntity existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在: " + id));
        existing.setTenantCode(entity.getTenantCode());
        existing.setUsageDate(entity.getUsageDate());
        existing.setProductCode(entity.getProductCode());
        existing.setProductName(entity.getProductName());
        existing.setPlanTier(entity.getPlanTier());
        existing.setRegion(entity.getRegion());
        existing.setUsageQty(entity.getUsageQty());
        existing.setUnit(entity.getUnit());
        existing.setUnitPrice(entity.getUnitPrice());
        existing.setAmount(entity.getAmount());
        existing.setCurrency(entity.getCurrency());
        existing.setTaxRate(entity.getTaxRate());
        existing.setBillable(entity.getBillable());
        existing.setStatus(entity.getStatus());
        existing.setMetadata(entity.getMetadata());
        // 新增字段
        existing.setDescription(entity.getDescription());
        existing.setPriority(entity.getPriority());
        existing.setTags(entity.getTags());
        existing.setUsageTime(entity.getUsageTime());
        existing.setQualityScore(entity.getQualityScore());
        existing.setDiscountPercentage(entity.getDiscountPercentage());
        existing.setCategoryPath(entity.getCategoryPath());
        existing.setDepartmentId(entity.getDepartmentId());
        existing.setCustomerName(entity.getCustomerName());
        existing.setThemeColor(entity.getThemeColor());
        existing.setApiKey(entity.getApiKey());
        existing.setSearchKeyword(entity.getSearchKeyword());
        existing.setStartDate(entity.getStartDate());
        existing.setEndDate(entity.getEndDate());
        existing.setSelectedFeatures(entity.getSelectedFeatures());
        existing.setMinValue(entity.getMinValue());
        existing.setMaxValue(entity.getMaxValue());
        existing.setStartTime(entity.getStartTime());
        existing.setEndTime(entity.getEndTime());
        existing.setPaymentMethod(entity.getPaymentMethod());
        existing.setNotes(entity.getNotes());
        existing.setAttachmentInfo(entity.getAttachmentInfo());
        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public Optional<DemoExportUsageEntity> findById(Long id) {
        return repository.findById(id);
    }

    public Page<DemoExportUsageEntity> search(
            String tenantCode,
            String productCode,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable) {

        // #region agent log
        try (FileWriter fw = new FileWriter("/Users/bliu/code/tiny-platform/.cursor/debug.log", true)) {
            fw.write("{\"sessionId\":\"debug-session\",\"runId\":\"list-debug\",\"hypothesisId\":\"H3\",\"location\":\"DemoExportUsageService.search:before\",\"message\":\"search called\",\"data\":{" +
                    "\"tenantCode\":\"" + (tenantCode == null ? "" : tenantCode) + "\"," +
                    "\"productCode\":\"" + (productCode == null ? "" : productCode) + "\"," +
                    "\"status\":\"" + (status == null ? "" : status) + "\"," +
                    "\"page\":" + pageable.getPageNumber() + "," +
                    "\"size\":" + pageable.getPageSize() +
                    "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
        } catch (IOException ignored) {
        }
        // #endregion agent log

        Specification<DemoExportUsageEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (tenantCode != null && !tenantCode.isBlank()) {
                predicates.add(cb.equal(root.get("tenantCode"), tenantCode));
            }
            if (productCode != null && !productCode.isBlank()) {
                predicates.add(cb.equal(root.get("productCode"), productCode));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("usageDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("usageDate"), endDate));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<DemoExportUsageEntity> page = repository.findAll(spec, pageable);

        long totalInTable;
        try {
            totalInTable = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM demo_export_usage", Long.class);
        } catch (Exception ex) {
            totalInTable = -1;
        }

        // #region agent log
        try (FileWriter fw = new FileWriter("/Users/bliu/code/tiny-platform/.cursor/debug.log", true)) {
            fw.write("{\"sessionId\":\"debug-session\",\"runId\":\"list-debug\",\"hypothesisId\":\"H3\",\"location\":\"DemoExportUsageService.search:after\",\"message\":\"search finished\",\"data\":{" +
                    "\"pageTotal\":" + page.getTotalElements() + "," +
                    "\"pageContentSize\":" + page.getContent().size() + "," +
                    "\"totalInTable\":" + totalInTable +
                    "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
        } catch (IOException ignored) {
        }
        // #endregion agent log

        return page;
    }

    /**
     * 调用存储过程生成测试数据 sp_generate_demo_export_usage
     * @param days 生成天数
     * @param rowsPerDay 每天生成行数
     * @param targetRows 目标总行数（0 表示不限制）
     * @param clearExisting 是否清空现有数据（true=清空，false=保留）
     */
    public int generateDemoData(int days, int rowsPerDay, int targetRows, boolean clearExisting) {
        long start = System.currentTimeMillis();
        // #region agent log
        try (FileWriter fw = new FileWriter("/Users/bliu/code/tiny-platform/.cursor/debug.log", true)) {
            fw.write("{\"sessionId\":\"debug-session\",\"runId\":\"pre-fix\",\"hypothesisId\":\"H1\",\"location\":\"DemoExportUsageService.generateDemoData:before\",\"message\":\"generateDemoData called\",\"data\":{\"days\":" + days + ",\"rowsPerDay\":" + rowsPerDay + ",\"targetRows\":" + targetRows + ",\"clearExisting\":" + clearExisting + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
        } catch (IOException ignored) {
        }
        // #endregion agent log

        // 简单防御性校验
        if (days <= 0) days = 7;
        if (rowsPerDay <= 0) rowsPerDay = 2000;
        if (targetRows < 0) targetRows = 0;

        // 将 boolean 转换为 TINYINT(1)：true=1, false=0
        int clearFlag = clearExisting ? 1 : 0;
        jdbcTemplate.update("CALL sp_generate_demo_export_usage(?, ?, ?, ?)", days, rowsPerDay, targetRows, clearFlag);

        long elapsed = System.currentTimeMillis() - start;
        // #region agent log
        try (FileWriter fw = new FileWriter("/Users/bliu/code/tiny-platform/.cursor/debug.log", true)) {
            fw.write("{\"sessionId\":\"debug-session\",\"runId\":\"pre-fix\",\"hypothesisId\":\"H1\",\"location\":\"DemoExportUsageService.generateDemoData:after\",\"message\":\"generateDemoData finished\",\"data\":{\"days\":" + days + ",\"rowsPerDay\":" + rowsPerDay + ",\"targetRows\":" + targetRows + ",\"clearExisting\":" + clearExisting + ",\"elapsedMs\":" + elapsed + "},\"timestamp\":" + System.currentTimeMillis() + "}\n");
        } catch (IOException ignored) {
        }
        // #endregion agent log

        // 无法直接从存储过程返回受影响行数，调用方可在前后查询总数差值
        return 0;
    }

    /**
     * 清空所有测试数据
     */
    public void clearAll() {
        repository.deleteAllInBatch();
    }
}


