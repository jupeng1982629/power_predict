from fastapi import FastAPI

app = FastAPI(title="inference-service")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "inference-service"}
