# 🐥 Quacky - Pato IA Flotante para Android

## ¿Qué hace?
- Flota sobre **TODAS tus apps** (Netflix, Chrome, WhatsApp, etc.)
- Sigue tu **dedo** donde lo toques
- Habla contigo por el **micrófono** usando Claude IA
- Se **mueve solo** cuando no lo estás tocando
- Arranca **automáticamente** al encender el teléfono

---

## Cómo compilar e instalar

### Opción A – Android Studio (recomendado)
1. Instala [Android Studio](https://developer.android.com/studio)
2. Abre esta carpeta como proyecto (`File > Open`)
3. **IMPORTANTE**: Abre `DuckOverlayService.java` y reemplaza:
   ```
   private static final String API_KEY = "TU_API_KEY_AQUI";
   ```
   con tu API key real de Anthropic (https://console.anthropic.com)
4. Conecta tu Android por USB con **depuración USB activada**
5. Pulsa ▶ **Run**

### Opción B – Compilar en línea (sin PC)
1. Crea cuenta en https://appetize.io o sube el código a https://replit.com
2. O usa [GitHub Actions](https://github.com) con una acción de build de Android

---

## Primer uso
1. Abre la app **Quacky**
2. Te pedirá dos permisos:
   - **"Mostrar sobre otras apps"** → Es NECESARIO para que flote
   - **Micrófono** → Para hablarle con voz
3. ¡El pato aparecerá flotando! La app se cierra pero el pato sigue ahí.

## Controles
| Gesto | Acción |
|-------|--------|
| Toca cualquier lugar | El pato camina hacia ahí |
| Arrastra el pato | Lo mueves directamente |
| Toca el pato | Abre el micrófono |
| Botón 🎤 naranja | Hablarle a Quacky |

## Parar a Quacky
- Notificación > Parar
- O Ajustes > Apps > Quacky > Forzar detención

---

## Estructura del proyecto
```
QuackyApp/
├── app/src/main/
│   ├── AndroidManifest.xml          ← Permisos
│   ├── java/com/quacky/duck/
│   │   ├── MainActivity.java        ← Pide permisos, lanza servicio
│   │   ├── DuckOverlayService.java  ← EL PATO (toda la lógica)
│   │   └── BootReceiver.java        ← Arranque automático
│   └── res/
│       ├── drawable/duck.png        ← Tu imagen del pato 🦆
│       ├── layout/overlay_duck.xml  ← Layout del pato flotante
│       └── values/                  ← Colores, textos, temas
└── README.md
```
