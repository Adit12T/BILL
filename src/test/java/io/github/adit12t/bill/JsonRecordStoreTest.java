package io.github.adit12t.bill;

import io.github.adit12t.bill.model.Model.StudentRecord;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonRecordStoreTest {

    @Test
    void sampleDataLoadsValidatesAndSurvivesASaveAndReload() throws Exception {
        Path dir = Files.createTempDirectory("bill");
        Path records = dir.resolve("records.json");
        Files.copy(Path.of("data/records.json"), records);
        JsonRecordStore store = new JsonRecordStore(Path.of("data/users.json"), records);

        BillService service = new BillService(store, FeeSchedule.illustrative(), BillServiceTest.CLOCK);
        service.loadUsers();
        service.loadRecords();
        service.logIn("admin-eng");
        assertEquals(List.of("s1001", "s1002"), service.getStudentIDs());

        service.logIn("s1001");
        // 6000 tuition + 200 technology + 250 health - 2000 merit scholarship - 1000 deposit
        assertEquals(new java.math.BigDecimal("3450.00"), service.generateBill("s1001").balance());
        service.applyPayment("s1001", "450.50", "Card payment");

        List<StudentRecord> reloaded = new JsonRecordStore(Path.of("data/users.json"), records).loadRecords();
        assertEquals(2, reloaded.get(0).transactions().size());
        assertEquals("Card payment", reloaded.get(0).transactions().get(1).note());
        assertEquals(store.loadRecords(), reloaded);
        assertTrue(Files.list(dir).noneMatch(p -> p.toString().endsWith(".tmp")), "no temporary files left");
    }

    @Test
    void cliPrintsABill() {
        String out = BillCli.format(sampleBill());
        assertTrue(out.contains("Balance"), out);
    }

    private static io.github.adit12t.bill.model.Model.Bill sampleBill() {
        var r = BillServiceTest.record("s9", io.github.adit12t.bill.model.Enums.College.BUSINESS,
                io.github.adit12t.bill.model.Enums.ClassStatus.SENIOR, true, BillServiceTest.courses(4, 3, false));
        var charges = new BillCalculator(FeeSchedule.illustrative()).chargesFor(r, BillServiceTest.TODAY);
        return new io.github.adit12t.bill.model.Model.Bill(r.student(), r.college(), r.classStatus(), charges, BillCalculator.balance(charges));
    }
}
