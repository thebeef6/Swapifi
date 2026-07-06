# 🎵 Swapify

Swapify detecta los anuncios de Spotify y reproduce tu música local mientras tanto, silenciando Spotify automáticamente y reanudándolo cuando el anuncio termina.

## ¿Qué hace?

- Detecta cuando Spotify reproduce un anuncio (sin usar la API oficial de Spotify)
- Silencia Spotify y reproduce una canción de tu carpeta elegida
- Cuando el anuncio termina, pausa tu canción y reanuda Spotify automáticamente
- Respeta el volumen que tenías configurado

## Características

- 🎧 Reproductor de música local con controles (play/pause/anterior/siguiente)
- 📁 Selector de carpeta de música personalizada
- ☕ Sistema de donaciones (próximamente)
- 🐛 Reporte de bugs integrado
- 🌍 Soporte multiidioma (Español/English) — en desarrollo

## Cómo funciona técnicamente

- **Detección de anuncios**: Escucha broadcasts locales que emite la app de Spotify (`com.spotify.music.metadatachanged`), sin usar la API de Spotify ni requerir cuenta de desarrollador
- **Control de audio**: Usa `AudioManager` con el stream `STREAM_ALARM` para reproducir música local sin interferir con el stream principal de Spotify
- **Control de Spotify**: Envía comandos `MEDIA_BUTTON` (play/pause) sin requerir permisos invasivos como `NotificationListener`

## Stack técnico

- Kotlin
- Jetpack Compose
- ExoPlayer (Media3)
- Android Foreground Service

## Estado del proyecto

🚧 En desarrollo activo. Aún no publicado en Google Play.

## Requisitos

- Android 8.0 (API 26) o superior
- Spotify instalado (versión gratuita, con anuncios)

## Instalación (desarrollo)

1. Clona el repositorio
2. Ábrelo en Android Studio
3. Sincroniza Gradle
4. Ejecuta en un dispositivo físico (recomendado, no emulador, ya que necesita Spotify instalado)

## Licencia

Por definir.