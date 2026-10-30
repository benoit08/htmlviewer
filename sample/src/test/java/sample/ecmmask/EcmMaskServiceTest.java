package sample.ecmmask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import sample.ecmmask.xvarm.XvarmClient;
import sample.ecmmask.xvarm.XvarmException;

/** 저장 흐름(엔진 키 교체, 원본 보관, 재마스킹 정리)과 형식 변환을 한 번에 확인 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:test;MODE=Oracle;DB_CLOSE_DELAY=-1",
        "xvarm.local-dir=target/test-xvarm/${random.uuid}" })
public class EcmMaskServiceTest {

    @Resource(name = "ecmMaskService")
    private EcmMaskService ecmMaskService;

    @Resource(name = "ecmMaskDAO")
    private EcmMaskDAO ecmMaskDAO;

    @Resource(name = "xvarmClient")
    private XvarmClient xvarmClient;

    @Test
    public void 저장하면_새_요소로_바뀌고_원본은_보관_재마스킹은_직전본_삭제() throws Exception {
        Map<String, Object> key = keyOf("ECM_C780411E_00001_1.jpg");
        String original = ecmMaskDAO.selectEcmFile(key).getElementId();

        ecmMaskService.saveMask(saveParam(key), pages(1));
        EcmFileVO once = ecmMaskDAO.selectEcmFile(key);
        assertEquals("Y", once.getMaskYn());
        assertNotEquals(original, once.getElementId());
        assertEquals(original, once.getMaskOrgElementId());
        xvarmClient.get(original);   // 원본 보관 (delete-original=false)

        ecmMaskService.saveMask(saveParam(key), pages(1));
        EcmFileVO twice = ecmMaskDAO.selectEcmFile(key);
        assertEquals(original, twice.getMaskOrgElementId());   // 원본 키는 최초 것 유지
        try {
            xvarmClient.get(once.getElementId());               // 직전 마스킹본은 정리됨
            fail("직전 마스킹본이 남아 있음");
        } catch (XvarmException expected) {
            // 정상
        }
    }

    @Test
    public void 보기_형식_변환과_TIF_여러장_저장() throws Exception {
        assertEquals("application/pdf", ecmMaskService.selectEcmFileContent(keyOf("ECM_5A1F8D65_00001_1.pdf")).contentType);
        assertEquals("application/pdf", ecmMaskService.selectEcmFileContent(keyOf("ECM_5A1F8D65_00001_2.tif")).contentType); // 여러 장 TIF → PDF
        assertEquals("image/png", ecmMaskService.selectEcmFileContent(keyOf("ECM_9B22D0A1_00001_1.tif")).contentType);       // 한 장 TIF → PNG

        Map<String, Object> key = keyOf("ECM_5A1F8D65_00001_2.tif");
        ecmMaskService.saveMask(saveParam(key), pages(2));
        String saved = ecmMaskDAO.selectEcmFile(key).getElementId();
        assertEquals(2, MaskFileCodec.readTiff(xvarmClient.get(saved)).size());   // 원래 형식(TIF 2쪽)으로 저장
    }

    @Test
    public void 삭제된_문서는_열수없음() throws Exception {
        try {
            ecmMaskService.selectEcmFileContent(keyOf("ECM_9B22D0A1_00001_2.jpg"));
            fail("삭제된 문서가 열림");
        } catch (IllegalArgumentException expected) {
            // 정상
        }
    }

    private Map<String, Object> keyOf(String fileName) throws Exception {
        List<EcmFileVO> list = ecmMaskService.selectEcmFileList(new HashMap<String, Object>());
        for (int i = 0; i < list.size(); i++) {
            EcmFileVO vo = list.get(i);
            if (vo.getFileName().equals(fileName)) {
                Map<String, Object> key = new HashMap<String, Object>();
                key.put("procDate", vo.getProcDate());
                key.put("procSeq", Long.valueOf(vo.getProcSeq()));
                key.put("fileSeq", Long.valueOf(vo.getFileSeq()));
                return key;
            }
        }
        throw new IllegalStateException("샘플 데이터 없음: " + fileName);
    }

    private Map<String, Object> saveParam(Map<String, Object> key) {
        Map<String, Object> param = new HashMap<String, Object>(key);
        param.put("regions", "[]");
        param.put("userId", "tester");
        return param;
    }

    private List<BufferedImage> pages(int n) {
        List<BufferedImage> list = new ArrayList<BufferedImage>();
        for (int i = 0; i < n; i++) {
            BufferedImage img = new BufferedImage(1240, 1754, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.dispose();
            list.add(img);
        }
        return list;
    }
}
