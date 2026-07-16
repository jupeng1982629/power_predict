from fastapi import FastAPI

app = FastAPI(title="model-service")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "model-service"}
