package br.com.assinapdf;

import java.io.Console;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;

/**
 * Ponto de entrada:  java -jar assina.jar "c:\arquivo1.pdf"
 */
public class AssinaPDF {

    private static final String SUFIXO = "_ASSINADO";

    public static void main(String[] args) {
        ajustarAcentosNoConsole();
        // Esconde avisos internos do PDFBox (ex.: "Building on-disk font cache")
        java.util.logging.Logger.getLogger("org.apache.pdfbox").setLevel(java.util.logging.Level.SEVERE);
        try {
            System.exit(executar(args));
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.err.println("Erro: " + e.getMessage());
            System.exit(2);
        } catch (Exception e) {
            System.err.println("Erro: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static int executar(String[] args) throws Exception {
        Opcoes opcoes = Opcoes.ler(args);

        if (opcoes.ajuda || (args.length == 0)) {
            Opcoes.imprimirAjuda();
            return 0;
        }
        if (opcoes.listar) {
            Certificado.listarWindows();
            return 0;
        }
        if (opcoes.arquivos.isEmpty()) {
            throw new IllegalArgumentException("Informe o arquivo PDF a assinar.");
        }

        Certificado certificado = opcoes.pfx != null
                ? Certificado.deArquivoPfx(opcoes.pfx, opcoes.senha)
                : Certificado.doWindows(opcoes.alias);
        verificarValidade(certificado);
        System.out.println("Assinando como: " + certificado.nomeTitular());

        AssinadorPDF assinador = new AssinadorPDF(certificado);
        assinador.setVisivel(opcoes.visivel);
        assinador.setPagina(opcoes.pagina);
        int falhas = 0;
        for (File entrada : opcoes.arquivos) {
            try {
                File saida = opcoes.saida != null ? new File(opcoes.saida) : arquivoDeSaida(entrada);
                assinarArquivo(assinador, entrada, saida, opcoes);
                System.out.println("OK: " + saida.getAbsolutePath());
            } catch (Exception e) {
                falhas++;
                System.err.println("FALHA: " + entrada + " -> " + e.getMessage());
            }
        }
        return falhas == 0 ? 0 : 1;
    }

    private static void assinarArquivo(AssinadorPDF assinador, File entrada, File saida, Opcoes opcoes)
            throws Exception {
        if (!entrada.isFile()) {
            throw new IllegalArgumentException("arquivo não encontrado");
        }
        // Grava num temporário: permite que a saída seja o próprio arquivo de entrada
        File pasta = saida.getAbsoluteFile().getParentFile();
        File temp = File.createTempFile("assina", ".pdf", pasta);
        try {
            assinador.assinar(entrada, temp, opcoes.motivo, opcoes.local);
            Files.move(temp.toPath(), saida.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }

    /**
     * arquivo1.pdf          -> arquivo1_ASSINADO.pdf
     * arquivo1_ASSINADO.pdf -> arquivo1_ASSINADO.pdf (adiciona a 2ª assinatura no mesmo arquivo)
     */
    static File arquivoDeSaida(File entrada) {
        String nome = entrada.getName();
        int ponto = nome.lastIndexOf('.');
        String base = ponto > 0 ? nome.substring(0, ponto) : nome;
        String ext = ponto > 0 ? nome.substring(ponto) : ".pdf";
        if (base.toUpperCase().endsWith(SUFIXO)) {
            return entrada;
        }
        return new File(entrada.getAbsoluteFile().getParentFile(), base + SUFIXO + ext);
    }

    private static void verificarValidade(Certificado certificado) {
        try {
            certificado.certificado().checkValidity();
        } catch (CertificateExpiredException e) {
            throw new IllegalStateException("O certificado está vencido.");
        } catch (CertificateNotYetValidException e) {
            throw new IllegalStateException("O certificado ainda não é válido.");
        }
    }

    /** No Windows o console usa outra codificação (cp850); sem isso os acentos saem errados. */
    private static void ajustarAcentosNoConsole() {
        Console console = System.console();
        if (console != null) {
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, console.charset()));
            System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, console.charset()));
        }
    }
}
