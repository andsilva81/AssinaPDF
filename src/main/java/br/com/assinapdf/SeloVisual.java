package br.com.assinapdf;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;

/**
 * Selo visível da assinatura ("Assinado digitalmente por ...").
 *
 * O selo é a aparência do próprio campo de assinatura, e não um desenho no conteúdo
 * da página. Por isso não invalida assinaturas anteriores (uma marca d'água desenhada
 * na página depois da 1ª assinatura seria vista como alteração do documento).
 */
class SeloVisual {

    static final float LARGURA = 190;
    static final float ALTURA = 56;
    private static final float MARGEM = 20;
    private static final float ESPACO = 8;
    private static final float TAMANHO_FONTE = 7.5f;

    private static final Color COR_BORDA = new Color(0, 82, 147);
    private static final Color COR_FUNDO = new Color(235, 243, 251);
    private static final Color COR_TEXTO = new Color(20, 20, 20);

    private SeloVisual() {
    }

    /**
     * Posição do selo no rodapé da página: lado a lado, da esquerda para a direita,
     * subindo uma linha quando não couber mais. {@code ordem} = quantos selos já existem.
     */
    static PDRectangle posicao(PDRectangle pagina, int ordem) {
        int porLinha = Math.max(1, (int) ((pagina.getWidth() - 2 * MARGEM + ESPACO) / (LARGURA + ESPACO)));
        int coluna = ordem % porLinha;
        int linha = ordem / porLinha;
        float x = pagina.getLowerLeftX() + MARGEM + coluna * (LARGURA + ESPACO);
        float y = pagina.getLowerLeftY() + MARGEM + linha * (ALTURA + ESPACO);
        return new PDRectangle(x, y, LARGURA, ALTURA);
    }

    /** Monta o "modelo" que o PDFBox usa como aparência do campo de assinatura. */
    static InputStream criar(PDRectangle tamanhoPagina, PDRectangle retangulo, String nome, String documento,
            Calendar data) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(tamanhoPagina);
            doc.addPage(page);

            PDAcroForm acroForm = new PDAcroForm(doc);
            doc.getDocumentCatalog().setAcroForm(acroForm);
            acroForm.setSignaturesExist(true);
            acroForm.setAppendOnly(true);
            acroForm.getCOSObject().setDirect(true);

            PDSignatureField campo = new PDSignatureField(acroForm);
            PDAnnotationWidget widget = campo.getWidgets().get(0);
            widget.setRectangle(retangulo);
            widget.setPrinted(true);
            acroForm.getFields().add(campo);

            PDFormXObject form = new PDFormXObject(new PDStream(doc));
            form.setResources(new PDResources());
            form.setFormType(1);
            form.setBBox(new PDRectangle(retangulo.getWidth(), retangulo.getHeight()));

            PDAppearanceDictionary aparencia = new PDAppearanceDictionary();
            aparencia.getCOSObject().setDirect(true);
            PDAppearanceStream stream = new PDAppearanceStream(form.getCOSObject());
            aparencia.setNormalAppearance(stream);
            widget.setAppearance(aparencia);

            try (PDPageContentStream cs = new PDPageContentStream(doc, stream)) {
                desenhar(cs, retangulo.getWidth(), retangulo.getHeight(), nome, documento, data);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private static void desenhar(PDPageContentStream cs, float w, float h, String nome, String documento,
            Calendar data) throws IOException {
        PDType1Font normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        PDType1Font negrito = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

        // Fundo e borda
        cs.setNonStrokingColor(COR_FUNDO);
        cs.addRect(0, 0, w, h);
        cs.fill();
        cs.setStrokingColor(COR_BORDA);
        cs.setLineWidth(1);
        cs.addRect(0.5f, 0.5f, w - 1, h - 1);
        cs.stroke();
        // Faixa lateral
        cs.setNonStrokingColor(COR_BORDA);
        cs.addRect(0, 0, 4, h);
        cs.fill();

        float x = 10;
        float larguraTexto = w - x - 6;
        float linha = TAMANHO_FONTE + 3;
        float y = h - 6 - TAMANHO_FONTE;

        texto(cs, negrito, TAMANHO_FONTE, COR_BORDA, x, y, "ASSINADO DIGITALMENTE POR:");
        y -= linha;
        texto(cs, negrito, ajustarFonte(negrito, nome, larguraTexto), COR_TEXTO, x, y, nome);
        y -= linha;
        if (documento != null) {
            texto(cs, normal, TAMANHO_FONTE, COR_TEXTO, x, y, documento);
            y -= linha;
        }
        String quando = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(data.getTime());
        texto(cs, normal, TAMANHO_FONTE, COR_TEXTO, x, y, "Data: " + quando);
    }

    private static void texto(PDPageContentStream cs, PDType1Font fonte, float tamanho, Color cor, float x, float y,
            String s) throws IOException {
        cs.beginText();
        cs.setFont(fonte, tamanho);
        cs.setNonStrokingColor(cor);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }

    /** Diminui a fonte para nomes longos caberem no selo. */
    private static float ajustarFonte(PDType1Font fonte, String s, float largura) throws IOException {
        float tamanho = TAMANHO_FONTE;
        while (tamanho > 4.5f && fonte.getStringWidth(s) / 1000 * tamanho > largura) {
            tamanho -= 0.25f;
        }
        return tamanho;
    }
}
