package com.innovax.ganadera.animal.importacion;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.innovax.ganadera.agrupacion.Agrupacion;
import com.innovax.ganadera.agrupacion.Lote;
import com.innovax.ganadera.agrupacion.LoteRepository;
import com.innovax.ganadera.agrupacion.Rodeo;
import com.innovax.ganadera.agrupacion.RodeoRepository;
import com.innovax.ganadera.animal.Animal;
import com.innovax.ganadera.animal.AnimalRepository;
import com.innovax.ganadera.animal.Categoria;
import com.innovax.ganadera.animal.EstadoAnimal;
import com.innovax.ganadera.animal.Sexo;
import com.innovax.ganadera.animal.importacion.dto.FilaImportacion;
import com.innovax.ganadera.animal.importacion.dto.ImportacionRequest;
import com.innovax.ganadera.animal.importacion.dto.ImportacionResponse;
import com.innovax.ganadera.animal.importacion.dto.ImportacionResponse.ErrorFila;
import com.innovax.ganadera.common.error.BusinessException;
import com.innovax.ganadera.common.error.NotFoundException;
import com.innovax.ganadera.establecimiento.Establecimiento;
import com.innovax.ganadera.establecimiento.EstablecimientoRepository;
import com.innovax.ganadera.membresia.AutorizacionService;
import com.innovax.ganadera.membresia.Permiso;

import lombok.RequiredArgsConstructor;

/**
 * Importación de animales desde una planilla (#31), en dos pasos:
 *
 * <ol>
 *   <li><b>Vista previa</b> ({@code confirmar=false}): revisa cada fila y devuelve los errores sin
 *       guardar nada.</li>
 *   <li><b>Confirmar</b> ({@code confirmar=true}): vuelve a revisar y, si no hay ningún error,
 *       guarda todo en una transacción. Si hay al menos uno, no guarda nada (422).</li>
 * </ol>
 *
 * <p>Aplica las mismas reglas que el alta manual ({@code AnimalService}): caravana única entre los
 * activos del establecimiento, electrónica única en la organización, categoría acorde al sexo y
 * madre hembra / padre macho. Además, los lotes y rodeos se indican por nombre y se crean si no
 * existen, y la madre o el padre se indican por caravana: pueden estar en la misma planilla.
 */
@Service
@RequiredArgsConstructor
public class ImportacionAnimalesService {

    private static final List<DateTimeFormatter> FORMATOS_FECHA = List.of(
        DateTimeFormatter.ofPattern("uuuu-M-d").withResolverStyle(ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT));

    private final AnimalRepository animalRepository;
    private final EstablecimientoRepository establecimientoRepository;
    private final LoteRepository loteRepository;
    private final RodeoRepository rodeoRepository;
    private final AutorizacionService autorizacion;
    private final Clock reloj;

    @Transactional
    public ImportacionResponse importar(UUID establecimientoId, ImportacionRequest request, boolean confirmar) {
        autorizacion.exigir(establecimientoId, Permiso.GESTIONAR_ANIMALES);
        Establecimiento establecimiento = establecimientoRepository.findByIdAndActivoTrue(establecimientoId)
            .orElseThrow(() -> NotFoundException.of("Establecimiento", establecimientoId));

        Revision revision = revisar(establecimientoId, request.filas());
        int validas = (int) revision.filas.stream().filter(f -> !revision.conError.contains(f.numero)).count();

        if (!confirmar) {
            return revision.respuesta(validas, 0);
        }
        if (!revision.errores.isEmpty()) {
            throw new BusinessException("La planilla tiene " + revision.errores.size()
                + (revision.errores.size() == 1 ? " error" : " errores") + ". Corregilos y volvé a cargarla.");
        }
        if (!revision.lotesNuevos.isEmpty() || !revision.rodeosNuevos.isEmpty()) {
            autorizacion.exigir(establecimientoId, Permiso.GESTIONAR_AGRUPACIONES);
        }
        guardar(establecimiento, revision);
        return revision.respuesta(validas, revision.filas.size());
    }

    // ---------- Revisión ----------

    /** Una fila ya interpretada. Los campos con error quedan en null. */
    private static final class Fila {
        int numero;
        String caravana;
        String caravanaElectronica;
        Sexo sexo;
        Categoria categoria;
        String raza;
        LocalDate fechaNacimiento;
        boolean fechaNacimientoEstimada;
        boolean castrado;
        String lote;
        String rodeo;
        String madre;
        String padre;
        String observaciones;
    }

