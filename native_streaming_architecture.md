# Especificación de Arquitectura de Streaming Nativa

## 1. Visión General
Este documento describe la arquitectura para una aplicación de streaming ultra ligera, de alto rendimiento y verdaderamente nativa, con soporte para Android, Linux, macOS y Windows. Un requisito fundamental es la capacidad de ejecutar las extensiones existentes de CloudStream en Kotlin (`.cs3` / `.jar`) sin necesidad de reescribirlas, utilizando un demonio Kotlin headless (sin interfaz gráfica) y un frontend nativo.

## 2. Análisis Profundo de Frameworks: Slint vs. egui vs. FLTK

Para lograr verdaderas capacidades multiplataforma con una única base de código, evaluamos tres frameworks GUI principales:

### Slint
- **Soporte Multiplataforma:** Excelente soporte en escritorio. Android está soportado vía `winit` y bindings JNI, aunque la entrada de texto en móviles y la gestión del ciclo de vida requieren un manejo cuidadoso.
- **Incrustación de Video:** Soporta backends de renderizado personalizados (WGPU, Skia). `libmpv` puede renderizar en una textura o en el handle puro de la ventana, lo cual Slint puede componer.
- **Uso de Recursos y Estilos:** Huella muy baja. El lenguaje declarativo `.slint` proporciona una excelente separación de intereses y diseño responsivo (similar a Compose/SwiftUI).
- **Veredicto:** **Recomendado** para aplicaciones multimedia de consumo debido a su motor de layout responsivo y capacidades de estilo de aspecto nativo.

### egui (Modo Inmediato)
- **Soporte Multiplataforma:** Portabilidad excepcional (backends WGPU/Glow). Se ejecuta en casi todas partes, incluyendo Android vía `android-activity`.
- **Incrustación de Video:** Posible renderizando `libmpv` en una textura y pasándola al renderizador de `egui` (ej. vía `egui-wgpu`).
- **Uso de Recursos y Estilos:** Uso extremadamente bajo de RAM/CPU, pero el renderizado es inmediato, lo que significa repintados constantes si hay animación. El estilo no es nativo (renderizado personalizado), lo que puede dar una sensación de "juego" en móviles.
- **Veredicto:** Altamente eficiente, pero carece de la experiencia de usuario nativa requerida para una aplicación multimedia mainstream en móviles/escritorio.

### FLTK
- **Soporte Multiplataforma:** Muy maduro en escritorio. El soporte para Android es altamente experimental y no estándar.
- **Incrustación de Video:** Excelente compartición del handle de ventana crudo en escritorio.
- **Uso de Recursos y Estilos:** Tamaño de binario minúsculo, uso de RAM extremadamente bajo. Sin embargo, la interfaz gráfica luce anticuada (era Win95) sin aplicar estilos personalizados pesados.
- **Veredicto:** No viable para una aplicación multimedia moderna enfocada en móviles debido a la falta de soporte robusto en Android y paradigmas de interfaz gráfica anticuados.

## 3. Pilares Arquitectónicos

### 3.1 Arquitectura del Cliente Frontend (Rust + Slint)
- **Gestión de Estado:** Estado reactivo vinculado entre Rust y Slint. Los eventos de la interfaz de usuario (clics, navegación) disparan tareas asíncronas en Rust.
- **Hilo de UI:** Mantenido estrictamente para el renderizado y el despacho de eventos. Las tareas pesadas (red, IPC) se delegan a workers asíncronos en `tokio`.
- **Controles del Reproductor:** Overlay (superposición) escrito en Slint, desvaneciéndose durante la reproducción. Los eventos táctiles se mapean a los overlays de la UI.

### 3.2 Subsistema del Motor Multimedia (FFI de `libmpv`)
- **Pipeline de Video:** Utiliza `libmpv` a través de bindings de Rust (ej. `mpv-rs` o `libmpv-sys`).
- **Aceleración por Hardware:** Decodificación por hardware zero-copy vía Vulkan / MediaCodec (Android) / VDPAU/VAAPI (Linux) / VideoToolbox (macOS) / DXVA2 (Windows).
- **Renderizado:** Se utiliza `mpv_render_context` para renderizar directamente en el contexto WGPU/OpenGL gestionado por el framework de UI, evitando la copia de buffers en la CPU.

