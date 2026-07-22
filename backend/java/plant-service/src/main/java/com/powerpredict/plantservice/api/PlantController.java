package com.powerpredict.plantservice.api;

import com.powerpredict.common.api.ApiResponse;
import com.powerpredict.plantservice.domain.PlantProfile;
import com.powerpredict.plantservice.service.PlantDomainService;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plants")
public class PlantController {
  private final PlantDomainService plantDomainService;

  public PlantController(PlantDomainService plantDomainService) {
    this.plantDomainService = plantDomainService;
  }

  @GetMapping
  public ApiResponse<Map<String, Object>> list(
      @RequestParam(name = "pageNo", defaultValue = "1") int pageNo,
      @RequestParam(name = "pageSize", defaultValue = "20") int pageSize,
      @RequestParam(name = "keyword", required = false) String keyword,
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "timezone", required = false) String timezone,
      @RequestParam(name = "sortBy", required = false) String sortBy,
      @RequestParam(name = "sortDir", required = false) String sortDir) {
    PlantDomainService.PlantPageResult page = plantDomainService.list(new PlantDomainService.PlantListQuery(
        pageNo,
        pageSize,
        keyword,
        status,
        timezone,
        sortBy,
        sortDir));
    return ApiResponse.ok(Map.of(
        "items", page.items(),
        "pageNo", page.pageNo(),
        "pageSize", page.pageSize(),
        "total", page.total()));
  }

  @GetMapping("/{plantId}")
  public ApiResponse<PlantProfile> get(@PathVariable("plantId") String plantId) {
    return ApiResponse.ok(plantDomainService.getById(plantId));
  }

  @PostMapping
  public ApiResponse<PlantProfile> create(@RequestBody CreatePlantRequest request) {
    PlantProfile created = plantDomainService.create(new PlantDomainService.CreatePlantCommand(
        request.plantId,
        request.plantName,
        request.capacityMw,
        request.latitude,
        request.longitude,
        request.elevationM,
        request.tiltAngle,
        request.azimuthAngle,
        request.timezone,
        request.status));
    return ApiResponse.ok(created);
  }

  @PutMapping("/{plantId}")
  public ApiResponse<PlantProfile> update(
      @PathVariable("plantId") String plantId,
      @RequestBody UpdatePlantRequest request) {
    PlantProfile updated = plantDomainService.update(plantId, new PlantDomainService.UpdatePlantCommand(
        request.plantName,
      request.capacityMw,
        request.latitude,
        request.longitude,
      request.elevationM,
      request.tiltAngle,
      request.azimuthAngle,
        request.timezone,
        request.status));
    return ApiResponse.ok(updated);
  }

  @DeleteMapping("/{plantId}")
  public ApiResponse<Map<String, Object>> delete(@PathVariable("plantId") String plantId) {
    plantDomainService.delete(plantId);
    return ApiResponse.ok(Map.of("deleted", true, "plantId", plantId));
  }

  public static class CreatePlantRequest {
    public String plantId;
    public String plantName;
    public Double capacityMw;
    public Double latitude;
    public Double longitude;
    public Double elevationM;
    public Double tiltAngle;
    public Double azimuthAngle;
    public String timezone;
    public String status;
  }

  public static class UpdatePlantRequest {
    public String plantName;
    public Double capacityMw;
    public Double latitude;
    public Double longitude;
    public Double elevationM;
    public Double tiltAngle;
    public Double azimuthAngle;
    public String timezone;
    public String status;
  }
}
