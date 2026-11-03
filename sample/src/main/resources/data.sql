-- 메뉴 (캡처의 사이드바 구성). 새 메뉴는 행 하나 추가로 생김 = 관리자 메뉴관리 등록과 같은 효과
MERGE INTO TB_MENU KEY (MENU_ID) VALUES ('M100', NULL,   '전자문서처리내역',     NULL,                 10, 'Y');
MERGE INTO TB_MENU KEY (MENU_ID) VALUES ('M200', NULL,   '전자문서처리현황',     NULL,                 20, 'Y');
MERGE INTO TB_MENU KEY (MENU_ID) VALUES ('M300', NULL,   'H.NET정보관리',        NULL,                 30, 'Y');
MERGE INTO TB_MENU KEY (MENU_ID) VALUES ('M400', NULL,   '로그관리',             NULL,                 40, 'Y');
MERGE INTO TB_MENU KEY (MENU_ID) VALUES ('M500', NULL,   '시스템관리',           NULL,                 50, 'Y');
MERGE INTO TB_MENU KEY (MENU_ID) VALUES ('M600', NULL,   'ECM파일처리내역',      NULL,                 60, 'Y');
MERGE INTO TB_MENU KEY (MENU_ID) VALUES ('M610', 'M600', 'ECM파일처리내역 조회', NULL,                 61, 'Y');
MERGE INTO TB_MENU KEY (MENU_ID) VALUES ('M620', 'M600', 'ECM 문서 마스킹',      '/ecm/mask/view.do',  62, 'Y');
MERGE INTO TB_MENU KEY (MENU_ID) VALUES ('M630', 'M600', 'JSP 변환 연습',        '/practice/view.do',  63, 'Y');
MERGE INTO TB_MENU KEY (MENU_ID) VALUES ('M700', NULL,   '교부문서처리내역',     NULL,                 70, 'Y');
