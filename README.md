# Flyzar TV — visor de URL para NVIDIA Shield

APK tipo kiosco: abre una URL a pantalla completa, sin barra de navegacion,
mantiene la pantalla prendida y arranca sola al bootear el Shield.

## 1. Compilar el APK (sin instalar nada)

1. Crear un repo **privado** en GitHub.
2. Subir todos los archivos de esta carpeta (drag & drop en la web sirve,
   pero ojo: hay que subir tambien `.github/workflows/build.yml`).
3. Pestaña **Actions** → el build arranca solo (o "Run workflow").
4. A los ~3 minutos, bajar el artifact **flyzar-tv-apk** → adentro esta
   `flyzar-tv-1.0.apk`.

## 2. Instalar en el Shield

**Opcion A — adb por red (la mas comoda):**

    Shield → Ajustes → Preferencias del dispositivo → Info →
    tocar 7 veces "Build" → vuelve a Opciones de desarrollador →
    activar "Depuracion por red"

    adb connect 192.168.x.x:5555
    adb install -r flyzar-tv-1.0.apk

**Opcion B — sin PC:** instalar *Downloader* (AFTVnews) desde la Play Store
del Shield y meter la URL directa del APK.

Despues aparece como **Flyzar TV** en la fila de apps del launcher.

## 3. Configurar la URL

- Ya viene con `https://lvveg.flightpath3d.biz` cargada por defecto.
- Para cambiarla: apretar **ATRAS 3 veces seguidas** (o MENU) → dialogo de config.
- Desde la compu, sin tocar el control:

      adb shell am start -a android.intent.action.VIEW \
        -d "https://tu-url" -n ar.com.flyzar.tvdisplay/.MainActivity

- Si preferis dejarla fija en el codigo: `MainActivity.java`, constante
  `DEFAULT_URL` arriba de todo (ya esta seteada ahi).

## Controles

| Tecla | Accion |
|---|---|
| ATRAS x3 (o MENU) | abrir configuracion |
| PLAY/PAUSE | recargar |
| ATRAS (una vez) | nada (modo kiosco) |

## Extras

- Si se cae la red muestra "Sin conexion" y reintenta cada 10 s.
- Recarga periodica opcional (config → minutos; 0 = nunca).
- Para desinstalar: `adb uninstall ar.com.flyzar.tvdisplay`

## Firma

`app/flyzar.jks` (pass `flyzar2026`) esta incluido para que las
actualizaciones se instalen encima sin desinstalar. Por eso conviene que el
repo sea **privado**. Si algun dia lo publicas, generá una clave nueva.
