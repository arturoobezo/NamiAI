# Nami AI — Offline AI Assistant for Android

**Nami** means *"to understand, to comprehend, to pay attention"* in Rarámuri 
(Tarahumara), an indigenous language of the Sierra Tarahumara in México.

Nami AI is a fully offline AI assistant for Android. No internet required after 
the initial model download. All processing happens on your device — your data 
never leaves your phone.

Built with Google's Gemma 4 and LiteRT-LM for the 
**Gemma 4 Good Hackathon 2026**.

## The Problem We Solve

Millions of people in rural communities or marginalized areas of Latin America lack
reliable internet access. They are excluded from the benefits of AI,
not because AI cannot help them, but because AI requires connectivity they do not have.

Nami AI bridges this gap by bringing powerful AI capabilities entirely on-device.

## Features

- **Offline AI Chat** — Converse with state-of-the-art AI models without internet
- **Voice Assistant** — Hands-free interaction with voice recognition and speech synthesis,
- especially useful for people without formal education.
- **Vision Analysis** — Analyze photos from camera or gallery using Gemma 4's 
  multimodal capabilities
- **Offline Translator** — Translate text, voice, and images across 12 languages 
  with no internet connection
- **100% Private** — Zero data collection, no cloud, no servers

## Supported AI Models

| Model | Size | Capabilities |
|-------|------|-------------|
| Qwen 0.5B | 547 MB | Fast responses, low RAM |
| DeepSeek R1 1.5B | 1.83 GB | Reasoning & logic |
| Gemma 4 E2B Vision | 2.58 GB | Vision + chat |
| Gemma 4 E4B Vision+ | 3.65 GB | Advanced vision + chat |
| Phi-4 Mini | 3.91 GB | High instruction following |

## Supported Languages (Translator)

Spanish, English, French, German, Italian, Portuguese, Chinese, Japanese, 
Korean, Arabic, Russian, Hindi

## Requirements

- Android 7.0+
- 4 GB RAM minimum (8 GB recommended for larger models)
- Storage space depending on selected model

## Tech Stack

- **Kotlin + Jetpack Compose** — Modern Android UI
- **LiteRT-LM** — On-device inference for Gemma 4, DeepSeek, Phi-4
- **MediaPipe Tasks GenAI** — On-device inference for Qwen
- **CameraX** — Camera integration
- **Android TTS + SpeechRecognizer** — Voice capabilities
- **DataStore** — Local settings persistence

## Download

Download the latest APK from our 
[Hugging Face repository](https://huggingface.co/arturoobezo/NamiAI).

## Impact

Nami AI is designed for communities where internet is unreliable or unavailable:
- Rural communities in Latin America
- Travelers without data plans
- Privacy-conscious users worldwide
- Anyone who needs AI without depending on cloud services

## License

Apache 2.0
