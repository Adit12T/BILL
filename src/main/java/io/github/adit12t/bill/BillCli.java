package io.github.adit12t.bill;

import io.github.adit12t.bill.model.Model.Bill;
import io.github.adit12t.bill.model.Model.Transaction;

import java.nio.file.Path;
import java.time.Clock;

/** Prints a bill: {@code BillCli USERS.json RECORDS.json USER_ID [STUDENT_ID]}. Uses the illustrative fee schedule. */
public final class BillCli {
    private BillCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4) {
            System.err.println("Usage: BillCli USERS.json RECORDS.json USER_ID [STUDENT_ID]");
            System.exit(2);
        }
        BillService service = new BillService(new JsonRecordStore(Path.of(args[0]), Path.of(args[1])),
                FeeSchedule.illustrative(), Clock.systemDefaultZone());
        service.loadUsers();
        service.loadRecords();
        service.logIn(args[2]);
        for (String id : args.length == 4 ? java.util.List.of(args[3]) : service.getStudentIDs()) {
            System.out.println(format(service.generateBill(id)));
        }
    }

    static String format(Bill bill) {
        StringBuilder sb = new StringBuilder();
        sb.append(bill.student().firstName()).append(' ').append(bill.student().lastName())
                .append(" (").append(bill.student().id()).append(") - ").append(bill.classStatus()).append(", ")
                .append(bill.college()).append('\n');
        for (Transaction t : bill.transactions()) {
            sb.append(String.format("  %s  %-8s %10s  %s%n", t.date(), t.type(),
                    (t.type().name().equals("PAYMENT") ? "-" : "") + t.amount(), t.note()));
        }
        sb.append(String.format("  %-19s %10s%n", "Balance", bill.balance()));
        return sb.toString();
    }
}
