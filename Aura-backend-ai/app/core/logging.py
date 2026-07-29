import logging
import logging.handlers
from pathlib import Path

# 确保日志目录存在
LOG_DIR = Path("./logs")
LOG_DIR.mkdir(exist_ok=True)

LOG_FILE = LOG_DIR / "python_service.log"


def setup_logging():
    """配置全局日志"""
    # 根 logger
    root_logger = logging.getLogger()
    root_logger.setLevel(logging.INFO)

    # 控制台 handler（输出到终端）
    console_handler = logging.StreamHandler()
    console_handler.setLevel(logging.INFO)
    console_format = logging.Formatter(
        "[%(asctime)s] %(levelname)s - %(name)s - %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S"
    )
    console_handler.setFormatter(console_format)
    root_logger.addHandler(console_handler)

    # 文件 handler（按大小滚动，保留 5 个备份）
    file_handler = logging.handlers.RotatingFileHandler(
        LOG_FILE,
        maxBytes=10 * 1024 * 1024,  # 10MB
        backupCount=5,
        encoding="utf-8"
    )
    file_handler.setLevel(logging.INFO)
    file_format = logging.Formatter(
        "[%(asctime)s] %(levelname)s - %(name)s - %(filename)s:%(lineno)d - %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S"
    )
    file_handler.setFormatter(file_format)
    root_logger.addHandler(file_handler)

    # 特别配置第三方库的日志级别（避免噪音）
    logging.getLogger("sqlalchemy.engine").setLevel(logging.WARNING)
    logging.getLogger("uvicorn").setLevel(logging.WARNING)
    logging.getLogger("httpx").setLevel(logging.WARNING)

    return root_logger


# 初始化日志（模块导入时自动执行）
setup_logging()