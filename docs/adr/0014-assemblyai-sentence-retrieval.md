# AssemblyAI sentence timing without speaker diarization

Date: 2026-10-01. Status: accepted for the owner-requested provider repair.

The first live AssemblyAI check completed one transcription, but SourceScribe marked its stored text partial:
the default asks for segment timestamps while disabling speaker diarization. AssemblyAI returns timed words;
its `utterances` are tied to speaker labels. Request provider-native sentence boundaries from the same accepted
transcript's `/sentences` endpoint. Do not enable paid speaker labels, infer sentence boundaries, change models,
or issue a second transcription submission.

Keep the transcript's original top-level receipt and ID. Store the sentence response under an explicit
SourceScribe field in that receipt, bound to the same canonical remote ID and existing response-size limit.
A new receipt whose sentence retrieval is pending must replay as the accepted remote handle. Submission returns
that handle before performing any further GET, so the scheduler durably records acceptance first. Successful retrieval
atomically replaces it with both provider responses. Retrieval failures retain the accepted transcription and
retry only retrieval. A receipt that cannot accommodate the pending marker within the size limit fails storage;
a paid submission then remains uncertain and must not be automatically repeated. Older receipts remain readable without inventing missing timing evidence. No Room migration
or new scheduler state is needed.

Contract sources checked on 1 October 2026: [transcript submission](https://www.assemblyai.com/docs/pre-recorded-audio/api-reference/transcripts/submit),
[sentence retrieval](https://www.assemblyai.com/docs/pre-recorded-audio/api-reference/transcripts/get-sentences).
