package com.procureai.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.procureai.config.InvestigationPolicyProperties;
import com.procureai.domain.entity.StorageCapacity;
import com.procureai.domain.entity.Warehouse;
import com.procureai.repository.StorageCapacityRepository;
import com.procureai.service.dto.StorageEvidence;
import com.procureai.service.exception.InvestigationException;
import org.springframework.stereotype.Service;

@Service
public class StorageInvestigationService {

    private final StorageCapacityRepository storageRepository;
    private final InvestigationPolicyProperties policy;
    private final Clock clock;

    public StorageInvestigationService(
            StorageCapacityRepository storageRepository,
            InvestigationPolicyProperties policy,
            Clock clock
    ) {
        this.storageRepository = storageRepository;
        this.policy = policy;
        this.clock = clock;
    }

    public StorageEvidence investigate(Warehouse warehouse) {
        StorageCapacity capacity = storageRepository.findByWarehouse(warehouse)
                .orElseThrow(() -> new InvestigationException("MISSING_STORAGE_CAPACITY",
                        "No storage capacity snapshot exists for " + warehouse.getCode()));
        if (capacity.getTotalVolumeM3() == null || capacity.getAllocatedVolumeM3() == null) {
            throw new InvestigationException("INVALID_STORAGE_CAPACITY",
                    "Storage volume values are missing for " + warehouse.getCode());
        }

        List<String> warnings = new ArrayList<>();
        boolean consistent = capacity.getTotalVolumeM3().signum() > 0
                && capacity.getAllocatedVolumeM3().signum() >= 0;
        if (!consistent) {
            warnings.add("Storage capacity values are outside their valid ranges.");
        }
        if (capacity.getAllocatedVolumeM3().compareTo(capacity.getTotalVolumeM3()) > 0) {
            consistent = false;
            warnings.add("WMS allocated volume exceeds total capacity; the inconsistency is preserved.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Duration threshold = policy.getStorageFreshnessThreshold();
        boolean fresh = capacity.getMeasuredAt() != null && !capacity.getMeasuredAt().isAfter(now)
                && !capacity.getMeasuredAt().isBefore(now.minus(threshold));
        if (capacity.getMeasuredAt() == null) {
            warnings.add("Storage measurement timestamp is missing.");
        } else if (capacity.getMeasuredAt().isAfter(now)) {
            warnings.add("Storage measurement timestamp is in the future.");
        } else if (!fresh) {
            warnings.add("Storage snapshot is older than the configured freshness threshold.");
        }
        return new StorageEvidence(warehouse.getCode(), capacity.getTotalVolumeM3(),
                capacity.getAllocatedVolumeM3(), capacity.getTotalVolumeM3().subtract(capacity.getAllocatedVolumeM3()),
                capacity.getMeasuredAt(), fresh, consistent, threshold, List.copyOf(warnings));
    }
}
