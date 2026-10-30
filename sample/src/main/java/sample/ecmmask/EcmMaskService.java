package sample.ecmmask;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/**
 * ECM 문서 마스킹 서비스.
 */
public interface EcmMaskService {

    /** 업로드 목록 조회 (param: procDate, procSeq, bizCd, fileSeq, delYn, maskYn) */
    List<EcmFileVO> selectEcmFileList(Map<String, Object> param) throws Exception;

    /** 문서 읽기 — XVARM 에서 받아 화면이 열 수 있는 형식으로 (param: procDate, procSeq, fileSeq) */
    MaskFileCodec.Content selectEcmFileContent(Map<String, Object> param) throws Exception;

    /** 마스킹 저장 → 저장 일시 (param: procDate, procSeq, fileSeq, regions, userId) */
    String saveMask(Map<String, Object> param, List<BufferedImage> pages) throws Exception;

    /** 사이드바 메뉴 (샘플 레이아웃용) */
    List<Map<String, Object>> selectMenuList() throws Exception;
}
