package app.sourcescribe.ui

import androidx.annotation.StringRes
import app.sourcescribe.R

/** Sections of the in-app glossary, in reading order. */
enum class HelpSection(@StringRes val title: Int) {
    BASICS(R.string.help_section_basics),
    AUDIO(R.string.help_section_audio),
    PROVIDER(R.string.help_section_provider),
    OUTPUT(R.string.help_section_output),
    APP(R.string.help_section_app),
}

/**
 * One glossary entry. Controls that need an explanation reference a topic instead of repeating prose,
 * so the wording exists exactly once and stays translatable.
 */
enum class HelpTopic(val section: HelpSection, @StringRes val title: Int, @StringRes val body: Int) {
    WORKFLOW(HelpSection.BASICS, R.string.help_workflow_title, R.string.help_workflow_body),
    MODES(HelpSection.BASICS, R.string.help_modes_title, R.string.help_modes_body),
    STATES(HelpSection.BASICS, R.string.help_states_title, R.string.help_states_body),
    PRIVACY(HelpSection.BASICS, R.string.help_privacy_title, R.string.help_privacy_body),

    AUDIO_TRACK(HelpSection.AUDIO, R.string.help_audio_track_title, R.string.help_audio_track_body),
    DRC(HelpSection.AUDIO, R.string.help_drc_title, R.string.help_drc_body),
    CODEC(HelpSection.AUDIO, R.string.help_codec_title, R.string.help_codec_body),
    BITRATE(HelpSection.AUDIO, R.string.help_bitrate_title, R.string.help_bitrate_body),
    CAPTION_TRACK(HelpSection.AUDIO, R.string.help_caption_track_title, R.string.help_caption_track_body),

    PROVIDERS(HelpSection.PROVIDER, R.string.help_provider_title, R.string.help_provider_body),
    REGION(HelpSection.PROVIDER, R.string.help_region_title, R.string.help_region_body),
    API_KEY(HelpSection.PROVIDER, R.string.help_key_title, R.string.help_key_body),
    DIARIZATION(HelpSection.PROVIDER, R.string.help_diarization_title, R.string.help_diarization_body),
    TIMESTAMPS(HelpSection.PROVIDER, R.string.help_timestamps_title, R.string.help_timestamps_body),
    CONTEXT_TERMS(HelpSection.PROVIDER, R.string.help_context_title, R.string.help_context_body),
    LIMITS(HelpSection.PROVIDER, R.string.help_limits_title, R.string.help_limits_body),
    COST(HelpSection.PROVIDER, R.string.help_cost_title, R.string.help_cost_body),

    EXPORT_FORMATS(HelpSection.OUTPUT, R.string.help_export_formats_title, R.string.help_export_formats_body),
    EXPORT_FOLDER(HelpSection.OUTPUT, R.string.help_export_folder_title, R.string.help_export_folder_body),
    FILE_NAMES(HelpSection.OUTPUT, R.string.help_filename_title, R.string.help_filename_body),
    PROVENANCE(HelpSection.OUTPUT, R.string.help_provenance_title, R.string.help_provenance_body),

    PRESETS(HelpSection.APP, R.string.help_presets_title, R.string.help_presets_body),
    RETENTION(HelpSection.APP, R.string.help_retention_title, R.string.help_retention_body),
    STORAGE(HelpSection.APP, R.string.help_storage_title, R.string.help_storage_body),
    DIAGNOSTICS(HelpSection.APP, R.string.help_diagnostics_title, R.string.help_diagnostics_body),
    ENGINES(HelpSection.APP, R.string.help_engines_title, R.string.help_engines_body),
}
