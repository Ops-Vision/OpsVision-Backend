package com.opsvision.evidence.repository;

import com.opsvision.evidence.entity.DeploymentEvidence;
import com.opsvision.evidence.model.EvidenceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeploymentEvidenceRepository extends JpaRepository<DeploymentEvidence, Long> {

    List<DeploymentEvidence> findByDeploymentId(Long deploymentId);

    List<DeploymentEvidence> findByDeploymentIdAndEvidenceType(Long deploymentId, EvidenceType evidenceType);
}
