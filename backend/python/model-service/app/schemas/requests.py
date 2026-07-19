from pydantic import BaseModel


class TrainRequest(BaseModel):
    plantId: str = "plant-demo-001"
