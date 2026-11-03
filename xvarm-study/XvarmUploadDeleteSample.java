import com.windfire.apis.asys.asysUsrElement;   // 문서(요소) 하나를 다루는 객체 — 등록 · 조회 · 삭제 메서드가 여기 있다
import com.windfire.apis.asysConnectData;       // 엔진 접속 객체 — 만들면 접속되고 close() 로 끊는다

/**
 * XVARM 등록(upload) · 삭제(delete) 학습용 샘플. 벤더 API 를 리플렉션 없이 바로 부른다.
 *
 * 컴파일 · 실행 (jar 는 프로젝트에 복사하지 말고 경로만 준다 — 라이선스 대상)
 *   javac -encoding UTF-8 -cp D:/work/samsung/docs/xtormapi_20210723.jar -d <출력폴더> XvarmUploadDeleteSample.java
 *   java -cp "<출력폴더>;D:/work/samsung/docs/xtormapi_20210723.jar" XvarmUploadDeleteSample <엔진IP> <계정> <비밀번호> <올릴파일>
 */
public class XvarmUploadDeleteSample {

    private static final int PORT = 2102;                 // 엔진 포트 (기본 2102)
    private static final String DESC = "ECM Mask";        // 등록 설명(m_descr) — 비우면 엔진이 거부한다(코드 5). 한글 금지
    private static final String GATEWAY = "XVARM_MAIN";   // 게이트웨이 이름 — ECM 담당자에게 받은 값으로 바꾼다
    private static final String C_CLASS = "BASIC";        // 컨텐츠 클래스(보관 분류)
    private static final String E_CLASS = "IMAGE";        // 요소 클래스(파일 종류) — 삭제 · 조회 때도 같은 값을 써야 한다
    private static final String S_CLASS = "PUBLIC";       // 보안 클래스(권한 분류)

    private final String ip;         // 엔진 IP
    private final String user;       // 엔진 계정
    private final String password;   // 엔진 비밀번호

    public XvarmUploadDeleteSample(String ip, String user, String password) {
        this.ip = ip;               // 접속 정보만 들고 있고, 접속은 호출할 때마다 새로 한다
        this.user = user;           // (접속 객체를 필드에 두지 않아야 여러 스레드가 같이 써도 안전하다)
        this.password = password;
    }

    /**
     * 파일 하나를 등록하고 shortId(16자리)를 돌려준다. DB 에는 이 shortId 만 저장한다.
     * 실패하면 RuntimeException — 메시지에 엔진 결과코드와 사유가 들어간다.
     */
    public String upload(String filePath) {
        asysConnectData con = null;                                          // finally 에서 닫으려고 밖에 선언
        try {
            con = new asysConnectData(ip, PORT, DESC, user, password);       // 1) 엔진 접속
            asysUsrElement usr = new asysUsrElement(con);                    // 2) 이 접속으로 요소 객체를 만든다
            usr.m_localFile = filePath;                                      // 3) 올릴 로컬 파일의 전체 경로
            usr.m_descr = DESC;                                              //    등록 설명 (필수)
            usr.m_cClassId = C_CLASS;                                        //    컨텐츠 클래스 (필수)
            usr.m_eClassId = E_CLASS;                                        //    요소 클래스 (필수)
            usr.m_userSClass = S_CLASS;                                      //    보안 클래스 (필수)
            int ret = usr.create(GATEWAY);                                   // 4) 등록 실행 — 0 이면 성공
            if (ret != 0) {                                                  //    0 이 아니면 실패
                throw new RuntimeException("upload 실패 [" + ret + "] " + usr.getLastError());   // 엔진이 준 사유를 붙인다
            }
            return usr.getShortID();                                         // 5) 새로 만들어진 요소의 짧은 ID
        } finally {
            if (con != null) {                                               // 접속이 됐으면
                con.close();                                                 // 6) 성공이든 실패든 반드시 끊는다
            }
        }
    }

    /**
     * shortId 로 요소를 삭제한다. 실패하면 RuntimeException.
     */
    public void delete(String shortId) {
        asysConnectData con = null;                                          // finally 에서 닫으려고 밖에 선언
        try {
            con = new asysConnectData(ip, PORT, DESC, user, password);       // 1) 엔진 접속
            asysUsrElement usr = new asysUsrElement(con);                    // 2) 요소 객체
            usr.m_elementId = fullId(shortId);                               // 3) 지울 대상 — shortId 가 아니라 전체 ID 를 넣는다
            int ret = usr.delete();                                          // 4) 삭제 실행 — 0 이면 성공
            if (ret != 0) {                                                  //    0 이 아니면 실패
                throw new RuntimeException("delete 실패 [" + ret + "] " + usr.getLastError());   // 엔진이 준 사유를 붙인다
            }
        } finally {
            if (con != null) {                                               // 접속이 됐으면
                con.close();                                                 // 5) 반드시 끊는다
            }
        }
    }

    /** 삭제 · 조회용 전체 ID = {게이트웨이}::{shortId}::{요소 클래스} */
    private static String fullId(String shortId) {
        return GATEWAY + "::" + shortId + "::" + E_CLASS;                    // 예) XVARM_MAIN::2026102616423700::IMAGE
    }

    /** 실행 예: 파일을 올리고 → 받은 shortId 로 바로 지운다 */
    public static void main(String[] args) {
        if (args.length != 4) {                                              // 인자 4개가 아니면 사용법 안내
            System.out.println("사용법: XvarmUploadDeleteSample <엔진IP> <계정> <비밀번호> <올릴파일>");
            return;
        }
        XvarmUploadDeleteSample xvarm = new XvarmUploadDeleteSample(args[0], args[1], args[2]);   // 접속 정보로 객체 생성

        String shortId = xvarm.upload(args[3]);                              // 등록 → shortId
        System.out.println("등록 완료 shortId=" + shortId);                   // 이 값을 DB(ELEMENT_ID)에 저장하면 된다

        xvarm.delete(shortId);                                               // 방금 올린 요소 삭제 (샘플이라 바로 정리)
        System.out.println("삭제 완료 " + fullId(shortId));                   // 삭제에 쓴 전체 ID 출력
    }
}
