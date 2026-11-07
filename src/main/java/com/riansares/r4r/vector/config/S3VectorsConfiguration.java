package com.riansares.r4r.vector.config;

/**
 * Reserved phase 3 wiring location, intentionally without Configuration or Bean
 * annotations. Future wiring will bind RagVectorProperties, conditionally create
 * the SDK v2 S3VectorsClient and inject the existing EmbeddingModel into the S3
 * adapter. It must leave PostgreSQL as the default and perform no AWS credential
 * resolution or requests while S3 is disabled. No wiring is implemented yet.
 */
public final class S3VectorsConfiguration { }
