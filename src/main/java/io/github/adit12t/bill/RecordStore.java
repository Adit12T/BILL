package io.github.adit12t.bill;

import io.github.adit12t.bill.model.Model.StudentRecord;
import io.github.adit12t.bill.model.Model.User;

import java.io.IOException;
import java.util.List;

/** Where users and student records come from and where permanent changes go. */
public interface RecordStore {
    List<User> loadUsers() throws IOException;

    List<StudentRecord> loadRecords() throws IOException;

    void saveRecords(List<StudentRecord> records) throws IOException;
}
