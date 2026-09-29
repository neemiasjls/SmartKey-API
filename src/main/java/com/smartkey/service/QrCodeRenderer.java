package com.smartkey.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Desenha o QR Code como SVG.
 *
 * Por que SVG e não imagem comum: o SVG é texto, fica nítido em qualquer
 * tamanho de tela, pesa poucos kilobytes e não exige biblioteca gráfica no
 * servidor — o que importa porque servidores de nuvem normalmente rodam sem
 * ambiente gráfico nenhum.
 */
@Service
public class QrCodeRenderer {

    /**
     * Nível de correção de erro MÉDIO (cerca de 15%).
     *
     * O QR continua legível mesmo com parte dele obstruída — dedo na tela,
     * reflexo, brilho. Subir esse nível deixaria o código mais tolerante,
     * porém maior e mais lento de ler; este é o equilíbrio usual.
     */
    private static final ErrorCorrectionLevel ERROR_CORRECTION = ErrorCorrectionLevel.M;

    /** Margem branca ao redor. Sem ela, muitas câmeras não reconhecem o código. */
    private static final int QUIET_ZONE = 2;

    public String toSvg(String content, int pixelSize) throws WriterException {
        Map<EncodeHintType, Object> hints = Map.of(
                EncodeHintType.ERROR_CORRECTION, ERROR_CORRECTION,
                EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name(),
                EncodeHintType.MARGIN, QUIET_ZONE);

        BitMatrix matrix = new QRCodeWriter()
                .encode(content, BarcodeFormat.QR_CODE, pixelSize, pixelSize, hints);

        int width = matrix.getWidth();
        int height = matrix.getHeight();

        StringBuilder path = new StringBuilder();

        // Junta pontos pretos vizinhos numa mesma linha do desenho.
        // Sem essa junção o SVG teria um retângulo por ponto - milhares deles -
        // e ficaria pesado para o navegador desenhar a cada poucos segundos.
        for (int y = 0; y < height; y++) {
            int runStart = -1;
            for (int x = 0; x <= width; x++) {
                boolean dark = x < width && matrix.get(x, y);
                if (dark && runStart < 0) {
                    runStart = x;
                } else if (!dark && runStart >= 0) {
                    path.append("M").append(runStart).append(",").append(y)
                        .append("h").append(x - runStart).append("v1h-")
                        .append(x - runStart).append("z");
                    runStart = -1;
                }
            }
        }

        return """
               <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" \
               shape-rendering="crispEdges" width="100%%" height="100%%">\
               <rect width="%d" height="%d" fill="#ffffff"/>\
               <path d="%s" fill="#000000"/></svg>"""
                .formatted(width, height, width, height, path);
    }
}
