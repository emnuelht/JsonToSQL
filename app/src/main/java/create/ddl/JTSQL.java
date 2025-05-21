package create.ddl;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import compare.json.CompareJson;

public class JTSQL {
    private String dir = "";

    public JTSQL(String dir) {
        this.dir = dir;
    }

    public void init(String jsoString, LocalDateTime yyyyMMdd_Hms) throws IOException {
        try {
            File file = new File("src/main/resources/.temp/fileOld.json");
            if (file.exists()) {
                // Pega o timestamp da última modificação
                long lastModified = file.lastModified();
                // Converte para Date
                LocalDateTime data = Instant.ofEpochMilli(lastModified).atZone(ZoneId.systemDefault()).toLocalDateTime();
                // Verificando se a data de modificação do jsonString é maior
                if (yyyyMMdd_Hms != null && yyyyMMdd_Hms.isAfter(data)) {
                    if (jsoString != null && !jsoString.isEmpty()) {
                        // Enviando o json para comparação
                        compareFiles(jsoString);
                    } else {
                        // O json está vazio
                        throw new IllegalArgumentException("Change NULL");
                    }
                }
            } else {
                getCatchException(jsoString, yyyyMMdd_Hms);
            }
        } catch (IOException e) {
            getCatchException(jsoString, yyyyMMdd_Hms);
        }
    }

    private void getCatchException(String jsoString, LocalDateTime yyyyMMdd_Hms) throws IOException {
        if (yyyyMMdd_Hms == null || jsoString == null) {
            throw new NullPointerException("yyyyMMdd_Hms ou jsoString está null. Nao e possivel continuar.");
        }
        // Criando o o file da pasta .temp
        boolean response = createFile(yyyyMMdd_Hms.toString().replace("T", " "));
        if (response) {
            // Enviando o json para comparação
            compareFiles(jsoString);
        } else {
            throw new IOException("Ocorreu um erro ao criar os arquivos na pasta .temp");
        }
    }

