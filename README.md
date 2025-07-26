# GPS Remote Tracker: Monitor Remoto de Dispositivos Android

Este repositorio contiene el código fuente de una aplicación Android diseñada para actuar como un nodo de monitoreo remoto. La aplicación tiene una doble funcionalidad: recolecta datos de GPS y del estado del dispositivo de forma configurable, y expone esta información a través de un servidor de API REST integrado y seguro.

## Características Principales

-   **Recolección de Datos Configurable:** Permite al usuario configurar el intervalo de recolección del GPS (en segundos), los días de la semana y el rango horario en que la recolección debe estar activa.
-   **Servicio en Segundo Plano:** Utiliza un `Foreground Service` para garantizar que la recolección de datos continúe de manera fiable incluso cuando la aplicación no está en primer plano.
-   **Almacenamiento Local:** Todos los datos recolectados se guardan de forma persistente en una base de datos local **SQLite**, gestionada a través de **Room**.
-   **Servidor de API Integrado:** Implementa un servidor web ligero utilizando **Ktor**, permitiendo que la aplicación responda a peticiones HTTP remotas.
-   **Endpoints Seguros:** Expone dos endpoints (`/api/device_status` y `/api/sensor_data`) que requieren autenticación mediante un `Bearer Token` para garantizar el acceso seguro a los datos.
-   **Interfaz de Usuario Moderna:** Desarrollada con **Jetpack Compose**, ofrece una interfaz limpia e interactiva para controlar el servicio, configurar los parámetros y visualizar el estado actual y los datos recolectados.

## Funcionamiento

A continuación, se muestra el funcionamiento y la interfaz de la aplicación.

#### Servicio Detenido
En la pantalla principal, además del botón para iniciar el servicio, se muestra el token de autenticación de la API y los parámetros de recolección configurables.

![alt text](./guide_images/first_screen.png)


#### Servicio Activo
Una vez iniciada la recolección, según el intervalo de tiempo definido, se actualizan constantemente los datos del estado del GPS y de estado del dispositivo como lo son:

* Hora de la última recolección
* Latitud y longitud
* ID
* Batería
* Tipo de red
* Almacenamiento

En relación con el servidor web, se muestra la IP y puerto en el que el servidor está corriendo, además de los respectivos endpoints de la API.

![alt text](./guide_images/service_activated.png)

## Arquitectura

La aplicación sigue una arquitectura modular por capas para separar responsabilidades, compuesta por:
1.  **Capa de UI (Jetpack Compose):** Gestiona la interacción con el usuario.
2.  **Capa de Servicios (Foreground Service & Ktor):** Realiza las tareas en segundo plano.
3.  **Capa de Datos y Lógica (Room, SharedPreferences, StateFlow):** Administra el almacenamiento, la configuración y el estado de la aplicación.

![alt text](guide_images/architecture.png)

## Cómo Empezar

Pasos para configurar y ejecutar el proyecto en entorno de desarrollo.

### Prerrequisitos
-   Android Studio (versión Iguana o superior recomendada)
-   Un dispositivo físico o emulador con Android API 29+
-   Tener `adb` configurado en las variables de entorno del sistema.

### Instalación
1.  Clonar el repositorio:
    ```sh
    git clone https://github.com/Diret03/GPSRemoteTracker.git
    ```
2.  Abrir el proyecto con Android Studio.
3.  Android Studio descargará automáticamente todas las dependencias necesarias a través de Gradle.
4.  Ejecutar la aplicación en un dispositivo móvil o emulador.

## Guía de Uso y Pruebas

### 1. Permisos
Al iniciar la aplicación por primera vez, se solicitarán los siguientes permisos, que son necesarios para su correcto funcionamiento:
-   **Ubicación:** Seleccionar **"Permitir todo el tiempo"** para que el servicio pueda recolectar datos en segundo plano.
-   **Notificaciones:** Necesario para mostrar la notificación del servicio en primer plano.

### 2. Configuración
En la tarjeta "Configuración de Recolección", se puede ajustar:
-   **Intervalo:** Deslizar para seleccionar cada cuántos segundos se obtendrá un punto de GPS.
-   **Días activos:** Tocar los círculos para activar o desactivar la recolección en días específicos.
-   **Horario activo:** Definir el rango de horas en el que la aplicación guardará los datos.

### 3. Iniciar el Servicio
Presionar el botón **"Iniciar Recolección"**. El estado cambia a "Servicio activo" y la aplicación comenzará a mostrar los datos recolectados.

### 4. Probar la API
Cuando el servicio está activo, el servidor web también lo está. Se puede usar una herramienta como **Postman** o `curl` para probar los endpoints.

