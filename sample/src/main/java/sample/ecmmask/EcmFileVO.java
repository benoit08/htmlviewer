package sample.ecmmask;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * TB_ECM_FILE 한 행.
 * 목록 API 에서는 이 객체가 그대로 JSON 으로 나가므로, ECM 키(elementId)는 화면에 내보내지 않는다.
 */
public class EcmFileVO {

    private String procDate;          // 처리일자 YYYYMMDD
    private String procTime;          // 처리시각 HHmmss
    private long procSeq;             // 처리일련번호
    private String bizCd;             // ECM처리업무구분
    private long fileSeq;             // 파일등록일련번호
    private String fileName;
    private String entityId;          // 고객엔티티ID
    private String delYn = "N";
    private String maskYn = "N";
    private String maskDt;            // yyyy/MM/dd HH:mm:ss
    private String elementId;         // XVARM shortId (현재 문서)
    private String maskOrgElementId;  // 마스킹 전 원본 shortId

    public String getProcDate() {
        return procDate;
    }

    public void setProcDate(String procDate) {
        this.procDate = procDate;
    }

    public String getProcTime() {
        return procTime;
    }

    public void setProcTime(String procTime) {
        this.procTime = procTime;
    }

    public long getProcSeq() {
        return procSeq;
    }

    public void setProcSeq(long procSeq) {
        this.procSeq = procSeq;
    }

    public String getBizCd() {
        return bizCd;
    }

    public void setBizCd(String bizCd) {
        this.bizCd = bizCd;
    }

    public long getFileSeq() {
        return fileSeq;
    }

    public void setFileSeq(long fileSeq) {
        this.fileSeq = fileSeq;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getDelYn() {
        return delYn;
    }

    public void setDelYn(String delYn) {
        this.delYn = delYn;
    }

    public String getMaskYn() {
        return maskYn;
    }

    public void setMaskYn(String maskYn) {
        this.maskYn = maskYn;
    }

    public String getMaskDt() {
        return maskDt;
    }

    public void setMaskDt(String maskDt) {
        this.maskDt = maskDt;
    }

    @JsonIgnore
    public String getElementId() {
        return elementId;
    }

    public void setElementId(String elementId) {
        this.elementId = elementId;
    }

    @JsonIgnore
    public String getMaskOrgElementId() {
        return maskOrgElementId;
    }

    public void setMaskOrgElementId(String maskOrgElementId) {
        this.maskOrgElementId = maskOrgElementId;
    }
}
