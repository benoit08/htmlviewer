package sample.ecmmask.xvarm;

/**
 * XVARM 엔진 호출. 키는 모두 shortId(DB 에 저장하는 16자리 ElementID).
 * 구현: {@link LocalXvarmClient}(엔진 없이 폴더 저장) / {@link InzentXvarmClient}(실제 엔진, 고객사 jar 필요)
 */
public interface XvarmClient {

    /** 등록(create) → 새 shortId */
    String create(byte[] content);

    /** 조회(getContent) */
    byte[] get(String shortId);

    /** 삭제(delete) */
    void delete(String shortId);
}
