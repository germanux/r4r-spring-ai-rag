# Revisión de pendientes del RAG — 2026-10-29

Alcance: contrastar las referencias al producto de los diagnósticos históricos
retirados de la raíz. No se activan tareas ni procesos de agentes. Estos documentos
se conservan íntegros en `py-ring-agent/archive/root-diagnostics/`.

## Comprobado

- `mvn clean verify`: correcto; 71 tests y 23 pruebas de integración, sin fallos
  ni omisiones. PostgreSQL de pruebas: Synology, puerto 5433. Log de esta ejecución:
  `/tmp/r4r-root-audit-maven.log` (temporal, no forma parte del repositorio).
- El CLI usa clasificación tipada de errores SQL y red, reemplazo del servicio
  mediante `BeanDefinitionRegistryPostProcessor` en los tests, cierre explícito
  del contexto y ejecución directa en una JVM hija con timeout. La compilación
  y las pruebas actuales pasan; los fallos antiguos no prueban un fallo vigente.
- La interfaz ya usa interpolación para la respuesta, `Subject` controlados en
  los tests, citas ordenadas y estados de carga, error y abstención. La
  configuración conserva HTTP sin proveer router.

## Pendientes del producto

1. **Población e idempotencia del corpus real:** no existe
   `docs/backend/production-ingestion-evidence.md`. No se ha ejecutado la ingesta
   de producción ni comprobado el contenido actual de esa base. Falta evidencia
   de dos ejecuciones y de los recuentos que demuestren ausencia de duplicados.
   Las instrucciones históricas de población aún usan el `.env` raíz, Docker
   local y `POSTGRES_APP_*`: son incompatibles con la configuración Synology
   vigente y no deben ejecutarse tal como están.
2. **Evaluación semántica:** faltan
   `src/test/java/com/riansares/r4r/rag/RagSemanticEvaluationTest.java` y
   `src/test/resources/rag-evaluation.json`. No hay evidencia suficiente para
   dar por validada la calidad de recuperación y abstención del corpus real.
3. **Smoke del artefacto de producción:** faltan
   `src/test/java/com/riansares/r4r/rag/RagProductionSmokeTest.java` y
   `docs/backend/production-rag-smoke.md`. La suite Maven no sustituye esta
   validación del JAR y sus respuestas fundamentadas.
4. **Playwright:** no existen `frontend/playwright.config.ts` ni `frontend/e2e/`;
   `frontend/package.json` tampoco define `e2e`. No se puede dar por cerrada
   la prueba de extremo a extremo del flujo RAG.
5. **Aserciones DOM de controles:**
   `frontend/src/app/features/rag/rag-page.component.spec.ts` aún comprueba el
   estado de habilitación del textarea mediante `questionControl`, y no su
   propiedad DOM `disabled`; tampoco prueba mediante DOM que el botón de envío
   se rehabilite tras éxito y error. Son requisitos históricos todavía no
   cubiertos íntegramente.

## Límites de esta revisión

- El gate `./scripts/frontend-task-gate.sh task-fe-03-rag-ui` se intentó, pero
  falló instalando dependencias por `EAI_AGAIN` al acceder a registry.npmjs.org.
  No llegaron a ejecutarse el build ni los tests Angular; no se declaran verdes.
- No se han llamado modelos reales ni endpoints de producción, ni se han
  alterado datos de producción, migraciones o lógica RAG.
- Los diagnósticos archivados son historia, no instrucciones vigentes. El
  manifiesto conserva las rutas originales del paquete; no se ha reescrito
  para aparentar que valida una distribución nueva.
- Esta lista registra carencias comprobadas y evidencia no disponible, sin
  declarar que el sistema desplegado esté fallando ni que todo esté terminado.
