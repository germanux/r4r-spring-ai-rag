# Task 03 — recuperación incremental y compilación obligatoria

## Activación

Este archivo debe llamarse exactamente:

```text
.opencode/commands/task-03-pgvector-incremental-compile-recovery.md
```

El controlador agrupa los companions mediante el prefijo `task-03-pgvector*.md`.
Un nombre como `task-03-incremental-compile-recovery.md` queda fuera del bundle.

Si existe un paquete de corrección de Codex y está activo el contexto compacto, lanzar
una vez:

```bash
R4R_COMPACT_REVISION_CONTEXT=false ./scripts/run-codex-agent.sh
```

Así el pass de edición recibe también los companions completos, no solo sus hashes.

## Objetivo

Recuperar el código existente sin reiniciar la implementación, sin reescribir clases
completas y sin añadir funcionalidad mientras exista un error de compilación.

El orden obligatorio es:

```text
archivo -> método -> bloque lógico -> compilación -> test focalizado
```

## Reglas no negociables

1. No crear nuevas clases, métodos, tests, migraciones, propiedades ni abstracciones
   mientras `compile` o `testCompile` estén rojos.
2. No usar `write` para sustituir un Java existente completo. Usar edición o patch
   acotado al método defectuoso.
3. Tras cada edición, reabrir el método modificado y ejecutar inmediatamente el
   compilador correspondiente.
4. No tocar más de un método entre dos compilaciones.
5. Mantener siempre activos:
   - `package` e imports necesarios;
   - declaración de clase;
   - anotaciones de clase y método;
   - constantes y propiedades;
   - campos inyectados;
   - constructores;
   - firmas públicas y `@Override`;
   - configuración Spring.
6. No ocultar errores excluyendo tests, desactivando plugins, cambiando dependencias
   ni relajando el gate.
7. No interpretar warnings de IntelliJ como errores de Java sin reproducirlos con
   Maven.
8. No ejecutar el gate completo mientras quede código en cuarentena.
9. No hacer Git writes.
10. No entregar a Codex código que no compile.

## Aislamiento temporal de un método defectuoso

Solo puede aislarse el cuerpo de un método que produzca un error real del compilador.
La firma, anotaciones y propiedades de clase permanecen activas.

```java
@Transactional
public void replaceSource(
        String source,
        List<MarkdownChunk> chunks) {

    /* TEMP-QUARANTINE START
    Cuerpo original pendiente de recuperación incremental.
    TEMP-QUARANTINE END */

    throw new UnsupportedOperationException(
            "TEMP-QUARANTINE: replaceSource");
}
```

Condiciones:

- no aislar una clase completa;
- no comentar campos, constantes, beans ni constructores;
- no dejar más de un método nuevo en cuarentena por iteración;
- no ejecutar tests funcionales de ese método mientras esté aislado;
- no ejecutar el gate oficial;
- eliminar totalmente la cuarentena antes de solicitar revisión.

Si el cuerpo contiene texto físicamente corrupto y no puede encerrarse en un comentario
válido, reemplazar solo ese cuerpo. El contenido anterior queda preservado por el diff y
la evidencia de runtime; no reescribir el resto del archivo.

## Bucle obligatorio

### Producción

```bash
mvn -DskipTests compile
```

### Tests

```bash
mvn -DskipTests test-compile
```

### Test unitario focalizado

```bash
mvn -Dtest=ClaseTest#metodo test
```

### Test de integración focalizado

```bash
mvn -Dit.test=ClaseIT#metodo verify
```

No usar `javac` directamente para el proyecto Spring completo: Maven debe construir el
classpath real de Spring AI 1.0.0.

## Procedimiento por error

Para cada diagnóstico:

```text
Archivo:
Clase:
Método:
Línea:
Mensaje exacto:
Símbolo o delimitador esperado:
Callers afectados según CodeGraph:
Cambio mínimo:
Comando de compilación:
Condición de salida:
```

Después:

1. abrir solo el archivo afectado y las definiciones directas necesarias;
2. corregir el primer error del primer método;
3. reabrir el método;
4. compilar;
5. no avanzar mientras el mismo error siga presente;
6. ejecutar el test focalizado cuando el método compile;
7. pasar al siguiente método únicamente tras evidencia verde.

## Recuperación bloque por bloque

Un método aislado se reactiva en este orden:

1. validación de parámetros;
2. construcción de objetos;
3. consultas o llamadas a dependencias;
4. transformaciones y bucles;
5. mutación;
6. retorno;
7. manejo de excepciones.

Descomentar un solo bloque, compilar y corregir. No descomentar el método completo de
una vez.

## Prohibición de expansión

Mientras exista un error de compilación o `TEMP-QUARANTINE`:

- no crear helpers nuevos;
- no renombrar APIs;
- no mover clases;
- no refactorizar estilo;
- no ampliar cobertura general;
- no añadir funcionalidades;
- no reescribir tests completos;
- no cambiar SQL o configuración que no estén implicados en el error actual.

Se permite únicamente el código mínimo exigido para recuperar el método actual y su
test focalizado.

## Disciplina de herramientas

Si una llamada `write` o `edit` devuelve un error de esquema, por ejemplo:

```text
Missing key: content
```

no repetir una escritura completa. Releer el archivo y cambiar a un patch acotado.
Después verificar que el archivo conserva saltos de línea reales y que no contiene
secuencias `\n` serializadas dentro del código.

Antes de cada compilación:

```text
- el archivo puede abrirse;
- llaves y text blocks están cerrados;
- no hay contenido JSON incrustado;
- no se ha modificado otro método;
- las propiedades y anotaciones siguen activas.
```

## IntelliJ y depuración

El aviso:

```text
Unable to resolve table 'vector_store'
```

es normalmente una inspección SQL del IDE sin datasource asociado. No es un error de
`javac`. Solo actuar si Maven, Flyway o PostgreSQL lo reproducen.

Para runtime, usar JDWP y conectar IntelliJ manualmente:

```bash
mvn -Dmaven.surefire.debug -Dtest=ClaseTest#metodo test
```

```bash
mvn -Dmaven.failsafe.debug -Dit.test=ClaseIT#metodo verify
```

El agente no puede afirmar que controla el depurador de IntelliJ salvo que exista una
herramienta explícita para breakpoints, stack frames y variables.

## Requisitos antes del gate

Ejecutar:

```bash
./scripts/task-gate.sh task-03-pgvector
```

solo cuando:

- `mvn -DskipTests compile` termina con exit `0`;
- `mvn -DskipTests test-compile` termina con exit `0`;
- no existe `TEMP-QUARANTINE`;
- no queda ningún stub temporal;
- todos los métodos están activos;
- cada método reparado tiene su test focalizado verde;
- los siete paths de Task 03 se han reabierto;
- el working tree contiene únicamente paths permitidos.

## Informe después de cada método

```text
RECOVERED METHOD
File:
Class:
Method:
Compile command:
Compile exit:
Focused test command:
Focused test exit:
Quarantine removed: yes/no
First remaining compiler error:
Next method:
```

## Parada segura

Detenerse y pedir revisión cuando:

- el mismo error reaparece dos veces;
- la corrección exige cambiar una API pública;
- sería necesario desactivar una propiedad o anotación Spring;
- el cambio requiere un path fuera de Task 03;
- el archivo está físicamente corrupto;
- dos métodos en cuarentena dependen circularmente;
- el compilador contradice las instrucciones;
- la única salida aparente es reescribir una clase completa.
