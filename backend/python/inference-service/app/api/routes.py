from datetime import UTC, date, datetime, time, timedelta
from fastapi import APIRouter, Body, Header, Query

from app.schemas.common import ApiResponse, HealthDTO
from app.schemas.requests import ForecastDayAheadRequest, InternalInferenceDayAheadRequest
from app.schemas.responses import (
    ActualPointsDTO,
    DashboardOverviewDTO,
    EvaluationItemsDTO,
    ForecastJobResultDTO,
    ForecastPointsDTO,
)
from app.services.common import ok
from app.services.job_service import write_prediction_job
from app.services.query_service import actual_points, build_overview, evaluation_items, forecast_points

router = APIRouter()


@router.get("/health")
def health() -> HealthDTO:
    return HealthDTO(status="ok", service="inference-service")


@router.get("/api/v1/dashboard/overview")
def get_overview(plant_id: str = Query(default="plant-demo-001")) -> ApiResponse[DashboardOverviewDTO]:
    return ok(build_overview(plant_id))


@router.get("/api/v1/plants/{plant_id}/forecasts")
def get_forecasts(plant_id: str, forecast_date: date = Query(default_factory=date.today)) -> ApiResponse[ForecastPointsDTO]:
    return ok(forecast_points(plant_id, forecast_date))


@router.get("/api/v1/plants/{plant_id}/actuals")
def get_actuals(
    plant_id: str,
    start_time: datetime | None = Query(default=None),
    end_time: datetime | None = Query(default=None),
    forecast_date: date | None = Query(default=None),
) -> ApiResponse[ActualPointsDTO]:
    if forecast_date is not None:
        start = datetime.combine(forecast_date, time.min, tzinfo=UTC)
        end = datetime.combine(forecast_date + timedelta(days=1), time.min, tzinfo=UTC)
    elif start_time is not None and end_time is not None:
        start = start_time
        end = end_time
    else:
        target_date = date.today()
        start = datetime.combine(target_date, time.min, tzinfo=UTC)
        end = datetime.combine(target_date + timedelta(days=1), time.min, tzinfo=UTC)
    return ok(actual_points(plant_id, start, end))


@router.get("/api/v1/plants/{plant_id}/evaluations")
def get_evaluations(
    plant_id: str,
    start_date: date | None = Query(default=None),
    end_date: date | None = Query(default=None),
) -> ApiResponse[EvaluationItemsDTO]:
    return ok(evaluation_items(plant_id, start_date, end_date))


@router.post("/api/v1/jobs/forecast-dayahead")
def run_day_ahead_job(
    payload: ForecastDayAheadRequest = Body(default_factory=ForecastDayAheadRequest),
    x_user_id: str | None = Header(default=None, alias="X-User-Id"),
) -> ApiResponse[ForecastJobResultDTO]:
    plant_id = payload.plantId or "plant-demo-001"
    forecast_date = payload.forecastDate or date.today()
    requested_by = x_user_id or "user-demo-admin"
    return ok(write_prediction_job(plant_id, forecast_date, requested_by))


@router.post("/internal/v1/inference/dayahead")
def run_day_ahead_internal(
    payload: InternalInferenceDayAheadRequest = Body(default_factory=InternalInferenceDayAheadRequest),
    x_request_user: str | None = Header(default=None, alias="X-User-Id"),
) -> ApiResponse[ForecastJobResultDTO]:
    plant_id = payload.plantId or "plant-demo-001"
    forecast_date = payload.forecastDate or date.today()
    requested_by = x_request_user or "forecast-service"
    return ok(write_prediction_job(plant_id, forecast_date, requested_by))
