from datetime import UTC, date, datetime, time, timedelta
from typing import Any, Mapping

from fastapi import HTTPException

from app.repositories.db import fetch_all, fetch_one
from app.schemas.responses import (
    ActualPointDTO,
    ActualPointsDTO,
    DashboardOverviewDTO,
    DataVolumeDTO,
    EvaluationItemDTO,
    EvaluationItemsDTO,
    ForecastPointDTO,
    ForecastPointsDTO,
    LatestEvaluationOverviewDTO,
    LatestJobOverviewDTO,
    NextWeatherOverviewDTO,
    PlantOverviewDTO,
)
from app.services.common import to_date_text, to_datetime_text


def require_row(row: Mapping[str, object] | None, message: str) -> Mapping[str, object]:
    if row is None:
        raise HTTPException(status_code=404, detail=message)
    return row


def build_overview(plant_id: str) -> DashboardOverviewDTO:
    plant = require_row(
        fetch_one(
            """
            SELECT plant_id, plant_name, capacity_mw, latitude, longitude, tilt_angle, azimuth_angle, timezone, status
            FROM mdm.plant_info
            WHERE plant_id = %s
            """,
            (plant_id,),
        ),
        f"Plant {plant_id} not found",
    )
    latest_job = fetch_one(
        """
        SELECT job_id, forecast_date, status, model_name, model_version, requested_by, requested_at, finished_at
        FROM ops.forecast_job
        WHERE plant_id = %s
        ORDER BY requested_at DESC
        LIMIT 1
        """,
        (plant_id,),
    )
    latest_eval = fetch_one(
        """
        SELECT forecast_date, model_name, model_version, rmse, mae, mape, r2, sample_count, updated_at
        FROM ops.forecast_evaluation
        WHERE plant_id = %s
        ORDER BY forecast_date DESC, updated_at DESC
        LIMIT 1
        """,
        (plant_id,),
    )
    next_weather = fetch_one(
        """
        SELECT forecast_run_time, target_time, ghi, dni, dhi, temperature, humidity, cloud_cover, wind_speed
        FROM ops.weather_forecast
        WHERE plant_id = %s
        ORDER BY forecast_run_time DESC, target_time ASC
        LIMIT 1
        """,
        (plant_id,),
    )
    point_counts = fetch_one(
        """
        SELECT
          (SELECT count(*) FROM ops.power_forecast WHERE plant_id = %s) AS forecast_points,
          (SELECT count(*) FROM ops.power_actual WHERE plant_id = %s) AS actual_points,
          (SELECT count(*) FROM ops.weather_forecast WHERE plant_id = %s) AS weather_points
        """,
        (plant_id, plant_id, plant_id),
    )

    plant_dto = PlantOverviewDTO(**plant, capacityKw=float(plant["capacity_mw"]) * 1000)
    latest_job_dto = (
        LatestJobOverviewDTO(
            **latest_job,
            forecast_date=to_date_text(latest_job["forecast_date"]),
            requested_at=to_datetime_text(latest_job["requested_at"]),
            finished_at=to_datetime_text(latest_job["finished_at"]),
        )
        if latest_job
        else None
    )
    latest_eval_dto = (
        LatestEvaluationOverviewDTO(
            **latest_eval,
            forecast_date=to_date_text(latest_eval["forecast_date"]),
            updated_at=to_datetime_text(latest_eval["updated_at"]),
        )
        if latest_eval
        else None
    )
    next_weather_dto = (
        NextWeatherOverviewDTO(
            **next_weather,
            forecast_run_time=to_datetime_text(next_weather["forecast_run_time"]),
            target_time=to_datetime_text(next_weather["target_time"]),
        )
        if next_weather
        else None
    )
    data_volume_dto = DataVolumeDTO(**point_counts) if point_counts else None

    return DashboardOverviewDTO(
        plant=plant_dto,
        latestJob=latest_job_dto,
        latestEvaluation=latest_eval_dto,
        nextWeather=next_weather_dto,
        dataVolume=data_volume_dto,
    )


