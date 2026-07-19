import json
from datetime import UTC, date, datetime
from typing import Any

import numpy as np
import xgboost as xgb
from fastapi import HTTPException

from app.core.config import model_store_dir
from app.repositories.db import execute, fetch_all
from app.schemas.responses import ModelLatestDTO, TrainJobResultDTO, TrainMetricsDTO


def build_training_dataset(plant_id: str) -> tuple[np.ndarray, np.ndarray, list[str]]:
    rows = fetch_all(
        """
        SELECT
          a.ts,
          extract(hour from a.ts) AS hour_of_day,
          extract(minute from a.ts) / 15 AS minute_slot,
          extract(dow from a.ts) AS day_of_week,
          extract(month from a.ts) AS month_of_year,
          COALESCE(w.ghi, 0) AS ghi,
          COALESCE(w.dni, 0) AS dni,
          COALESCE(w.dhi, 0) AS dhi,
          COALESCE(w.temperature, 25) AS temperature,
          COALESCE(w.humidity, 60) AS humidity,
          COALESCE(w.cloud_cover, 0) AS cloud_cover,
          COALESCE(w.wind_speed, 0) AS wind_speed,
          a.active_power_kw,
          lag(a.active_power_kw, 1) OVER (ORDER BY a.ts) AS power_lag_1,
          lag(a.active_power_kw, 2) OVER (ORDER BY a.ts) AS power_lag_2,
          lag(a.active_power_kw, 4) OVER (ORDER BY a.ts) AS power_lag_4
        FROM ops.power_actual a
        LEFT JOIN ops.weather_forecast w
          ON w.plant_id = a.plant_id
         AND w.target_time = a.ts
        WHERE a.plant_id = %s
        ORDER BY a.ts ASC
        """,
        (plant_id,),
    )
    usable = [row for row in rows if row["power_lag_4"] is not None]
    if len(usable) < 64:
        raise HTTPException(status_code=400, detail="Not enough historical samples to train the XGBoost model")

    feature_names = [
        "hour_of_day",
        "minute_slot",
        "day_of_week",
        "month_of_year",
        "ghi",
        "dni",
        "dhi",
        "temperature",
        "humidity",
        "cloud_cover",
        "wind_speed",
        "power_lag_1",
        "power_lag_2",
        "power_lag_4",
    ]

    features = np.array(
        [
            [
                float(row[name])
                for name in feature_names
            ]
            for row in usable
        ],
        dtype=float,
    )
    target = np.array([float(row["active_power_kw"]) for row in usable], dtype=float)
    return features, target, feature_names


def metrics(actual: np.ndarray, predicted: np.ndarray) -> dict[str, float]:
    errors = actual - predicted
    rmse = float(np.sqrt(np.mean(np.square(errors))))
    mae = float(np.mean(np.abs(errors)))
    mask = actual != 0
    mape = float(np.mean(np.abs(errors[mask] / actual[mask])) * 100) if np.any(mask) else 0.0
    denom = float(np.sum(np.square(actual - np.mean(actual))))
    r2 = float(1 - np.sum(np.square(errors)) / denom) if denom > 0 else 0.0
    return {"rmse": rmse, "mae": mae, "mape": mape, "r2": r2}


def train_model(plant_id: str) -> TrainJobResultDTO:
    features, target, feature_names = build_training_dataset(plant_id)
    split = max(48, int(len(features) * 0.8))
    train_x, valid_x = features[:split], features[split:]
    train_y, valid_y = target[:split], target[split:]

    model = xgb.XGBRegressor(
        n_estimators=200,
        max_depth=6,
        learning_rate=0.05,
        subsample=0.9,
        colsample_bytree=0.9,
        objective="reg:squarederror",
        random_state=42,
    )
    model.fit(train_x, train_y)

    eval_x = valid_x if len(valid_x) > 0 else train_x
    eval_y = valid_y if len(valid_y) > 0 else train_y
    predicted = model.predict(eval_x)
    metric_values = metrics(eval_y, predicted)

    version = datetime.now(tz=UTC).strftime("%Y.%m.%d.%H%M%S")
    model_name = "pv-xgboost-dayahead"
    artifact_path = model_store_dir() / f"{model_name}-{version}.json"
    model.save_model(artifact_path)

    execute(
        """
        INSERT INTO model.model_registry_ref (
          model_name, model_version, feature_version, framework, metrics_json, artifact_uri, stage,
          train_start_time, train_end_time
        ) VALUES (%s, %s, %s, %s, %s::jsonb, %s, 'production', now() - interval '30 second', now())
        ON CONFLICT (model_name, model_version) DO UPDATE
        SET feature_version = EXCLUDED.feature_version,
            framework = EXCLUDED.framework,
            metrics_json = EXCLUDED.metrics_json,
            artifact_uri = EXCLUDED.artifact_uri,
            stage = EXCLUDED.stage,
            train_start_time = EXCLUDED.train_start_time,
            train_end_time = EXCLUDED.train_end_time,
            updated_at = now()
        """,
        (
            model_name,
            version,
            "feature-v2-xgb",
            "XGBoost",
            json.dumps(metric_values),
            str(artifact_path),
        ),
    )

    execute(
        """
        INSERT INTO model.feature_snapshot (plant_id, feature_time, feature_version, feature_payload, dataset_role)
        VALUES (%s, %s, %s, %s::jsonb, %s)
        ON CONFLICT (plant_id, feature_time, feature_version, dataset_role) DO UPDATE
        SET feature_payload = EXCLUDED.feature_payload,
            updated_at = now()
        """,
        (
            plant_id,
            datetime.now(tz=UTC),
            "feature-v2-xgb",
            json.dumps({"featureNames": feature_names}),
            "training",
        ),
    )

    execute(
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
        (
            plant_id,
            date.today(),
            model_name,
            version,
            metric_values["rmse"],
            metric_values["mae"],
            metric_values["mape"],
            metric_values["r2"],
            int(len(eval_y)),
        ),
    )

    return TrainJobResultDTO(
        plantId=plant_id,
        modelName=model_name,
        modelVersion=version,
        artifactUri=str(artifact_path),
        framework="XGBoost",
        sampleCount=int(len(features)),
        metrics=TrainMetricsDTO(**metric_values),
    )


def latest_model(model_name: str = "pv-xgboost-dayahead") -> ModelLatestDTO:
    rows = fetch_all(
        """
        SELECT model_name, model_version, feature_version, framework, metrics_json, artifact_uri, stage, train_end_time
        FROM model.model_registry_ref
        WHERE model_name = %s
        ORDER BY train_end_time DESC NULLS LAST, updated_at DESC
        LIMIT 1
        """,
        (model_name,),
    )
    if not rows:
        raise HTTPException(status_code=404, detail=f"No model found for {model_name}")
    item = rows[0]
    item["metrics_json"] = item["metrics_json"] if isinstance(item["metrics_json"], dict) else json.loads(item["metrics_json"])
    train_end_time = item.get("train_end_time")
    return ModelLatestDTO(
        model_name=item["model_name"],
        model_version=item["model_version"],
        feature_version=item["feature_version"],
        framework=item["framework"],
        metrics_json=item["metrics_json"],
        artifact_uri=item["artifact_uri"],
        stage=item["stage"],
        train_end_time=train_end_time.isoformat() if train_end_time else None,
    )
