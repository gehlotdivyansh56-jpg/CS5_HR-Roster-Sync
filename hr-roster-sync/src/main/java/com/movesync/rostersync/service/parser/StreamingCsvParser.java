package com.movesync.rostersync.service.parser;

import com.movesync.rostersync.domain.enums.EventType;
import com.movesync.rostersync.dto.SyncRecordDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/**
 * Memory-bounded streaming CSV parser.
 * Reads line-by-line via BufferedReader to ensure O(1) memory consumption regardless of file size.
 */
@Component
public class StreamingCsvParser {

    private static final Logger log = LoggerFactory.getLogger(StreamingCsvParser.class);

    public int parseStream(InputStream inputStream, Consumer<SyncRecordDto> recordConsumer) throws IOException {
        int recordCount = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return 0;
            }

            Map<String, Integer> headerIndexMap = parseHeaders(headerLine);

            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                List<String> tokens = parseCsvLine(trimmed);
                SyncRecordDto record = mapTokensToDto(tokens, headerIndexMap);
                if (record != null) {
                    recordConsumer.accept(record);
                    recordCount++;
                }
            }
        }

        return recordCount;
    }

    private Map<String, Integer> parseHeaders(String headerLine) {
        Map<String, Integer> map = new HashMap<>();
        List<String> headers = parseCsvLine(headerLine);
        for (int i = 0; i < headers.size(); i++) {
            map.put(headers.get(i).trim().toLowerCase(), i);
        }
        return map;
    }

    private SyncRecordDto mapTokensToDto(List<String> tokens, Map<String, Integer> headerIndexMap) {
        String empId = getToken(tokens, headerIndexMap, "source_employee_id", 0);
        if (empId == null || empId.trim().isEmpty()) {
            return null;
        }

        SyncRecordDto dto = new SyncRecordDto();
        dto.setSourceEmployeeId(empId.trim());
        dto.setEventType(EventType.CREATE); // Nightly file entries are authoritative state
        dto.setName(getToken(tokens, headerIndexMap, "name", 1));
        dto.setEmail(getToken(tokens, headerIndexMap, "email", 2));
        dto.setPhone(getToken(tokens, headerIndexMap, "phone", 3));
        dto.setManagerSourceId(getToken(tokens, headerIndexMap, "manager_source_id", 4));
        dto.setOfficeCode(getToken(tokens, headerIndexMap, "office_code", 5));
        dto.setDepartment(getToken(tokens, headerIndexMap, "department", 6));
        dto.setShift(getToken(tokens, headerIndexMap, "shift", 7));
        return dto;
    }

    private String getToken(List<String> tokens, Map<String, Integer> headerIndexMap, String headerName, int fallbackIndex) {
        Integer index = headerIndexMap.get(headerName);
        if (index == null) {
            index = fallbackIndex;
        }
        if (index < tokens.size()) {
            String val = tokens.get(index).trim();
            return val.isEmpty() ? null : val;
        }
        return null;
    }

    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        result.add(current.toString().trim());
        return result;
    }
}

