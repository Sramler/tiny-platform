package com.tiny.platform.core.dict.service.impl;

import com.tiny.platform.core.dict.dto.DictItemQueryDto;
import com.tiny.platform.core.dict.dto.DictItemResponseDto;
import com.tiny.platform.core.dict.model.DictItem;
import com.tiny.platform.core.dict.model.DictType;
import com.tiny.platform.core.dict.repository.DictItemRepository;
import com.tiny.platform.core.dict.repository.DictTypeRepository;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = DictServiceJpaIntegrationTest.TestApplication.class,
        properties = {
                "spring.datasource.type=org.springframework.jdbc.datasource.DriverManagerDataSource",
                "spring.datasource.url=jdbc:h2:mem:dictdb;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;NON_KEYWORDS=VALUE",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.show-sql=false",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.liquibase.enabled=false"
        }
)
@ActiveProfiles("integration")
@Transactional
class DictServiceJpaIntegrationTest {

    @Autowired
    private DictTypeServiceImpl dictTypeService;

    @Autowired
    private DictItemServiceImpl dictItemService;

    @Autowired
    private DictTypeRepository dictTypeRepository;

    @Autowired
    private DictItemRepository dictItemRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void findVisibleTypes_should_only_return_platform_and_current_tenant_types() {
        TenantContext.setTenantId(7L);

        saveType("PLATFORM_STATUS", 0L, 1);
        saveType("CURRENT_STATUS", 7L, 2);
        saveType("OTHER_STATUS", 8L, 3);

        List<DictType> visibleTypes = dictTypeService.findVisibleTypes();

        assertThat(visibleTypes)
                .extracting(DictType::getDictCode)
                .containsExactly("PLATFORM_STATUS", "CURRENT_STATUS");
    }

    @Test
    void findByDictCode_should_merge_overlay_label_but_keep_platform_metadata() {
        TenantContext.setTenantId(7L);

        DictType platformType = saveType("ENABLE_STATUS", 0L, 1);
        saveItem(platformType, 0L, "ENABLED", "启用", "平台描述", false, 9);
        saveItem(platformType, 7L, "ENABLED", "租户可用", "租户描述", true, 1);

        List<DictItem> items = dictItemService.findByDictCode("ENABLE_STATUS");

        assertThat(items).hasSize(1);
        DictItem merged = items.getFirst();
        assertThat(merged.getTenantId()).isEqualTo(7L);
        assertThat(merged.getLabel()).isEqualTo("租户可用");
        assertThat(merged.getDescription()).isEqualTo("平台描述");
        assertThat(merged.getEnabled()).isFalse();
        assertThat(merged.getSortOrder()).isEqualTo(9);
    }

    @Test
    void query_for_platform_type_should_return_merged_page_under_h2() {
        TenantContext.setTenantId(7L);

        DictType platformType = saveType("ORDER_STATUS", 0L, 1);
        saveItem(platformType, 0L, "PAID", "已支付", "平台说明", false, 6);
        saveItem(platformType, 7L, "PAID", "租户已支付", "租户说明", true, 1);

        DictItemQueryDto query = new DictItemQueryDto();
        query.setDictTypeId(platformType.getId());

        Page<DictItemResponseDto> page = dictItemService.query(query, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        DictItemResponseDto item = page.getContent().getFirst();
        assertThat(item.getTenantId()).isEqualTo(7L);
        assertThat(item.getLabel()).isEqualTo("租户已支付");
        assertThat(item.getDescription()).isEqualTo("平台说明");
        assertThat(item.getEnabled()).isFalse();
        assertThat(item.getSortOrder()).isEqualTo(6);
    }

    @Test
    void query_without_type_filter_should_return_merged_page_under_h2() {
        TenantContext.setTenantId(7L);

        DictType platformType = saveType("PAY_STATUS", 0L, 1);
        saveItem(platformType, 0L, "PAID", "已支付", "平台说明", false, 6);
        saveItem(platformType, 7L, "PAID", "租户已支付", "租户说明", true, 1);

        DictItemQueryDto query = new DictItemQueryDto();

        Page<DictItemResponseDto> page = dictItemService.query(query, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        DictItemResponseDto item = page.getContent().getFirst();
        assertThat(item.getTenantId()).isEqualTo(7L);
        assertThat(item.getLabel()).isEqualTo("租户已支付");
        assertThat(item.getDescription()).isEqualTo("平台说明");
        assertThat(item.getEnabled()).isFalse();
        assertThat(item.getSortOrder()).isEqualTo(6);
    }

    private DictType saveType(String dictCode, Long tenantId, int sortOrder) {
        DictType dictType = new DictType();
        dictType.setDictCode(dictCode);
        dictType.setDictName(dictCode + "-name");
        dictType.setTenantId(tenantId);
        dictType.setEnabled(true);
        dictType.setSortOrder(sortOrder);
        return dictTypeRepository.saveAndFlush(dictType);
    }

    private DictItem saveItem(
            DictType dictType,
            Long tenantId,
            String value,
            String label,
            String description,
            boolean enabled,
            int sortOrder
    ) {
        DictItem item = new DictItem();
        item.setDictTypeId(dictType.getId());
        item.setValue(value);
        item.setLabel(label);
        item.setDescription(description);
        item.setTenantId(tenantId);
        item.setEnabled(enabled);
        item.setSortOrder(sortOrder);
        return dictItemRepository.saveAndFlush(item);
    }

    @SpringBootConfiguration
    @EntityScan(basePackageClasses = {DictType.class, DictItem.class})
    @EnableJpaRepositories(basePackageClasses = {DictTypeRepository.class, DictItemRepository.class})
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class,
            TransactionAutoConfiguration.class
    })
    @Import({DictTypeServiceImpl.class, DictItemServiceImpl.class})
    static class TestApplication {
    }
}
