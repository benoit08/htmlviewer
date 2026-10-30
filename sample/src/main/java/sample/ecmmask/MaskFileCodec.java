package sample.ecmmask;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * 파일 형식 변환.
 *  - 보기(file API): 브라우저가 못 여는 TIF 를 PNG(1장) 또는 PDF(여러 장)로 바꿔 내려준다.
 *  - 저장(save API): 화면이 보낸 마스킹 페이지 PNG 들을 원래 형식(PDF, TIF, JPG ...)으로 다시 만든다.
 *
 * TIF 읽기/쓰기는 JDK 9 이상의 ImageIO 기본 기능이다.
 * JDK 8 이하면 jai-imageio-core jar 를 클래스패스에 추가한다 (코드는 그대로).
 */
public class MaskFileCodec {

    /** 화면(PDF.js)이 PDF 를 그리는 배율. ecmMask.js 의 getViewport({scale:1.5}) 와 같아야 페이지 크기가 보존된다 */
    public static final float VIEW_SCALE = 1.5f;

    /** 내려줄 파일 */
    public static class Content {
        public final String contentType;
        public final byte[] data;

        public Content(String contentType, byte[] data) {
            this.contentType = contentType;
            this.data = data;
        }
    }

    private MaskFileCodec() {
    }

    public static String ext(String fileName) {
        if (fileName == null) {
            return "";
        }
        int idx = fileName.lastIndexOf('.');
        return idx < 0 ? "" : fileName.substring(idx + 1).toLowerCase();
    }

    public static String contentType(String ext) {
        if ("pdf".equals(ext)) return "application/pdf";
        if ("jpg".equals(ext) || "jpeg".equals(ext)) return "image/jpeg";
        if ("png".equals(ext)) return "image/png";
        if ("gif".equals(ext)) return "image/gif";
        if ("bmp".equals(ext)) return "image/bmp";
        if ("webp".equals(ext)) return "image/webp";
        if (isTiff(ext)) return "image/tiff";
        return "application/octet-stream";
    }

    public static boolean isTiff(String ext) {
        return "tif".equals(ext) || "tiff".equals(ext);
    }

    /** 보기용 변환 */
    public static Content forViewer(String ext, byte[] data) throws IOException {
        if (!isTiff(ext)) {
            return new Content(contentType(ext), data);
        }
        List<BufferedImage> pages = readTiff(data);
        if (pages.size() == 1) {
            return new Content("image/png", writeImage(pages.get(0), "png"));
        }
        return new Content("application/pdf", toPdf(pages, VIEW_SCALE));
    }

    /** 저장용 변환: 마스킹된 페이지들 → 원래 형식 */
    public static byte[] fromPages(String ext, List<BufferedImage> pages) throws IOException {
        if ("pdf".equals(ext)) {
            return toPdf(pages, VIEW_SCALE);   // 이미지 PDF 가 된다 (가린 글자의 텍스트 레이어도 사라짐)
        }
        if (isTiff(ext)) {
            return writeTiff(pages);
        }
        if (pages.size() != 1) {
            throw new IllegalArgumentException("이미지 문서는 1페이지여야 합니다: " + pages.size());
        }
        String fmt = "jpeg".equals(ext) ? "jpg" : ext;
        // webp 등 JDK 가 못 쓰는 형식은 PNG 로 저장 (파일명은 그대로, 화면은 내용으로 판별)
        if (!ImageIO.getImageWritersBySuffix(fmt).hasNext()) {
            fmt = "png";
        }
        return writeImage(pages.get(0), fmt);
    }

    public static List<BufferedImage> readTiff(byte[] data) throws IOException {
        ImageReader reader = first(ImageIO.getImageReadersByFormatName("tiff"));
        ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data));
        try {
            reader.setInput(in);
            int n = reader.getNumImages(true);
            List<BufferedImage> pages = new ArrayList<BufferedImage>(n);
            for (int i = 0; i < n; i++) {
                pages.add(reader.read(i));
            }
            return pages;
        } finally {
            reader.dispose();
            in.close();
        }
    }

    public static byte[] writeTiff(List<BufferedImage> pages) throws IOException {
        ImageWriter writer = first(ImageIO.getImageWritersByFormatName("tiff"));
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionType("LZW");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageOutputStream out = ImageIO.createImageOutputStream(bos);
        try {
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
            for (int i = 0; i < pages.size(); i++) {
                writer.writeToSequence(new IIOImage(toRgb(pages.get(i)), null, null), param);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
            out.close();
        }
        return bos.toByteArray();
    }

    /** 페이지 이미지 → PDF. 페이지 크기 = 이미지 픽셀 / scale (pt). 무손실 압축 — 마스킹 경계가 번지지 않게 */
    public static byte[] toPdf(List<BufferedImage> pages, float scale) throws IOException {
        PDDocument doc = new PDDocument();
        try {
            for (int i = 0; i < pages.size(); i++) {
                BufferedImage img = pages.get(i);
                PDPage page = new PDPage(new PDRectangle(img.getWidth() / scale, img.getHeight() / scale));
                doc.addPage(page);

                PDImageXObject xobj = LosslessFactory.createFromImage(doc, img);
                PDPageContentStream cs = new PDPageContentStream(doc, page);
                try {
                    cs.drawImage(xobj, 0, 0, page.getMediaBox().getWidth(), page.getMediaBox().getHeight());
                } finally {
                    cs.close();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } finally {
            doc.close();
        }
    }

    public static byte[] writeImage(BufferedImage img, String fmt) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // 화면이 보낸 PNG 는 알파 채널이 있어 JPG/BMP 로는 그대로 못 쓴다 → RGB 로
        BufferedImage src = "png".equals(fmt) ? img : toRgb(img);
        if (!ImageIO.write(src, fmt, out)) {
            throw new IOException("이미지 쓰기 실패: " + fmt);
        }
        return out.toByteArray();
    }

    private static BufferedImage toRgb(BufferedImage img) {
        if (img.getType() == BufferedImage.TYPE_INT_RGB) {
            return img;
        }
        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(img, 0, 0, Color.WHITE, null);
        g.dispose();
        return rgb;
    }

    private static <T> T first(Iterator<T> it) {
        if (!it.hasNext()) {
            throw new IllegalStateException("TIFF 처리기가 없습니다 — JDK 9 이상 또는 jai-imageio-core 필요");
        }
        return it.next();
    }
}
