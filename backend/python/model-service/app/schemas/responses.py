from pydantic import BaseModel


class TrainMetricsDTO(BaseModel):
    rmse: float
    mae: float
    mape: float
    r2: float


class TrainJobResultDTO(BaseModel):
    plantId: str
    modelName: str
    modelVersion: str
    artifactUri: str
    framework: str
    sampleCount: int
    metrics: TrainMetricsDTO


class ModelLatestDTO(BaseModel):
    model_name: str
    model_version: str
    feature_version: str
    framework: str
    metrics_json: dict[str, float]
    artifact_uri: str
    stage: str
    train_end_time: str | None = None
