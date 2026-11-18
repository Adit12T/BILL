package io.github.adit12t.bill;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.adit12t.bill.model.Model.StudentRecord;
import io.github.adit12t.bill.model.Model.User;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Users and records kept in two JSON files. Saves are atomic: write a temporary file, then move it into place. */
public final class JsonRecordStore implements RecordStore {

    static final ObjectMapper JSON = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final Path usersFile;
    private final Path recordsFile;

    public JsonRecordStore(Path usersFile, Path recordsFile) {
        this.usersFile = usersFile;
        this.recordsFile = recordsFile;
    }

    @Override
    public List<User> loadUsers() throws IOException {
        return JSON.readValue(usersFile.toFile(), new TypeReference<List<User>>() { });
    }

    @Override
    public List<StudentRecord> loadRecords() throws IOException {
        return JSON.readValue(recordsFile.toFile(), new TypeReference<List<StudentRecord>>() { });
    }

    @Override
    public void saveRecords(List<StudentRecord> records) throws IOException {
        Path dir = recordsFile.toAbsolutePath().getParent();
        Path tmp = Files.createTempFile(dir, "records", ".tmp");
        try {
            JSON.writeValue(tmp.toFile(), records);
            Files.move(tmp, recordsFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
