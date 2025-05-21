package create.ddl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Main {
    public static void main(String[] args) {

        try {
            String fileString = Files.readString(Paths.get("src/main/resources/fileTest.json"));
            LocalDateTime dateTime = LocalDateTime.parse("2025-05-21 14:05:33", DateTimeFormatter.ofPattern("yyyy-MM-dd H:m:s"));
            // Voce pode utilizar a propria data do file se quiser também
            // Recupero a ultima modificação do file com o lastModified()
            // long lastModified = file.lastModified();
            // 
            // Converte para LocalDateTime e pronto
            // LocalDateTime data = Instant.ofEpochMilli(lastModified).atZone(ZoneId.systemDefault()).toLocalDateTime();
            // 
            // Informe o caminho absoluto para salvar o arquivo.
            // Caso o diretório não exista, ele será criado automaticamente.
            // Ex:
            // "C:/Users/seu_usuario/Desktop"
            // "C:/Users/seu_usuario/Desktop/Meu_SQL" (a pasta "Meu_SQL" será criada se não existir)
            new CreateDDL("./").init(fileString, dateTime);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
