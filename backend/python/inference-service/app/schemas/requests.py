from datetime import date

from pydantic import BaseModel


class ForecastDayAheadRequest(BaseModel):
    plantId: str = "plant-demo-001"
    forecastDate: date | None = None
