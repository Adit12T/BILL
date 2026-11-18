package io.github.adit12t.bill.model;

/** Holder for the small enums used across the model (kept together so the model reads in one place). */
public final class Enums {
    private Enums() {
    }

    public enum Role { ADMIN, STUDENT }

    public enum College { ARTS_AND_SCIENCES, ENGINEERING_AND_COMPUTING, BUSINESS, GRADUATE_SCHOOL }

    public enum ClassStatus {
        FRESHMAN, SOPHOMORE, JUNIOR, SENIOR, MASTERS, PHD, GRADUATED;

        public boolean undergraduate() {
            return ordinal() <= SENIOR.ordinal();
        }
    }

    public enum Season { SPRING, SUMMER, FALL }

    public enum InternationalStatus { NONE, SHORT_TERM, SPONSORED }

    public enum StudyAbroad { NONE, REGULAR, COHORT }

    public enum Scholarship { NONE, MERIT, ATHLETIC, NEED_BASED }

    public enum TransactionType { CHARGE, PAYMENT }
}
