from fastapi import APIRouter, Body, Query

from app.schemas.common import ApiResponse, HealthDTO
from app.schemas.requests import InternalTrainRequest, TrainRequest
from app.schemas.responses import (
    InternalTrainAcceptedDTO,
    InternalTrainStatusDTO,
    ModelLatestDTO,
    TrainJobResultDTO,
)
from app.services.common import ok
from app.services.training_service import latest_model, train_model

router = APIRouter()


@router.get("/health")
def health() -> HealthDTO:
    return HealthDTO(status="ok", service="model-service")


@router.post("/api/v1/jobs/train")
def train(payload: TrainRequest = Body(default_factory=TrainRequest)) -> ApiResponse[TrainJobResultDTO]:
    plant_id = payload.plantId or "plant-demo-001"
    return ok(train_model(plant_id))


@router.get("/api/v1/models/latest")
def latest_model_api(model_name: str = Query(default="pv-xgboost-dayahead")) -> ApiResponse[ModelLatestDTO]:
    return ok(latest_model(model_name=model_name))


@router.post("/internal/v1/models/train")
def internal_train(payload: InternalTrainRequest = Body(default_factory=InternalTrainRequest)) -> ApiResponse[InternalTrainAcceptedDTO]:
    trained = train_model(payload.plantId or "plant-demo-001")
    return ok(InternalTrainAcceptedDTO(
        trainingJobId=trained.modelVersion,
        modelVersion=trained.modelVersion,
        status="success",
    ))


@router.get("/internal/v1/models/train/{training_job_id}")
def internal_train_status(training_job_id: str) -> ApiResponse[InternalTrainStatusDTO]:
    latest = latest_model("pv-xgboost-dayahead")
    if latest.model_version != training_job_id:
        return ok(InternalTrainStatusDTO(
            trainingJobId=training_job_id,
            status="not_found",
            modelVersion=None,
            metrics=None,
        ))
    return ok(InternalTrainStatusDTO(
        trainingJobId=training_job_id,
        status="success",
        modelVersion=latest.model_version,
        metrics=latest.metrics_json,
    ))
