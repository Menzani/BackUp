package it.menzani.backup;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

class BackupName {
    private static final DateTimeFormatter LOCAL_DATE_FORMATTER = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG);

    private final String name, fileName;

    BackupName(LocalDate localDate) {
        name = localDate.format(LOCAL_DATE_FORMATTER);
        fileName = name + ".tar.gz";
    }

    String getFileName() {
        return fileName;
    }

    @Override
    public String toString() {
        return name;
    }
}
