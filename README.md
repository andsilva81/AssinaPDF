# AssinaPDF

Programa de linha de comando em Java para **assinar digitalmente arquivos PDF** com certificados ICP-Brasil (e-CPF / e-CNPJ, A1 ou A3).

Permite **várias assinaturas no mesmo arquivo** sem invalidar as anteriores. Por exemplo, a empresa assina e depois o responsável assina, e as duas assinaturas continuam válidas.

```
java -jar assina.jar "C:\docs\contrato.pdf"
  -> C:\docs\contrato_ASSINADO.pdf
```

## Recursos

- Assinatura PAdES (CMS/PKCS#7 destacado, SHA-256) compatível com Adobe Reader e validadores ICP-Brasil
- **Múltiplas assinaturas**: cada uma é gravada como atualização incremental, sem alterar os bytes já assinados
- **Selo visível** no rodapé da página com nome, CPF/CNPJ e data/hora. Os selos ficam lado a lado, um por assinante
- Certificado **A1** em arquivo `.pfx` / `.p12`
- Certificado instalado no **Windows** (A1 importado ou A3 em token/cartão)
- Configuração padrão em `config.cfg`, para rodar informando só o arquivo
- Vários PDFs de uma vez

## Requisitos

- Java 17 ou superior
- Maven 3.8+ (só para compilar)

## Compilando

```
mvn package
```

Gera `target/assina.jar`, um jar único com todas as dependências.

## Uso

```
java -jar assina.jar <arquivo.pdf> [<arquivo2.pdf> ...] [opções]
```

### Nome do arquivo gerado

| Entrada | Saída |
|---|---|
| `contrato.pdf` | `contrato_ASSINADO.pdf` |
| `contrato_ASSINADO.pdf` | a nova assinatura é adicionada no próprio arquivo |

Para usar outro nome, informe `--saida`.

### Opções

| Opção | Descrição |
|---|---|
| `--pfx <arquivo>` | certificado A1 (`.pfx` / `.p12`) |
| `--senha <senha>` | senha do certificado. Se omitida, é pedida no console |
| `--alias <alias>` | certificado do repositório do Windows |
| `--listar` | lista os certificados do Windows e sai |
| `--pagina <n>` | página do selo visível (padrão: última) |
| `--invisivel` | assina sem selo visível |
| `--motivo <texto>` | motivo da assinatura |
| `--local <texto>` | local da assinatura |
| `--saida <arquivo>` | arquivo de saída (só com um PDF) |
| `--ajuda` | mostra a ajuda |

Sem `--pfx`, o programa usa o repositório de certificados do Windows. Se houver mais de um certificado, ele pergunta qual usar ou aceita `--alias`. Para A3 (token/cartão), o PIN é pedido pelo driver do dispositivo.

### Exemplo: duas assinaturas

```
java -jar assina.jar "contrato.pdf" --pfx empresa.pfx
java -jar assina.jar "contrato_ASSINADO.pdf" --pfx responsavel.pfx
```

O `contrato_ASSINADO.pdf` final contém as duas assinaturas válidas e dois selos no rodapé.

## Instalação

Crie uma pasta (por exemplo `C:\assinatura`) e coloque nela:

```
C:\assinatura\
├── assina.jar          programa (copiado de target\assina.jar)
├── config.cfg          configuração: certificado padrão e senha
├── empresa.pfx         certificado A1 padrão (o indicado no config.cfg)
└── responsavel.pfx     outros certificados (opcional, usados com --pfx)
```

- O `config.cfg` precisa estar **na mesma pasta do `assina.jar`**. O programa procura o arquivo lá, não na pasta de onde o comando é executado.
- O certificado indicado em `arquivop12` pode ficar na mesma pasta (basta o nome do arquivo) ou em qualquer outro lugar (informe o caminho completo).
- Os PDFs a assinar podem estar em qualquer pasta. O arquivo assinado é gravado ao lado do original.

Com essa estrutura, basta informar o PDF:

```
java -jar C:\assinatura\assina.jar "D:\documentos\contrato.pdf"
```

## Arquivo de configuração (config.cfg)

Os valores do `config.cfg` são usados quando não são informados na linha de comando.

### Conteúdo

Exemplo completo:

```ini
# Certificado padrão (nome do arquivo na pasta do jar, ou caminho completo)
arquivop12=empresa.pfx

# Senha do certificado acima
senha=suaSenha

# Selo visível: sim ou nao
visivel=sim

# Página do selo: número (1 = primeira) ou ultima
pagina=ultima

# Opcionais: aparecem nos detalhes da assinatura
motivo=Aprovação do documento
local=São Paulo - SP
```

O mínimo necessário são só as duas primeiras chaves:

```ini
arquivop12=empresa.pfx
senha=suaSenha
```

Formato:
- uma configuração por linha, no formato `chave=valor`;
- linhas iniciadas por `#` ou `;` são comentários;
- linhas de seção como `[assinatura]` são ignoradas, então o arquivo pode ser compartilhado com outros programas;
- caminhos do Windows podem ser escritos normalmente, com `\` (por exemplo `arquivop12=D:\certificados\empresa.pfx`);
- salve o arquivo em ANSI (padrão do Bloco de Notas) se usar acentos em `motivo` ou `local`.

Para usar um certificado do Windows (incluindo A3) em vez de um `.pfx`, remova `arquivop12` e `senha` e informe o `alias` (veja os disponíveis com `--listar`):

```ini
alias=nome do certificado
```

### Chaves

| Chave | Descrição |
|---|---|
| `arquivop12` | certificado `.pfx`. Um caminho relativo é procurado na pasta do jar |
| `senha` | senha do certificado |
| `alias` | certificado do Windows (alternativa ao `arquivop12`) |
| `visivel` | `sim` / `nao` |
| `pagina` | número da página ou `ultima` |
| `motivo`, `local` | dados opcionais da assinatura |

### Linha de comando x config.cfg

O programa lê o `config.cfg` primeiro e depois aplica os parâmetros da linha de comando por cima. **A linha de comando sempre tem prioridade**, e o que não for informado nela continua valendo do `config.cfg`.

Exemplos, com um `config.cfg` que aponta para `empresa.pfx`:

| Comando | Certificado usado | Senha usada |
|---|---|---|
| `java -jar assina.jar a.pdf` | `empresa.pfx` (config) | do config |
| `java -jar assina.jar a.pdf --pfx responsavel.pfx --senha 123` | `responsavel.pfx` | `123` |
| `java -jar assina.jar a.pdf --senha outra` | `empresa.pfx` (config) | `outra` |
| `java -jar assina.jar a.pdf --pfx responsavel.pfx` | `responsavel.pfx` | **pedida no console** |
| `java -jar assina.jar a.pdf --alias "meu certificado"` | certificado do Windows | PIN pedido pelo Windows/token, se houver |

Há duas exceções a essa regra:
- **Senha ao trocar de certificado:** se `--pfx` for informado sem `--senha`, a senha do `config.cfg` é descartada, porque ela pertence ao certificado do config e não ao novo. Nesse caso a senha é pedida no console.
- **Selo visível:** `--invisivel` desliga o selo mesmo que o config tenha `visivel=sim`. O contrário não existe: com `visivel=nao` no config, não há opção na linha de comando para religar o selo.

As demais opções (`--alias`, `--pagina`, `--motivo`, `--local`) substituem o valor correspondente do config.

> ⚠️ O `config.cfg` guarda a senha em texto puro. Proteja a pasta e **nunca** envie o `config.cfg` ou arquivos `.pfx` para o repositório.

## Por que as assinaturas anteriores não são invalidadas

Uma assinatura de PDF protege um intervalo exato de bytes do arquivo. A assinatura anterior quebra quando:

1. **O PDF é salvo de novo por inteiro.** Os bytes mudam e a assinatura anterior deixa de conferir. Este programa grava cada assinatura como **atualização incremental**, anexada ao final do arquivo.
2. **A primeira assinatura é de "certificação" (DocMDP) sem permitir alterações.** Esse tipo de assinatura proíbe qualquer assinatura posterior. Este programa usa assinatura de **aprovação**, que não bloqueia as próximas. Se o PDF recebido já estiver certificado dessa forma, o programa avisa e não assina.
3. **Algo é desenhado na página depois de assinado** (marca d'água, carimbo). Por isso o selo visível não é desenhado na página: ele é a aparência do próprio campo de assinatura, o que é permitido entre assinaturas.

## Estrutura do código

```
src/main/java/br/com/assinapdf/
├── AssinaPDF.java      ponto de entrada, nomes de saída, laço de arquivos
├── Opcoes.java         linha de comando + config.cfg
├── Certificado.java    leitura do .pfx ou do repositório do Windows
├── AssinadorPDF.java   assinatura incremental (PDFBox + BouncyCastle)
└── SeloVisual.java     aparência do selo visível
```

## Códigos de saída

| Código | Significado |
|---|---|
| `0` | todos os arquivos assinados |
| `1` | falha em pelo menos um arquivo |
| `2` | erro de uso ou de certificado (opção inválida, certificado vencido etc.) |

## Dependências

- [Apache PDFBox](https://pdfbox.apache.org/) 3.0
- [Bouncy Castle](https://www.bouncycastle.org/) 1.78

## Limitações conhecidas

- O selo é posicionado no rodapé esquerdo e pode cobrir conteúdo que já esteja nessa área.
- Páginas com rotação (`/Rotate`) podem ter o selo posicionado incorretamente.
- Não inclui carimbo de tempo (TSA) nem dados de revogação (LTV).