    private static final class Revision {
        final List<Fila> filas = new ArrayList<>();
        final List<ErrorFila> errores = new ArrayList<>();
        final Set<Integer> conError = new HashSet<>();
        /** Animales ya cargados en el establecimiento, por caravana (los de la planilla y sus padres). */
        Map<String, Animal> existentes = Map.of();
        final Map<String, Lote> lotes = new HashMap<>();
        final Map<String, Rodeo> rodeos = new HashMap<>();
        final List<String> lotesNuevos = new ArrayList<>();
        final List<String> rodeosNuevos = new ArrayList<>();

        void error(int fila, String campo, String mensaje) {
            errores.add(new ErrorFila(fila, campo, mensaje));
            conError.add(fila);
        }

        ImportacionResponse respuesta(int validas, int creados) {
            errores.sort(Comparator.comparingInt(ErrorFila::fila));
            return new ImportacionResponse(filas.size(), validas, creados, errores, lotesNuevos, rodeosNuevos);
        }
    }

    private Revision revisar(UUID establecimientoId, List<FilaImportacion> entrada) {
        Revision r = new Revision();
        for (int i = 0; i < entrada.size(); i++) {
            FilaImportacion original = entrada.get(i);
            // La fila 1 de la planilla son los títulos: los datos empiezan en la 2
            int numero = original.fila() != null ? original.fila() : i + 2;
            r.filas.add(interpretar(original, numero, r));
        }

        revisarCaravanas(establecimientoId, r);
        revisarProgenitores(r);
        r.lotes.putAll(agrupaciones(loteRepository.findByEstablecimientoIdAndActivoTrueOrderByNombreAsc(establecimientoId)));
        r.rodeos.putAll(agrupaciones(rodeoRepository.findByEstablecimientoIdAndActivoTrueOrderByNombreAsc(establecimientoId)));
        r.lotesNuevos.addAll(faltantes(r.filas, f -> f.lote, r.lotes.keySet()));
        r.rodeosNuevos.addAll(faltantes(r.filas, f -> f.rodeo, r.rodeos.keySet()));
        return r;
    }

    private Fila interpretar(FilaImportacion o, int numero, Revision r) {
        Fila f = new Fila();
        f.numero = numero;

        f.caravana = Animal.normalizarCaravana(o.caravana());
        if (f.caravana == null) {
            r.error(numero, "caravana", "Falta la caravana");
        } else if (f.caravana.length() > 30) {
            r.error(numero, "caravana", "La caravana tiene más de 30 caracteres");
            f.caravana = null;
        }

        f.caravanaElectronica = Animal.normalizarCaravana(o.caravanaElectronica());
        if (f.caravanaElectronica != null && f.caravanaElectronica.length() > 30) {
            r.error(numero, "caravanaElectronica", "La caravana electrónica tiene más de 30 caracteres");
            f.caravanaElectronica = null;
        }

        String categoria = texto(o.categoria());
        if (categoria == null) {
            r.error(numero, "categoria", "Falta la categoría");
        } else {
            f.categoria = categoria(categoria);
            if (f.categoria == null) {
                r.error(numero, "categoria", "\"" + categoria + "\" no es una categoría (vaca, vaquillona, ternera, "
                    + "ternero, novillito, novillo, torito o toro)");
            }
        }

        String sexo = texto(o.sexo());
        if (sexo == null) {
            // Sin sexo, alcanza con la categoría: "vaca" ya dice que es hembra
            if (f.categoria != null) f.sexo = f.categoria.sexo();
            else if (categoria == null) r.error(numero, "sexo", "Falta el sexo");
        } else {
            f.sexo = sexo(sexo);
            if (f.sexo == null) {
                r.error(numero, "sexo", "\"" + sexo + "\" no es un sexo válido (hembra o macho)");
            } else if (f.categoria != null && f.categoria.sexo() != f.sexo) {
                r.error(numero, "categoria", "La categoría " + nombre(f.categoria) + " no corresponde a un "
                    + nombre(f.sexo));
                f.categoria = null;
            }
        }

        f.raza = limitado(o.raza(), 50, numero, "raza", "La raza", r);
        f.observaciones = limitado(o.observaciones(), 500, numero, "observaciones", "Las observaciones", r);

        String fecha = texto(o.fechaNacimiento());
        if (fecha != null) {
            f.fechaNacimiento = fecha(fecha);
            if (f.fechaNacimiento == null) {
                r.error(numero, "fechaNacimiento", "\"" + fecha + "\" no es una fecha (usá 15/09/2024)");
            } else if (f.fechaNacimiento.isAfter(LocalDate.now(reloj))) {
                r.error(numero, "fechaNacimiento", "La fecha de nacimiento no puede ser futura");
                f.fechaNacimiento = null;
            }
        }
        Boolean estimada = siNo(o.fechaNacimientoEstimada(), numero, "fechaNacimientoEstimada", r);
        f.fechaNacimientoEstimada = f.fechaNacimiento != null && Boolean.TRUE.equals(estimada);
        Boolean castrado = siNo(o.castrado(), numero, "castrado", r);
        f.castrado = f.categoria != null && f.categoria.castrado(Boolean.TRUE.equals(castrado));

        f.lote = limitado(o.lote(), 100, numero, "lote", "El nombre del lote", r);
        f.rodeo = limitado(o.rodeo(), 100, numero, "rodeo", "El nombre del rodeo", r);
        f.madre = Animal.normalizarCaravana(o.madre());
        f.padre = Animal.normalizarCaravana(o.padre());
        return f;
    }

