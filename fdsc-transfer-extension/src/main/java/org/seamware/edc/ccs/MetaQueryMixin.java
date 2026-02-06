package org.seamware.edc.ccs;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

public abstract class MetaQueryMixin {

    @JsonDeserialize(using = TypeValuesDeserializer.class)
    abstract List<List<String>> getTypeValues();

}