### 3.3 Demonio Headless de Extensiones en Kotlin
- **Entorno de Ejecución (Runtime):** Un runtime JVM mínimo (empaquetado vía `jlink`) o Dalvik en Android.
- **Sandbox de Carga de Clases:** Utiliza un `ClassLoader` personalizado para cargar dinámicamente archivos `.cs3` (ZIP con DEX/JAR). Las extensiones se ejecutan en un grupo de hilos aislado (sandbox).
- **Ciclo de Vida de Memoria:** El demonio se genera como un proceso hijo (Escritorio) o como un servicio en primer plano vinculado (Android). Se suspende agresivamente o se mata cuando pasa a segundo plano para ahorrar RAM.
- **Polyfill:** Proporciona implementaciones simuladas (stubs) de `MainAPI`, `OkHttp` y `Jsoup` que coinciden con la firma de la API de CloudStream.

### 3.4 Protocolo IPC de Alto Rendimiento
- **Transporte:** Unix Domain Sockets (Linux/macOS/Android) y Named Pipes (Windows).
- **Protocolo:** **Cap'n Proto** para serialización zero-copy.
- **Flujo de Trabajo:**
  1. El frontend en Rust solicita datos (ej. `search("query")`).
  2. La petición se serializa usando Cap'n Proto sobre UDS.
  3. El demonio Kotlin deserializa, invoca la extensión (`MainAPI.search`), y devuelve resultados.
  4. Los resultados se envían de vuelta a Rust en fragmentos (chunks) para evitar grandes picos de consumo de memoria.

## 4. Revisión Crítica y Recomendaciones de Expertos (Análisis Subagente Pro)

### 4.1 Vulnerabilidades y Casos Extremos
- **Ciclo de vida de procesos en Android:** Android gestiona los procesos estrictamente. El Demonio Kotlin (ejecutándose como un Servicio) podría ser asesinado por el OOM killer independientemente de la interfaz nativa.
  - *Mitigación:* El frontend en Rust debe manejar las desconexiones IPC de forma segura y reiniciar proactivamente el Servicio del Demonio.
- **Sandbox de Extensiones:** Ejecutar extensiones no confiables de la comunidad en una JVM headless plantea riesgos de seguridad (acceso al sistema de archivos, minería de criptomonedas).
  - *Mitigación:* Aplicar un `SecurityManager` estricto (si es Java 17 o inferior) o hooks JVMTI, y forzar políticas de solo red.
- **Sincronización de Texturas:** Renderizar `libmpv` en una textura de la UI asíncronamente puede causar desgarro de pantalla (tearing) o caída de fotogramas si el bucle de UI y el bucle de renderizado de MPV se desincronizan.
  - *Mitigación:* Utilizar primitivas explícitas de sincronización de hardware (ej. semáforos de Vulkan) entre el contexto de renderizado de `mpv` y la swapchain de la UI.

### 4.2 Mejoras Concretas y Reglas Inquebrantables
1. **JNI vs IPC en Android:** En Android, usar IPC mediante UDS añade una sobrecarga innecesaria. Es altamente recomendable usar **JNI** para el Demonio Kotlin en Android (dado que la aplicación ya está en un entorno de ejecución de Android), y reservar UDS/Named Pipes para el Escritorio, donde la JVM es verdaderamente un proceso externo.
2. **Prohibición Total de Tecnologías Web (Anti-Web/Anti-Tauri):** Se descarta y prohíbe formalmente el uso de **Tauri, Wails, WebViews (WebKit/Blink) o tecnologías basadas en HTML/DOM/JS**. Aunque facilitan la maquetación inicial, los motores de navegador introducen fugas de memoria crónicas (memory leaks por retención de texturas en GPU, caches de Chromium/WebKit y desasignación lenta de memoria en segundo plano), además de impedir la renderización zero-copy pura de `libmpv` sin conversiones de buffer. La UI debe ser **100% nativa y compilada a código máquina**.
3. **Unificación de la Capa de Red:** En lugar de que el Demonio Kotlin use su propio OkHttp, enrutar todas las peticiones de red de vuelta a través del IPC hacia el frontend en Rust (usando `reqwest`). Esto evita problemas con Cloudflare consistentemente en todas las plataformas usando una única huella TLS unificada en Rust.

