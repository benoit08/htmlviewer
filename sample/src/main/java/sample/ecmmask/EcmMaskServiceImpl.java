package sample.ecmmask;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import sample.ecmmask.xvarm.XvarmClient;

/**
 * ECM 문서 마스킹 서비스 구현.
 *
 * 저장 순서 (명세 4.1 "새로 등록 → DB 갱신 → 옛 것 정리" 와 같음. 교체 replaceContent 는 쓰지 않음)
 *   1) 마스킹본 create → 새 shortId
 *   2) DB: ELEMENT_ID 를 새 키로, 원본 키는 MASK_ORG_ELEMENT_ID 에 (최초 1회만)
 *      실패하면 1) 에서 만든 요소를 delete (엔진에 고아 요소를 남기지 않음)
 *   3) 재마스킹이면 직전 마스킹본은 delete. 원본은 ecm-mask.delete-original=true 일 때만 delete
 */
@Service("ecmMaskService")
public class EcmMaskServiceImpl implements EcmMaskService {

    private static final Logger logger = LoggerFactory.getLogger(EcmMaskServiceImpl.class);

    @Resource(name = "ecmMaskDAO")
    private EcmMaskDAO ecmMaskDAO;

    @Resource(name = "xvarmClient")
    private XvarmClient xvarmClient;

    /** 마스킹본 저장 후 원본 요소 삭제 여부 (기본 false = 원본 보관) */
    @Value("${ecm-mask.delete-original:false}")
    private boolean deleteOriginal;

    public List<EcmFileVO> selectEcmFileList(Map<String, Object> param) throws Exception {
        return ecmMaskDAO.selectEcmFileList(param);
    }

    public MaskFileCodec.Content selectEcmFileContent(Map<String, Object> param) throws Exception {
        EcmFileVO vo = selectTarget(param);
        byte[] data = xvarmClient.get(vo.getElementId());
        return MaskFileCodec.forViewer(MaskFileCodec.ext(vo.getFileName()), data);
    }

    public String saveMask(Map<String, Object> param, List<BufferedImage> pages) throws Exception {
        EcmFileVO vo = selectTarget(param);
        String oldId = vo.getElementId();

        // 1) 원래 형식으로 만들어 등록
        byte[] masked = MaskFileCodec.fromPages(MaskFileCodec.ext(vo.getFileName()), pages);
        String newId = xvarmClient.create(masked);

        // 2) DB 갱신 (ELEMENT_ID = oldId 조건 → 그 사이 다른 사람이 먼저 저장했으면 0건)
        Map<String, Object> upd = new HashMap<String, Object>(param);
        upd.put("newId", newId);
        upd.put("oldId", oldId);
        int cnt;
        try {
            cnt = ecmMaskDAO.updateMask(upd);
        } catch (Exception e) {
            deleteElementQuietly(newId);
            throw e;
        }
        if (cnt != 1) {
            deleteElementQuietly(newId);
            throw new IllegalStateException("다른 사용자가 먼저 저장했습니다. 다시 조회 후 처리하세요.");
        }

        // 3) 정리: 재마스킹이면 oldId 는 직전 마스킹본 → 삭제. 첫 마스킹이면 oldId 는 원본 → 설정에 따라
        boolean remasked = vo.getMaskOrgElementId() != null;
        if (remasked || deleteOriginal) {
            deleteElementQuietly(oldId);
        }

        // 감사용: 어떤 좌표를 가렸는지. 필요하면 이력 테이블에 저장
        logger.info("마스킹 저장 " + param.get("procDate") + "/" + param.get("procSeq") + "/" + param.get("fileSeq")
                + " user=" + param.get("userId") + " " + oldId + " -> " + newId + " regions=" + param.get("regions"));

        return selectTarget(param).getMaskDt();
    }

    public List<Map<String, Object>> selectMenuList() throws Exception {
        return ecmMaskDAO.selectMenuList();
    }

    private EcmFileVO selectTarget(Map<String, Object> param) {
        EcmFileVO vo = ecmMaskDAO.selectEcmFile(param);
        if (vo == null) {
            throw new IllegalArgumentException("대상 문서가 없습니다.");
        }
        if ("Y".equals(vo.getDelYn())) {
            throw new IllegalArgumentException("삭제된 문서입니다.");
        }
        return vo;
    }

    /** 정리 단계의 삭제 실패는 저장 결과를 바꾸지 않음 — 키를 로그에 남겨 운영자가 정리 */
    private void deleteElementQuietly(String shortId) {
        try {
            xvarmClient.delete(shortId);
        } catch (Exception e) {
            logger.warn("XVARM 요소 삭제 실패 — 수동 정리 필요: " + shortId + " (" + e.getMessage() + ")");
        }
    }
}