    private boolean createFile(String timeString) {
        try {
            File tempDir = new File("src/main/resources/.temp");
            if (!tempDir.exists()) {
                boolean created = tempDir.mkdirs();
                if (!created) {
                    throw new IOException("Nao foi possivel criar a pasta .temp");
                }
            }
            // Criando um arquivo temporario do json, para guardar o file antigo para comparação
            BufferedWriter file = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("src/main/resources/.temp/fileOld.json"), StandardCharsets.UTF_8));
            file.write("{}");
            file.close();

            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void compareFiles(String jsonString) {
        // Criando um file temporario com o conteudo do jsonString
        File file = createFileTemporary(jsonString);
        if (file != null) {
            File fileOld = new File("src/main/resources/.temp/fileOld.json");
            try {
                Map<String, String> change = CompareJson.run(fileOld, file);
                if (change.size() > 0) {
                    Map<String, String> mapDeletes = new HashMap<>();
                    Map<String, String> mapChange = new HashMap<>();
                    Map<String, String> mapAdd = new HashMap<>();
                    // Separando os map de delete, change e add
                    for (Map.Entry<String, String> entry : change.entrySet()) {
                        String key = entry.getKey();
                        String value = entry.getValue();
                        if (key.contains("deleted")) {
                            // DELETE
                            mapDeletes.put(key, value);
                        } else if (key.contains("change") && !key.contains("new")) {
                            // CHANGE
                            mapChange.put(key, value);
                        } else if (key.contains("new")) {
                            // ADD
                            mapAdd.put(key, value);
                        }
                    }
                    StringBuilder stringSQL = new StringBuilder();

                    // Recebendo os comandos de DELETE, como delete table ou column
                    Set<String> commandSQL_DELETE = setDelete(mapDeletes, 1);
                    // Recebendo os comandos de MUDANÇAS, como renomear, tipos, tamanhos...
                    Set<String> commandSQL_CHANGE = setChange(mapChange, setDelete(mapDeletes, 0));
                    // Recebendo os comandos de ADD, como add column ou create table
                    Set<String> commandSQL_ADD = setAdd(mapAdd);
                    // Adicionando todos esses comandos em só uma string
                    for (String command : commandSQL_DELETE) {
                        stringSQL.append(command);
                    }
                    for (String command : commandSQL_CHANGE) {
                        stringSQL.append(command);
                    }
                    for (String command : commandSQL_ADD) {
                        stringSQL.append(command);
                    }
                    // Criando o arquivo .sql com os comandos
                    boolean response = createFileSQL(stringSQL.toString());
                    if (response) {
                        System.out.println("Arquivo sql foi criado com sucesso!");
                        System.out.println(updateFileOld() ? "FileOld atualizado!" : "Ocorreu um erro ao atualizar o FileOld...");
                    } else {
                        throw new IOException("Ocorreu um erro ao criar o arquivo sql");
                    }
                } else {
                    System.out.println("Nada para se comparar");
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("ERROR ao receber as mudancas do json");
            }
        } else {
            System.out.println("Não foi possivel criar o arquivo");
        }
    }

    private File createFileTemporary(String content) {
        try {
            File file = new File("src/main/resources/.temp/fileNew.json");
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
            bufferedWriter.write(content);
            bufferedWriter.close();
            return file;
        } catch (IOException e) {
            return null;
        }
    }

    private Set<String> setDelete(Map<String, String> deletes, int output) {
        Map<String, String> orderDeletes = orderMap(deletes);
        
        Set<String> deleted = new HashSet<>();
        Set<String> commandSQL = new LinkedHashSet<>();
        // Criando o comando SQL
        for (Map.Entry<String, String> entry : orderDeletes.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key.contains("table")) {
                // DROP TABLE
                String[] keyDeleted = key.split("\\.");
                // Adicionando o cod da table deleted.table.tb02
                deleted.add(keyDeleted[2]); // Ex: tb02

                String sql = "DROP TABLE `" + value + "`;\n";
                // Adicionando o comando
                commandSQL.add(sql);
            } else if (key.contains("column")) {
                // ALTER TABLE
                String[] keyDeleted = key.split("\\.");
                // Verificando se essa table ja foi deletada
                if (!deleted.contains(keyDeleted[2])) {
                    // // Adicionando o cod da column deleted.column.tb03.cn08
                    deleted.add(keyDeleted[3]); // Ex: cn08
                    // Recuperando o nome ta table
                    String table = searchNameKey(key, 0, 0, false, false);
    
                    String sql = "ALTER TABLE `" + table + "` DROP COLUMN " + value + ";\n";
                    // Adicionando o comando
                    commandSQL.add(sql);
                }
            }
        }
        // Retornando só os deletados ou o comando SQL
        return output == 0 ? deleted : commandSQL;
    }

