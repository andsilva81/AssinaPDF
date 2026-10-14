package br.com.assinapdf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Calendar;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

/**
 * Assina PDFs permitindo várias assinaturas no mesmo arquivo.
 *
 * O que permite múltiplas assinaturas:
 *  1. Salvamento INCREMENTAL: a nova assinatura é anexada ao final do arquivo sem
 *     alterar nenhum byte já assinado. Salvar o PDF inteiro de novo (doc.save)
 *     reescreve o arquivo e invalida as assinaturas anteriores.
 *  2. Assinatura de APROVAÇÃO (não "certificação"/DocMDP), que não bloqueia
 *     assinaturas posteriores.
 *  3. O selo visível é a aparência do campo de assinatura (ver {@link SeloVisual}),
 *     e não um desenho no conteúdo da página.
 */
public class AssinadorPDF implements SignatureInterface {

    private final Certificado certificado;
    private boolean visivel = true;
    private Integer pagina; // 1 = primeira; null = última

    public AssinadorPDF(Certificado certificado) {
        this.certificado = certificado;
    }

    public void setVisivel(boolean visivel) {
        this.visivel = visivel;
    }

    public void setPagina(Integer pagina) {
        this.pagina = pagina;
    }

    public void assinar(File entrada, File saida, String motivo, String local) throws IOException {
        try (PDDocument doc = Loader.loadPDF(entrada);
             SignatureOptions opcoes = new SignatureOptions()) {

            if (permissaoDocMDP(doc) == 1) {
                throw new IllegalStateException(
                        "O documento foi certificado sem permitir alterações; não aceita novas assinaturas.");
            }

            PDSignature assinatura = new PDSignature();
            assinatura.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            assinatura.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            assinatura.setName(certificado.nomeTitular());
            Calendar agora = Calendar.getInstance();
            assinatura.setSignDate(agora);
            if (motivo != null) {
                assinatura.setReason(motivo);
            }
            if (local != null) {
                assinatura.setLocation(local);
            }

            // Espaço reservado para a assinatura (cadeia ICP-Brasil completa é grande)
            opcoes.setPreferredSignatureSize(SignatureOptions.DEFAULT_SIGNATURE_SIZE * 2);

            if (visivel) {
                int indice = indicePagina(doc);
                PDPage page = doc.getPage(indice);
                PDRectangle area = page.getCropBox();
                PDRectangle retangulo = SeloVisual.posicao(area, selosExistentes(page));
                opcoes.setVisualSignature(SeloVisual.criar(page.getMediaBox(), retangulo,
                        certificado.nome(), certificado.documentoFormatado(), agora));
                opcoes.setPage(indice);
            }

            doc.addSignature(assinatura, this, opcoes);

            try (OutputStream out = new FileOutputStream(saida)) {
                doc.saveIncremental(out);
            }
        }
    }

    private int indicePagina(PDDocument doc) {
        int total = doc.getNumberOfPages();
        if (pagina == null) {
            return total - 1;
        }
        if (pagina < 1 || pagina > total) {
            throw new IllegalArgumentException("Página " + pagina + " não existe (o PDF tem " + total + ").");
        }
        return pagina - 1;
    }

    /** Quantos selos de assinatura visíveis já existem na página (para posicionar o próximo ao lado). */
    private static int selosExistentes(PDPage page) throws IOException {
        int n = 0;
        for (PDAnnotation anotacao : page.getAnnotations()) {
            if (anotacao instanceof PDAnnotationWidget widget && ehAssinatura(widget.getCOSObject())) {
                PDRectangle r = widget.getRectangle();
                if (r != null && r.getWidth() > 0 && r.getHeight() > 0) {
                    n++;
                }
            }
        }
        return n;
    }

    private static boolean ehAssinatura(COSDictionary campo) {
        for (int nivel = 0; campo != null && nivel < 10; nivel++) {
            if (COSName.SIG.equals(campo.getCOSName(COSName.FT))) {
                return true;
            }
            campo = campo.getCOSDictionary(COSName.PARENT);
        }
        return false;
    }

    /** Chamado pelo PDFBox com os bytes a assinar; devolve o CMS/PKCS#7 destacado. */
    @Override
    public byte[] sign(InputStream conteudo) throws IOException {
        try {
            String algoritmo = "EC".equals(certificado.chave.getAlgorithm()) ? "SHA256withECDSA" : "SHA256withRSA";
            ContentSigner signer = new JcaContentSignerBuilder(algoritmo).build(certificado.chave);

            CMSSignedDataGenerator gerador = new CMSSignedDataGenerator();
            gerador.addSignerInfoGenerator(
                    new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().build())
                            .build(signer, certificado.certificado()));
            gerador.addCertificates(new JcaCertStore(Arrays.asList(certificado.cadeia)));

            CMSSignedData dados = gerador.generate(new CMSProcessableByteArray(conteudo.readAllBytes()), false);
            return dados.getEncoded();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Falha ao gerar a assinatura: " + e.getMessage(), e);
        }
    }

    /**
     * Permissão DocMDP de uma assinatura de certificação existente:
     * 0 = não há; 1 = nenhuma alteração permitida; 2 = preencher formulário e assinar; 3 = 2 + anotações.
     */
    private static int permissaoDocMDP(PDDocument doc) {
        COSDictionary perms = doc.getDocumentCatalog().getCOSObject().getCOSDictionary(COSName.PERMS);
        if (perms == null) {
            return 0;
        }
        COSDictionary docMdp = perms.getCOSDictionary(COSName.DOCMDP);
        if (docMdp == null) {
            return 0;
        }
        COSArray referencias = docMdp.getCOSArray(COSName.REFERENCE);
        if (referencias == null) {
            return 0;
        }
        for (int i = 0; i < referencias.size(); i++) {
            COSBase base = referencias.getObject(i);
            if (base instanceof COSDictionary ref
                    && COSName.DOCMDP.equals(ref.getDictionaryObject(COSName.TRANSFORM_METHOD))) {
                COSDictionary params = ref.getCOSDictionary(COSName.TRANSFORM_PARAMS);
                if (params != null) {
                    int p = params.getInt(COSName.P, 2);
                    return (p < 1 || p > 3) ? 2 : p;
                }
            }
        }
        return 0;
    }
}
