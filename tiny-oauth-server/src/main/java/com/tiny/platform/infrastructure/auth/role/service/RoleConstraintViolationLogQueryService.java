package com.tiny.platform.infrastructure.auth.role.service;

import com.tiny.platform.infrastructure.auth.role.domain.RoleConstraintViolationLog;
import com.tiny.platform.infrastructure.auth.role.repository.RoleConstraintViolationLogRepository;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
public class RoleConstraintViolationLogQueryService {

    private final RoleConstraintViolationLogRepository repository;

    public RoleConstraintViolationLogQueryService(RoleConstraintViolationLogRepository repository) {
        this.repository = repository;
    }

    public Page<RoleConstraintViolationLog> query(
        Long tenantId,
        String principalType,
        Long principalId,
        String scopeType,
        Long scopeId,
        String violationType,
        String violationCode,
        LocalDateTime createdAtFrom,
        LocalDateTime createdAtTo,
        Pageable pageable
    ) {
        Specification<RoleConstraintViolationLog> spec = (root, q, cb) -> cb.equal(root.get("tenantId"), tenantId);

        if (principalType != null && !principalType.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("principalType"), principalType));
        }
        if (principalId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("principalId"), principalId));
        }
        if (scopeType != null && !scopeType.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("scopeType"), scopeType));
        }
        if (scopeId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("scopeId"), scopeId));
        }
        if (violationType != null && !violationType.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("violationType"), violationType));
        }
        if (violationCode != null && !violationCode.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("violationCode"), violationCode));
        }
        if (createdAtFrom != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), createdAtFrom));
        }
        if (createdAtTo != null) {
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), createdAtTo));
        }

        return repository.findAll(spec, Objects.requireNonNull(pageable, "pageable"));
    }

    public Page<RoleConstraintViolationLog> queryInPlatform(
        String principalType,
        Long principalId,
        String scopeType,
        Long scopeId,
        String violationType,
        String violationCode,
        LocalDateTime createdAtFrom,
        LocalDateTime createdAtTo,
        Pageable pageable
    ) {
        Specification<RoleConstraintViolationLog> spec = (root, q, cb) -> cb.isNull(root.get("tenantId"));
        return queryWithSpecification(
            spec,
            principalType,
            principalId,
            scopeType,
            scopeId,
            violationType,
            violationCode,
            createdAtFrom,
            createdAtTo,
            pageable
        );
    }

    private Page<RoleConstraintViolationLog> queryWithSpecification(
        Specification<RoleConstraintViolationLog> spec,
        String principalType,
        Long principalId,
        String scopeType,
        Long scopeId,
        String violationType,
        String violationCode,
        LocalDateTime createdAtFrom,
        LocalDateTime createdAtTo,
        Pageable pageable
    ) {
        if (principalType != null && !principalType.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("principalType"), principalType));
        }
        if (principalId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("principalId"), principalId));
        }
        if (scopeType != null && !scopeType.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("scopeType"), scopeType));
        }
        if (scopeId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("scopeId"), scopeId));
        }
        if (violationType != null && !violationType.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("violationType"), violationType));
        }
        if (violationCode != null && !violationCode.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("violationCode"), violationCode));
        }
        if (createdAtFrom != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), createdAtFrom));
        }
        if (createdAtTo != null) {
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), createdAtTo));
        }
        return repository.findAll(spec, Objects.requireNonNull(pageable, "pageable"));
    }
}

