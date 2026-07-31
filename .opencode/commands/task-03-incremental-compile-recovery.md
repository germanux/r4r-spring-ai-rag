# Protocolo de recuperación incremental de código Java

## Objetivo

Recuperar una tarea Java/Spring existente sin reescribirla desde cero y sin añadir código nuevo mientras haya errores de compilación pendientes.

El trabajo debe avanzar **clase por clase, método por método y parámetro por parámetro**. Cada cambio debe ser pequeño, compilable y verificable antes de continuar.

**EN summary:** Recover the existing implementation incrementally; do not restart or expand it while compilation errors remain.

---

## 1. Reglas obligatorias

1. No crear nuevas clases, métodos, tests, migraciones ni configuraciones mientras exista un error de compilación en los archivos actuales.
2. No reescribir archivos completos salvo que el archivo esté corrupto y Codex lo autorice expresamente.
3. No corregir varios métodos a la vez.
4. No hacer refactorizaciones, cambios de estilo ni limpieza general durante la recuperación.
5. No borrar código funcional para simplificar la compilación.
6. No modificar Git, `progress.json`, `memory.md`, el controlador, los gates ni los documentos de tarea.
7. No declarar progreso por intuición: cada avance debe quedar demostrado por el compilador o por un test focalizado.
8. Consultar CodeGraph antes de lecturas amplias para identificar símbolos, callers, dependencias y tests afectados.
9. Mantener siempre activas las propiedades de clase:
   - campos;
   - constantes;
   - beans;
   - inyecciones;
   - anotaciones de clase;
   - constructores;
   - configuración Spring;
   - nombres y firmas públicas exigidas por el contrato.
10. No ocultar errores cambiando dependencias, desactivando plugins, excluyendo tests o relajando el gate.

**EN summary:** Preserve the class structure and public contract; make one evidence-backed correction at a time.

---

## 2. Qué se puede comentar temporalmente

Solo se puede aislar temporalmente código dentro de un método que actualmente impida compilar.

### 2.1 Métodos privados o package-private sin contrato externo

Se puede comentar temporalmente el cuerpo original completo y dejar un cuerpo mínimo compilable.

```java
private Result calculate(Input input) {
    /* TEMP-QUARANTINE START
    Código original pendiente de recuperación.
    Mantenerlo aquí; no borrarlo ni reescribirlo todavía.
    TEMP-QUARANTINE END */

    throw new UnsupportedOperationException(
            "TEMP-QUARANTINE: calculate is pending incremental recovery");
}
```

### 2.2 Métodos públicos, sobrescritos o usados por otros símbolos

No comentar la firma, las anotaciones ni `@Override`. El método debe permanecer visible para no romper callers, interfaces, Spring o tests.

Comentar únicamente el cuerpo original y dejar el stub mínimo compatible con el tipo de retorno.

```java
@Override
public List<MarkdownChunk> search(
        String query,
        int topK,
        double minScore) {

    /* TEMP-QUARANTINE START
    Cuerpo original conservado temporalmente.
    TEMP-QUARANTINE END */

    throw new UnsupportedOperationException(
            "TEMP-QUARANTINE: search is pending incremental recovery");
}
```

### 2.3 Métodos `void`

```java
public void index(List<MarkdownChunk> chunks) {
    /* TEMP-QUARANTINE START
    Cuerpo original conservado temporalmente.
    TEMP-QUARANTINE END */

    throw new UnsupportedOperationException(
            "TEMP-QUARANTINE: index is pending incremental recovery");
}
```

### 2.4 Métodos usados por inicialización de Spring

No usar un stub que impida arrancar el contexto si ese arranque es necesario para compilar o ejecutar el test focalizado. En ese caso:

1. conservar la firma y anotaciones;
2. comentar solo el bloque defectuoso;
3. mantener el comportamiento mínimo ya demostrado;
4. no devolver `null` salvo que el contrato lo permita expresamente.

### 2.5 Prohibiciones

No comentar temporalmente:

- campos o propiedades;
- constantes;
- constructores requeridos;
- anotaciones Spring;
- declaraciones de clase;
- imports necesarios para métodos todavía activos;
- migraciones ya aplicadas;
- firmas públicas exigidas;
- tests completos para conseguir un build verde artificial.

