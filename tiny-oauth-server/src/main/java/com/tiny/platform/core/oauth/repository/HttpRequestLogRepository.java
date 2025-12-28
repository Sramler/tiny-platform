package com.tiny.platform.core.oauth.repository;

import com.tiny.platform.core.oauth.model.HttpRequestLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HttpRequestLogRepository extends JpaRepository<HttpRequestLog, Long> {
}