def forecast_points(plant_id: str, forecast_date: date) -> ForecastPointsDTO:
    rows = fetch_all(
        """
        WITH latest_run AS (
            SELECT max(forecast_run_time) AS forecast_run_time
            FROM ops.power_forecast
            WHERE plant_id = %s
              AND target_time >= %s
              AND target_time < %s
        )
        SELECT pf.forecast_run_time, pf.target_time, pf.model_name, pf.model_version, pf.pred_power_kw, pf.lower_bound_kw, pf.upper_bound_kw, pf.horizon_minutes
        FROM ops.power_forecast pf
        JOIN latest_run lr
          ON pf.forecast_run_time = lr.forecast_run_time
        WHERE pf.plant_id = %s
          AND pf.target_time >= %s
          AND pf.target_time < %s
        ORDER BY target_time ASC
        """,
        (
            plant_id,
            datetime.combine(forecast_date, time.min, tzinfo=UTC),
            datetime.combine(forecast_date + timedelta(days=1), time.min, tzinfo=UTC),
            plant_id,
            datetime.combine(forecast_date, time.min, tzinfo=UTC),
            datetime.combine(forecast_date + timedelta(days=1), time.min, tzinfo=UTC),
        ),
    )
    if not rows:
        raise HTTPException(status_code=404, detail=f"No forecast points found for {plant_id} on {forecast_date.isoformat()}")

    points = [
        ForecastPointDTO(
            targetTime=to_datetime_text(row["target_time"]),
            predPowerKw=float(row["pred_power_kw"]),
            lowerBoundKw=float(row["lower_bound_kw"]) if row["lower_bound_kw"] is not None else None,
            upperBoundKw=float(row["upper_bound_kw"]) if row["upper_bound_kw"] is not None else None,
            horizonMinutes=row["horizon_minutes"],
        )
        for row in rows
    ]
    return ForecastPointsDTO(
        plantId=plant_id,
        forecastDate=forecast_date.isoformat(),
        forecastRunTime=to_datetime_text(rows[0]["forecast_run_time"]),
        modelName=rows[0]["model_name"],
        modelVersion=rows[0]["model_version"],
        points=points,
    )


def actual_points(plant_id: str, start_time: datetime, end_time: datetime) -> ActualPointsDTO:
    rows = fetch_all(
        """
        SELECT ts, active_power_kw, curtailment_flag, fault_flag, data_quality_flag
        FROM ops.power_actual
        WHERE plant_id = %s
          AND ts >= %s
          AND ts < %s
        ORDER BY ts ASC
        """,
        (plant_id, start_time, end_time),
    )
    points = [
        ActualPointDTO(
            ts=to_datetime_text(row["ts"]),
            activePowerKw=float(row["active_power_kw"]),
            curtailmentFlag=row["curtailment_flag"],
            faultFlag=row["fault_flag"],
            dataQualityFlag=row["data_quality_flag"],
        )
        for row in rows
    ]
    return ActualPointsDTO(
        plantId=plant_id,
        startTime=to_datetime_text(start_time),
        endTime=to_datetime_text(end_time),
        points=points,
    )


def evaluation_items(plant_id: str, start_date: date | None, end_date: date | None) -> EvaluationItemsDTO:
    filters: list[str] = ["plant_id = %s"]
    params: list[Any] = [plant_id]
    if start_date:
        filters.append("forecast_date >= %s")
        params.append(start_date)
    if end_date:
        filters.append("forecast_date <= %s")
        params.append(end_date)
    where_clause = " AND ".join(filters)
    rows = fetch_all(
        f"""
        SELECT forecast_date, model_name, model_version, rmse, mae, mape, r2, sample_count, updated_at
        FROM ops.forecast_evaluation
        WHERE {where_clause}
        ORDER BY forecast_date DESC
        """,
        tuple(params),
    )
    items = [
        EvaluationItemDTO(
            forecastDate=to_date_text(row["forecast_date"]),
            modelName=row["model_name"],
            modelVersion=row["model_version"],
            rmse=float(row["rmse"]),
            mae=float(row["mae"]),
            mape=float(row["mape"]) if row["mape"] is not None else None,
            r2=float(row["r2"]) if row["r2"] is not None else None,
            sampleCount=row["sample_count"],
            updatedAt=to_datetime_text(row["updated_at"]),
        )
        for row in rows
    ]
    return EvaluationItemsDTO(plantId=plant_id, items=items)