**EN summary:** Quarantine only failing method bodies; keep fields, annotations, signatures and contracts active.

---

## 3. Orden de recuperación

Seguir este orden exacto:

1. Obtener la lista completa de errores de compilación.
2. Elegir el primer error real del primer archivo.
3. Identificar con CodeGraph:
   - símbolo afectado;
   - callers;
   - callees;
   - tests relacionados;
   - tipo y firma esperados.
4. Abrir solo:
   - el archivo con el error;
   - la definición directa del tipo o método implicado;
   - como máximo un test focalizado relacionado.
5. Comprobar parámetro por parámetro:
   - nombre;
   - tipo;
   - nullability;
   - orden;
   - genéricos;
   - mutabilidad;
   - excepciones;
   - anotaciones.
6. Corregir únicamente ese método.
7. Compilar de nuevo.
8. No pasar al siguiente método hasta que el error anterior haya desaparecido.
9. Cuando una clase compile, ejecutar su test focalizado.
10. Solo después pasar a la siguiente clase.

**EN summary:** Resolve the first compiler error, verify it, and only then move to the next method or class.

---

## 4. Bucle de compilación obligatorio

No usar `javac` directamente para compilar el proyecto Spring Boot completo porque no reconstruye de forma fiable el classpath de Maven.

Usar Maven como compilador principal:

```bash
mvn -DskipTests compile
```

Si existe wrapper ejecutable:

```bash
./mvnw -DskipTests compile
```

Después de corregir una clase de test:

```bash
mvn -DskipTests test-compile
```

Después de corregir un método con test unitario focalizado:

```bash
mvn -Dtest=NombreDelTest#nombreDelMetodo test
```

Después de corregir un test de integración focalizado:

```bash
mvn -Dit.test=NombreDelIT#nombreDelMetodo verify
```

No ejecutar todavía el gate completo mientras haya métodos en `TEMP-QUARANTINE` o errores de compilación conocidos.

**EN summary:** Use Maven for classpath-correct compilation and run the narrowest relevant test after each method repair.

---

## 5. Tratamiento exacto de cada error

Para cada error, registrar internamente este checklist antes de editar:

```text
Archivo:
Clase:
Método:
Línea:
Error exacto del compilador:
Símbolo esperado:
Símbolo encontrado:
Parámetros esperados:
Parámetros actuales:
Callers directos:
Test focalizado:
Cambio mínimo propuesto:
Condición para considerar reparado:
```

No corregir por aproximación. La salida del compilador debe confirmar que ese error concreto desapareció.

**EN summary:** Translate every compiler diagnostic into an explicit, verifiable repair checklist.

---

## 6. Política de descomentado

Un método en cuarentena se recupera así:

1. Descomentar únicamente la primera unidad lógica del cuerpo.
2. Compilar.
3. Corregir el primer error de esa unidad.
4. Volver a compilar.
5. Continuar bloque por bloque.
6. No descomentar el resto del método hasta que el bloque actual compile.
7. Cuando todo el método compile:
   - eliminar el stub temporal;
   - eliminar `TEMP-QUARANTINE`;
   - ejecutar el test focalizado;
   - revisar callers afectados.
8. Solo entonces recuperar el siguiente método.

Unidad lógica significa uno de estos elementos:

- una validación de entrada;
- una construcción de objeto;
- una consulta;
- una transformación;
- un bucle;
- una llamada a dependencia;
- un `return`;
- un bloque de manejo de excepciones.

**EN summary:** Reactivate one logical block at a time and compile after every reactivation.

---

## 7. Restricción contra código nuevo

Mientras exista cualquier método en cuarentena:

- no añadir nuevas funcionalidades;
- no añadir nuevas abstracciones;
- no extraer helpers;
- no cambiar APIs públicas;
- no crear nuevos tests generales;
- no ampliar cobertura fuera del método recuperado;
- no optimizar;
- no cambiar nombres;
- no mover clases;
- no introducir patrones de diseño.

Solo se permite nuevo código mínimo cuando sea imprescindible para compilar o probar el método actual, y debe quedar justificado por un error concreto.

**EN summary:** Do not expand the design during recovery; new code is allowed only when a specific compiler or test failure requires it.

---

## 8. Depuración

### 8.1 Primera opción: compilador y tests focalizados

La fuente principal de diagnóstico será:

