package com.smartsplit.smartsplitback.model.converter;

import com.smartsplit.smartsplitback.model.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleConverterTest {

    private final RoleConverter converter = new RoleConverter();


    @Test
    void convertToDatabaseColumn_null_returnsNull() {
        Integer out = converter.convertToDatabaseColumn(null);
        assertThat(out).isNull();
    }

    @Test
    void convertToDatabaseColumn_allRoles_matchRoleCode() {
        for (Role r : Role.values()) {
            Integer code = converter.convertToDatabaseColumn(r);
            assertThat(code).isEqualTo(r.code());
        }
    }

    @Test
    void convertToEntityAttribute_null_returnsNull() {
        Role out = converter.convertToEntityAttribute(null);
        assertThat(out).isNull();
    }

    @Test
    void convertToEntityAttribute_allCodes_roundTripToRole() {
        for (Role r : Role.values()) {
            Role out = converter.convertToEntityAttribute(r.code());
            assertThat(out).isEqualTo(r);
        }
    }
}
