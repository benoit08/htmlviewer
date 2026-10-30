package sample.ecmmask.xvarm;

/** 엔진 결과코드(ret != 0) 또는 접속 오류. ret = -1 은 라이브러리 예외(접속 불가 등). */
public class XvarmException extends RuntimeException {
    public final int ret;

    public XvarmException(int ret, String message) {
        super("[XVARM " + ret + "] " + message);
        this.ret = ret;
    }

    public XvarmException(String message, Throwable cause) {
        super("[XVARM -1] " + message + ": " + cause, cause);
        this.ret = -1;
    }
}
