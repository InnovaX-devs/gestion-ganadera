package com.innovax.ganadera.mercado.fuente;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pasa una página HTML a texto plano para buscar valores con expresiones regulares. No es un parser
 * completo: alcanza para páginas simples y evita depender de la estructura exacta del HTML.
 */
final class TextoHtml {

    private static final Pattern SCRIPTS = Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>");
    private static final Pattern ETIQUETAS = Pattern.compile("<[^>]+>");
    private static final Pattern NUMERICAS = Pattern.compile("&#(x?)([0-9a-fA-F]+);");
    private static final Pattern ESPACIOS = Pattern.compile("\\s+");

    private static final Map<String, String> ENTIDADES = Map.ofEntries(
        Map.entry("&nbsp;", " "),
        Map.entry("&amp;", "&"),
        Map.entry("&quot;", "\""),
        Map.entry("&lt;", "<"),
        Map.entry("&gt;", ">"),
        Map.entry("&aacute;", "á"),
        Map.entry("&eacute;", "é"),
        Map.entry("&iacute;", "í"),
        Map.entry("&oacute;", "ó"),
        Map.entry("&uacute;", "ú"),
        Map.entry("&ntilde;", "ñ"));

    private TextoHtml() {
    }

    static String aTexto(String html) {
        String texto = SCRIPTS.matcher(html).replaceAll(" ");
        texto = ETIQUETAS.matcher(texto).replaceAll(" ");
        for (var entidad : ENTIDADES.entrySet()) {
            texto = texto.replace(entidad.getKey(), entidad.getValue());
        }
        Matcher m = NUMERICAS.matcher(texto);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int codigo = Integer.parseInt(m.group(2), m.group(1).isEmpty() ? 10 : 16);
            m.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(codigo))));
        }
        m.appendTail(sb);
        return ESPACIOS.matcher(sb).replaceAll(" ").trim();
    }
}
