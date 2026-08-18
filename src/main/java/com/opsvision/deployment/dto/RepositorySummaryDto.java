package com.opsvision.deployment.dto;

public record RepositorySummaryDto(
        Long id,
        String owner,
        String name,
        String fullName,
        String url
) {
}
