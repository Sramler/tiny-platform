package com.tiny.platform.infrastructure.export.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface DemoExportUsageRepository extends JpaRepository<DemoExportUsageEntity, Long>,
        JpaSpecificationExecutor<DemoExportUsageEntity> {
}


