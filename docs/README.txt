R4R OpenCode model fix v1

Cambios:
- opencode.jsonc: elimina provider/models de la configuración base.
- .opencode/opencode.json: elimina el catálogo antiguo que anulaba el resolvedor.
- .opencode/agents/r4r-laptop.md: perfil LP revisado, sin cambios de modelo.
- install.sh: copia de seguridad, instalación y limpieza de runtime resuelto.

Instalación:
  ./install.sh --repo /home/german/Desarrollo/r4r-spring-ai-rag-laptop-agent.git
