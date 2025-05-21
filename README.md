# 🧩 JTSQL (JsonToSQL)

JTSQL é uma ferramenta que converte arquivos JSON em comandos SQL prontos para uso em bancos de dados MySQL. Ideal para automatizar a criação de tabelas e colunas a partir de estruturas JSON padronizadas.

## ⚙️ Como funciona

O JTSQL espera um arquivo JSON no seguinte formato:

```json
{
    "usuario__tb01": {
        "nome__cn01": {
            "type": "varchar",
            "length": 10,
            "default": "Sem",
            "null": false
        },
        "criado_em__cn02": {
            "type": "timestamp",
            "length": 100,
            "default": "current_timestamp",
            "null": false
        }
    }
}
```

**Regras dos IDs:**
- Para tabelas: o nome deve ser seguido de `__tbXX` (XX = dois dígitos únicos para cada tabela).
- Para colunas: o nome deve ser seguido de `__cnXX` (XX = dois dígitos únicos para cada coluna dentro da tabela).

**Campos para cada coluna:**
- `type`: Tipo de dado aceito pelo MySQL (varchar, timestamp, text, int, integer, etc).
- `length`: Tamanho do campo (se aplicável).
- `default`: Valor padrão. Use `"none"` para não definir, `false` para remover, ou outro valor para definir o default.
- `null`: Se `true`, permite NULL. Se `false`, será NOT NULL.

## 💡 Exemplo de uso

### 📝 1. Preparando o JSON

Você pode criar o JSON como uma string:
```java
String fileString = "{ ... seu json ... }";
```

Ou ler de um arquivo:
```java
String fileString = Files.readString(Paths.get("diretorio/do/arquivo/file.json"));
```

### ⏱ 2. Definindo a data de modificação

Manual (para testes):
```java
LocalDateTime dateTime = LocalDateTime.parse("2025-05-21 14:05:33", DateTimeFormatter.ofPattern("yyyy-MM-dd H:m:s"));
```

Ou usando a data de modificação real do arquivo:
```java
long lastModified = file.lastModified(); 
LocalDateTime data = Instant.ofEpochMilli(lastModified).atZone(ZoneId.systemDefault()).toLocalDateTime();
```

### 🛠️ 3. Convertendo e salvando o SQL

Basta instanciar o JTSQL com o diretório de destino:
```java
new JTSQL("C:/Users/seu_usuario/Desktop/Meu_SQL").init(fileString, dateTime);
```
- O diretório será criado automaticamente, se não existir.
- O arquivo `.sql` será gerado no local indicado.

## 📦 Requisitos

- **Java 21**
- **Gradle** (com Kotlin DSL)
- Acesso ao repositório [JitPack](https://jitpack.io)

## 📚 Dependências

No seu `build.gradle.kts` adicione:

```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.0")
    implementation("com.github.emnuelht:compare-json:v1.0.3")
}
```

Essas dependências são essenciais para o funcionamento do JTSQL.

---

## 🚀 Futuro

A ideia é tornar o JTSQL flexível para outros bancos de dados além do MySQL.

## 📄 Licença

Este projeto está licenciado sob a Licença MIT - veja o arquivo [LICENSE](LICENSE) para detalhes.

---

> 👨‍💻 Feito por emnuelht – contribuições são bem-vindas!
