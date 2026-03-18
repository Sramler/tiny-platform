package com.tiny.platform.infrastructure.auth.role.service;

import com.tiny.platform.infrastructure.auth.role.domain.RoleConstraintViolationLog;
import com.tiny.platform.infrastructure.auth.role.repository.RoleConstraintViolationLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.lang.NonNull;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleConstraintViolationLogWriteService {

    private final RoleConstraintViolationLogRepository repository;

    public RoleConstraintViolationLogWriteService(RoleConstraintViolationLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(@NonNull RoleConstraintViolationLog log) {
        repository.save(log);
    }
}