> **Nota Importante para Usuarios de Emulador:**
> Si se está ejecutando la aplicación en un emulador de Android Studio, es necesario redirigir el puerto de red para que la computadora pueda comunicarse con el servidor dentro del emulador. Antes de hacer peticiones, ejecutar el siguiente comando en la terminal (PowerShell, CMD, etc.):
> ```sh
> adb forward tcp:9999 tcp:9999
> ```
> Después de ejecutar este comando, es posible hacer peticiones a `http://localhost:9999`.

-   **Token de Autenticación:** Copiarlo desde la tarjeta "Token de Autenticación (API)".
-   **URL Base (Dispositivo Físico):** Disponible en la tarjeta "Endpoints de la API" (ej. `http://192.168.1.10:9999`).
-   **URL Base (Emulador):** `http://localhost:9999` (después de ejecutar `adb forward`).

#### Endpoint: Estado del Dispositivo
-   **Método:** `GET`
-   **URL:** `/api/device_status`
-   **Cabeceras (Headers):**
    -   `Authorization`: `Bearer TOKEN_DE_AUTENTICACION`

**Ejemplo de Petición (Emulador):**
```
GET http://localhost:9999/api/device_status
```
![alt text](guide_images/device_status_API.PNG)

**Formato de Respuesta (`application/json`)**
```json
{
    "battery": {
        "levelPercent": 100,
        "isCharging": false
    },
    "network": {
        "isConnected": true,
        "connectionType": "WiFi"
    },
    "storage": {
        "availableGB": "1.40",
        "totalGB": "5.80"
    },
    "osVersion": "15",
    "deviceModel": "sdk_gphone64_x86_64",
    "sdkVersion": 35
}
```
-   **battery**: Objeto con el estado de la batería.
    -   `levelPercent`: Nivel de batería actual (0-100).
    -   `isCharging`: `true` si el dispositivo se está cargando.
-   **network**: Objeto con el estado de la red.
    -   `isConnected`: `true` si el dispositivo tiene conexión a internet.
    -   `connectionType`: Tipo de conexión (ej. "WiFi", "Cellular").
-   **storage**: Objeto con el estado del almacenamiento.
    -   `availableGB`: Espacio de almacenamiento disponible en Gigabytes.
    -   `totalGB`: Espacio de almacenamiento total en Gigabytes.
-   **osVersion**: Versión del sistema operativo Android (ej. "12", "15").
-   **deviceModel**: Modelo del dispositivo (ej. "Pixel 7", "M2101K7BG").
-   **sdkVersion**: Nivel de la API de Android (ej. 31, 35).

---

#### Endpoint: Datos del Sensor
-   **Método:** `GET`
-   **URL:** `/api/sensor_data`
-   **Parámetros (Query Params):**
    -   `start_time`: Marca de tiempo de inicio (en milisegundos).
    -   `end_time`: Marca de tiempo de fin (en milisegundos).
-   **Cabeceras (Headers):**
    -   `Authorization`: `Bearer TOKEN_DE_AUTENTICACION`


**Ejemplo de Petición (Emulador):**
```
GET http://localhost:9999/api/sensor_data?start_time=0&end_time=1893456000000
```
![alt text](guide_images/sensor_data_API.PNG)

**Formato de Respuesta (`application/json`)**
El endpoint devuelve un array de objetos, donde cada objeto representa un punto de datos GPS.
```json
[
    {
        "deviceId": "da636985b8e971aa",
        "id": 417,
        "latitude": 37.4219983,
        "longitude": -122.084,
        "timestamp": 1753496123672
    },
    {
        "deviceId": "da636985b8e971aa",
        "id": 416,
        "latitude": 37.4219983,
        "longitude": -122.084,
        "timestamp": 1753496092864
    }
]
```
-   **deviceId**: Identificador único del dispositivo Android.
-   **id**: Clave primaria autoincremental del registro en la base de datos.
-   **latitude**: Coordenada de latitud.
-   **longitude**: Coordenada de longitud.
-   **timestamp**: Marca de tiempo Unix (en milisegundos) de cuándo se tomó la lectura.

---

## Tecnologías Utilizadas
-   **Lenguaje:** Kotlin
-   **Interfaz de Usuario:** Jetpack Compose
-   **Asincronía:** Corrutinas de Kotlin y Flow
-   **Base de Datos:** Room (SQLite)
-   **Servidor Web:** Ktor
-   **Gestión de Ubicación:** Google Play Services - FusedLocationProviderClient
-   **Persistencia de Configuración:** SharedPreferences
