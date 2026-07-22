from datetime import UTC, datetime

from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(title="feature-service")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "feature-service"}


class TrainingDatasetRequest(BaseModel):
    plantId: str
    startTime: datetime
    endTime: datetime
    featureSetVersion: str = "feature-v2-xgb"


@app.post("/internal/v1/features/training-dataset")
def training_dataset(payload: TrainingDatasetRequest) -> dict[str, object]:
    key = payload.startTime.strftime("%Y%m%d%H%M") + "-" + payload.endTime.strftime("%Y%m%d%H%M")
    return {
        "datasetUri": f"memory://feature-service/training/{payload.plantId}/{key}.parquet",
        "featureVersion": payload.featureSetVersion,
        "sampleCount": 96,
        "generatedAt": datetime.now(tz=UTC).isoformat(),
    }
