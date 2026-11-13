---
title: "Riansares 4R — datos estructurados adicionales"
document_type: "Precios y presupuestos de referencia"
language: "es"
created_at: "2026-07-27"
updated_at: "2026-07-27"
version: "1.0"
status: "research_enriched"
content_zone: "EDITORIAL"
ingest_into_editorial_rag: true
---

# Riansares 4R — datos estructurados adicionales

## Regla de uso

Estos importes no son tarifas de Riansares 4R ni ofertas vinculantes. Son referencias fechadas procedentes de guías de mercado y una unidad técnica. Antes de presupuestar deben revisarse mediciones, estado, acceso, calidades, residuos, licencias, IVA, margen y riesgos.

## Tabla de precios

| ID | Categoría | Descripción | Unidad | Bajo | Central | Alto | Fuente |
|---|---|---|---|---:|---:|---:|---|
| P-001 | Reforma integral | Vivienda, rango medio orientativo | EUR/m2 | 400 | 525 | 650 | SRC-HAB-VIVIENDA |
| P-002 | Casa antigua | Integral sin estructura ni redistribución | EUR/m2 | 350 | 416.66 | 450 | SRC-HAB-ANTIGUA |
| P-003 | Casa antigua | Integral con refuerzo de estructura | EUR/m2 | 533.33 | 533.33 | 533.33 | SRC-HAB-ANTIGUA |
| P-004 | Casa antigua | Integral con cambio de distribución | EUR/m2 | 600 | 600 | 600 | SRC-HAB-ANTIGUA |
| P-005 | Cocina | Reforma de cocina 4 m2 | EUR/project | 3850 | 3850 | 5000 | SRC-HAB-COCINA |
| P-006 | Cocina | Reforma de cocina 7 m2 | EUR/project | 5600 | 6000 | 9000 | SRC-HAB-COCINA |
| P-007 | Cocina | Reforma de cocina 10 m2 | EUR/project | 7000 | 8100 | 11000 | SRC-HAB-COCINA |
| P-008 | Cocina | Reforma de cocina 12 m2 | EUR/project | 7800 | 8500 | 12000 | SRC-HAB-COCINA |
| P-009 | Baño | Baño pequeño 2 m2 | EUR/project | 1560 | 1560 | 1560 | SRC-HAB-BANO |
| P-010 | Baño | Baño sin obras 4 m2 | EUR/project | 2000 | 2000 | 2000 | SRC-HAB-BANO |
| P-011 | Baño | Baño sin quitar azulejos 4 m2 | EUR/project | 2900 | 2900 | 2900 | SRC-HAB-BANO |
| P-012 | Baño | Baño calidades medias 5 m2 | EUR/project | 4250 | 4250 | 4250 | SRC-HAB-BANO |
| P-013 | Baño | Baño calidades altas 6 m2 | EUR/project | 4860 | 4860 | 4860 | SRC-HAB-BANO |
| P-014 | Suelo laminado | Calidad media con mano de obra | EUR/m2 | 25 | 30 | 30 | SRC-HAB-LAMINADO |
| P-015 | Suelo laminado | Rango general publicado | EUR/m2 | 20 | 30 | 65 | SRC-HAB-LAMINADO |
| P-016 | Pintura | Pintura blanca de piso | EUR/m2 | 10 | 11 | 12 | SRC-HAB-PINTURA |
| P-017 | Pintura | Pintar piso 100 m2 | EUR/project | 1300 | 1300 | 1300 | SRC-HAB-PINTURA |
| P-018 | Demolición | Demolición manual de alicatado DRA010 | EUR/m2 | 8.9 | 8.9 | 8.9 | SRC-CYPE-DEMO-ALIC |

## Presupuestos ejemplo no vinculantes

### B-001 — Reforma integral de vivienda de 80 m2

- **Rango bajo:** 32,000 EUR
- **Base:** 42,000 EUR
- **Rango alto:** 52,000 EUR
- **Base documental:** P-001, SRC-HAB-VIVIENDA
- **Supuestos:** calidad media, sin estructura, distribución moderada, acceso normal
- **Exclusiones:** IVA, proyecto cuando proceda, tasas, amianto, daños estructurales
- **Cálculo:** 80 m2 multiplicados por el rango de 400–650 EUR/m2; la base se aproxima al ejemplo publicado desde 40.000 EUR.

### B-002 — Casa antigua de 120 m2 sin refuerzo estructural