## 5. Evolución Arquitectónica: Eliminación de la JVM y Adopción de Extensiones Nativas (WASM / QuickJS)

A raíz del análisis de recursos y complejidad del demonio Kotlin, se propone una alternativa radical: eliminar por completo la dependencia de la JVM reescribiendo o adaptando las extensiones para su ejecución nativa.

### 5.1 Evaluación de Rendimiento de Scraping (JVM vs Rust)
El scraping tradicional en la JVM (utilizando OkHttp + Jsoup) acarrea una penalización de memoria inherente, con consumos típicos de ~50MB por instancia de parsing y pausas introducidas por el recolector de basura (GC). En contraste, un motor nativo en Rust (usando `reqwest` + `tl` o `lol_html`) reduce drásticamente el impacto en RAM (a unos ~3MB), aumenta la velocidad de parsing y elimina el GC. Esto resulta crítico para dispositivos móviles de gama baja y para evitar el estrangulamiento térmico o el OOM killer del SO.

### 5.2 El Problema de la Distribución Pura en Rust (Librerías Dinámicas)
Compilar extensiones como librerías dinámicas (`.so`, `.dll`, `.dylib`) en Rust no es viable por tres motivos:
1. **Falta de ABI Estable:** Rust no garantiza compatibilidad binaria entre versiones del compilador.
2. **Matriz de Compilación:** Exigiría compilar cada extensión para al menos 8 arquitecturas diferentes (arm64, x86_64, etc. multiplicadas por los SO).
3. **Seguridad / Riesgo de Malware:** Ejecutar código nativo descargado dinámicamente rompe el modelo de confianza, abriendo vectores críticos de malware.

### 5.3 La Solución Arquitectónica Maestra: WebAssembly (WASM) y QuickJS
Para mantener portabilidad universal sin comprometer la seguridad:
- **WebAssembly (WASM):** Las extensiones se compilan a un único archivo `.wasm` (`wasm32-wasi`). Este módulo se ejecuta en el cliente usando un runtime integrado en Rust (como Wasmtime, Wasmer o Extism). Proporciona sandboxing estricto por defecto, rendimiento cuasi-nativo y un único binario distribuible para Android, Windows, Mac y Linux.
- **QuickJS (TypeScript/JS):** Como alternativa complementaria, integrar el motor QuickJS permite cargar extensiones escritas en JavaScript/TypeScript, lo que reduce la barrera de entrada para la comunidad y facilita el desarrollo y prueba sin tiempos de compilación de Rust.

### 5.4 Impacto en la Arquitectura Global
Al eliminar el demonio Kotlin, la arquitectura global cambia drásticamente:
- **Binario Único:** La aplicación pasa a ser un único ejecutable nativo puro (Rust + Slint + libmpv + runtime WASM/QuickJS).
- **Consumo Mínimo:** Tamaño del ejecutable de ~20 MB y consumo en reposo inferior a 35 MB de RAM.
- **Simplicidad IPC:** Se elimina la necesidad de protocolos IPC complejos (Cap'n Proto / JNI) ya que las extensiones corren integradas en el mismo proceso del frontend de forma asilada y coordinada.

### 5.5 Estrategia de Transición y Coexistencia
Para no romper el ecosistema de extensiones `.cs3` actuales durante la transición:
1. **Fase de Coexistencia (Híbrida):** Mantener temporalmente el demonio Kotlin / IPC como motor *legacy* secundario para soportar los `.cs3` existentes.
2. **Soporte Nativo Simultáneo:** Cargar nativamente los nuevos `.wasm` o `.js` directamente en el motor integrado de Rust.
3. **Migración Progresiva:** Incentivar a la comunidad para migrar las extensiones más populares usando SDKs proporcionados en TS o Rust (compilables a WASM). Una vez que la mayoría esté migrada, depreciar el demonio Kotlin para consolidar la arquitectura de binario puro.
