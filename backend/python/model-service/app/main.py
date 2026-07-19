from fastapi import FastAPI

from app.api.routes import router

app = FastAPI(title="model-service")
app.include_router(router)
