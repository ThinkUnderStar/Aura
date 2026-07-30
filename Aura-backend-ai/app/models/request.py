from pydantic import BaseModel


class PromptDto(BaseModel):
    prompt: str