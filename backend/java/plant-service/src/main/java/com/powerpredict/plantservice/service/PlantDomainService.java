package com.powerpredict.plantservice.service;

import com.powerpredict.plantservice.domain.PlantProfile;
import java.util.List;

public interface PlantDomainService {
  PlantPageResult list(PlantListQuery query);

  PlantProfile getById(String plantId);

  PlantProfile create(CreatePlantCommand command);

  PlantProfile update(String plantId, UpdatePlantCommand command);

  void delete(String plantId);

    record PlantListQuery(
      int pageNo,
      int pageSize,
      String keyword,
      String status,
      String timezone,
      String sortBy,
      String sortDir
    ) {
    }

    record PlantPageResult(
      List<PlantProfile> items,
      int pageNo,
      int pageSize,
      long total
    ) {
    }

  record CreatePlantCommand(
      String plantId,
      String plantName,
      Double capacityMw,
      Double latitude,
      Double longitude,
      Double elevationM,
      Double tiltAngle,
      Double azimuthAngle,
      String timezone,
      String status
  ) {
  }

  record UpdatePlantCommand(
      String plantName,
      Double capacityMw,
      Double latitude,
      Double longitude,
      Double elevationM,
      Double tiltAngle,
      Double azimuthAngle,
      String timezone,
      String status
  ) {
  }
}
