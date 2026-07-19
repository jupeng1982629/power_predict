import math
from datetime import UTC, date, datetime, time, timedelta
from pathlib import Path
from typing import Any
from uuid import uuid4

import numpy as np
import xgboost as xgb
from fastapi import HTTPException

from app.repositories.db import fetch_all, fetch_one, open_connection
from app.schemas.responses import ForecastJobResultDTO

DbRow = dict[str, Any]


def latest_registered_model() -> DbRow | None:
    return fetch_one(
        """
        SELECT model_name, model_version, feature_version, framework, metrics_json, artifact_uri, train_end_time
        FROM model.model_registry_ref
        WHERE model_name = 'pv-xgboost-dayahead'
        ORDER BY train_end_time DESC NULLS LAST, updated_at DESC
        LIMIT 1
        """
    )


def build_prediction_points(plant_id: str, forecast_date: date) -> tuple[str, str, list[DbRow]]:
    rows = fetch_all(
        """
        SELECT target_time, ghi, dni, dhi, temperature, humidity, cloud_cover, wind_speed
        FROM ops.weather_forecast
        WHERE plant_id = %s
          AND target_time >= %s
          AND target_time < %s
        ORDER BY target_time ASC
        """,
        (
            plant_id,
            datetime.combine(forecast_date, time.min, tzinfo=UTC),
            datetime.combine(forecast_date + timedelta(days=1), time.min, tzinfo=UTC),
        ),
    )
    if not rows:
        raise HTTPException(status_code=404, detail="Weather forecast data is required before running day-ahead prediction")

    latest_model = latest_registered_model()
    if latest_model is None:
        raise HTTPException(status_code=404, detail="No trained XGBoost model is registered. Run model-service training first.")

    artifact_path = Path(str(latest_model["artifact_uri"]))
    if not artifact_path.exists():
        raise HTTPException(status_code=404, detail=f"Model artifact not found: {artifact_path}")

    model = xgb.XGBRegressor()
    model.load_model(artifact_path)

    lag_rows = fetch_all(
        """
        SELECT active_power_kw
        FROM ops.power_actual
        WHERE plant_id = %s
          AND ts < %s
        ORDER BY ts DESC
        LIMIT 4
        """,
        (plant_id, datetime.combine(forecast_date, time.min, tzinfo=UTC)),
    )
    if len(lag_rows) < 4:
        raise HTTPException(status_code=400, detail="At least 4 historical power points are required for inference")

    lag_values = [float(row["active_power_kw"]) for row in lag_rows]

    forecast_run_time = datetime.now(tz=UTC).replace(second=0, microsecond=0)
    model_name = str(latest_model["model_name"])
    model_version = str(latest_model["model_version"])
    points: list[DbRow] = []

    for index, row in enumerate(rows):
        target_time = row["target_time"]
        feature_vector = np.array(
            [[
                float(target_time.hour),
                float(target_time.minute // 15),
                float(target_time.weekday()),
                float(target_time.month),
                float(row["ghi"] or 0.0),
                float(row["dni"] or 0.0),
                float(row["dhi"] or 0.0),
                float(row["temperature"] or 25.0),
                float(row["humidity"] or 60.0),
                float(row["cloud_cover"] or 0.0),
                float(row["wind_speed"] or 0.0),
                lag_values[0],
                lag_values[1],
                lag_values[3],
            ]],
            dtype=float,
        )
        predicted = round(max(0.0, float(model.predict(feature_vector)[0])), 2)
        points.append(
            {
                "forecast_run_time": forecast_run_time,
                "target_time": target_time,
                "model_name": model_name,
                "model_version": model_version,
                "pred_power_kw": predicted,
                "lower_bound_kw": round(max(0.0, predicted * 0.9), 2),
                "upper_bound_kw": round(predicted * 1.1, 2),
                "horizon_minutes": index * 15,
            }
        )
        lag_values = [predicted, lag_values[0], lag_values[1], lag_values[2]]

    return model_name, model_version, points


def write_prediction_job(
    plant_id: str,
    forecast_date: date,
    requested_by: str,
) -> ForecastJobResultDTO:
    model_name, model_version, points = build_prediction_points(plant_id, forecast_date)
    existing_job = fetch_one(
        """
        SELECT job_id
        FROM ops.forecast_job
        WHERE plant_id = %s
          AND forecast_date = %s
          AND trigger_type = 'manual'
        LIMIT 1
        """,
        (plant_id, forecast_date),
    )
    job_id = existing_job["job_id"] if existing_job else f"job-{uuid4().hex[:12]}"
    started_at = datetime.now(tz=UTC)

    with open_connection() as conn:
        with conn.cursor() as cursor:
            if existing_job:
                cursor.execute(
                    """
                    UPDATE ops.forecast_job
                    SET status = 'running',
                        model_name = %s,
                        model_version = %s,
                        requested_by = %s,
                        requested_at = %s,
                        started_at = %s,
                        finished_at = NULL,
                        error_message = NULL,
                        updated_at = now()
                    WHERE job_id = %s
                    """,
                    (model_name, model_version, requested_by, started_at, started_at, job_id),
                )
            else:
                cursor.execute(
                    """
                    INSERT INTO ops.forecast_job (
                      job_id, plant_id, forecast_date, trigger_type, status, model_name, model_version,
                      requested_by, requested_at, started_at, finished_at, error_message
                    ) VALUES (%s, %s, %s, 'manual', 'running', %s, %s, %s, %s, %s, NULL, NULL)
                    """,
                    (job_id, plant_id, forecast_date, model_name, model_version, requested_by, started_at, started_at),
                )

            cursor.execute(
                """
                DELETE FROM ops.power_forecast
                WHERE plant_id = %s
                  AND target_time >= %s
                  AND target_time < %s
                  AND model_name = %s
                """,
                (
                    plant_id,
                    datetime.combine(forecast_date, time.min, tzinfo=UTC),
                    datetime.combine(forecast_date + timedelta(days=1), time.min, tzinfo=UTC),
                    model_name,
                ),
            )

            for point in points:
                cursor.execute(
                    """
                    INSERT INTO ops.power_forecast (
                      plant_id, forecast_run_time, target_time, model_name, model_version,
                      pred_power_kw, lower_bound_kw, upper_bound_kw, horizon_minutes
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
                    """,
                    (
                        plant_id,
                        point["forecast_run_time"],
                        point["target_time"],
                        point["model_name"],
                        point["model_version"],
                        point["pred_power_kw"],
                        point["lower_bound_kw"],
                        point["upper_bound_kw"],
                        point["horizon_minutes"],
                    ),
                )

            actual_rows = fetch_all(
                """
                SELECT ts, active_power_kw
                FROM ops.power_actual
                WHERE plant_id = %s
                  AND ts >= %s
                  AND ts < %s
                ORDER BY ts ASC
                """,
                (
                    plant_id,
                    datetime.combine(forecast_date, time.min, tzinfo=UTC),
                    datetime.combine(forecast_date + timedelta(days=1), time.min, tzinfo=UTC),
                ),
            )
            if actual_rows:
                actual_by_time = {row["ts"]: float(row["active_power_kw"]) for row in actual_rows}
                paired = [
                    (actual_by_time[point["target_time"]], float(point["pred_power_kw"]))
                    for point in points
                    if point["target_time"] in actual_by_time
                ]
                if paired:
                    errors = [actual - predicted for actual, predicted in paired]
                    abs_errors = [abs(value) for value in errors]
                    rmse = math.sqrt(sum(value * value for value in errors) / len(errors))
                    mae = sum(abs_errors) / len(abs_errors)
                    mape = (
                        sum(abs((actual - predicted) / actual) for actual, predicted in paired if actual > 0)
                        / max(1, sum(1 for actual, _ in paired if actual > 0))
                    ) * 100
                    mean_actual = sum(actual for actual, _ in paired) / len(paired)
                    ss_tot = sum((actual - mean_actual) ** 2 for actual, _ in paired)
                    ss_res = sum((actual - predicted) ** 2 for actual, predicted in paired)
                    r2 = 1 - (ss_res / ss_tot) if ss_tot > 0 else 0.0
                    cursor.execute(
                        """
                        INSERT INTO ops.forecast_evaluation (
                          plant_id, forecast_date, model_name, model_version, rmse, mae, mape, r2, sample_count
                        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
                        ON CONFLICT (plant_id, forecast_date, model_name, model_version) DO UPDATE
                        SET rmse = EXCLUDED.rmse,
                            mae = EXCLUDED.mae,
                            mape = EXCLUDED.mape,
                            r2 = EXCLUDED.r2,
                            sample_count = EXCLUDED.sample_count,
                            updated_at = now()
                        """,
                        (plant_id, forecast_date, model_name, model_version, rmse, mae, mape, r2, len(paired)),
                    )

            cursor.execute(
                """
                UPDATE ops.forecast_job
                SET status = 'success', finished_at = %s, updated_at = now()
                WHERE job_id = %s
                """,
                (datetime.now(tz=UTC), job_id),
            )
        conn.commit()

    return ForecastJobResultDTO(
        jobId=job_id,
        plantId=plant_id,
        forecastDate=forecast_date.isoformat(),
        modelName=model_name,
        modelVersion=model_version,
        pointsWritten=len(points),
        status="success",
    )
