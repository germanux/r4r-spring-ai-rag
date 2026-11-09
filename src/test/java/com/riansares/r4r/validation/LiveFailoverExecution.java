package com.riansares.r4r.validation;

import java.util.*;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.MapPropertySource;

/** Opt-in operator launcher: Spring loads runtime config; secrets only enter child environment, never output/files. */
public final class LiveFailoverExecution {
    public static void main(String[] args) throws Exception {
        var env=new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("validation-bootstrap",Map.of(
                "spring.config.location","file:src/main/resources/application.yml","spring.profiles.active","default")));
        ConfigDataEnvironmentPostProcessor.applyTo(env);
        if (args.length==1 && args[0].equals("inventory")) {
            var result=new LinkedHashMap<String,Object>();
            for (String name:List.of("RAG_GENERATION_PROVIDERS","RAG_GENERATION_TEMPERATURE","RAG_EMBEDDING_PROVIDERS","RAG_EMBEDDING_MODEL",
                    "RAG_EMBEDDING_EXPECTED_DIGEST","RAG_VECTOR_PRIMARY","RAG_VECTOR_FAILOVER_ENABLED",
                    "RAG_S3_VECTORS_ENABLED","RAG_OPENAI_ENABLED","RAG_S3_VECTORS_REGION","RAG_S3_VECTORS_BUCKET",
                    "RAG_S3_VECTORS_INDEX","POSTGRES_HOST","POSTGRES_PORT","POSTGRES_DB","POSTGRES_TEST_PORT",
                    "POSTGRES_TEST_DB","POSTGRES_TEST_USER")) result.put(name,env.getProperty(name));
            for (String name:List.of("AWS_ACCESS_KEY_ID","AWS_SECRET_ACCESS_KEY","OPENAI_API_KEY","POSTGRES_PASSWORD"))
                result.put(name+"_PRESENT",env.getProperty(name)!=null && !env.getProperty(name).isBlank());
            System.out.println(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result));
            return;
        }
        if (args.length==0) throw new IllegalArgumentException("Child command required");
        var child=new ProcessBuilder(args).inheritIO();
        for (String name:List.of("AWS_ACCESS_KEY_ID","AWS_SECRET_ACCESS_KEY","AWS_SESSION_TOKEN","OPENAI_API_KEY","POSTGRES_PASSWORD")) {
            String value=env.getProperty(name);
            if (value!=null && !value.isBlank()) child.environment().put(name,unquote(value));
        }
        child.environment().put("AWS_EC2_METADATA_DISABLED","true");
        Process process=child.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> { if (process.isAlive()) process.destroy(); }));
        System.exit(process.waitFor());
    }
    private static String unquote(String text) {
        text=text.strip();
        if (text.length()>1 && ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))))
            return text.substring(1,text.length()-1);
        return text;
    }
}