- **Rango bajo:** 42,000 EUR
- **Base:** 50,000 EUR
- **Rango alto:** 54,000 EUR
- **Base documental:** P-002, SRC-HAB-ANTIGUA
- **Supuestos:** sin estructura, sin redistribución amplia, calidades medias
- **Exclusiones:** cubierta completa, cimentación, humedad estructural, proyecto y tasas
- **Cálculo:** 120 m2 por 350–450 EUR/m2. La fuente publica un desglose de 41.900 EUR como ejemplo.

### B-003 — Casa antigua de 120 m2 con estructura y redistribución

- **Rango bajo:** 64,000 EUR
- **Base:** 72,000 EUR
- **Rango alto:** 100,000 EUR
- **Base documental:** P-003, P-004, SRC-HAB-ANTIGUA
- **Supuestos:** refuerzo localizado, redistribución, instalaciones completas
- **Exclusiones:** patologías graves no detectadas, cubierta integral, cimentación especial
- **Cálculo:** Las referencias publicadas alcanzan 533–600 EUR/m2 y la propia guía señala que una intervención profunda puede llegar a 100.000 EUR.

### B-004 — Cocina de 7 m2, calidad media

- **Rango bajo:** 5,600 EUR
- **Base:** 6,500 EUR
- **Rango alto:** 9,000 EUR
- **Base documental:** P-006
- **Supuestos:** mobiliario básico, sin muro de carga, instalaciones cercanas
- **Exclusiones:** electrodomésticos premium, encimera premium, apertura al salón
- **Cálculo:** Rango directo de la guía de precios consultada.

### B-005 — Baño de 5 m2, calidad media

- **Rango bajo:** 4,250 EUR
- **Base:** 5,200 EUR
- **Rango alto:** 7,500 EUR
- **Base documental:** P-012, P-013
- **Supuestos:** distribución conservada, impermeabilización estándar, sanitarios medios
- **Exclusiones:** cambio de bajante, daños de forjado, mobiliario de lujo
- **Cálculo:** La base amplía la tabla publicada para incluir contingencias habituales no uniformes entre proyectos.

### B-006 — Suelo laminado en 70 m2

- **Rango bajo:** 1,750 EUR
- **Base:** 2,100 EUR
- **Rango alto:** 4,550 EUR
- **Base documental:** P-014, P-015
- **Supuestos:** soporte razonablemente plano, calidad media
- **Exclusiones:** retirada, nivelación intensa, rodapié especial, recorte de puertas
- **Cálculo:** 70 m2 multiplicados por 25–30 EUR/m2; extremo alto según rango general de 65 EUR/m2.

### B-007 — Pintura blanca de piso de 100 m2

- **Rango bajo:** 1,000 EUR
- **Base:** 1,300 EUR
- **Rango alto:** 2,800 EUR
- **Base documental:** P-016, P-017
- **Supuestos:** paredes razonables, protección estándar
- **Exclusiones:** humedad activa, alisar toda la vivienda, pintura decorativa
- **Cálculo:** La base usa el ejemplo de 1.300 EUR; el extremo alto contempla preparación y reparación adicional.

### B-008 — Demolición de 30 m2 de alicatado

- **Rango bajo:** 267 EUR
- **Base:** 650 EUR
- **Rango alto:** 1,200 EUR
- **Base documental:** P-018
- **Supuestos:** demolición manual, acceso normal
- **Exclusiones:** contenedor y tasas si no se indican, protecciones complejas, retirada de instalaciones
- **Cálculo:** El mínimo es 30 × 8,90 EUR/m2 según unidad técnica; base y alto añaden logística, protección y gestión real de obra.

## Campos recomendados para un presupuesto real

```json
{
  "id": "R4R-BUDGET-YYYY-NNN",
  "date": "YYYY-MM-DD",
  "location": "municipio",
  "scope": [],
  "measurements": [],
  "line_items": [],
  "materials_quality": "basic|medium|high|custom",
  "access": "direct|limited|manual",
  "waste_included": true,
  "permit_responsibility": "client|contractor|technician",
  "vat_rate": 21,
  "vat_basis_note": "",
  "exclusions": [],
  "hidden_damage_rule": "",
  "payment_milestones": [],
  "estimated_duration_days": null,
  "valid_until": "YYYY-MM-DD"
}
```

## Criterios de cautela

- Un precio por m² sirve para comparar, no para presupuestar sin mediciones.
- La unidad CYPE describe una configuración concreta y no incluye automáticamente todos los costes empresariales.
- Las guías de mercado agregan proyectos heterogéneos.
- Las referencias deben conservar fecha y fuente.
- IVA y licencias se verifican para cada operación.