    /** Repetidas dentro de la planilla y contra lo que ya hay cargado. */
    private void revisarCaravanas(UUID establecimientoId, Revision r) {
        Map<String, Integer> vistas = new HashMap<>();
        Map<String, Integer> electronicasVistas = new HashMap<>();
        for (Fila f : r.filas) {
            if (f.caravana != null) {
                Integer anterior = vistas.putIfAbsent(f.caravana, f.numero);
                if (anterior != null) {
                    r.error(f.numero, "caravana", "La caravana " + f.caravana + " ya está en la fila " + anterior);
                }
            }
            if (f.caravanaElectronica != null) {
                Integer anterior = electronicasVistas.putIfAbsent(f.caravanaElectronica, f.numero);
                if (anterior != null) {
                    r.error(f.numero, "caravanaElectronica", "La caravana electrónica " + f.caravanaElectronica
                        + " ya está en la fila " + anterior);
                }
            }
        }

        Set<String> buscadas = new HashSet<>(vistas.keySet());
        r.filas.forEach(f -> {
            if (f.madre != null) buscadas.add(f.madre);
            if (f.padre != null) buscadas.add(f.padre);
        });
        r.existentes = buscadas.isEmpty() ? Map.of() : animalRepository
            .findByEstablecimientoIdAndCaravanaInAndEstado(establecimientoId, buscadas, EstadoAnimal.ACTIVO)
            .stream().collect(Collectors.toMap(Animal::getCaravana, Function.identity(), (a, b) -> a));

        Set<String> electronicasCargadas = electronicasVistas.isEmpty() ? Set.of() : animalRepository
            .findByCaravanaElectronicaInAndEstado(electronicasVistas.keySet(), EstadoAnimal.ACTIVO)
            .stream().map(Animal::getCaravanaElectronica).collect(Collectors.toSet());

        for (Fila f : r.filas) {
            if (f.caravana != null && r.existentes.containsKey(f.caravana)) {
                r.error(f.numero, "caravana", "Ya hay un animal con la caravana " + f.caravana
                    + " en este establecimiento");
            }
            if (f.caravanaElectronica != null && electronicasCargadas.contains(f.caravanaElectronica)) {
                r.error(f.numero, "caravanaElectronica", "Ya hay un animal con la caravana electrónica "
                    + f.caravanaElectronica);
            }
        }
    }

    /** La madre y el padre pueden estar ya cargados o venir en la planilla. */
    private void revisarProgenitores(Revision r) {
        Map<String, Fila> delArchivo = new HashMap<>();
        r.filas.forEach(f -> {
            if (f.caravana != null) delArchivo.putIfAbsent(f.caravana, f);
        });
        for (Fila f : r.filas) {
            revisarProgenitor(f, f.madre, "madre", Sexo.HEMBRA, delArchivo, r);
            revisarProgenitor(f, f.padre, "padre", Sexo.MACHO, delArchivo, r);
            Fila madre = f.madre == null ? null : delArchivo.get(f.madre);
            if (madre != null && madre != f && f.caravana != null && f.caravana.equals(madre.madre)) {
                r.error(f.numero, "madre", "La madre no puede ser hija de este mismo animal");
            }
        }
    }

    private void revisarProgenitor(Fila f, String caravana, String campo, Sexo sexo,
                                   Map<String, Fila> delArchivo, Revision r) {
        if (caravana == null) return;
        String quien = campo.equals("madre") ? "La madre" : "El padre";
        if (caravana.equals(f.caravana)) {
            r.error(f.numero, campo, "Un animal no puede ser su propia " + campo);
            return;
        }
        Animal cargado = r.existentes.get(caravana);
        Sexo sexoProgenitor;
        if (cargado != null) {
            sexoProgenitor = cargado.getSexo();
        } else if (delArchivo.containsKey(caravana)) {
            sexoProgenitor = delArchivo.get(caravana).sexo;
        } else {
            r.error(f.numero, campo, "No hay ningún animal con la caravana " + caravana
                + " (ni en la planilla ni en este establecimiento)");
            return;
        }
        if (sexoProgenitor != null && sexoProgenitor != sexo) {
            r.error(f.numero, campo, quien + " (" + caravana + ") tiene que ser " + nombre(sexo));
        }
    }

