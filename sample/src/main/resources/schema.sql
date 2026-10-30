-- 사이드바 메뉴 (EDMS 관리자 > 메뉴관리 에 해당)
CREATE TABLE IF NOT EXISTS TB_MENU (
  MENU_ID   VARCHAR(20)  PRIMARY KEY,
  PARENT_ID VARCHAR(20),
  MENU_NM   VARCHAR(100) NOT NULL,
  MENU_URL  VARCHAR(200),
  SORT_NO   INT,
  USE_YN    CHAR(1)      DEFAULT 'Y'
);

-- ECM 업로드 이력 (EDMS 기존 테이블에 MASK_* 컬럼을 추가하는 것에 해당)
CREATE TABLE IF NOT EXISTS TB_ECM_FILE (
  PROC_DATE           CHAR(8)      NOT NULL,   -- 처리일자 YYYYMMDD
  PROC_TIME           CHAR(6),                 -- 처리시각 HHmmss
  PROC_SEQ            NUMBER(10)   NOT NULL,   -- 처리일련번호
  BIZ_CD              VARCHAR(10),             -- ECM처리업무구분
  FILE_SEQ            NUMBER(5)    NOT NULL,   -- 파일등록일련번호
  FILE_NM             VARCHAR(200),
  ENTITY_ID           VARCHAR(20),             -- 고객엔티티ID
  DEL_YN              CHAR(1)      DEFAULT 'N',
  ELEMENT_ID          VARCHAR(40),             -- XVARM shortId (현재 문서)
  -- ↓ 마스킹으로 추가하는 컬럼
  MASK_YN             CHAR(1)      DEFAULT 'N' NOT NULL,
  MASK_DT             TIMESTAMP,
  MASK_USER           VARCHAR(20),
  MASK_ORG_ELEMENT_ID VARCHAR(40),             -- 마스킹 전 원본 shortId (최초 1회만 기록)
  PRIMARY KEY (PROC_DATE, PROC_SEQ, FILE_SEQ)
);
