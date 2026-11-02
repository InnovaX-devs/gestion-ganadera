package com.innovax.ganadera.animal;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** {@code animal.versiones}: "caravana=1761832800000;raza=1761836400000" (epoch millis). */
@Converter
public class VersionesConverter implements AttributeConverter<Map<CampoAnimal, Instant>, String> {

    @Override
    public String convertToDatabaseColumn(Map<CampoAnimal, Instant> versiones) {
        if (versiones == null || versiones.isEmpty()) return null;
        return versiones.entrySet().stream()
            .map(e -> e.getKey().nombre() + "=" + e.getValue().toEpochMilli())
            .collect(Collectors.joining(";"));
    }

    @Override
    public Map<CampoAnimal, Instant> convertToEntityAttribute(String texto) {
        Map<CampoAnimal, Instant> versiones = new EnumMap<>(CampoAnimal.class);
        if (texto == null || texto.isBlank()) return versiones;
        for (String parte : texto.split(";")) {
            String[] clave = parte.split("=", 2);
            if (clave.length != 2) continue;
            try {
                long millis = Long.parseLong(clave[1].trim());
                CampoAnimal.deNombre(clave[0].trim()).ifPresent(c -> versiones.put(c, Instant.ofEpochMilli(millis)));
            } catch (NumberFormatException ignorado) {
                // Una entrada rota se ignora: el campo vuelve a valer created_at
            }
        }
        return versiones;
    }
}
