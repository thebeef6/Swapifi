# Swapifi — Contexto del proyecto

## ¿Qué hace?
Swapifi detecta los anuncios de Spotify y reproduce música local del usuario mientras tanto, silenciando Spotify automáticamente y reanudándolo cuando el anuncio termina.

## Cómo funciona técnicamente

### Detección de anuncios
- Escucha broadcasts locales de Spotify (`com.spotify.music.playbackstatechanged`, `metadatachanged`, `queuechanged`) sin usar la API oficial
- Calcula el tiempo restante de la canción (`length - playbackPosition`) y programa un temporizador (con un pequeño margen tras el final previsto) para silenciar al final
- Mute en dos fases con ventana de confirmación (~1,3 s): al dispararse el temporizador solo se baja Spotify a 0; si el broadcast de la siguiente canción llega dentro de la ventana era una transición normal (se restaura el volumen, sin música local), y si no llega se confirma el anuncio y arranca la música local. Evita falsos positivos cuando el broadcast de la siguiente canción llega tarde
- No usa polling ni API de Spotify, solo eventos del sistema Android
- El manifest declara `<queries>` para `com.spotify.music`: sin ello, Android 11+ bloquea silenciosamente los `sendOrderedBroadcast` de los comandos PLAY/PAUSE

### Control de audio
- Usa `STREAM_MUSIC` para Spotify y `STREAM_ALARM` para la música local
- Al detectar anuncio: baja `STREAM_MUSIC` a 0 y ajusta `STREAM_ALARM` proporcionalmente al volumen que tenía Spotify
- Sistema de variables de volumen:
    - `originalAlarmVolume` (A): volumen de alarma original del usuario, se restaura siempre al volver a Spotify
    - `spotifyMusicVolume` (B): volumen de música de Spotify, se actualiza en tiempo real via `ContentObserver`
    - `userAlarmVolumeWhileLocal` (C): volumen de alarma que el usuario ajusta durante la música local, se convierte en el nuevo volumen de Spotify al reanudar
- Tras restaurar, `alarmReassertRunnable` reintenta una vez la escritura de A (`ALARM_REASSERT_DELAY_MS`, 1500ms): el sistema a veces la pisa mientras el stream se desactiva. La ventana de eco de nuestras propias escrituras (`ALARM_ECHO_GUARD_MS`) se deriva de ese mismo valor (+500ms) para que nunca quede una brecha sin cubrir entre ambos — una brecha así permitía que un empujón del sistema se adoptara como cambio genuino del usuario y corrompiera A de forma permanente y acumulativa ciclo a ciclo (bug confirmado y arreglado 2026-07-20)

