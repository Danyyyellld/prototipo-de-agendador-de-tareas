# Recordatorio de Tareas — Guía paso a paso

App de Android que:
1. Toma una foto de tu guía de tareas.
2. Envía la foto a la IA de Claude, que lee el texto y detecta título, descripción y fecha límite.
3. Crea automáticamente un evento con recordatorio en tu Calendario de Android.
4. Mantiene una lista de tareas pendientes en la app.

---

Esta guía usa **GitHub Actions** para compilar el APK automáticamente en la nube.
No necesitas instalar Android Studio ni nada en tu computadora.

## Paso 1: Crear una cuenta de GitHub (gratis)

1. Ve a https://github.com/signup y crea una cuenta si no tienes.

## Paso 2: Crear un repositorio nuevo

1. Ve a https://github.com/new
2. Ponle un nombre, por ejemplo `RecordatorioTareas`.
3. Puede ser **Public** o **Private**, no importa.
4. Haz clic en **Create repository**. NO marques "Add a README" (para que quede vacío).

## Paso 3: Subir los archivos del proyecto

1. Descomprime el archivo `RecordatorioTareas.zip` en tu computadora.
2. En la página de tu repositorio recién creado, haz clic en **"uploading an existing file"** (o **Add file > Upload files**).
3. Abre la carpeta descomprimida y **arrastra todo su contenido** (las carpetas `app`, los archivos `build.gradle.kts`, `settings.gradle.kts`, `LEEME.md`, etc.) hacia la página. GitHub preserva las carpetas.
4. Haz clic en **Commit changes**.

> ⚠️ Importante: la carpeta `.github` (con el punto al inicio) suele estar **oculta** en el explorador de archivos de Windows/Mac, así que probablemente NO se arrastre junto con las demás. Por eso el siguiente paso la agrega por separado, directamente desde la web (más fácil y seguro):

## Paso 4: Agregar el archivo que compila el APK automáticamente

1. En tu repositorio, haz clic en **Add file > Create new file**.
2. En el campo de nombre del archivo, escribe exactamente:
   `.github/workflows/build-apk.yml`
   (GitHub creará las carpetas automáticamente al ver las diagonales `/`).
3. Pega este contenido:

```yaml
name: Build APK

on:
  push:
    branches: [ "main", "master" ]
  workflow_dispatch: {}

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Descargar el código
        uses: actions/checkout@v4

      - name: Instalar Java 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Configurar Gradle
        uses: gradle/actions/setup-gradle@v3
        with:
          gradle-version: '8.7'

      - name: Compilar APK (versión debug, instalable directamente)
        run: gradle assembleDebug --no-daemon

      - name: Subir el APK como resultado descargable
        uses: actions/upload-artifact@v4
        with:
          name: RecordatorioTareas-apk
          path: app/build/outputs/apk/debug/*.apk
```

4. Haz clic en **Commit changes** (esto ya dispara la compilación automáticamente).

## Paso 5: Descargar el APK compilado

1. Ve a la pestaña **Actions** de tu repositorio (arriba).
2. Verás una ejecución en curso o completada, llamada "Build APK". Haz clic en ella.
3. Espera a que termine (unos 3-6 minutos, verás un ✅ verde cuando acabe).
4. Abajo, en la sección **Artifacts**, haz clic en **RecordatorioTareas-apk** para descargar un .zip.
5. Descomprime ese .zip: dentro está `app-debug.apk`. Ese es tu app real.

## Paso 6: Instalar el APK en tu teléfono

1. Transfiere `app-debug.apk` a tu teléfono (por WhatsApp a ti mismo, Google Drive, cable USB, o descárgalo directo desde GitHub en el navegador del teléfono).
2. Ábrelo desde el teléfono. Android pedirá permiso para **"instalar apps de orígenes desconocidos"** — acéptalo (es normal en apps que no vienen de la Play Store).
3. Instala. Ya tendrás el ícono de "Recordatorio de Tareas" en tu teléfono, como cualquier otra app.

## Paso 7: Conseguir tu clave de API de Claude

1. Ve a https://console.anthropic.com/
2. Crea una cuenta y ve a la sección **API Keys**.
3. Crea una clave nueva y cópiala (empieza con `sk-ant-...`). La pegarás dentro de la app.

## Paso 8: Usar la app

1. Al abrir la app por primera vez, te pedirá tu clave de API — pégala y guarda.
2. Toca **"Conceder permisos"** para autorizar cámara y calendario.
3. Toca **"📷 Tomar foto de guía"**, fotografía tu hoja de tareas.
4. Espera unos segundos mientras la IA la lee.
5. Revisa las tareas detectadas y toca **"Agendar todo"**.
6. Listo: se crean los eventos en tu Calendario con recordatorio, y aparecen en la lista de la app.

---

## Estructura del proyecto

```
RecordatorioTareas/
├── .github/workflows/build-apk.yml   <- compila el APK automáticamente en GitHub
├── app/
│   ├── build.gradle.kts          <- dependencias del módulo
│   └── src/main/
│       ├── AndroidManifest.xml   <- permisos de la app
│       ├── java/com/tuapp/recordatorio/
│       │   ├── MainActivity.kt         <- pantalla principal (Compose)
│       │   ├── Task.kt                 <- modelo de una tarea
│       │   ├── TaskRepository.kt        <- guarda las tareas en el teléfono
│       │   ├── CalendarHelper.kt        <- crea eventos en el Calendario
│       │   └── ClaudeVisionClient.kt    <- llama a la IA de Claude
│       └── res/
│           ├── values/strings.xml
│           ├── values/themes.xml
│           └── xml/file_paths.xml
├── build.gradle.kts
└── settings.gradle.kts
```

## Posibles mejoras futuras

- Guardar la clave de API de forma más segura (Android Keystore) en vez de SharedPreferences.
- Elegir a qué calendario específico agendar (si tienes varios).
- Notificaciones propias de la app además del recordatorio del calendario.
- Editar manualmente una tarea detectada antes de confirmarla (por ahora solo se puede aceptar o cancelar todo el lote).
- Soporte para PDF además de fotos.

## Problemas comunes

- **"Error de API (401)"**: tu clave de API es incorrecta o no tiene saldo. Verifica en console.anthropic.com.
- **La app no crea el evento en el calendario**: asegúrate de tener al menos una cuenta con Calendario configurada en el teléfono (Google Calendar, por ejemplo) y de haber concedido los permisos.
- **La foto sale muy oscura o borrosa y la IA no detecta bien la fecha**: intenta con buena luz y que el texto se vea nítido; puedes tomar la foto de nuevo.