    // ---------- Guardado ----------

    private void guardar(Establecimiento establecimiento, Revision r) {
        for (String nombre : r.lotesNuevos) {
            r.lotes.put(clave(nombre), loteRepository.save(new Lote(establecimiento, nombre)));
        }
        for (String nombre : r.rodeosNuevos) {
            r.rodeos.put(clave(nombre), rodeoRepository.save(new Rodeo(establecimiento, nombre)));
        }

        // Primero todos los animales y después madre y padre: así una cría puede estar más arriba
        // que su madre en la planilla sin importar el orden de los INSERT.
        Map<String, Animal> nuevos = new LinkedHashMap<>();
        for (Fila f : r.filas) {
            Animal a = new Animal(establecimiento, f.caravana, f.sexo, f.categoria);
            a.setCaravanaElectronica(f.caravanaElectronica);
            a.setRaza(f.raza);
            a.setFechaNacimiento(f.fechaNacimiento);
            a.setFechaNacimientoEstimada(f.fechaNacimientoEstimada);
            a.setCastrado(f.castrado);
            a.setObservaciones(f.observaciones);
            if (f.lote != null) a.setLote(r.lotes.get(clave(f.lote)));
            if (f.rodeo != null) a.setRodeo(r.rodeos.get(clave(f.rodeo)));
            nuevos.put(f.caravana, a);
        }
        animalRepository.saveAllAndFlush(nuevos.values());

        boolean conProgenitores = false;
        for (Fila f : r.filas) {
            Animal a = nuevos.get(f.caravana);
            if (f.madre != null) {
                a.setMadre(r.existentes.getOrDefault(f.madre, nuevos.get(f.madre)));
                conProgenitores = true;
            }
            if (f.padre != null) {
                a.setPadre(r.existentes.getOrDefault(f.padre, nuevos.get(f.padre)));
                conProgenitores = true;
            }
        }
        if (conProgenitores) animalRepository.flush();
    }

    // ---------- Interpretación de valores ----------

    private static String texto(String valor) {
        if (valor == null) return null;
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    /** Minúsculas y sin acentos, para comparar lo que escribió el usuario. */
    private static String clave(String valor) {
        return Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT);
    }

    private static Categoria categoria(String valor) {
        String c = clave(valor);
        for (Categoria categoria : Categoria.values()) {
            if (categoria.name().toLowerCase(Locale.ROOT).equals(c)) return categoria;
        }
        return null;
    }

    private static Sexo sexo(String valor) {
        return switch (clave(valor)) {
            case "hembra", "h", "f" -> Sexo.HEMBRA;
            case "macho", "m" -> Sexo.MACHO;
            default -> null;
        };
    }

    private static LocalDate fecha(String valor) {
        for (DateTimeFormatter formato : FORMATOS_FECHA) {
            try {
                return LocalDate.parse(valor, formato);
            } catch (DateTimeParseException e) {
                // probar el siguiente formato
            }
        }
        return null;
    }

    private static Boolean siNo(String valor, int fila, String campo, Revision r) {
        String v = texto(valor);
        if (v == null) return null;
        return switch (clave(v)) {
            case "si", "s", "x", "true", "verdadero", "1" -> true;
            case "no", "n", "false", "falso", "0" -> false;
            default -> {
                r.error(fila, campo, "\"" + v + "\" no se entiende: poné Sí o No");
                yield null;
            }
        };
    }

    private static String limitado(String valor, int maximo, int fila, String campo, String que, Revision r) {
        String v = texto(valor);
        if (v != null && v.length() > maximo) {
            r.error(fila, campo, que + " tiene más de " + maximo + " caracteres");
            return null;
        }
        return v;
    }

    private static <E extends Agrupacion> Map<String, E> agrupaciones(List<E> existentes) {
        Map<String, E> porNombre = new HashMap<>();
        existentes.forEach(a -> porNombre.putIfAbsent(clave(a.getNombre()), a));
        return porNombre;
    }

    /** Nombres usados en la planilla que no existen todavía, con la escritura de la primera aparición. */
    private static List<String> faltantes(List<Fila> filas, Function<Fila, String> campo, Set<String> existentes) {
        Map<String, String> nuevos = new LinkedHashMap<>();
        for (Fila f : filas) {
            String nombre = campo.apply(f);
            if (nombre != null && !existentes.contains(clave(nombre))) nuevos.putIfAbsent(clave(nombre), nombre);
        }
        return new ArrayList<>(nuevos.values());
    }

    private static String nombre(Enum<?> valor) {
        return valor.name().toLowerCase(Locale.ROOT);
    }
}