```bash
mvn -DskipTests compile
mvn -DskipTests test-compile
mvn -Dtest=ClaseTest#metodo test
mvn -Dit.test=ClaseIT#metodo verify
```

### 8.2 Depuración JVM remota

Cuando un método compile pero falle en runtime, se puede abrir un puerto JDWP y conectar IntelliJ manualmente.

Para un test Surefire:

```bash
mvn -Dmaven.surefire.debug -Dtest=ClaseTest#metodo test
```

Para un test Failsafe:

```bash
mvn -Dmaven.failsafe.debug -Dit.test=ClaseIT#metodo verify
```

Para una aplicación Spring Boot:

```bash
MAVEN_OPTS='-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005' \
  mvn spring-boot:run
```

El agente no debe afirmar que está conectado al depurador de IntelliJ si no existe una integración real. Puede preparar el proceso JDWP y dejar evidencia del puerto, PID y comando; la conexión desde IntelliJ es externa.

### 8.3 IntelliJ

Si existe una herramienta MCP, ACP o plugin de IDE que exponga explícitamente:

- breakpoints;
- stack frames;
- variables;
- evaluate expression;
- step into/over/out;

puede utilizarse. Si esas herramientas no están disponibles, no inventar acceso al depurador.

**EN summary:** Use Maven diagnostics first; use JDWP for runtime debugging and never pretend IntelliJ debugger access exists.

---

## 9. Criterios para ejecutar el gate completo

Ejecutar:

```bash
./scripts/task-gate.sh task-03-pgvector
```

solo cuando se cumplan todas estas condiciones:

1. `mvn -DskipTests compile` está verde.
2. `mvn -DskipTests test-compile` está verde.
3. No queda ningún `TEMP-QUARANTINE`.
4. No queda ningún `UnsupportedOperationException` temporal.
5. Cada clase modificada tiene su test focalizado verde.
6. No hay métodos comentados pendientes.
7. No hay errores rojos reales del compilador.
8. Los warnings del IDE se han distinguido de errores de compilación reales.
9. Se han revisado los callers relevantes mediante CodeGraph.
10. El working tree contiene solo paths permitidos por la tarea.

**EN summary:** Run the full task gate only after compilation, focused tests and all quarantined methods are fully restored.

---

## 10. Advertencia sobre IntelliJ y la captura actual

El mensaje de IntelliJ:

```text
Unable to resolve table 'vector_store'
```

puede ser una inspección SQL del IDE sin datasource o schema configurado. No demuestra por sí solo que Java o PostgreSQL fallen.

Antes de modificar código por ese aviso:

1. comprobar `mvn ... compile`;
2. comprobar el test de integración real;
3. comprobar que Flyway crea `vector_store`;
4. comprobar el datasource de IntelliJ;
5. tratarlo como error de código solo si Maven, Flyway o PostgreSQL lo reproducen.

No comentar métodos únicamente para eliminar advertencias visuales del IDE.

**EN summary:** IntelliJ SQL inspection warnings are not compiler failures; verify them against Maven, Flyway and PostgreSQL first.

---

## 11. Informe obligatorio al terminar cada método

Después de cada método recuperado, emitir únicamente:

```text
RECOVERED METHOD
File:
Class:
Method:
Compiler command:
Compiler exit:
Focused test command:
Focused test exit:
Quarantine removed: yes/no
Remaining first compiler error:
Next method:
```

No presentar un resumen global hasta que todos los métodos estén recuperados.

**EN summary:** Report one recovered method at a time with exact compiler and test evidence.

---

## 12. Condición de parada segura

Detenerse y pedir revisión de Codex cuando ocurra cualquiera de estos casos:

- la corrección mínima exige cambiar una API pública;
- una propiedad o anotación Spring tendría que desactivarse;
- el mismo error reaparece dos veces;
- dos métodos dependen circularmente de cuerpos todavía en cuarentena;
- el compilador y CodeGraph contradicen el documento de tarea;
- el test focalizado requiere cambiar un archivo fuera de `allowed_paths`;
- aparece corrupción física del archivo;
- se necesitaría reescribir una clase completa;
- el agente no puede demostrar el siguiente cambio con un error concreto.

No continuar improvisando.

**EN summary:** Stop on ambiguity, contract changes or repeated failures rather than improvising broad rewrites.
