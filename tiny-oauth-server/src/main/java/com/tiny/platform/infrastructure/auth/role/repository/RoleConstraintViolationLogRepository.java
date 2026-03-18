package com.tiny.platform.infrastructure.auth.role.repository;

import com.tiny.platform.infrastructure.auth.role.domain.RoleConstraintViolationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RoleConstraintViolationLogRepository extends JpaRepository<RoleConstraintViolationLog, Long>,
    JpaSpecificationExecutor<RoleConstraintViolationLog> {
}

