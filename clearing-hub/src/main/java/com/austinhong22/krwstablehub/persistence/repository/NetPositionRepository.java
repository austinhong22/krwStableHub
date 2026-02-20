package com.austinhong22.krwstablehub.persistence.repository;

import com.austinhong22.krwstablehub.persistence.model.NetPositionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NetPositionRepository extends JpaRepository<NetPositionEntity, Long> {

    List<NetPositionEntity> findByEpochIdOrderByIdAsc(Long epochId);

    Optional<NetPositionEntity> findByEpochIdAndParticipantId(Long epochId, Long participantId);

    void deleteByEpochId(Long epochId);
}
