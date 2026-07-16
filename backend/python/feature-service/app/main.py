from fastapi import FastAPI

app = FastAPI(title="feature-service")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "feature-service"}