### Compensación de volumen en auriculares Bluetooth
Los índices de stream en Android son por dispositivo, y el índice de alarma del auricular BT (`volume_alarm_bt_a2dp` en `Settings.System`) no es escribible por la app: con valores bajos el sistema atenúa la música local hasta −40dB respecto a Spotify. `LocalPlayer` lo compensa digitalmente:
- Lee el índice BT persistido (o lo estima con el índice visible si la clave aún no existe), lo acota a `getStreamMinVolume`/`Max` y lo convierte a dB con `getStreamVolumeDb()`
- Iguala la sonoridad objetivo de Spotify: `player.volume` si hay que atenuar, `LoudnessEnhancer` si hay que amplificar (tope +36dB; validado por oído 2026-07-21 que a +28dB suena bien — su limitador no degrada de forma apreciable). `DynamicsProcessing` (solo ganancia de entrada, sin limitador) queda como plan B si LE falla al crearse, pero es un camino SIN validar: en el dispositivo de prueba su creación falla con `AudioEffect: bad parameter value`. Sin datos de curva, boost fijo de emergencia +12dB
- **Creación del efecto de ganancia (bug histórico, arreglado 2026-07-21)**: el efecto se creaba solo en `onAudioSessionIdChanged`, pero Media3 genera el id de sesión al construir el player y ese callback ya no se dispara en el arranque — resultado: **el efecto no se creaba nunca y ningún boost se aplicó jamás**; ese era el verdadero origen del "volumen bajo/plano en BT" (no el limitador del LE, teoría que resultó falsa). Ahora `play()` lee `player.audioSessionId` directamente (el callback se mantiene por si el id cambiara a mitad), y además el efecto se recrea 800ms después del arranque y de cada cambio de dispositivo de salida — el track migra de hilo al aplicar `setPreferredAudioDevice` y un efecto creado antes de la migración podría quedarse atrás (medida preventiva; la pérdida por migración no se llegó a confirmar de forma aislada). Diagnóstico hecho con `dumpsys media.audio_flinger` (el track sonaba con −30dB y su sesión no tenía cadena de efectos) + logcat (jamás aparecía el log de creación)
- Recalibra en vivo (cada 500ms) si el índice BT cambia — p. ej. si el usuario mueve el slider de Alarma con la música sonando
- Verificado en MIUI: el panel de volumen del sistema solo escribe el índice BT **mientras la música local suena por BT**; con el índice al máximo la atenuación baja a −9dB y el boost queda mínimo (sonido limpio). Escribirlo por `adb settings put` no sobrevive a la reconexión
- **Fix B — ABANDONADO (2026-07-20)**: se intentó "enseñar" al sistema un `volume_alarm_bt_a2dp` alto para reducir la atenuación real en vez de compensarla solo digitalmente. Cuatro intentos reales en dispositivo, todos descartados:
    1. Re-escribir `STREAM_ALARM` al máximo (imitando al panel de volumen) — con el índice visible ya al máximo era un no-op que el framework ni notificaba.
    2. Forzar una transición real (bajar un escalón y subir de vuelta) — confirmada como cambio real por el log, pero `volume_alarm_bt_a2dp` no se movió.
    3. Añadir `AudioManager.FLAG_SHOW_UI` a esas escrituras — tampoco.
    4. Escribir `volume_alarm_bt_a2dp` directamente con `Settings.System.putInt` (con permiso `WRITE_SETTINGS` concedido) — lanza `"You cannot keep your settings in the secure settings"`: esa clave vive en `Settings.Secure`, no en `System`, así que `WRITE_SETTINGS` nunca fue el permiso correcto. Escribirla de verdad requeriría `WRITE_SECURE_SETTINGS`, que un usuario normal no puede conceder (solo vía `adb shell pm grant`, inviable para una app publicada) — **conclusión: esta vía está cerrada en cualquier dispositivo Android, no es específico del hardware de David**. Se revirtieron el permiso `WRITE_SETTINGS`, la UI en Ajustes y el código de `LocalPlayer`
    - Con `volume_alarm_bt_a2dp` fijo (en el dispositivo de prueba, en 7 → −30dB), la compensación puramente digital (`LoudnessEnhancer`) necesita boosts de hasta +28dB para volumen máximo — por debajo del tope de +36dB, pero un boost así de grande ya suena comprimido/plano por el limitador del propio efecto, lo que explica el volumen bajo percibido pese a que la app compensa correctamente en el papel
- `USAGE_ALARM` duplica la salida al altavoz además del auricular; se evita fijando el dispositivo preferido con `setPreferredAudioDevice` (prioridad A2DP > USB > jack)

### Control de Spotify
- Envía comandos `MEDIA_BUTTON` (play/pause) para controlar Spotify sin permisos invasivos
- Cuando termina la canción local, espera a que Spotify intente reanudar, lo pausa, y cuando la canción local acaba del todo le manda PLAY

### Reproductor local
- Usa ExoPlayer (Media3) con `USAGE_ALARM` y `STREAM_ALARM`, sin audio focus (`handleAudioFocus=false`)
- Selecciona canciones aleatoriamente de la carpeta elegida por el usuario; si no hay carpeta con canciones, cae a las tres canciones incluidas en `raw/`
- Expone `currentPosition` y `duration` como `MutableState` para la barra de progreso en la UI
- `AudioService` publica una notificación multimedia con `MediaSession` (play/pausa/siguiente/anterior, barra de progreso y seek), sincronizada con la UI vía `PlayerState`

