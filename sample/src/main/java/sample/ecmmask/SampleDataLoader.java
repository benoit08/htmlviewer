package sample.ecmmask;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import sample.ecmmask.xvarm.XvarmClient;

/**
 * 샘플 데이터 (EDMS 로 옮기지 않음).
 * TB_ECM_FILE 이 비어 있으면 가짜 개인정보 문서를 만들어 XVARM 에 등록하고 행을 넣는다.
 * jpg, png, pdf(2쪽), tif(2쪽 → 화면엔 PDF 로), tif(1쪽 → 화면엔 PNG 로), 삭제건.
 * 다시 만들려면 sample/data 폴더를 지우고 재기동.
 */
@Component
public class SampleDataLoader implements ApplicationRunner {

    @Resource(name = "ecmMaskDAO")
    private EcmMaskDAO ecmMaskDAO;

    @Resource(name = "xvarmClient")
    private XvarmClient xvarmClient;

    public void run(ApplicationArguments args) throws Exception {
        if (ecmMaskDAO.selectEcmFileCount() > 0) {
            return;
        }
        String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
        float a4 = 150f / 72f;   // 150dpi 이미지를 A4 크기 PDF 로

        add(today, "133233", 15, 1, "ECM_C780411E_00001_1.jpg", "N", MaskFileCodec.writeImage(page("종합소득세 신고서", 1, 1), "jpg"));
        add(today, "133233", 15, 2, "ECM_C780411E_00001_2.png", "N", MaskFileCodec.writeImage(page("주민등록표 등본", 1, 1), "png"));
        add(today, "130323", 14, 1, "ECM_5A1F8D65_00001_1.pdf", "N",
                MaskFileCodec.toPdf(Arrays.asList(page("대출 신청서", 1, 2), page("대출 신청서", 2, 2)), a4));
        add(today, "130323", 14, 2, "ECM_5A1F8D65_00001_2.tif", "N",
                MaskFileCodec.writeTiff(Arrays.asList(page("스캔 서류 (TIF)", 1, 2), page("스캔 서류 (TIF)", 2, 2))));
        add(today, "120511", 13, 1, "ECM_9B22D0A1_00001_1.tif", "N",
                MaskFileCodec.writeTiff(Collections.singletonList(page("신분증 사본 (TIF)", 1, 1))));
        add(today, "120511", 13, 2, "ECM_9B22D0A1_00001_2.jpg", "Y", MaskFileCodec.writeImage(page("삭제된 문서", 1, 1), "jpg"));
    }

    private void add(String date, String time, long procSeq, long fileSeq, String name, String delYn, byte[] content) {
        EcmFileVO vo = new EcmFileVO();
        vo.setProcDate(date);
        vo.setProcTime(time);
        vo.setProcSeq(procSeq);
        vo.setBizCd("SR02");
        vo.setFileSeq(fileSeq);
        vo.setFileName(name);
        vo.setEntityId("2011869183");
        vo.setDelYn(delYn);
        vo.setElementId(xvarmClient.create(content));
        ecmMaskDAO.insertEcmFile(vo);
    }

    /** A4 150dpi 가짜 서류 한 장 (모든 개인정보는 가상) */
    private static BufferedImage page(String title, int no, int total) {
        BufferedImage img = new BufferedImage(1240, 1754, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setColor(new Color(34, 34, 34));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 48));
        g.drawString(title, 120, 170);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 32));
        List<String> lines = Arrays.asList(
                "성명: 홍길동",
                "주민등록번호: 800101-1234567",
                "주소: 서울특별시 서초구 서초대로 000, 101동 1001호",
                "전화번호: 010-1234-5678",
                "계좌번호: 국민은행 123456-01-234567",
                "소득금액: 12,345,678 원");
        for (int i = 0; i < lines.size(); i++) g.drawString(lines.get(i), 140, 330 + i * 80);
        g.setStroke(new BasicStroke(3));
        g.drawRect(100, 250, 1040, 560);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 26));
        g.drawString("※ 샘플 문서 — 모든 정보는 가상입니다.", 120, 1600);
        g.drawString(no + " / " + total, 1060, 1690);
        g.dispose();
        return img;
    }
}
