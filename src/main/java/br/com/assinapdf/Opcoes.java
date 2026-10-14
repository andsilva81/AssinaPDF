package br.com.assinapdf;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Opções de linha de comando. Valores não informados na linha de comando são
 * lidos do arquivo "config.cfg" que fica na mesma pasta do jar.
 */
public class Opcoes {

    public static final String ARQUIVO_CONFIG = "config.cfg";

    final List<File> arquivos = new ArrayList<>();
    String pfx;
    String senha;
    String alias;
    String saida;
    String motivo;
    String local;
    boolean visivel = true;
    Integer pagina;
    boolean listar;
    boolean ajuda;

    public static Opcoes ler(String[] args) throws IOException {
        Opcoes o = new Opcoes();
        o.carregarConfig();
        boolean pfxNaLinhaDeComando = false;
        boolean senhaNaLinhaDeComando = false;
        boolean aliasNaLinhaDeComando = false;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--pfx":    o.pfx = valor(args, ++i, a); pfxNaLinhaDeComando = true; break;
                case "--senha":  o.senha = valor(args, ++i, a); senhaNaLinhaDeComando = true; break;
                case "--alias":  o.alias = valor(args, ++i, a); aliasNaLinhaDeComando = true; break;
                case "--saida":  o.saida = valor(args, ++i, a); break;
                case "--motivo": o.motivo = valor(args, ++i, a); break;
                case "--local":  o.local = valor(args, ++i, a); break;
                case "--pagina": o.pagina = numeroPagina(valor(args, ++i, a)); break;
                case "--invisivel": o.visivel = false; break;
                case "--listar": o.listar = true; break;
                case "-h":
                case "--ajuda":  o.ajuda = true; break;
                default:
                    if (a.startsWith("--")) {
                        throw new IllegalArgumentException("Opção desconhecida: " + a);
                    }
                    o.arquivos.add(new File(a));
            }
        }

        // A senha do config.cfg é do certificado do config.cfg, não de outro passado em --pfx
        if (pfxNaLinhaDeComando && !senhaNaLinhaDeComando) {
            o.senha = null;
        }
        // --alias na linha de comando troca o .pfx do config.cfg pelo certificado do Windows
        if (aliasNaLinhaDeComando && !pfxNaLinhaDeComando) {
            o.pfx = null;
        }
        if (o.saida != null && o.arquivos.size() > 1) {
            throw new IllegalArgumentException("--saida só pode ser usado com um único arquivo.");
        }
        return o;
    }

    private static Integer numeroPagina(String s) {
        if (s == null || s.isEmpty() || s.equalsIgnoreCase("ultima") || s.equalsIgnoreCase("última")) {
            return null;
        }
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Página inválida: " + s + " (use um número ou \"ultima\")");
        }
    }

    private static String valor(String[] args, int i, String opcao) {
        if (i >= args.length) {
            throw new IllegalArgumentException("Faltou o valor da opção " + opcao);
        }
        return args[i];
    }

    private void carregarConfig() throws IOException {
        File pasta = pastaDoJar();
        File config = new File(pasta, ARQUIVO_CONFIG);
        if (!config.isFile()) {
            return;
        }
        Map<String, String> p = lerChaveValor(config);

        // "arquivop12" é a chave usada pelo programa antigo; caminho relativo é procurado na pasta do jar
        String certificado = p.getOrDefault("arquivop12", p.get("pfx"));
        if (certificado != null && !certificado.isEmpty()) {
            File f = new File(certificado);
            pfx = (f.isAbsolute() ? f : new File(pasta, f.getPath())).getPath();
        }
        senha = p.get("senha");
        alias = p.get("alias");
        motivo = p.get("motivo");
        local = p.get("local");
        String v = p.get("visivel");
        if (v != null) {
            visivel = !(v.equalsIgnoreCase("nao") || v.equalsIgnoreCase("não") || v.equalsIgnoreCase("n")
                    || v.equalsIgnoreCase("false") || v.equals("0"));
        }
        pagina = numeroPagina(p.get("pagina"));
    }

    /**
     * Lê linhas "chave=valor". Ignora seções [xxx] e comentários (# ou ;).
     * Não usa java.util.Properties porque ela trata "\" como escape e quebraria caminhos do Windows.
     */
    private static Map<String, String> lerChaveValor(File arquivo) throws IOException {
        Map<String, String> valores = new HashMap<>();
        for (String linha : Files.readAllLines(arquivo.toPath(), StandardCharsets.ISO_8859_1)) {
            linha = linha.trim();
            int igual = linha.indexOf('=');
            if (linha.isEmpty() || linha.startsWith("[") || linha.startsWith("#") || linha.startsWith(";") || igual < 1) {
                continue;
            }
            valores.putIfAbsent(linha.substring(0, igual).trim().toLowerCase(), linha.substring(igual + 1).trim());
        }
        return valores;
    }

    private static File pastaDoJar() {
        try {
            File jar = new File(AssinaPDF.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return jar.isFile() ? jar.getParentFile() : jar;
        } catch (Exception e) {
            return new File(".");
        }
    }

    public static void imprimirAjuda() {
        System.out.println("""
                Uso: java -jar assina.jar <arquivo.pdf> [<arquivo2.pdf> ...] [opções]

                Gera <arquivo>_ASSINADO.pdf. Se o arquivo já termina com "_ASSINADO",
                a nova assinatura é adicionada no próprio arquivo, preservando as anteriores.

                Certificado A1 (arquivo .pfx/.p12):
                  --pfx <arquivo>      caminho do certificado
                  --senha <senha>      senha do certificado (se omitida, é perguntada)

                Certificado instalado no Windows (A1 importado ou A3 em token/cartão):
                  --alias <alias>      alias do certificado (veja com --listar)
                  --listar             lista os certificados disponíveis e sai

                Selo visível (padrão: rodapé da última página, um ao lado do outro):
                  --pagina <n>         página do selo (1 = primeira; padrão: última)
                  --invisivel          assina sem selo visível

                Outras opções:
                  --saida <arquivo>    nome do arquivo de saída
                  --motivo <texto>     motivo da assinatura
                  --local <texto>      local da assinatura
                  --ajuda              mostra esta ajuda

                Os valores arquivop12, senha, alias, motivo, local, visivel (sim/nao) e pagina podem ficar em
                "config.cfg" na pasta do jar. Opções da linha de comando têm prioridade.
                """);
    }
}
