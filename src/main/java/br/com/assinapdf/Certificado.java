package br.com.assinapdf;

import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;

/** Chave privada + cadeia de certificados usadas para assinar. */
public class Certificado {

    private static final String REPOSITORIO_WINDOWS = "Windows-MY";

    final PrivateKey chave;
    final Certificate[] cadeia;

    private Certificado(PrivateKey chave, Certificate[] cadeia) {
        this.chave = chave;
        this.cadeia = cadeia;
    }

    X509Certificate certificado() {
        return (X509Certificate) cadeia[0];
    }

    /** Nome do titular (CN). Em certificados ICP-Brasil vem como "NOME:CPF". */
    String nomeTitular() {
        try {
            X500Name subject = new JcaX509CertificateHolder(certificado()).getSubject();
            RDN[] cn = subject.getRDNs(BCStyle.CN);
            if (cn.length > 0) {
                return IETFUtils.valueToString(cn[0].getFirst().getValue());
            }
        } catch (Exception e) {
            // usa o subject completo abaixo
        }
        return certificado().getSubjectX500Principal().getName();
    }

    /** Nome sem o CPF/CNPJ ("NOME:CPF" -> "NOME"). */
    String nome() {
        String cn = nomeTitular();
        int i = cn.lastIndexOf(':');
        return i > 0 ? cn.substring(0, i) : cn;
    }

    /** "CPF: 000.000.000-00" / "CNPJ: 00.000.000/0000-00", ou null se o CN não trouxer o número. */
    String documentoFormatado() {
        String cn = nomeTitular();
        int i = cn.lastIndexOf(':');
        String num = i > 0 ? cn.substring(i + 1).replaceAll("\\D", "") : "";
        if (num.length() == 11) {
            return "CPF: " + num.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4");
        }
        if (num.length() == 14) {
            return "CNPJ: " + num.replaceFirst("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
        }
        return null;
    }

    /** Certificado A1 em arquivo .pfx / .p12. */
    static Certificado deArquivoPfx(String caminho, String senha) throws Exception {
        File arquivo = new File(caminho);
        if (!arquivo.isFile()) {
            throw new IllegalArgumentException("Certificado não encontrado: " + caminho);
        }
        char[] pin = senha != null ? senha.toCharArray() : perguntarSenha(arquivo.getName());

        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(arquivo)) {
            ks.load(in, pin);
        }
        for (String alias : Collections.list(ks.aliases())) {
            if (ks.isKeyEntry(alias)) {
                return new Certificado((PrivateKey) ks.getKey(alias, pin), ks.getCertificateChain(alias));
            }
        }
        throw new IllegalArgumentException("O arquivo " + caminho + " não contém chave privada.");
    }

    /**
     * Certificado do repositório do Windows. Funciona para A1 importado e para
     * A3 (token/cartão) com o driver instalado — o PIN é pedido pelo próprio driver.
     */
    static Certificado doWindows(String alias) throws Exception {
        KeyStore ks = KeyStore.getInstance(REPOSITORIO_WINDOWS);
        ks.load(null, null);

        if (alias == null) {
            alias = escolherAlias(ks);
        }
        if (!ks.isKeyEntry(alias)) {
            throw new IllegalArgumentException("Certificado não encontrado no Windows: " + alias);
        }
        return new Certificado((PrivateKey) ks.getKey(alias, null), ks.getCertificateChain(alias));
    }

    static void listarWindows() throws Exception {
        KeyStore ks = KeyStore.getInstance(REPOSITORIO_WINDOWS);
        ks.load(null, null);
        List<String> aliases = aliasesComChave(ks);
        if (aliases.isEmpty()) {
            System.out.println("Nenhum certificado com chave privada encontrado no Windows.");
            return;
        }
        for (int i = 0; i < aliases.size(); i++) {
            System.out.println(descrever(i + 1, aliases.get(i), (X509Certificate) ks.getCertificate(aliases.get(i))));
        }
    }

    private static String escolherAlias(KeyStore ks) throws Exception {
        List<String> aliases = aliasesComChave(ks);
        if (aliases.isEmpty()) {
            throw new IllegalStateException("Nenhum certificado com chave privada encontrado no Windows.");
        }
        if (aliases.size() == 1) {
            return aliases.get(0);
        }
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("Há vários certificados no Windows. Informe qual usar com --alias.");
        }
        System.out.println("Certificados disponíveis:");
        for (int i = 0; i < aliases.size(); i++) {
            System.out.println(descrever(i + 1, aliases.get(i), (X509Certificate) ks.getCertificate(aliases.get(i))));
        }
        String resposta = console.readLine("Escolha o número do certificado: ");
        try {
            return aliases.get(Integer.parseInt(resposta.trim()) - 1);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Opção inválida: " + resposta);
        }
    }

    private static List<String> aliasesComChave(KeyStore ks) throws Exception {
        List<String> lista = new ArrayList<>();
        for (String alias : Collections.list(ks.aliases())) {
            if (ks.isKeyEntry(alias)) {
                lista.add(alias);
            }
        }
        return lista;
    }

    private static String descrever(int numero, String alias, X509Certificate cert) {
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy");
        return String.format("  [%d] %s  (válido até %s)", numero, alias, fmt.format(cert.getNotAfter()));
    }

    private static char[] perguntarSenha(String nomeArquivo) {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("Informe a senha do certificado com --senha.");
        }
        return console.readPassword("Senha do certificado %s: ", nomeArquivo);
    }
}
