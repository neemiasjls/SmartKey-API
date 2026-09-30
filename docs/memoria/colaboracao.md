# Colaboração — como trabalhar com o usuário

### COL-01 · Idioma e didática
[confirmado · 2026-09] Português. O usuário ainda não é especialista: explicar
de forma simples o que está sendo feito, quais programas instalar, quais
comandos rodar e onde fica cada arquivo. Código realmente executável, nunca
pseudocódigo. Quando for mais seguro, mostrar o arquivo completo atualizado.

### COL-02 · Formato de fim de etapa
[confirmado · 2026-09] Ao fim de cada fase/etapa: seção **COMO TESTAR AGORA**
(o que abrir, comando, dados a cadastrar, resultado esperado) e seção
**PRÓXIMO PASSO**.

### COL-03 · Ações externas
[confirmado · 2026-09] O usuário faz o `git push` pessoalmente; o Claude
fornece os comandos. Confirmar antes de qualquer ação externa (push, deploy,
alteração no Supabase/Render).

### COL-04 · Roteamento de modelos e exceção da memória
[confirmado · 2026-09] Regra global: classificar cada tarefa como SIMPLES
(→ `simple-tasks`) ou COMPLEXA (→ `complex-tasks`) e dizer em uma linha.
Exceção aprovada: atualizar memória e `/memoria salvar` são feitos inline
(subagente não vê a conversa). `/memoria revisar` pode ir para `complex-tasks`.

### COL-06 · Comandos para o usuário: PowerShell, uma linha só
[confirmado · 2026-09] Comandos que o usuário vai rodar devem funcionar no
terminal do Windows 11 (PowerShell) e vir num único comando: separar com `;`
(o PowerShell 5.1 não aceita `&&`) e incluir o `cd` para a pasta do projeto.

### COL-05 · Preocupação com mistura de projetos
[confirmado · 2026-09] O usuário tem outros projetos no Supabase e se
preocupa em não misturá-los. Ver DAD-01.
