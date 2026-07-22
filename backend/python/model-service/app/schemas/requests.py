from pydantic import BaseModel


class TrainRequest(BaseModel):
    plantId: str = "plant-demo-001"


class InternalTrainRequest(BaseModel):
    plantId: str = "plant-demo-001"
    datasetUri: str | None = None
    algorithm: str = "xgboost"
    featureVersion: str = "feature-v2-xgb"
