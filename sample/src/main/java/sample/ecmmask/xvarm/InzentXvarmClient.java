package sample.ecmmask.xvarm;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * 실제 XVARM 엔진 호출 (Inzent xvarmapi — com.windfire.apis.*).
 *
 * jar 는 라이선스 문제로 빌드에 넣지 않고, 실행 시 xvarm.jar-path 에서 읽는다(리플렉션).
 * 그래서 jar 없이도 컴파일되고, 테스트할 때 경로만 주고 xvarm.mode=inzent 로 바꾸면 된다.
 * EDMS 처럼 jar 가 이미 클래스패스에 있는 환경이면 jar-path 를 비워 두면 된다.
 * (EDMS 는 jar 를 직접 import 하므로, 옮길 때는 아래 리플렉션 대신 asysConnectData / asysUsrElement 를 바로 써도 된다)
 *
 * 호출 단위는 명세대로 "요청 1건 = 접속 1회 → 작업 1회 → close". 접속 객체는 스레드 간 공유하지 않는다.
 * 시그니처 근거: docs/명세_ECM_XVARM_저장API.md 부록 B (xtormapi_20210723.jar javap)
 */
public class InzentXvarmClient implements XvarmClient {

    private static final String CONNECT = "com.windfire.apis.asysConnectData";
    private static final String ELEMENT = "com.windfire.apis.asys.asysUsrElement";
    private static final Class<?>[] NO_ARGS = new Class<?>[0];
    private static final Class<?>[] STRING_ARG = new Class<?>[] { String.class };

    private final XvarmProperties prop;
    private final ClassLoader loader;

    public InzentXvarmClient(XvarmProperties prop) {
        this.prop = prop;
        String jar = prop.getJarPath();
        try {
            if (jar == null || jar.length() == 0) {
                this.loader = getClass().getClassLoader();
            } else {
                URL url = new File(jar).getAbsoluteFile().toURI().toURL();
                this.loader = new URLClassLoader(new URL[] { url }, getClass().getClassLoader());
            }
            Class.forName(CONNECT, false, loader);   // 기동 시 jar 확인 — 없으면 바로 알 수 있게
        } catch (Exception e) {
            throw new IllegalStateException("XVARM jar 를 찾을 수 없습니다 (xvarm.jar-path=" + jar + ")", e);
        }
    }

    public String create(byte[] content) {
        File tmp = null;
        Object con = null;
        try {
            tmp = File.createTempFile("xvarm-up-", ".bin");
            writeFile(tmp, content);

            con = connect();
            Object usr = newElement(con);
            setField(usr, "m_localFile", tmp.getAbsolutePath());
            setField(usr, "m_descr", prop.getDesc());
            setField(usr, "m_cClassId", prop.getContentClass());
            setField(usr, "m_eClassId", prop.getElementClass());
            setField(usr, "m_userSClass", prop.getSecurityClass());

            Object ret = invoke(usr, "create", STRING_ARG, new Object[] { prop.getGateway() });
            checkResult(usr, ret, "create");
            return (String) invoke(usr, "getShortID", NO_ARGS, new Object[0]);
        } catch (XvarmException e) {
            throw e;
        } catch (Exception e) {
            throw new XvarmException("create 호출 오류", unwrap(e));
        } finally {
            close(con);
            deleteFile(tmp);
        }
    }

    public byte[] get(String shortId) {
        File tmp = null;
        Object con = null;
        try {
            tmp = File.createTempFile("xvarm-down-", ".bin");
            tmp.delete();   // getContent 는 받을 경로에 파일이 없어야 한다

            con = connect();
            Object usr = newElement(con);
            setField(usr, "m_elementId", fullId(shortId));

            Object ret = invoke(usr, "getContent", STRING_ARG, new Object[] { tmp.getAbsolutePath() });
            checkResult(usr, ret, "getContent");
            return readFile(tmp);
        } catch (XvarmException e) {
            throw e;
        } catch (Exception e) {
            throw new XvarmException("getContent 호출 오류", unwrap(e));
        } finally {
            close(con);
            deleteFile(tmp);
        }
    }

    public void delete(String shortId) {
        Object con = null;
        try {
            con = connect();
            Object usr = newElement(con);
            setField(usr, "m_elementId", fullId(shortId));

            Object ret = invoke(usr, "delete", NO_ARGS, new Object[0]);
            checkResult(usr, ret, "delete");
        } catch (XvarmException e) {
            throw e;
        } catch (Exception e) {
            throw new XvarmException("delete 호출 오류", unwrap(e));
        } finally {
            close(con);
        }
    }

    /** 조회, 삭제용 전체 ID = {GATEWAY}::{shortId}::{E-Class} (E-Class 는 등록 때와 같아야 한다) */
    private String fullId(String shortId) {
        return prop.getGateway() + "::" + shortId + "::" + prop.getElementClass();
    }

    /** new asysConnectData(ip, port, desc, user, password) */
    private Object connect() throws Exception {
        Class<?> cls = Class.forName(CONNECT, true, loader);
        Constructor<?> c = cls.getConstructor(new Class<?>[] { String.class, int.class, String.class, String.class, String.class });
        return c.newInstance(new Object[] { prop.getIp(), Integer.valueOf(prop.getPort()), prop.getDesc(), prop.getUser(), prop.getPassword() });
    }

    /** new asysUsrElement(con) — 생성자 인자 타입(asysConnectBase)은 접속 객체의 상위 타입이라 맞는 것을 찾는다 */
    private Object newElement(Object con) throws Exception {
        Constructor<?>[] list = Class.forName(ELEMENT, true, loader).getConstructors();
        for (int i = 0; i < list.length; i++) {
            Class<?>[] types = list[i].getParameterTypes();
            if (types.length == 1 && types[0].isInstance(con)) {
                return list[i].newInstance(new Object[] { con });
            }
        }
        throw new IllegalStateException("asysUsrElement(접속) 생성자를 찾을 수 없습니다 — jar 버전 확인");
    }

    private static void setField(Object obj, String name, Object value) throws Exception {
        obj.getClass().getField(name).set(obj, value);
    }

    private static Object invoke(Object obj, String name, Class<?>[] types, Object[] args) throws Exception {
        return obj.getClass().getMethod(name, types).invoke(obj, args);
    }

    /** 결과코드 0 = 성공, 그 밖은 getLastError() 를 붙여 예외 */
    private static void checkResult(Object usr, Object ret, String op) throws Exception {
        int code = ((Integer) ret).intValue();
        if (code != 0) {
            throw new XvarmException(code, op + " 실패: " + invoke(usr, "getLastError", NO_ARGS, new Object[0]));
        }
    }

    private static void close(Object con) {
        if (con == null) {
            return;
        }
        try {
            invoke(con, "close", NO_ARGS, new Object[0]);
        } catch (Exception ignore) {
            // 종료 실패는 결과에 영향 없음
        }
    }

    private static Throwable unwrap(Exception e) {
        if (e instanceof InvocationTargetException && e.getCause() != null) {
            return e.getCause();
        }
        return e;
    }

    private static void writeFile(File f, byte[] data) throws IOException {
        OutputStream out = new FileOutputStream(f);
        try {
            out.write(data);
        } finally {
            out.close();
        }
    }

    private static byte[] readFile(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream((int) f.length());
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } finally {
            in.close();
        }
    }

    private static void deleteFile(File f) {
        if (f != null && f.exists()) {
            f.delete();
        }
    }
}
