from datetime import date, datetime

from pydantic import BaseModel


class PlantOverviewDTO(BaseModel):
    plant_id: str
    plant_name: str
    capacity_mw: float
    latitude: float | None = None
    longitude: float | None = None
    tilt_angle: float | None = None
    azimuth_angle: float | None = None
    timezone: str | None = None
    status: str | None = None
    capacityKw: float


class LatestJobOverviewDTO(BaseModel):
    job_id: str
    forecast_date: str | None = None
    status: str
    model_name: str | None = None
    model_version: str | None = None
    requested_by: str | None = None
    requested_at: str | None = None
    finished_at: str | None = None


class LatestEvaluationOverviewDTO(BaseModel):
    forecast_date: str
    model_name: str
    model_version: str
    rmse: float
    mae: float
    mape: float | None = None
    r2: float | None = None
    sample_count: int
    updated_at: str


class NextWeatherOverviewDTO(BaseModel):
    forecast_run_time: str
    target_time: str
    ghi: float | None = None
    dni: float | None = None
    dhi: float | None = None
    temperature: float | None = None
    humidity: float | None = None
    cloud_cover: float | None = None
    wind_speed: float | None = None


class DataVolumeDTO(BaseModel):
    forecast_points: int
    actual_points: int
    weather_points: int


class DashboardOverviewDTO(BaseModel):
    plant: PlantOverviewDTO
    latestJob: LatestJobOverviewDTO | None = None
    latestEvaluation: LatestEvaluationOverviewDTO | None = None
    nextWeather: NextWeatherOverviewDTO | None = None
    dataVolume: DataVolumeDTO | None = None


class ForecastPointDTO(BaseModel):
    targetTime: str
    predPowerKw: float
    lowerBoundKw: float | None = None
    upperBoundKw: float | None = None
    horizonMinutes: int


class ForecastPointsDTO(BaseModel):
    plantId: str
    forecastDate: str
    forecastRunTime: str
    modelName: str
    modelVersion: str
    points: list[ForecastPointDTO]


class ActualPointDTO(BaseModel):
    ts: str
    activePowerKw: float
    curtailmentFlag: bool | None = None
    faultFlag: bool | None = None
    dataQualityFlag: str | None = None


class ActualPointsDTO(BaseModel):
    plantId: str
    startTime: str
    endTime: str
    points: list[ActualPointDTO]


class EvaluationItemDTO(BaseModel):
    forecastDate: str
    modelName: str
    modelVersion: str
    rmse: float
    mae: float
    mape: float | None = None
    r2: float | None = None
    sampleCount: int
    updatedAt: str


class EvaluationItemsDTO(BaseModel):
    plantId: str
    items: list[EvaluationItemDTO]


class ForecastJobResultDTO(BaseModel):
    jobId: str
    plantId: str
    forecastDate: str
    modelName: str
    modelVersion: str
    pointsWritten: int
    status: str