    private Set<String> setChange(Map<String, String> changes, Set<String> deleted) {
        // Recebendo as mudanças em ordem
        Map<String, String> orderChanges = orderMap(changes);
        
        Set<String> commandSQL = new LinkedHashSet<>();
        // Percorrendo as mudanças
        for (Map.Entry<String, String> entry : orderChanges.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key.contains("table")) {
                // RENAME TABLE
                // Recebendo o nome da nova table change.table.tb01
                String tableNew = searchNameKey(key, 0, 0, false, false); // Ex: user__tb01
                // Recebendo o nome da antiga table change.table.tb01
                String tableOld = searchNameKey(key, 0, 1, false, false); // usuario__tb01
                String sql = "RENAME TABLE " + tableOld + " TO " + tableNew + ";\n";
                // Adicionado o comando
                commandSQL.add(sql);
            } else if (key.contains("column")) {
                String[] keySplit = key.split("\\.");
                // Verificando se a coluna foi deletada
                if (!deleted.contains(keySplit[2])) {
                    // Recebendo o nome da table change.table.tb01
                    String table = searchNameKey(key, 0, 0, false, false); // usuario__tb01
                    // Recebendo o nome da antiga column change.column.tb01.cn01
                    String columnOld = searchNameKey(key, 1, 1, false, false); // nome__cn01
                    String sql = "ALTER TABLE " + table + " RENAME COLUMN " + columnOld + " TO " + value + ";\n";
                    // Adicionado o comando
                    commandSQL.add(sql);
                }
            } else if (key.contains("value")) {
                String[] keySplit = key.split("\\.");
                // Verificando se a coluna foi deletada
                if (!deleted.contains(keySplit[3])) {
                    // Recebendo o nome da table change.table.tb01
                    String table = searchNameKey(key, 0, 0, false, false); // usuario__tb01
                    // Recebendo o nome da column change.column.tb01.cn01
                    String column = searchNameKey(key, 1, 0, false, false); // nome__cn01
                    // Recebendo os valores do key
                    Map<String, JsonNode> options = searchOptionsColumn(key); // Ex: {default="Sem", null=false, length=10, type="varchar"}
                    String optType = options.get("type").toString().replaceAll("\"","").toUpperCase(); // Ex: varchar
                    String optLength = options.get("length").toString().replaceAll("\"",""); // Ex: 10
                    String optDefault = options.get("default").toString().replaceAll("\"",""); // Ex: Sem
                    boolean optNull = options.get("null").asBoolean(); // Ex: false
                    String sql = "";

                    // Criando uma lista de tipos que nao precisam de tamanho no MySQL
                    List<String> typesIgnore = Arrays.asList("INT","INTEGER","BIGINT","BOOLEAN","DATE","DATETIME","TEXT","BLOB","ENUM","SET","TIMESTAMP");

                    // Criando os comandos de SQL, MODIFY COLUMN, ALTER COLUMN...
                    switch (keySplit[4]) {
                        case "length":
                        case "type":
                            sql = "ALTER TABLE " + table + " MODIFY COLUMN " + column + " " + (typesIgnore.contains(optType) ? optType : optType + "(" + optLength + ")") + (optNull ? " NULL" : " NOT NULL") +";\n";
                            break;
                        case "default":
                            sql = "ALTER TABLE `" + table + "` ALTER COLUMN `" + column + (optDefault.equals("none") ? "" : optDefault.equals("false") ? "` DROP DEFAULT" : "` SET DEFAULT " + optDefault) + (optNull ? " NULL" : " NOT NULL") + ";\n";
                            break;
                        case "null":
                            sql = "ALTER TABLE " + table + " MODIFY COLUMN " + column + " " + (typesIgnore.contains(optType) ? optType : optType + "(" + optLength + ")") + (optNull ? " NULL" : " NOT NULL") + ";\n";
                            break;
                        default:
                            break;
                    }
                    commandSQL.add(sql);
                }
            }
        }
        return commandSQL;
    }

    private Set<String> setAdd(Map<String, String> adds) {
        Map<String, String> orderDeletes = orderMap(adds);

        Set<String> commandSQL = new LinkedHashSet<>();

        Set<String> newTables = new LinkedHashSet<>();
        Map<String, String> newColumns = new HashMap<>();
        Map<String, Map<String, Object>> columnProperties = new HashMap<>();
        for (Map.Entry<String, String> entry : orderDeletes.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key.contains("table")) {
                // Adicionando a key da table 
                newTables.add(key.split("\\.")[3]); // Ex: tb01
            } else if (key.contains("change.new.column")) {
                // Ex: change.new.column.tb01.cn11 = testando__cn11
                String[] parts = key.split("\\.");
                String colId = parts[3] + "." + parts[4]; // tb01.cn11
                newColumns.put(colId, value);
            } else if (key.contains("change.new.value")) {
                // Ex: change.new.value.tb01.cn11.type = cn11.type:"timestamp"
                String[] keySplit = key.split("\\.");
                String colId = keySplit[3] + "." + keySplit[4]; // Ex: tb01.cn11

                String valueSplit = value.split("\\.")[1]; // Ex: cn11.type:"timestamp"
                String propKey = valueSplit.split("\\:")[0]; // Ex: type
                String proValue = valueSplit.split("\\:")[1]; // "timestamp"

                columnProperties.computeIfAbsent(colId, k -> new LinkedHashMap<>()).put(propKey, proValue);
            }
        }

        Map<String, List<String>> createTablesColumns = new LinkedHashMap<>();
        Map<String, List<String>> addColumns = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : newColumns.entrySet()) {
            String colId = entry.getKey(); // Ex: tb04.cn55
            String tbId = colId.split("\\.")[0]; // tb04
            String colName = entry.getValue(); // nome da coluna
            // Recuperando as propriedades das columns adicionadas
            Map<String, Object> props = columnProperties.get(colId); // Ex: {default="0", null=false, type="Integer", length=11}
            String optType = props.get("type").toString().replaceAll("\"", "").toUpperCase(); // Ex: INTEGER
            String optLength = props.get("length").toString().replaceAll("\"", ""); // Ex: 11
            String optDefault = props.get("default").toString().replaceAll("\"", ""); // Ex: 0
            String optNull = props.get("null").toString(); // Ex: false

            // Criando uma lista de valores do default, que existem no MySQL
            List<String> optionsDefault = Arrays.asList("CURRENT_TIMESTAMP","CURRENT_DATE","CURRENT_TIME");
            // Criando uma lista de tipos que nao precisam de tamanho no MySQL
            List<String> typesIgnore = Arrays.asList("INT","INTEGER","BIGINT","BOOLEAN","DATE","DATETIME","TEXT","BLOB","ENUM","SET","TIMESTAMP");

            // Criando as colunas, exemplo do retorno: testando__cn11 TIMESTAMP DEFAULT CURRENT_TIMESTAMP NULL
            String columnDef = colName + " " +
                (typesIgnore.contains(optType) ? optType : optType + "(" + optLength + ")") +
                (optDefault.equals("none") || optDefault.equals("false") ? "" :
                        " DEFAULT " + (optionsDefault.contains(optDefault.toUpperCase()) ? optDefault.toUpperCase() : "'" + optDefault + "'")) +
                (optNull.equals("true") ? " NULL" : " NOT NULL");

            // Separando para adicionar uma em create table e a outras em alter table
            if (newTables.contains(colId.split("\\.")[0])) {
                createTablesColumns.computeIfAbsent(tbId, k -> new ArrayList<>()).add(columnDef);
            } else {
                addColumns.computeIfAbsent(tbId, k -> new ArrayList<>()).add(columnDef);
            }
        }

        // Percorrendo o Map dos create tables adicionados acima
        for (Map.Entry<String, List<String>> tableEntry : createTablesColumns.entrySet()) {
            String tbId = tableEntry.getKey();
            List<String> columns = tableEntry.getValue();
            // Criando o comando SQL
            StringBuilder sql = new StringBuilder();
            sql.append("CREATE TABLE `").append(searchNameKey(tbId, 0, 0, false, true)).append("` (\n")
            .append("id BIGINT PRIMARY KEY AUTO_INCREMENT,\n")
            .append(String.join(",\n", columns)).append("\n")
            .append(");\n");
            // Adicionando o comando
            commandSQL.add(sql.toString());
        }
        // Percorrendo o Map dos alter tables adicionados acima
        for (Map.Entry<String, List<String>> tableEntry : addColumns.entrySet()) {
            String tbId = tableEntry.getKey();
            List<String> columns = tableEntry.getValue();
            // Criando o comando SQL
            StringBuilder sql = new StringBuilder();
            for (String column : columns) {
                sql.append("ALTER TABLE `").append(searchNameKey(tbId, 0, 0, false, true)).append("` ").append("ADD COLUMN ").append(column).append(";\n");
            }
            // Adicionando o comando
            commandSQL.add(sql.toString());
        }
        // Retornando os comandos SQL
        return commandSQL;
    }

    private Map<String, String> orderMap(Map<String, String> map) {
        Map<String, String> order = new LinkedHashMap<>();
        // Ordenando o map
        // Primeiro as Tables
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key.contains("table")) order.put(key, value);
        }
        // Segundo as columns
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key.contains("column")) order.put(key, value);
        }
        // Terceiro os values
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (!key.contains("table") && !key.contains("column")) order.put(key, value);
        }
        // Retornando o Map em ordem
        return order;
    }

    private String searchNameKey(String key, int output, int newOrOld, boolean changeAdd, boolean other) {
        // Recebendo um diretorio, dependendo da resposta do changeAdd ele pode ser o file antigo ou o novo
        String filePath = newOrOld == 0
            ? "src/main/resources/.temp/fileNew.json"
            : "src/main/resources/.temp/fileOld.json";

        String[] splitKey = key.split("\\.");
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(new File(filePath));
            
            for (Iterator<Map.Entry<String, JsonNode>> it = root.fields(); it.hasNext(); ) {
                if (output == 0) { // Busca pelo nome da tabela
                    String tbId = other ? splitKey[0] : changeAdd ? splitKey[3] : splitKey[2]; // Ex: change.table.tb01 ou table.tb01
                    // Recebendo os valores do json
                    Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fields.next();
                        // Verificando se o cod da table existe
                        if (entry.getKey().contains(tbId)) {
                            // Retorna o nome da table
                            return entry.getKey();
                        }
                    }
                } else if (output == 1) { // Buscando pelo nome da coluna
                    String colId = other ? splitKey[1] : changeAdd ? splitKey[4] : splitKey[3]; // Ex: change.column.tb01.cn01
                    // Recebendo os valores do json
                    Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> entry = fields.next();
                        // Recebendo os valores do pai atual
                        Iterator<Map.Entry<String, JsonNode>> entryFields = entry.getValue().fields();
                        while (entryFields.hasNext()) {
                            Map.Entry<String, JsonNode> entryF = entryFields.next();
                            // Verificando se o cod da column existe
                            if (entryF.getKey().contains(colId)) {
                                // Retornando o nome da column
                                return entryF.getKey();
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao ler JSON de: " + filePath);
            e.printStackTrace();
        }
        return null;
    }

    private Map<String, JsonNode> searchOptionsColumn(String key) {
        Map<String, JsonNode> response = new HashMap<>();
        String filePath = "src/main/resources/.temp/fileNew.json";
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(new File(filePath));

            // Dividindo em partes o key: change.value.tb01.cn01.type
            String[] splitStrings = key.split("\\.");
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();

            while (fields.hasNext()) {
                // Recebendo os valores do json
                Map.Entry<String, JsonNode> entry = fields.next();
                // Recebendo os valores do pai atual
                Iterator<Map.Entry<String, JsonNode>> entryFields = entry.getValue().fields();
                while (entryFields.hasNext()) {
                    Map.Entry<String, JsonNode> entryF = entryFields.next();
                    // Verificnado se a coluna existe
                    if (entryF.getKey().contains(splitStrings[3])) {
                        Iterator<Map.Entry<String, JsonNode>> entryFieldsFields = entryF.getValue().fields();
                        while (entryFieldsFields.hasNext()) {
                            // Adiconando os vamores das colunas
                            Map.Entry<String, JsonNode> entryFF = entryFieldsFields.next();
                            response.put(entryFF.getKey(), entryFF.getValue());
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao ler JSON de: " + filePath);
            e.printStackTrace();
        }
        // Returnando o Map, com os valores das colunas adicionadas
        return response;
    }

    private boolean createFileSQL(String content) {
        try {
            // Diretório onde o arquivo será salvo
            File dir = new File(this.dir);
            // Cria o diretório se ele não existir
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                if (!created) {
                    throw new IOException("Nao foi possivel criar a pasta .temp");
                }
            }
            // Cria o arquivo command_sql.sql e escreve o conteúdo nele
            BufferedWriter writer = new BufferedWriter(new FileWriter(dir + "/command_sql.sql"));
            writer.write(content);
            writer.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean updateFileOld() {
        try {
            String contentFileNew = Files.readString(Paths.get("src/main/resources/.temp/fileNew.json"));
            File fileOld = new File("src/main/resources/.temp/fileOld.json");

            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileOld), StandardCharsets.UTF_8)); 
            bufferedWriter.write(contentFileNew);
            bufferedWriter.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
