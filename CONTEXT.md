# Swapifi — Contexto del proyecto

## ¿Qué hace?
Swapifi detecta los anuncios de Spotify y reproduce música local del usuario mientras tanto, silenciando Spotify automáticamente y reanudándolo cuando el anuncio termina.

## Cómo funciona técnicamente

### Detección de anuncios
- Escucha broadcasts locales de Spotify (`com.spotify.music.playbackstatechanged`, `metadatachanged`, `queuechanged`) sin usar la API oficial
- Calcula el tiempo restante de la canción (`length - playbackPosition`) y programa un temporizador para silenciar al final
- No usa polling ni API de Spotify, solo eventos del sistema Android

### Control de audio
- Usa `STREAM_MUSIC` para Spotify y `STREAM_ALARM` para la música local
- Al detectar anuncio: baja `STREAM_MUSIC` a 0 y ajusta `STREAM_ALARM` proporcionalmente al volumen que tenía Spotify
- Sistema de variables de volumen:
    - `originalAlarmVolume` (A): volumen de alarma original del usuario, se restaura siempre al volver a Spotify
    - `spotifyMusicVolume` (B): volumen de música de Spotify, se actualiza en tiempo real via `ContentObserver`
    - `userAlarmVolumeWhileLocal` (C): volumen de alarma que el usuario ajusta durante la música local, se convierte en el nuevo volumen de Spotify al reanudar

### Control de Spotify
- Envía comandos `MEDIA_BUTTON` (play/pause) para controlar Spotify sin permisos invasivos
- Cuando termina la canción local, espera a que Spotify intente reanudar, lo pausa, y cuando la canción local acaba del todo le manda PLAY

### Reproductor local
- Usa ExoPlayer (Media3) con `USAGE_ALARM` y `STREAM_ALARM`
- Selecciona canciones aleatoriamente de la carpeta elegida por el usuario
- Expone `currentPosition` y `duration` como `MutableState` para la barra de progreso en la UI

## Stack técnico
- Kotlin + Jetpack Compose
- ExoPlayer (Media3)
- Android Foreground Service
- ContentObserver para cambios de volumen en tiempo real
- MediaStore para listar archivos de audio

## Estructura de archivos clave
services/AudioService.kt      → lógica principal (detección, volumen, control Spotify)
player/LocalPlayer.kt         → reproductor ExoPlayer con callbacks
state/PlayerState.kt          → estado compartido entre UI y Service
utils/RawMusicExporter.kt     → exporta canciones de raw/ a Music/Swapifi/ al instalar
ui/MainActivity.kt            → pantalla principal
ui/PlayerSection.kt           → reproductor con barra de progreso y controles
ui/FileExplorerSection.kt     → lista de canciones filtrada por carpeta
ui/TopBar.kt                  → barra superior con menús
ui/DonatePopup.kt             → donaciones via Ko-fi
ui/HelpPopup.kt               → ayuda primer uso
ui/SettingsPopup.kt           → ajustes (carpeta de música)
ui/BugReportPopup.kt          → reporte de bugs via email
ui/ContactPopup.kt            → contacto
ui/AboutPopup.kt              → acerca de
ui/FolderPickerPopup.kt       → selector de carpeta

## Decisiones técnicas importantes
- **No se usa la API de Spotify** — solo broadcasts locales del sistema, igual que Mutify
- **No se usa NotificationListener** — demasiado invasivo para el usuario
- **STREAM_ALARM en lugar de STREAM_MUSIC** para la música local — permite que suenen simultáneamente sin interferirse
- **No se usa Audio Focus** — Spotify pausaba el anuncio al perder el foco, rompiendo el ciclo de detección
- **MediaStore** para listar archivos de audio en Android 13+

## Problemas pendientes

### 🔴 Importantes
- Música local suena simultáneamente por auriculares Bluetooth Y por el altavoz — STREAM_ALARM ignora el routing de audio
- Música local atenuada en auriculares BT (diagnosticado 15-jul-2026, verificado en MIUI): la causa es el índice de alarma del dispositivo BT (`volume_alarm_bt_a2dp`), que con valores bajos mete atenuaciones de −40dB que el boost (capado a +36dB, con compresión del limitador) no puede tapar del todo. Verificado en dispositivo:
    - El panel de volumen de MIUI SÍ escribe el índice BT, pero **solo mientras la música local está sonando por BT**; con el índice a 15 la atenuación baja a −9dB, el boost queda en ~700mB y suena bien (confirmado por el usuario)
    - Escribir `volume_alarm_bt_a2dp` con `settings put` NO sobrevive a la reconexión (el audioserver re-persiste su valor en memoria)
    - Los `setStreamVolume(STREAM_ALARM)` de la app en `mute()` van al índice del altavoz porque se ejecutan ANTES de que el stream esté activo en BT — sin probar aún: re-asertar la escritura CON la reproducción ya activa podría escribir el índice BT como hace el panel
    - `STREAM_MUSIC=0` NO afecta al volumen absoluto A2DP en este dispositivo (descartado)
    - `LocalPlayer` recalibra el boost en vivo cada 500ms si el índice BT cambia, y acota el índice a `getStreamMinVolume` (a índice 1 `getStreamVolumeDb` lanza "Invalid stream volume index")
    - Pendiente de decidir: automatizar la subida del índice de alarma BT durante la reproducción local vs. mostrar un aviso al usuario para que suba el slider de alarma una vez

### 🟡 Cambios mínimos
- Cambiar emails placeholder en ContactPopup.kt y BugReportPopup.kt
- Cambiar URL Ko-fi en DonatePopup.kt por URL real (https://ko-fi.com/TUNOMBRE)
- Cambiar el icono de la app
- Crear cuenta Ko-fi con nombre Swapifi

## Idiomas soportados
- Español (por defecto) — `res/values/strings.xml`
- Inglés — `res/values-en/strings.xml`
- La app sigue el idioma del sistema automáticamente

## Requisitos
- Android 8.0 (API 26) mínimo
- Spotify instalado en versión gratuita (con anuncios)
- Permiso `READ_MEDIA_AUDIO` (Android 13+) o `READ_EXTERNAL_STORAGE` (Android 12-)