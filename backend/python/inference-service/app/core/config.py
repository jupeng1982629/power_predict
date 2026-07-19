import os


def database_url() -> str:
    explicit = os.getenv("DATABASE_URL")
    if explicit:
        return explicit

    host = os.getenv("POSTGRES_HOST", "localhost")
    port = os.getenv("POSTGRES_PORT", "5432")
    db_name = os.getenv("POSTGRES_DB", "power_predict")
    user = os.getenv("POSTGRES_USER", "power_predict")
    password = os.getenv("POSTGRES_PASSWORD", "power_predict")
    return f"postgresql://{user}:{password}@{host}:{port}/{db_name}"
