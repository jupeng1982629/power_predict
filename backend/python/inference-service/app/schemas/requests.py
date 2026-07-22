from datetime import date

from pydantic import BaseModel


class ForecastDayAheadRequest(BaseModel):
    plantId: str = "plant-demo-001"
    forecastDate: date | None = None


class InternalInferenceDayAheadRequest(BaseModel):
    plantId: str = "plant-demo-001"
    forecastDate: date | None = None
    modelVersion: str | None = None
    weatherSnapshot: dict | None = None
