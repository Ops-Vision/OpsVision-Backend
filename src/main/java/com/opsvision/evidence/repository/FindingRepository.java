package com.opsvision.evidence.repository;

import com.opsvision.evidence.entity.Finding;
import com.opsvision.evidence.model.FindingSeverity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FindingRepository extends JpaRepository<Finding, Long> {

    List<Finding> findByDeploymentId(Long deploymentId);

    List<Finding> findByDeploymentIdAndSeverity(Long deploymentId, FindingSeverity severity);

    long countByDeploymentIdAndSeverity(Long deploymentId, FindingSeverity severity);
}