### Primera ejecución
- `RawMusicExporter` exporta las canciones de `raw/` a `Música/Swapifi/`, que es la carpeta por defecto hasta que el usuario elige otra (su elección se guarda en SharedPreferences)
- Se muestra un popup de ayuda la primera vez y se piden los permisos de audio y notificaciones

## Stack técnico
- Kotlin + Jetpack Compose
- ExoPlayer (Media3)
- Android Foreground Service (`mediaPlayback`) + MediaSession
- ContentObserver para cambios de volumen en tiempo real
- MediaStore para listar archivos de audio

## Estructura de archivos clave
MainActivity.kt               → pantalla principal, petición de permisos, arranque del servicio
services/AudioService.kt      → lógica principal (detección, volumen, control Spotify, notificación multimedia)
player/LocalPlayer.kt         → reproductor ExoPlayer con compensación de ganancia BT
state/PlayerState.kt          → estado compartido entre UI y Service (playlist, callbacks, carpeta)
utils/RawMusicExporter.kt     → exporta canciones de raw/ a Música/Swapifi/ al instalar
ui/PlayerSection.kt           → reproductor con barra de progreso y controles
ui/FileExplorerSection.kt     → lista de canciones filtrada por carpeta
ui/TopBar.kt                  → barra superior con menús (ajustes, reportar bug, contacto, acerca de)
ui/HelpPopup.kt               → ayuda primer uso
ui/SettingsPopup.kt           → ajustes (carpeta de música)
ui/BugReportPopup.kt          → reporte de bugs via email (texto prefijado)
ui/ContactPopup.kt            → contacto
ui/AboutPopup.kt              → acerca de
ui/FolderPickerPopup.kt       → selector de carpeta
ui/theme/                     → colores y tema Compose (rojos Swapifi)

Las donaciones son un enlace directo a Ko-fi (https://ko-fi.com/davidig6).

## Decisiones técnicas importantes
- **No se usa la API de Spotify** — solo broadcasts locales del sistema, igual que Mutify
- **No se usa NotificationListener** — demasiado invasivo para el usuario
- **STREAM_ALARM en lugar de STREAM_MUSIC** para la música local — permite que suenen simultáneamente sin interferirse; el precio es la compensación de volumen BT descrita arriba
- **No se usa Audio Focus** — Spotify pausaba el anuncio al perder el foco, rompiendo el ciclo de detección
- **MediaStore** para listar archivos de audio en Android 13+

## Pendientes

- **Volumen bajo/plano en auriculares BT — RESUELTO 2026-07-21, validado por David por oído** ("así es como debería sonar al volumen máximo"). La causa real no era la curva BT congelada ni el limitador del `LoudnessEnhancer`: el efecto de ganancia **nunca llegaba a crearse** (ver "Creación del efecto de ganancia" en la sección de compensación BT). Con el fix, el volumen máximo suena correcto con boost de +28dB del LE. Las conclusiones de Fix B siguen vigentes como historia: "enseñar" `volume_alarm_bt_a2dp` está cerrado a nivel de plataforma (requeriría `WRITE_SECURE_SETTINGS`)

## Idiomas soportados
- Inglés (por defecto) — `res/values/strings.xml`
- Español — `res/values-es/strings.xml`
- La app sigue el idioma del sistema automáticamente

## Requisitos
- Android 8.0 (API 26) mínimo, targetSdk 36
- Spotify instalado en versión gratuita (con anuncios)
- Permiso `READ_MEDIA_AUDIO` (Android 13+) o `READ_EXTERNAL_STORAGE` (Android 12-)
- Permiso `POST_NOTIFICATIONS` (Android 13+) para la notificación del servicio y la multimedia
