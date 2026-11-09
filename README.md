# KoalaTV

Aplicación Android (celular y **Android TV**) para ver televisión en vivo desde listas **M3U / M3U8**, con interfaz estilo decodificador satelital.

## Características

- **Pantalla de inicio** con logo de KoalaTV y barra de progreso azul con porcentaje.
- **Siempre inicia en Caracol** (`Canal Caracol`).
- **Numeración de canales desde el 100** en adelante, en el orden de la lista.
- **OSD** estilo deco satelital: fondo azul con 70 % de transparencia, letras blancas, con el nombre **KoalaTV** en la parte superior. Muestra número, nombre, posición, resolución y hora.
- **Lista de canales a la izquierda**, fuente discreta (13–14 sp), estilo broadcasting.
- **Listas adicionales**: agregar más canales por URL o desde un archivo `.m3u8`.
- Compatible con celular (gestos táctiles) y control remoto de Android TV.

## Controles

| Acción | Control remoto / teclado | Celular |
|---|---|---|
| Canal siguiente / anterior | `CH+` / `CH-` (también ↑ / ↓) | Deslizar arriba / abajo |
| Abrir / cerrar lista de canales | `ENTER` (OK) / ← abre, → cierra, `BACK` cierra | Un toque |
| Ir a un canal por número | Teclas `0-9` (ej. `1`,`0`,`5`) | – |
| Mostrar OSD | `INFO` o cambiar de canal | Doble toque |
| Menú de listas M3U8 | `MENU`, o botón **＋ Listas** en el panel | Pulsación larga, o botón **＋ Listas** |

## Estructura del repositorio

```
koalatv/
├── README.md
├── build.gradle.kts / settings.gradle.kts / gradle.properties
├── .github/workflows/build.yml      ← compila el APK en GitHub Actions
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/canales.m3u8      ← lista base de canales
        ├── java/com/koalatv/app/
        │   ├── SplashActivity.kt    ← logo + progreso
        │   ├── MainActivity.kt      ← reproductor, OSD, lista, menú de listas
        │   ├── ChannelStore.kt      ← carga y numeración de canales
        │   ├── M3uParser.kt         ← lector M3U/M3U8
        │   └── ChannelAdapter.kt    ← lista lateral
        └── res/                     ← layouts, logo, icono, banner TV
```

## Cómo compilar con GitHub

1. Crea un repositorio llamado **koalatv** en GitHub.
2. Sube **todo el contenido** de esta carpeta a la rama `main` (incluida la carpeta oculta `.github`).
3. Ve a la pestaña **Actions** → ejecuta **Compilar KoalaTV (APK)** (se ejecuta solo con cada push).
4. Al terminar, descarga el APK desde **Artifacts → KoalaTV-debug-apk**.

## Instalación

- **Celular**: copia el APK y ábrelo (activa "instalar apps de orígenes desconocidos").
- **Android TV**: instala con una app de archivos / USB, o por ADB: `adb install app-debug.apk`.

## Personalización

- **Logo**: reemplaza `app/src/main/res/drawable/ic_logo.xml` (o agrega un `ic_logo.png` y borra el `.xml`).
- **Icono**: `res/drawable/ic_launcher.xml`. **Banner de TV**: `res/drawable/banner.xml` (320×180).
- **Canal de inicio**: función `startIndex()` en `ChannelStore.kt`.
- **Lista base**: `app/src/main/assets/canales.m3u8`.

## Notas

- Las URL de la lista usan `http://` (sin cifrado); la app lo permite (`usesCleartextTraffic`).
- La información del OSD no incluye guía de programación (EPG); muestra datos del canal y de la señal.
- El APK generado es de tipo *debug* (instalable). Para publicar en Play Store se necesita un APK/AAB *release* firmado.
