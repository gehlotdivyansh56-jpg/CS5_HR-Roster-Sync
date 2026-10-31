package com.movesync.rostersync.service.parser;

import com.movesync.rostersync.dto.SyncRecordDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingCsvParserTest {

    private final StreamingCsvParser parser = new StreamingCsvParser();

    @Test
    @DisplayName("Should stream CSV lines and parse fields into SyncRecordDto")
    void shouldParseCsvStream() throws IOException {
        String csvData = """
                source_employee_id,name,email,phone,manager_source_id,office_code,department,shift
                EMP-001,Alice Johnson,alice@corp.com,+12025550101,MGR-10,BLR-01,Engineering,DAY
                EMP-002,Bob Smith,bob@corp.com,+12025550102,,BLR-02,Marketing,NIGHT
                """;

        List<SyncRecordDto> records = new ArrayList<>();
        int count = parser.parseStream(
                new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8)),
                records::add
        );

        assertThat(count).isEqualTo(2);
        assertThat(records).hasSize(2);

        SyncRecordDto rec1 = records.get(0);
        assertThat(rec1.getSourceEmployeeId()).isEqualTo("EMP-001");
        assertThat(rec1.getName()).isEqualTo("Alice Johnson");
        assertThat(rec1.getEmail()).isEqualTo("alice@corp.com");
        assertThat(rec1.getPhone()).isEqualTo("+12025550101");
        assertThat(rec1.getManagerSourceId()).isEqualTo("MGR-10");
        assertThat(rec1.getOfficeCode()).isEqualTo("BLR-01");
        assertThat(rec1.getDepartment()).isEqualTo("Engineering");
        assertThat(rec1.getShift()).isEqualTo("DAY");

        SyncRecordDto rec2 = records.get(1);
        assertThat(rec2.getSourceEmployeeId()).isEqualTo("EMP-002");
        assertThat(rec2.getManagerSourceId()).isNull();
        assertThat(rec2.getShift()).isEqualTo("NIGHT");
    }

    @Test
    @DisplayName("Should skip empty lines and commented lines starting with #")
    void shouldSkipEmptyAndCommentedLines() throws IOException {
        String csvData = """
                source_employee_id,name,email,phone,manager_source_id,office_code,department,shift
                # Comment line
                
                EMP-100,Charlie Brown,charlie@corp.com,,,,,
                """;

        List<SyncRecordDto> records = new ArrayList<>();
        int count = parser.parseStream(
                new ByteArrayInputStream(csvData.getBytes(StandardCharsets.UTF_8)),
                records::add
        );

        assertThat(count).isEqualTo(1);
        assertThat(records.get(0).getSourceEmployeeId()).isEqualTo("EMP-100");
    }
}

