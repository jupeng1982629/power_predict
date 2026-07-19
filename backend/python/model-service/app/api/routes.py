from fastapi import APIRouter, Body, Query

from app.schemas.common import ApiResponse, HealthDTO
from app.schemas.requests import TrainRequest
from app.schemas.responses import ModelLatestDTO, TrainJobResultDTO
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
