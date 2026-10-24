from config.model_config import FALLBACK_MODEL, PRIMARY_MODEL, SYSTEM_PROMPT, TEMPERATURE


def test_primary_and_fallback_are_different_models():
    assert PRIMARY_MODEL.model_id != FALLBACK_MODEL.model_id


def test_output_tokens_fit_in_context_window():
    for model in (PRIMARY_MODEL, FALLBACK_MODEL):
        assert 0 < model.max_output_tokens < model.context_window


def test_temperature_is_in_supported_range():
    assert 0 <= TEMPERATURE <= 2


def test_system_prompt_is_not_blank():
    assert SYSTEM_PROMPT.strip()