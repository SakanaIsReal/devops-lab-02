package com.smartsplit.smartsplitback.model.converter;

import com.smartsplit.smartsplitback.model.Role;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RoleConverter implements AttributeConverter<Role, Integer> {

    @Override
    public Integer convertToDatabaseColumn(Role attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public Role convertToEntityAttribute(Integer dbData) {
        return dbData == null ? null : Role.fromCode(dbData);
    }
}
