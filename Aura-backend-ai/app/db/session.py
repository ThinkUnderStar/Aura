from sqlalchemy import create_engine

from app.core.config import settings

create_engine(
    settings.MYSQL_URL,

)